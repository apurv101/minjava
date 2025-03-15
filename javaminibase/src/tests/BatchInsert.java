import global.*;
import heap.*;
import index.IndexException;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFEntry;
import btree.KeyClass;
import java.io.*;
import java.util.*;

public class BatchInsert {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }

        int h = Integer.parseInt(args[0]);          // number of hash functions per layer
        int L = Integer.parseInt(args[1]);            // number of layers
        String dataFileName = args[2];
        String dbName = args[3];

        // Initialize disk I/O counters (assuming PCounter is implemented)
        PCounter.initialize();

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(dataFileName));

            // First line: number of attributes
            int n = Integer.parseInt(br.readLine().trim());

            // Second line: attribute type codes (1=int, 2=real, 3=string, 4=100D-vector)
            String[] typeTokens = br.readLine().trim().split("\\s+");
            if (typeTokens.length != n) {
                throw new IllegalArgumentException("Declared attribute count doesn't match type codes provided.");
            }
            int[] attrTypeCodes = new int[n];
            for (int i = 0; i < n; i++) {
                attrTypeCodes[i] = Integer.parseInt(typeTokens[i]);
            }

            // Create one LSHFIndex per 100D-vector attribute.
            Map<Integer, LSHFIndex> vectorIndexes = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (attrTypeCodes[i] == AttrType.attrVector100D) {
                    LSHFIndex index = new LSHFIndex(h, L);
                    vectorIndexes.put(i, index);
                }
            }

            // Initialize MiniBase with DB name.
            SystemDefs sysdef = new SystemDefs(dbName, 4096, 50, "LRU");
            Heapfile heapfile = new Heapfile("batch_insert_output.in");

            // Insert each tuple.
            int tupleCount = 0;
            while (true) {
                String[] fieldValues = new String[n];
                for (int i = 0; i < n; i++) {
                    String line = br.readLine();
                    if (line == null) {
                        if (i > 0) {
                            System.err.println("Warning: partial tuple found, ignoring it.");
                        }
                        break;
                    }
                    fieldValues[i] = line.trim();
                }
                if (fieldValues[0] == null)
                    break;

                Tuple t = createTuple(fieldValues, attrTypeCodes);
                byte[] record = t.getTupleByteArray();
                RID rid = heapfile.insertRecord(record);
                tupleCount++;

                // For each vector attribute, insert into the index.
                for (int i = 0; i < n; i++) {
                    if (attrTypeCodes[i] == AttrType.attrVector100D) {
                        short[] vectorData = parseVector100D(fieldValues[i]);
                        Vector100Dtype vect = new Vector100Dtype(vectorData);
                        Vector100DKey key = new Vector100DKey(vect);
                        vectorIndexes.get(i).insert(key, rid);
                    }
                }
            }

            System.out.println("Inserted " + tupleCount + " tuples into heapfile.");

            // Write each LSH index to a file.
            for (Map.Entry<Integer, LSHFIndex> entry : vectorIndexes.entrySet()) {
                int attrNo = entry.getKey();
                LSHFIndex index = entry.getValue();
                String indexFileName = dbName + "_" + attrNo + "_" + h + "_" + L;
                index.writeIndexToFile(indexFileName);
                System.out.println("LSH-forest index for attr #" + attrNo + " written to " + indexFileName);
            }

            // Report disk I/O statistics.
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
     * Creates a Tuple from an array of field values.
     * Checks for empty strings and uses default values if needed.
     */
    private static Tuple createTuple(String[] fieldValues, int[] attrTypeCodes) throws Exception {
        int n = fieldValues.length;
        AttrType[] types = new AttrType[n];
        int strCount = 0;
        for (int i = 0; i < n; i++) {
            types[i] = new AttrType(attrTypeCodes[i]);
            if (attrTypeCodes[i] == AttrType.attrString)
                strCount++;
        }
        short[] strSizes = new short[strCount];
        Arrays.fill(strSizes, (short)30);

        Tuple t = new Tuple();
        t.setHdr((short)n, types, strSizes);

        for (int i = 0; i < n; i++) {
            switch (attrTypeCodes[i]) {
                case AttrType.attrInteger:
                    // If empty, default to 0.
                    t.setIntFld(i+1, fieldValues[i].isEmpty() ? 0 : Integer.parseInt(fieldValues[i]));
                    break;
                case AttrType.attrReal:
                    // If empty, default to 0.0f.
                    t.setFloFld(i+1, fieldValues[i].isEmpty() ? 0.0f : Float.parseFloat(fieldValues[i]));
                    break;
                case AttrType.attrString:
                    // Use the string as-is (may be empty).
                    t.setStrFld(i+1, fieldValues[i]);
                    break;
                case AttrType.attrVector100D:
                    // If empty, create a vector of 100 zeros.
                    if (fieldValues[i].trim().isEmpty()) {
                        short[] zeros = new short[100];
                        t.set100DVectFld(i+1, new Vector100Dtype(zeros));
                    } else {
                        short[] vectorData = parseVector100D(fieldValues[i]);
                        t.set100DVectFld(i+1, new Vector100Dtype(vectorData));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown attribute code: " + attrTypeCodes[i]);
            }
        }
        return t;
    }

    /**
     * Parses a line containing 100 integers into a short array.
     */
    private static short[] parseVector100D(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 100) {
            throw new IllegalArgumentException("Expected 100 integers for a 100D-vector, found " + tokens.length);
        }
        short[] arr = new short[100];
        for (int i = 0; i < 100; i++) {
            arr[i] = Short.parseShort(tokens[i]);
        }
        return arr;
    }
}
