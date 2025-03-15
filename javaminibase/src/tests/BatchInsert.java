import global.*;
import heap.*;
import diskmgr.*;
import index.IndexException;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFEntry;
import btree.KeyClass;      // used for KeyClass pointer
import java.io.*;
import java.util.*;

// Example command line:
//   java BatchInsert 4 2 datafile.txt MyDB
//
// Where:
//   h = 4
//   L = 2
//   datafile.txt = path to data file
//   MyDB = name of the DB
//
// Data file format:
//   1) line1: n (number of attributes)
//   2) line2: n attribute type codes (1=int, 2=real, 3=string, 4=100D-vector)
//   3) next n lines: values for first tuple
//   4) next n lines: values for second tuple
//   etc.
//   - For a 100D-vector line (attr type=4), it contains 100 integers separated by whitespace.
public class BatchInsert {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }

        // Parse the command-line arguments
        int h = Integer.parseInt(args[0]);          // number of hash functions per layer
        int L = Integer.parseInt(args[1]);          // number of layers
        String dataFileName = args[2];              // data filename
        String dbName = args[3];                    // DB name

        // 1) Initialize disk/page counters so we can report reads/writes after insertion.
        PCounter.initialize();

        BufferedReader br = null;
        try {
            // 2) Read from the data file
            br = new BufferedReader(new FileReader(dataFileName));

            // First line: number of attributes
            int n = Integer.parseInt(br.readLine().trim());

            // Second line: n attribute-type codes
            String[] typeTokens = br.readLine().trim().split("\\s+");
            if (typeTokens.length != n) {
                throw new IllegalArgumentException(
                    "Mismatch between declared attribute count and type codes provided."
                );
            }
            int[] attrTypeCodes = new int[n];
            for (int i = 0; i < n; i++) {
                attrTypeCodes[i] = Integer.parseInt(typeTokens[i]);
            }

            // 3) Create one LSHFIndex per 100D-vector attribute (type code=4),
            //    storing them in a Map keyed by attribute index (0-based).
            Map<Integer, LSHFIndex> vectorIndexes = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (attrTypeCodes[i] == AttrType.attrVector100D) {
                    // Create the LSH-forest index for that attribute
                    LSHFIndex index = new LSHFIndex(h, L);
                    vectorIndexes.put(i, index);
                }
            }

            // 4) Initialize MiniBase for the given DB name.
            //    Use page size = 4096 (or whatever is in GlobalConst) and 50 pages for demonstration.
            //    (Adjust these as needed for your environment.)
            SystemDefs sysdef = new SystemDefs(dbName, 4096, 50, "LRU");

            // Create a heap file to store the actual data table
            Heapfile heapfile = new Heapfile("batch_insert_output.in");

            // 5) Insert each tuple
            int tupleCount = 0;
            while (true) {
                // We read n lines, each line is the value for an attribute.
                // If we cannot read the first attribute line => break (EOF)
                String[] fieldValues = new String[n];
                for (int i = 0; i < n; i++) {
                    String line = br.readLine();
                    if (line == null) {
                        // End of file or partial read => break
                        if (i > 0) {
                            System.err.println("Warning: partial tuple found, ignoring it.");
                        }
                        break;
                    }
                    fieldValues[i] = line.trim();
                }
                if (fieldValues[0] == null) {
                    // No more full tuples
                    break;
                }

                // Build a Tuple in memory (we won't do the full setHdr logic here, just store raw).
                // For your system, you'd typically do something like:
                //   Tuple t = new Tuple();
                //   t.setHdr(...) ...
                //   if (attrType is int) t.setIntFld(...)
                //   etc.
                //   Insert into heapfile with heapfile.insertRecord(t.getTupleByteArray());
                // Below is a minimal demonstration.

                Tuple t = createTuple(fieldValues, attrTypeCodes); // See helper below
                byte[] record = t.getTupleByteArray();
                RID rid = heapfile.insertRecord(record);
                tupleCount++;

                // 6) For each vector attribute, insert into the LSHFIndex
                for (int i = 0; i < n; i++) {
                    if (attrTypeCodes[i] == AttrType.attrVector100D) {
                        // Convert fieldValues[i] into a Vector100Dtype
                        short[] vectorData = parseVector100D(fieldValues[i]);
                        Vector100Dtype vect = new Vector100Dtype(vectorData);

                        // Build a key object
                        Vector100DKey key = new Vector100DKey(vect);

                        // Insert into the index
                        vectorIndexes.get(i).insert(key, rid);
                    }
                }
            }

            System.out.println("Inserted " + tupleCount + " tuples into heapfile.");

            // 7) Write out each LSH index to a file named: DBNAME_attrIndex_h_L
            //    e.g. MyDB_2_4_2 means attribute=2, h=4, L=2
            //    (Adjust as you see fit for your environment.)
            for (Map.Entry<Integer, LSHFIndex> entry : vectorIndexes.entrySet()) {
                int attrNo = entry.getKey();  // 0-based
                LSHFIndex index = entry.getValue();
                String indexFileName = dbName + "_" + attrNo + "_" + h + "_" + L;
                index.writeIndexToFile(indexFileName);
                System.out.println("LSH-forest index for attr #" + attrNo
                    + " written to " + indexFileName);
            }

            // 8) Print out disk read/write counters from PCounter
            System.out.println("\nDisk pages read   : " + PCounter.rcounter);
            System.out.println("Disk pages written: " + PCounter.wcounter);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try { br.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    /**
     * Helper that creates a basic Tuple object from the string values (one per attribute).
     * You’d usually do the setHdr(...) and setXxxFld(...) for each attribute properly.
     * Here we keep it minimal to show the concept.
     */
    private static Tuple createTuple(String[] fieldValues, int[] attrTypeCodes) throws Exception {
        // For demonstration only: we’ll do a quick pass that sets a header
        // (with the correct # of fields, ignoring string size array),
        // and then sets data for each field in a simplistic manner.

        int n = fieldValues.length;
        AttrType[] types = new AttrType[n];
        // Count how many string fields to build strSizes
        int strCount = 0;
        for (int i = 0; i < n; i++) {
            types[i] = new AttrType(attrTypeCodes[i]);
            if (attrTypeCodes[i] == AttrType.attrString) {
                strCount++;
            }
        }
        short[] strSizes = new short[strCount];
        for (int i = 0; i < strCount; i++) {
            strSizes[i] = 30; // default size
        }

        Tuple t = new Tuple();
        t.setHdr((short)n, types, strSizes);

        // Now fill the fields
        int stringIndex = 0; // next string size in array
        for (int i = 0; i < n; i++) {
            switch(attrTypeCodes[i]) {
                case AttrType.attrInteger:
                    t.setIntFld(i+1, Integer.parseInt(fieldValues[i]));
                    break;
                case AttrType.attrReal:
                    t.setFloFld(i+1, Float.parseFloat(fieldValues[i]));
                    break;
                case AttrType.attrString:
                    t.setStrFld(i+1, fieldValues[i]);
                    break;
                case AttrType.attrVector100D:
                    // parseVector100D is called *before* in the main code,
                    // but let's be consistent:
                    short[] vectorData = parseVector100D(fieldValues[i]);
                    Vector100Dtype vect = new Vector100Dtype(vectorData);
                    t.set100DVectFld(i+1, vect);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown attribute code: " + attrTypeCodes[i]);
            }
        }
        return t;
    }

    /**
     * Parses a single line containing 100 integers into a short[100] array.
     */
    private static short[] parseVector100D(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 100) {
            throw new IllegalArgumentException(
                "Expected 100 integers for a 100D-vector, found " + tokens.length
            );
        }
        short[] arr = new short[100];
        for (int i = 0; i < 100; i++) {
            arr[i] = Short.parseShort(tokens[i]);
        }
        return arr;
    }
}
