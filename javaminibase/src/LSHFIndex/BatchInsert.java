package LSHFIndex;

import global.*;
import heap.*;
import diskmgr.*;

import java.io.*;
import java.util.*;

// Example command line:
//   java LSHFIndex.BatchInsert 4 2 datafile.txt MyDB
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
        int L = Integer.parseInt(args[1]);            // number of layers
        String dataFileName = args[2];                // data filename
        String dbName = args[3];                      // DB name

        // Initialize disk/page counters so we can report reads/writes after insertion.
        PCounter.initialize();

        BufferedReader br = null;
        try {
            // Open the data file
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
                System.out.println("Creating AttrType with value: " + attrTypeCodes[i]);
            }

            // Create one LSHFIndex per 100D-vector attribute (type code=4)
            Map<Integer, LSHFIndex> vectorIndexes = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (attrTypeCodes[i] == AttrType.attrVector100D) {
                    // Create the LSH-forest index for that attribute
                    LSHFIndex index = new LSHFIndex(h, L);
                    vectorIndexes.put(i, index);
                }
            }

            // Initialize MiniBase for the given DB name.
            // Using page size = 4096 bytes and 50 pages for demonstration.
            SystemDefs sysdef = new SystemDefs(dbName, 4096, 50, "LRU");

            // Create a heap file to store the actual data table
            Heapfile heapfile = new Heapfile("batch_insert_output.in");

            int tupleCount = 0;
            while (true) {
                // Read n lines, one for each attribute.
                String[] fieldValues = new String[n];
                boolean partialTuple = false;
                for (int i = 0; i < n; i++) {
                    String line = br.readLine();
                    if (line == null) {
                        partialTuple = true;
                        if (i > 0) {
                            System.err.println("Warning: partial tuple found, ignoring it.");
                        }
                        break;
                    }
                    fieldValues[i] = line.trim();
                }
                if (partialTuple || fieldValues[0] == null) {
                    break;
                }

                // Debug: print the tuple being processed
                System.out.println("Processing tuple " + (tupleCount+1) + ": " + Arrays.toString(fieldValues));

                // Create the tuple using our helper.
                Tuple t = createTuple(fieldValues, attrTypeCodes);
                byte[] record = t.getTupleByteArray();
                RID rid = heapfile.insertRecord(record);
                System.out.println("DEBUG: Inserted tuple of length " + record.length);
                tupleCount++;

                // For each vector attribute, insert into the LSHFIndex.
                for (int i = 0; i < n; i++) {
                    if (attrTypeCodes[i] == AttrType.attrVector100D) {
                        // Parse the vector data.
                        short[] vectorData = parseVector100D(fieldValues[i]);
                        Vector100Dtype vect = new Vector100Dtype(vectorData);
                        Vector100DKey key = new Vector100DKey(vect);
                        vectorIndexes.get(i).insert(key, rid);
                    }
                }
            }

            System.out.println("Inserted " + tupleCount + " tuples into heapfile.");

            // Write out each LSH index to a file named: DBNAME_attrIndex_h_L
            for (Map.Entry<Integer, LSHFIndex> entry : vectorIndexes.entrySet()) {
                int attrNo = entry.getKey();  // 0-based
                LSHFIndex index = entry.getValue();
                String indexFileName = dbName + "_" + attrNo + "_" + h + "_" + L;
                index.writeIndexToFile(indexFileName);
                System.out.println("LSH-forest index for attr #" + attrNo
                    + " written to " + indexFileName);
            }

            // Print out disk I/O counters
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
     * Helper that creates a basic Tuple object from the string values.
     * For numeric fields, if an empty string is encountered, a default value is used.
     */
    private static Tuple createTuple(String[] fieldValues, int[] attrTypeCodes) throws Exception {
        int n = fieldValues.length;
        AttrType[] types = new AttrType[n];
        int strCount = 0;
        for (int i = 0; i < n; i++) {
            types[i] = new AttrType(attrTypeCodes[i]);
            if (attrTypeCodes[i] == AttrType.attrString) {
                strCount++;
            }
        }
        short[] strSizes = new short[strCount];
        for (int i = 0; i < strCount; i++) {
            strSizes[i] = 30; // default string size
        }

        Tuple t = new Tuple();
        t.setHdr((short)n, types, strSizes);

        // Set each field
        for (int i = 0; i < n; i++) {
            String val = fieldValues[i];
            switch (attrTypeCodes[i]) {
                case AttrType.attrInteger:
                    if (val.isEmpty()) {
                        System.out.println("Warning: Empty integer attribute at field " + (i+1) + ". Using default 0.");
                        t.setIntFld(i+1, 0);
                    } else {
                        t.setIntFld(i+1, Integer.parseInt(val));
                    }
                    break;
                case AttrType.attrReal:
                    if (val.isEmpty()) {
                        System.out.println("Warning: Empty real attribute at field " + (i+1) + ". Using default 0.0.");
                        t.setFloFld(i+1, 0.0f);
                    } else {
                        t.setFloFld(i+1, Float.parseFloat(val));
                    }
                    break;
                case AttrType.attrString:
                    // For strings, an empty string is allowed.
                    t.setStrFld(i+1, val);
                    break;
                case AttrType.attrVector100D:
                    // For vector fields, parsing is handled separately.
                    short[] vectorData = parseVector100D(val);
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
     * Parses a line containing 100 integers into a short[100] array.
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
