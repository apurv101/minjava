package LSHFIndex;

import global.*;
import heap.*;
import diskmgr.*;

import java.io.*;
import java.util.*;

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

            // IMPORTANT: The ordering of attribute types must match what the DB expects.
            // For example, if you want the 100D vector to be the last attribute (as required by the project),
            // your data file should provide fields in that order. In our fixed code we assume the order is:
            // Field 1: Integer, Field 2: String, Field 3: Real, Field 4: 100D-vector
            //
            // Create one LSHFIndex per 100D-vector attribute. We use (attrNumber = i+1) as key.
            Map<Integer, LSHFIndex> vectorIndexes = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int attrNum = i + 1; // 1-based attribute numbering
                if (attrTypeCodes[i] == AttrType.attrVector100D) {
                    // Create the LSH-forest index for that attribute
                    LSHFIndex index = new LSHFIndex(h, L);
                    vectorIndexes.put(attrNum, index);
                }
            }

            // Initialize MiniBase using a page size of 4096 bytes and 50 pages
            SystemDefs sysdef = new SystemDefs(dbName, 4096, 50, "LRU");

            // Create a heap file to store the data table
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

                Tuple t = new Tuple();

                // Build the schema
                AttrType[] types = new AttrType[attrTypeCodes.length];
                int strCount = 0;
                for (int i = 0; i < attrTypeCodes.length; i++) {
                    types[i] = new AttrType(attrTypeCodes[i]);
                    if (attrTypeCodes[i] == AttrType.attrString) {
                        strCount++;
                    }
                }

                // Define string sizes (default size = 30 bytes)
                short[] strSizes = new short[strCount];
                Arrays.fill(strSizes, (short) 30);

                // Set the tuple header
                t.setHdr((short) attrTypeCodes.length, types, strSizes);
                // Populate the tuple with field values
                t = createTuple(fieldValues, attrTypeCodes);
                byte[] record = t.getTupleByteArray();
                RID rid = heapfile.insertRecord(record);
                System.out.println("DEBUG: Inserted tuple of length " + record.length);
                tupleCount++;

                // For each vector attribute, parse the vector and insert into the LSHFIndex.

                for (int i = 0; i < n; i++) {
                    if (attrTypeCodes[i] == AttrType.attrVector100D) {
                        int attrnum = i +1;
                        short[] vectorData = parseVector100D(fieldValues[i]);
                        Vector100Dtype vect = new Vector100Dtype(vectorData);
                        Vector100DKey key = new Vector100DKey(vect);
                        vectorIndexes.get(attrnum).insert(key, rid);
                    }
                }
            }

            System.out.println("Inserted " + tupleCount + " tuples into heapfile.");

            for (int i = 0; i < attrTypeCodes.length; i++) {
                System.out.println("DEBUG: Attribute " + i + " has type " + attrTypeCodes[i]);
            }

            // Write out each LSH index to a file named: DBNAME_attrIndex_h_L
            for (Map.Entry<Integer, LSHFIndex> entry : vectorIndexes.entrySet()) {
                int attrNo = entry.getKey();
                LSHFIndex index = entry.getValue();
                String indexFileName = dbName + "_" + attrNo + "_" + L + "_" + h;
                index.writeIndexToFile(indexFileName);
                System.out.println("LSH-forest index for attr #" + attrNo + " written to " + indexFileName);
            }
            System.out.println("DEBUG: Created vectorIndexes map:");
            for (Map.Entry<Integer, LSHFIndex> entry : vectorIndexes.entrySet()) {
                System.out.println("DEBUG: Attribute " + entry.getKey() + " -> Index Object: " + entry.getValue());
            }

            SystemDefs.JavabaseBM.flushAllPages();
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
        t.setHdr((short) n, types, strSizes);

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
                    t.setStrFld(i+1, val);
                    break;
                case AttrType.attrVector100D:
                    // Parse the vector field (expecting 100 values)
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

    private static short[] parseVector100D(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 100) {
            throw new IllegalArgumentException(
                    "Expected 100 values for a 100D-vector, found " + tokens.length
            );
        }
        short[] arr = new short[100];
        for (int i = 0; i < 100; i++) {
            float value = Float.parseFloat(tokens[i]); // Parse as float
            arr[i] = (short) value; // Cast to short (this truncates decimals)
        }
        return arr;
    }
}
