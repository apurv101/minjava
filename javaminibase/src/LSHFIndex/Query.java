package LSHFIndex;

import global.*;
import heap.*;
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

        // Open the existing DB; do not overwrite. 
        //   Typically you'd do something like new SystemDefs(dbName, <num_pages>, <num_buffers>, "<replacer>")
        //   but you need to be sure not to re-create the DB file. 
        //   If your SystemDefs constructor overwrites the DB by default, 
        //   you might need a custom "open existing" constructor. 
        // For demonstration, assume we can do something like:
        //   SystemDefs sysdef = new SystemDefs(dbName, 0, 0, "Clock", false /*openExisting*/);
        // The details differ depending on how your code is structured. Adjust as needed.
        // ----------------------------------------------------------------------
        SystemDefs sysdef = null;
        try {
            // Example: passing zero pages might open the DB if it already exists
            // and using 4096 page size. 
            // Or pass enough pages so we won't break things. Adjust for your project:
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
        // The underlying table (Heapfile) is presumably the same used in batch insertion.
        // If you used "batch_insert_output.in" or some table name in BatchInsert, match that:
        String heapFileName = "batch_insert_output.in";
        Heapfile heap = new Heapfile(heapFileName);

        // If multiple vector columns, we must figure out which attribute # is the query attribute.
        // The user gave us spec.queryAttrNum (1-based).
        int queryAttrIndex = spec.queryAttrNum;

        // We'll read the target vector from spec.targetVector (already parsed).
        // The user might want to output certain fields, in spec.outputFields.

        if (spec.isRangeQuery) {
            int distance = spec.rangeOrK;
            System.out.println("Running RANGE query on attribute #"+queryAttrIndex
                               +" with distance <= " + distance);

            if (useIndex) {
                // Use RSIndexScan. We need the LSHFIndex file name:
                // By Task 2/5 instructions, index is named: DBNAME_attrNo_h_L. 
                // But we need to know h, L from the user or store them. 
                // If you do not store them, either guess or loop over possibilities. 
                // For demonstration, let's guess h=4, L=3:
                int h = 4; 
                int L = 3;
                String indexFileName = dbName + "_" + (queryAttrIndex) + "_" + h + "_" + L;

                // RSIndexScan wants:
                //   new RSIndexScan(IndexType, relName, indexName, 
                //       AttrType[], short[] str_sizes, 
                //       int noInFlds, int noOutFlds, FldSpec[] outFlds,
                //       CondExpr[] selects, int fldNum,
                //       Vector100Dtype query, int distance)
                //
                // For simplicity, assume the table has N attributes. We'll do a 1-attr schema if only the vector matters:
                AttrType[] inTypes = new AttrType[1];
                inTypes[0] = new AttrType(AttrType.attrVector100D); 
                short[]    str_sizes = new short[0];
                FldSpec[]  proj = new FldSpec[1];
                proj[0] = new FldSpec(new RelSpec(RelSpec.outer), 1);

                RSIndexScan scan = new RSIndexScan(
                    new IndexType(IndexType.LSHFIndex),
                    heapFileName,            // relName 
                    indexFileName,           // the on-disk index file
                    inTypes, 
                    str_sizes, 
                    1,  // noInFlds
                    1,  // noOutFlds
                    proj,
                    null, // no extra conditions
                    1,    // which field to apply index on
                    spec.targetVector,
                    distance
                );

                dumpQueryResults(scan, heap, spec.outputFields);
                scan.close();
            } 
            else {
                // No index => do a full scan, check distance for each record
                fullScanRange(heap, queryAttrIndex, spec.targetVector, distance, spec.outputFields);
            }
        }
        else {
            // NN query
            int k = spec.rangeOrK; 
            System.out.println("Running NN query on attribute #"+queryAttrIndex
                               +" with k="+k);

            if (useIndex) {
                // Use NNIndexScan 
                int h = 4;
                int L = 3;
                String indexFileName = dbName + "_" + (queryAttrIndex) + "_" + h + "_" + L;

                AttrType[] inTypes = new AttrType[1];
                inTypes[0] = new AttrType(AttrType.attrVector100D); 
                short[]    str_sizes = new short[0];
                FldSpec[]  proj = new FldSpec[1];
                proj[0] = new FldSpec(new RelSpec(RelSpec.outer), 1);

                NNIndexScan scan = new NNIndexScan(
                    new IndexType(IndexType.LSHFIndex),
                    heapFileName,
                    indexFileName,
                    inTypes,
                    str_sizes,
                    1,  // noInFlds
                    1,  // noOutFlds
                    proj,
                    null,
                    1,  // field number
                    spec.targetVector,
                    k
                );

                dumpQueryResults(scan, heap, spec.outputFields);
                scan.close();
            }
            else {
                // Full scan, compute distance from spec.targetVector for each record, store them,
                // then sort by distance, then output top-k (if k>0) or all if k=0
                fullScanNN(heap, queryAttrIndex, spec.targetVector, k, spec.outputFields);
            }
        }
    }

    /**
     * Helper: for Range or NN scans (with an index), the "get_next()" call
     * returns a simplified tuple that just has (vector, pageNo).  
     * In reality, you want to re-fetch the *actual* record from the heap
     * so that you can retrieve all needed output fields. 
     *
     * Below is a simple demonstration that re-fetches from the heap using
     * the returned pageNo as a partial key.  You might store the full RID, 
     * or store pageNo+slotNo, etc.  Adjust to match your design.
     */
    private static void dumpQueryResults(Iterator scan, Heapfile heap, List<Integer> outFields)
            throws Exception
    {
        System.out.println("\n--- Query Results ---\n");
        Tuple t = null;
        while ((t = scan.get_next()) != null) {
            // We expect the tuple to have the vector in field 1, rid.pageNo in field 2
            // Then re-fetch from the heap if needed
            //   In your code, you might store the entire RID, etc. 
            Vector100Dtype foundVect = t.get100DVectFld(1);
            int pageNoPid = t.getIntFld(2);

            // Demo approach: we only have pageNo, not slotNo, so let's do a 
            // naive approach that might not be correct in real usage:
            //    for each record in that page, see if the vector matches?
            //    This is a simplified example, so you should adapt it 
            //    to match your actual index record layout (which hopefully stores RID fully).
            // 
            // We'll just print the vector's first dimension for demonstration:
            System.out.println("Found record: vector[0] = " + foundVect.getValue(0)
                               + "; pageNo=" + pageNoPid);
            // If you want to fetch all "output fields" from the actual record in the table, 
            // you'd do something like:
            //   - parse the table schema
            //   - do a normal Heapfile Scan
            //   - match the actual rid / or check if same vector
            //   - then parse fields
            //   - print them. 
            // 
            // This is purely for demonstration of concept.
        }
    }


    /**
     * If no index is used, we do a full file scan and check distance from each record's
     * vector field.  If distance <= D, we output it.
     */
    private static void fullScanRange(Heapfile heap, int vectFieldNo, Vector100Dtype target, int distance, List<Integer> outFields)
        throws Exception
    {
        System.out.println("Doing full-scan range, dist<="+distance+", on field #"+vectFieldNo);
        Scan scan = heap.openScan();
        RID rid = new RID();
        Tuple t;
        int count = 0;
        while ((t = scan.getNext(rid)) != null) {
            // We need to parse the record's schema.  
            // If your table has multiple fields (some int, some string, etc.) 
            // you must setHdr on the tuple so that get100DVectFld works. 
            // For demonstration, let's guess the table has all the same schema used in BatchInsert 
            // and we can do: t.setHdr(...) with the correct AttrType array. 
            // 
            // For simplicity, assume we know the field is attrVector100D in 'vectFieldNo'.
            // Then compute the distance:
            Vector100Dtype v = t.get100DVectFld(vectFieldNo);
            int dist = computeDistance(v, target);
            if (dist <= distance) {
                count++;
                // Print or store the needed output fields
                // For demonstration, print the first dimension of the vector:
                System.out.println("Match (dist="+dist+"): vector[0] = " + v.getValue(0)
                                   + " -> RID = " + rid);
            }
        }
        scan.closescan();
        System.out.println("Full-scan range found " + count + " matching records.");
    }

    /**
     * If no index is used, do a full file scan, compute distance for each record,
     * store them in a list, sort by ascending distance, then either 
     * return the top K or all if K=0.
     */
    private static void fullScanNN(Heapfile heap, int vectFieldNo, Vector100Dtype target, int k, List<Integer> outFields)
        throws Exception
    {
        System.out.println("Doing full-scan NN, top K="+k+", on field #"+vectFieldNo);
        Scan scan = heap.openScan();
        RID rid = new RID();
        Tuple t;
        // We'll store <distance, copyOfTuple, rid> in a list
        ArrayList<NNEntry> entries = new ArrayList<>();
        while ((t = scan.getNext(rid)) != null) {
            Vector100Dtype v = t.get100DVectFld(vectFieldNo);
            int dist = computeDistance(v, target);
            // We must make a copy of the tuple if we need to keep it, because getNext reuses the same memory
            Tuple tcopy = new Tuple(t.returnTupleByteArray(), 0, t.getLength());
            tcopy.setHdr(t.getFldCnt(), t.getTypes(), t.getStrSizes());
            entries.add(new NNEntry(dist, tcopy, rid.pageNo.pid, rid.slotNo));
        }
        scan.closescan();

        // Sort by ascending dist
        entries.sort(Comparator.comparingInt(e -> e.dist));

        // If k=0 => all; else top k
        int end = (k==0 || k>entries.size()) ? entries.size() : k;
        System.out.println("Full-scan NN found " + entries.size() + " total, returning " + end);
        for (int i=0; i<end; i++) {
            NNEntry e = entries.get(i);
            // Print something.  You might want to do a real schema parse
            System.out.println("Rank #"+(i+1)+" dist="+ e.dist
                               + " vector[0]=" + e.tuple.get100DVectFld(vectFieldNo).getValue(0)
                               + " RID(page="+e.pageNo+", slot="+e.slotNo+")");
        }
    }

    // Quick helper for computing Euclidean distance
    private static int computeDistance(Vector100Dtype v1, Vector100Dtype v2) {
        float dist = 0;
        for (int i = 0; i < 100; i++) {
            int diff = v1.getValue(i) - v2.getValue(i);
            dist += diff * diff;
        }
        return (int)Math.sqrt(dist);
    }

    /**
     * Simple struct-like class for storing info in the no-index NN scan.
     */
    private static class NNEntry {
        int dist;
        Tuple tuple;
        int pageNo;
        int slotNo;
        NNEntry(int d, Tuple t, int p, int s){
            dist=d; tuple=t; pageNo=p; slotNo=s;
        }
    }

    /**
     * Parses the query specification file.  Expects exactly one line with either:
     *   Range(QA, T, D, outFields...)
     * or
     *   NN(QA, T, K, outFields...)
     * 
     * Where:
     *   QA = integer (1-based attribute #)
     *   T  = path to a file containing 100 integers for the target vector
     *   D/K= non-negative integer (distance or number of nearest neighbors)
     *   outFields... = one or more integer field numbers to output
     */
    private static QuerySpec parseQuerySpecFile(String qsName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(qsName))) {
            String line = br.readLine();
            if (line == null) {
                throw new IOException("Empty query spec file: " + qsName);
            }
            line = line.trim();

            // We expect something like: Range(2, myVector.txt, 10, 1 2 3)
            // or NN(2, myVector.txt, 5, 1 2 3)
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
        // e.g. "Range(QA, T, D, f1 f2 f3...)"
        // Strip "Range(" at the front and ")" at the end
        int idx1 = line.indexOf("(");
        int idx2 = line.lastIndexOf(")");
        if (idx1 < 0 || idx2 < 0) {
            throw new IOException("Malformed Range(...) syntax.");
        }
        String inside = line.substring(idx1+1, idx2).trim();
        // inside => e.g. "2, target.txt, 10, 1 2 3"

        // We'll split by commas for the first 3, then for the last we split by whitespace
        String[] parts = inside.split(",", 4);
        if (parts.length < 3) {
            throw new IOException("Not enough fields in Range(...)");
        }
        // parts[0] = "2"
        // parts[1] = " target.txt"
        // parts[2] = " 10"
        // parts[3] = " 1 2 3"   (possibly; or if there's no final part, we handle it)
        int queryAttr = Integer.parseInt(parts[0].trim());
        String vectorFile = parts[1].trim();
        int distance = Integer.parseInt(parts[2].trim());

        List<Integer> outFields = new ArrayList<>();
        if (parts.length == 4) {
            // parse outFields from the last part, which might look like "1 2 3"
            String[] fs = parts[3].trim().split("\\s+");
            for (String f : fs) {
                outFields.add(Integer.valueOf(f));
            }
        }

        // read the target vector from the file
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
        // e.g. "NN(QA, T, K, f1 f2...)"
        int idx1 = line.indexOf("(");
        int idx2 = line.lastIndexOf(")");
        if (idx1 < 0 || idx2 < 0) {
            throw new IOException("Malformed NN(...) syntax.");
        }
        String inside = line.substring(idx1+1, idx2).trim();
        // inside => e.g. "2, target.txt, 5, 1 2 3"

        String[] parts = inside.split(",", 4);
        if (parts.length < 3) {
            throw new IOException("Not enough fields in NN(...)");
        }
        int queryAttr = Integer.parseInt(parts[0].trim());
        String vectorFile = parts[1].trim();
        int k = Integer.parseInt(parts[2].trim());

        List<Integer> outFields = new ArrayList<>();
        if (parts.length == 4) {
            String[] fs = parts[3].trim().split("\\s+");
            for (String f : fs) {
                outFields.add(Integer.valueOf(f));
            }
        }

        Vector100Dtype vect = readVectorFile(vectorFile);

        QuerySpec spec = new QuerySpec();
        spec.isRangeQuery = false;  // means NN
        spec.queryAttrNum = queryAttr;
        spec.targetVector = vect;
        spec.rangeOrK     = k;
        spec.outputFields = outFields;
        return spec;
    }

    /**
     * Reads a file containing exactly 100 integers (each in range -10000..10000),
     * returning them as a Vector100Dtype.
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

    /**
     * Simple holder for query details:
     *   isRangeQuery = true => Range query, false => NN query
     *   queryAttrNum => 1-based field # for the vector attribute
     *   targetVector => the 100D target
     *   rangeOrK     => if range query, the distance threshold; if NN, the k
     *   outputFields => list of fields the user wants to output
     */
    private static class QuerySpec {
        boolean isRangeQuery;
        int queryAttrNum;
        Vector100Dtype targetVector;
        int rangeOrK;
        List<Integer> outputFields;
    }

}
