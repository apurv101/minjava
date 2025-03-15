package LSHFIndex;

import global.*;
import heap.*;
import diskmgr.*;
import index.IndexException;
import index.NNIndexScan;
import index.RSIndexScan;
import iterator.Iterator;
import iterator.FldSpec;
import iterator.RelSpec;
import iterator.CondExpr;

import java.io.*;
import java.util.*;

/**
 * Example "query" driver program for Task 8.
 *
 * Usage:
 *   java LSHFIndex.Query DBNAME QSNAME INDEXOPTION NUMBUF
 *
 * Where:
 *   DBNAME       = existing database name
 *   QSNAME       = file containing one query line, e.g. Range(2, myTargetVector.txt, 10, 1 2 3)
 *                  or NN(2, myTargetVector.txt, 5, 1 3)
 *   INDEXOPTION  = "Y" or "N" (use LSH-forest index or not)
 *   NUMBUF       = max number of buffer pages
 */
public class Query {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: java LSHFIndex.Query DBNAME QSNAME INDEXOPTION NUMBUF");
            System.exit(1);
        }

        String dbName       = args[0];
        String qsName       = args[1];
        String indexOption  = args[2];  // "Y" or "N"
        int    numBuf       = Integer.parseInt(args[3]);

        // Initialize disk/page counters so we can report reads/writes after.
        PCounter.initialize();

        // Open the existing DB (not overwriting).
        SystemDefs sysdef = null;
        try {
            // Example: pass zero pages to open existing DB; or set 4096 for page size, etc.
            sysdef = new SystemDefs(dbName, 4096, numBuf, "Clock");
        } catch (Exception e) {
            System.err.println("Could not open existing DB: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        QuerySpec querySpec = null;
        try {
            querySpec = parseQuerySpecFile(qsName);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Now run the query
        try {
            runQuery(querySpec, dbName, indexOption.equalsIgnoreCase("Y"), numBuf);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Print out disk I/O counters
        System.out.println("\nDisk pages read   : " + PCounter.rcounter);
        System.out.println("Disk pages written: " + PCounter.wcounter);
    }

    /**
     * Runs either a Range or NN query depending on QuerySpec contents.
     */
    private static void runQuery(QuerySpec spec, String dbName, boolean useIndex, int numBuf)
        throws Exception
    {
        // The underlying table (Heapfile) presumably from batch insertion.
        String heapFileName = "batch_insert_output.in";
        Heapfile heap = new Heapfile(heapFileName);

        // The user gave us spec.queryAttrNum (1-based).
        int queryAttrIndex = spec.queryAttrNum;

        if (spec.isRangeQuery) {
            int distance = spec.rangeOrK;
            System.out.println("Running RANGE query on attribute #" + queryAttrIndex
                               + " with distance <= " + distance);

            if (useIndex) {
                int h = 4;
                int L = 3;
                String indexFileName = dbName + "_" + queryAttrIndex + "_" + h + "_" + L;

                AttrType[] inTypes = new AttrType[1];
                inTypes[0] = new AttrType(AttrType.attrVector100D);
                short[] str_sizes = new short[0];
                FldSpec[] proj = new FldSpec[1];
                proj[0] = new FldSpec(new RelSpec(RelSpec.outer), 1);

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
                    1,   // which field to query
                    spec.targetVector,
                    distance
                );

                dumpQueryResults(scan, heap, spec.outputFields);
                scan.close();
            }
            else {
                // No index => full scan
                fullScanRange(heap, queryAttrIndex, spec.targetVector, distance, spec.outputFields);
            }
        }
        else {
            // NN query
            int k = spec.rangeOrK;
            System.out.println("Running NN query on attribute #" + queryAttrIndex + " with k=" + k);

            if (useIndex) {
                int h = 4;
                int L = 3;
                String indexFileName = dbName + "_" + queryAttrIndex + "_" + h + "_" + L;

                AttrType[] inTypes = new AttrType[1];
                inTypes[0] = new AttrType(AttrType.attrVector100D);
                short[] str_sizes = new short[0];
                FldSpec[] proj = new FldSpec[1];
                proj[0] = new FldSpec(new RelSpec(RelSpec.outer), 1);

                NNIndexScan scan = new NNIndexScan(
                    new IndexType(IndexType.LSHFIndex),
                    heapFileName,
                    indexFileName,
                    inTypes,
                    str_sizes,
                    1,   // noInFlds
                    1,   // noOutFlds
                    proj,
                    null,
                    1,   // field # for the vector
                    spec.targetVector,
                    k
                );

                dumpQueryResults(scan, heap, spec.outputFields);
                scan.close();
            }
            else {
                // No index => compute distances & sort
                fullScanNN(heap, queryAttrIndex, spec.targetVector, k, spec.outputFields);
            }
        }
    }

    /**
     * For Range or NN index scans, we get a minimal (vector, pageNo) tuple.
     */
    private static void dumpQueryResults(Iterator scan, Heapfile heap, List<Integer> outFields)
            throws Exception
    {
        System.out.println("\n--- Query Results ---\n");
        Tuple t = null;
        while ((t = scan.get_next()) != null) {
            // Field1 => vector, Field2 => pageNo
            Vector100Dtype foundVect = t.get100DVectFld(1);
            int pageNoPid = t.getIntFld(2);

            // Demo approach: we only have pageNo, no slotNo. A real approach would store the full RID.
            System.out.println("Found record: vector[0] = " + foundVect.getValue(0)
                               + "; pageNo=" + pageNoPid);
        }
    }

    /** 
     * If no index is used, do a full file scan, check distance, output matches.
     */
    private static void fullScanRange(Heapfile heap,
                                      int vectFieldNo,
                                      Vector100Dtype target,
                                      int distance,
                                      List<Integer> outFields)
        throws Exception
    {
        System.out.println("Doing full-scan range, dist<=" + distance + ", on field #" + vectFieldNo);
        Scan scan = heap.openScan();
        RID rid = new RID();
        Tuple t;
        int count = 0;

        while ((t = scan.getNext(rid)) != null) {
            // If needed, setHdr(...) for your schema
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

    /**
     * If no index is used, do full scan, store distance, sort, return top-k/all.
     */
    private static void fullScanNN(Heapfile heap,
                                   int vectFieldNo,
                                   Vector100Dtype target,
                                   int k,
                                   List<Integer> outFields)
        throws Exception
    {
        System.out.println("Doing full-scan NN, top K=" + k + ", on field #" + vectFieldNo);
        Scan scan = heap.openScan();
        RID rid = new RID();
        Tuple t;

        List<NNEntry> entries = new ArrayList<>();

        while ((t = scan.getNext(rid)) != null) {
            Vector100Dtype v = t.get100DVectFld(vectFieldNo);
            int dist = computeDistance(v, target);
            // Make a copy so we can read fields after the scan:
            Tuple tcopy = new Tuple(t);
            entries.add(new NNEntry(dist, tcopy, rid.pageNo.pid, rid.slotNo));
        }
        scan.closescan();

        // Sort ascending by distance
        entries.sort(Comparator.comparingInt(e -> e.dist));

        int end = (k == 0 || k > entries.size()) ? entries.size() : k;
        System.out.println("Full-scan NN found " + entries.size() + " total, returning " + end);

        for (int i = 0; i < end; i++) {
            NNEntry e = entries.get(i);
            Vector100Dtype vect = e.tuple.get100DVectFld(vectFieldNo);
            System.out.println("Rank #"+(i+1)
                               + " dist=" + e.dist
                               + " vector[0]=" + vect.getValue(0)
                               + " RID(page="+ e.pageNo +", slot="+ e.slotNo +")");
        }
    }

    private static int computeDistance(Vector100Dtype v1, Vector100Dtype v2) {
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

    // ---------------------- PARSING QUERY FILES ----------------------

    /**
     * Reads exactly one line from QSNAME, e.g.:
     *   Range(2, target.txt, 10, 1, 2, 3)
     * or
     *   NN(2, target.txt, 5, 1,2)
     */
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
        // e.g. "2, target1, 1, 1,2"
        String inside = line.substring(idx1 + 1, idx2).trim();

        // We'll split by comma for first 3 items: [queryAttr, vectorFile, distance], then remainder
        String[] parts = inside.split(",", 4);
        if (parts.length < 3) {
            throw new IOException("Not enough arguments in Range(...)");
        }

        int queryAttr = Integer.parseInt(parts[0].trim()); 
        String vectorFile = parts[1].trim();
        int distance = Integer.parseInt(parts[2].trim());

        List<Integer> outFields = new ArrayList<>();
        if (parts.length == 4) {
            // parts[3] might look like " 1, 2 , 3"
            // remove all commas:
            String lastPart = parts[3].trim().replaceAll(",", " ");
            // now split on whitespace
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
        // e.g. "2, target1, 5, 1, 2"
        String inside = line.substring(idx1 + 1, idx2).trim();

        String[] parts = inside.split(",", 4);
        if (parts.length < 3) {
            throw new IOException("Not enough arguments in NN(...)");
        }

        int queryAttr = Integer.parseInt(parts[0].trim());
        String vectorFile = parts[1].trim();
        int k = Integer.parseInt(parts[2].trim());

        List<Integer> outFields = new ArrayList<>();
        if (parts.length == 4) {
            // remove commas from the last chunk
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

    /**
     * Reads exactly 100 integers from the given file into a Vector100Dtype.
     */
    private static Vector100Dtype readVectorFile(String filename) throws IOException {
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
