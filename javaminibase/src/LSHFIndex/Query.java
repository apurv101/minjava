package LSHFIndex;

import global.*;
import heap.*;
import diskmgr.*;
import index.NNIndexScan;
import index.RSIndexScan;
import iterator.Iterator;
import iterator.FldSpec;
import iterator.RelSpec;
import iterator.Sort;


import java.io.*;
import java.util.*;


public class Query {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: java LSHFIndex.Query DBNAME QSNAME INDEXOPTION NUMBUF");
            System.exit(1);
        }

        String dbName       = args[0];
        String qsName       = args[1];
        String indexOption  = args[2];  // Y/N
        int    numBuf       = Integer.parseInt(args[3]);

        // Initialize disk/page counters.
        PCounter.initialize();

        // Open the existing DB
        SystemDefs sysdef = null;
        try {
            sysdef = new SystemDefs(dbName, 0, numBuf, "Clock");
        } catch (Exception e) {
            System.err.println("Could not open existing DB: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Parse the query
        QuerySpec querySpec = null;
        try {
            querySpec = parseQuerySpecFile(qsName);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }


        try {
            runQuery(querySpec, dbName, indexOption.equalsIgnoreCase("Y"), numBuf);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Print out disk I/O
        System.out.println("\nDisk pages read   : " + PCounter.rcounter);
        System.out.println("Disk pages written: " + PCounter.wcounter);
    }

    private static void runQuery(QuerySpec spec, String dbName, boolean useIndex, int numBuf)
        throws Exception
    {
        String heapFileName = "batch_insert_output.in";
        Heapfile heap = new Heapfile(heapFileName);

        int queryAttrIndex = spec.queryAttrNum;
        AttrType[] attrTypes = new AttrType[] {
                new AttrType(AttrType.attrInteger),
                new AttrType(AttrType.attrReal),
                new AttrType(AttrType.attrString),
                new AttrType(AttrType.attrVector100D)
        };
        // Define string sizes if there are any string attributes
        short[] strSizes = new short[] { 30 };
        // fullScanNN()
        if (spec.isRangeQuery) {
            int distance = spec.rangeOrK;
            System.out.println("Running RANGE query on attribute #" + queryAttrIndex
                               + " with distance <= " + distance);
            if (useIndex) {
                int h = 10;
                int L = 5;
                String indexFileName = dbName + "_" + queryAttrIndex + "_" + h + "_" + L;
                System.out.println("DEBUG: Query Attr Index = " + queryAttrIndex);
                System.out.println("DEBUG: Expected Index File = " + indexFileName);
                // Load the index from the file
                LSHFIndex index = new LSHFIndex(h, L);
                try {
                    index.loadIndexFromFile(indexFileName);
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Failed to load index from file: " + indexFileName);
                    e.printStackTrace();
                    return;  // Stop execution if index loading fails
                }

                AttrType[] inTypes = { new AttrType(AttrType.attrVector100D) };
                short[] str_sizes = new short[0];
                FldSpec[] proj = { new FldSpec(new RelSpec(RelSpec.outer), 1) };

                RSIndexScan scan = new RSIndexScan(
                        new IndexType(IndexType.LSHFIndex),
                        heapFileName,
                        indexFileName,
                        inTypes,
                        str_sizes,
                        1,   // noInFlds
                        1,   // noOutFlds
                        proj,
                        null,
                        1,
                        spec.targetVector,
                        distance
                );

                dumpQueryResults(scan, heap, spec.outputFields);
                scan.close();
            }
            else {
                fullScanRange(heap, queryAttrIndex, spec.targetVector, distance, spec.outputFields,attrTypes,strSizes);
            }
        }
        else {
            // NN query
            int k = spec.rangeOrK;
            System.out.println("Running NN query on attribute #" + queryAttrIndex + " with k=" + k);

            if (useIndex) {
                System.out.println("running query with index");
                int h = 10;
                int L = 5;
                String indexFileName = dbName + "_" + queryAttrIndex + "_" + h + "_" + L;

                AttrType[] inTypes = { new AttrType(AttrType.attrVector100D) };
                short[] str_sizes = new short[0];
                FldSpec[] proj = { new FldSpec(new RelSpec(RelSpec.outer), 1) };

                NNIndexScan scan = new NNIndexScan(
                    new IndexType(IndexType.LSHFIndex),
                    heapFileName,
                    indexFileName,
                    inTypes,
                    str_sizes,
                    1,
                    1,
                    proj,
                    null,
                    1,
                    spec.targetVector,
                    k
                );

                dumpQueryResults(scan, heap, spec.outputFields);
                scan.close();
            }
            else {
                fullScanNN(heap, queryAttrIndex, spec.targetVector, k, spec.outputFields,attrTypes,strSizes);
            }
        }
    }


    private static void dumpQueryResults(Iterator scan, Heapfile heap, List<Integer> outFields)
        throws Exception
    {
        System.out.println("\n--- Query Results ---\n");
        Tuple t;
        while ((t = scan.get_next()) != null) {
            Vector100Dtype foundVect = t.get100DVectFld(1);
            int pageNoPid = t.getIntFld(2);
            System.out.println("Found record: vector[0] = " + foundVect.getValue(0)
                               + "; pageNo=" + pageNoPid);
        }
    }

    private static void fullScanRange(Heapfile heap,
                                      int vectFieldNo,
                                      Vector100Dtype target,
                                      int distance,
                                      List<Integer> outFields,
                                      AttrType[] attrTypes,
                                      short[] strSizes)
        throws Exception
    {
        System.out.println("Doing full-scan range, dist<=" + distance + ", on field #" + vectFieldNo);

        Scan scan = heap.openScan();
        RID rid = new RID();
        Tuple t;
        int count = 0;

        while ((t = scan.getNext(rid)) != null) {

            t.setHdr((short) attrTypes.length, attrTypes, strSizes);
            // If needed, setHdr(...) for your known schema
            Vector100Dtype v = t.get100DVectFld(vectFieldNo);
            int dist = computeDistance(v, target);
            if (dist <= distance) {
                count++;
                System.out.println("Match (dist=" + dist + "): vector[0] = " + v.getValue(0)
                                   + " -> RID = " + rid);
            }
        }
        scan.closescan();
        System.out.println("Full-scan range found " + count + " matching records.");
    }

    private static void fullScanNN(Heapfile heap,
                                   int vectFieldNo,
                                   Vector100Dtype target,
                                   int k,
                                   List<Integer> outFields,
                                   AttrType[] attrTypes,
                                   short[] strSizes) // <-- Pass schema here
            throws Exception
    {
        System.out.println("Doing full-scan NN, top K=" + k + ", on field #" + vectFieldNo);
        Scan scan = heap.openScan();
        RID rid = new RID();
        Tuple t;

        List<NNEntry> entries = new ArrayList<>();

        while ((t = scan.getNext(rid)) != null) {

            // Set tuple header using schema passed as argument
            try {
                t.setHdr((short) attrTypes.length, attrTypes, strSizes);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            // Check if tuple actually has fields
            if (t.noOfFlds() == 0) {
                System.out.println("ERROR: Tuple has no fields, skipping.");
                continue;
            }
            // Validate requested field number
            if (vectFieldNo < 1 || vectFieldNo > t.noOfFlds()) {
                System.out.println("ERROR: Requested field #" + vectFieldNo + " but tuple only has " + t.noOfFlds() + " fields.");
                continue;
            }
            System.out.println("DEBUG: Tuple Field Count = " + t.noOfFlds());
            System.out.println("DEBUG: Expected Field Offsets = " + Arrays.toString(t.fldOffset));
            System.out.println("DEBUG: Tuple Length = " + t.getLength());

            try {
                Vector100Dtype v = t.get100DVectFld(vectFieldNo);
                int dist = computeDistance(v, target);
                Tuple tcopy = new Tuple(t);
                entries.add(new NNEntry(dist, tcopy, rid.pageNo.pid, rid.slotNo));
            } catch (Exception e) {
                System.out.println("ERROR: Failed to read tuple field #" + vectFieldNo);
                e.printStackTrace();
                continue;
            }
        }

        scan.closescan();
        Iterator tupleIterator = new Iterator() {
            private int index = 0;
            public Tuple get_next() {
                return (index < entries.size()) ? entries.get(index++).tuple : null;
            }
            public void close() {}
        };
        if (attrTypes[vectFieldNo - 1].attrType == AttrType.attrVector100D) {
            // Use the new top‑k vector sort
            Sort sortedIterator = new Sort(
                    attrTypes,
                    (short) attrTypes.length,
                    strSizes,
                    tupleIterator,
                    vectFieldNo,
                    new TupleOrder(TupleOrder.Ascending),
                    200,      // sort field length for vector (if applicable)
                    10,       // number of pages (or appropriate value)
                    target,
                    k
            );
            Tuple sortedTuple;
            int rank = 1;
            while ((sortedTuple = sortedIterator.get_next()) != null) {
                Vector100Dtype vect = sortedTuple.get100DVectFld(vectFieldNo);
                System.out.println("Rank #" + rank++ + " vector[0]=" + vect.getValue(0));
            }
            // Process sortedIterator…
        } else {
            // Use the regular sort for non‑vector attributes
            Sort sortedIterator = new Sort(
                    attrTypes,
                    (short) attrTypes.length,
                    strSizes,
                    tupleIterator,
                    vectFieldNo,
                    new TupleOrder(TupleOrder.Ascending),
                    30,
                    10
            );
            Tuple sortedTuple;
            int rank = 1;
            while ((sortedTuple = sortedIterator.get_next()) != null) {
                Vector100Dtype vect = sortedTuple.get100DVectFld(vectFieldNo);
                System.out.println("Rank #" + rank++ + " vector[0]=" + vect.getValue(0));
            }

        }

    }

    public static int computeDistance(Vector100Dtype v1, Vector100Dtype v2) {
        float sumSq = 0;
        for (int i = 0; i < 100; i++) {
            int diff = v1.getValue(i) - v2.getValue(i);
            sumSq += diff * diff;
        }
        return (int)Math.sqrt(sumSq);
    }

    private static class NNEntry {
        int dist;
        Tuple tuple;
        int pageNo;
        int slotNo;
        NNEntry(int d, Tuple t, int p, int s){
            dist = d;
            tuple = t;
            pageNo = p;
            slotNo = s;
        }
    }

    private static QuerySpec parseQuerySpecFile(String qsName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(qsName))) {
            String line = br.readLine();
            if (line == null) {
                throw new IOException("Empty query spec file: " + qsName);
            }
            line = line.trim();

            if (line.startsWith("Range(")) {
                return parseRange(line);
            } else if (line.startsWith("NN(")) {
                return parseNN(line);
            } else {
                throw new IOException("Invalid query line: " + line);
            }
        }
    }

    private static QuerySpec parseRange(String line) throws IOException {
        int idx1 = line.indexOf("(");
        int idx2 = line.lastIndexOf(")");
        if (idx1 < 0 || idx2 < 0) {
            throw new IOException("Malformed Range(...) syntax.");
        }
        String inside = line.substring(idx1+1, idx2).trim();

        String[] parts = inside.split(",", 4);
        if (parts.length < 3) {
            throw new IOException("Not enough args in Range(...)");
        }

        int queryAttr = Integer.parseInt(parts[0].trim()); 
        String vectorFile = parts[1].trim();
        int distance = Integer.parseInt(parts[2].trim());

        List<Integer> outFields = new ArrayList<>();
        if (parts.length == 4) {
            // remove commas regex
            String lastPart = parts[3].trim().replaceAll(",", " ");
            String[] fs = lastPart.split("\\s+");
            for (String f : fs) {
                outFields.add(Integer.valueOf(f));
            }
        }

        Vector100Dtype vect = readVectorFile(vectorFile);
        QuerySpec spec = new QuerySpec();
        spec.isRangeQuery = true;
        spec.queryAttrNum = queryAttr;
        spec.targetVector = vect;
        spec.rangeOrK     = distance;
        spec.outputFields = outFields;
        return spec;
    }

    private static QuerySpec parseNN(String line) throws IOException {
        int idx1 = line.indexOf("(");
        int idx2 = line.lastIndexOf(")");
        if (idx1 < 0 || idx2 < 0) {
            throw new IOException("Malformed NN(...) syntax.");
        }
        String inside = line.substring(idx1+1, idx2).trim();

        String[] parts = inside.split(",", 4);
        if (parts.length < 3) {
            throw new IOException("Not enough args in NN(...)");
        }

        int queryAttr = Integer.parseInt(parts[0].trim());
        String vectorFile = parts[1].trim();
        int k = Integer.parseInt(parts[2].trim());

        List<Integer> outFields = new ArrayList<>();
        if (parts.length == 4) {
            String lastPart = parts[3].trim().replaceAll(",", " ");
            String[] fs = lastPart.split("\\s+");
            for (String f : fs) {
                outFields.add(Integer.valueOf(f));
            }
        }

        Vector100Dtype vect = readVectorFile(vectorFile);

        QuerySpec spec = new QuerySpec();
        spec.isRangeQuery = false;
        spec.queryAttrNum = queryAttr;
        spec.targetVector = vect;
        spec.rangeOrK     = k;
        spec.outputFields = outFields;
        return spec;
    }


    private static Vector100Dtype readVectorFile(String filename) throws IOException {
        filename = "sample_data/" +filename;
        // 1) Attempt direct file
        File f = new File(filename);
        if (!f.exists()) {
            // 2) If not found, try appending .txt
            File f2 = new File(filename + ".txt");
            if (f2.exists()) {
                filename = filename + ".txt";
            } else {
                throw new IOException("Could not find file '" + filename
                                      + "' or '" + filename + ".txt'");
            }
        }

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            if (line == null) {
                throw new IOException("Vector file is empty: " + filename);
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length != 100) {
                throw new IOException("Expected 100 integers in vector file, found " + tokens.length);
            }
            short[] arr = new short[100];
            for (int i = 0; i < 100; i++) {
                arr[i] = Short.parseShort(tokens[i]);
            }
            return new Vector100Dtype(arr);
        }
    }

    private static class QuerySpec {
        boolean isRangeQuery;
        int queryAttrNum;
        Vector100Dtype targetVector;
        int rangeOrK;
        List<Integer> outputFields;
    }

}
