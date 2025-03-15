package tests;

import global.*;
import heap.*;
import iterator.*;
import java.io.*;
import java.util.*;

public class BatchInsert {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }

        // Parse command-line arguments
        int h = Integer.parseInt(args[0]);
        int L = Integer.parseInt(args[1]);
        String dataFileName = args[2];
        String dbName = args[3];

        // (Optional) Use h and L for custom indexing or other logic as needed:
        System.out.println("Received h=" + h + ", L=" + L);

        BufferedReader reader = null;
        try {
            // 1) Open the data file
            reader = new BufferedReader(new FileReader(dataFileName));

            // 2) Read the number of attributes (n)
            int n = Integer.parseInt(reader.readLine().trim());
            System.out.println("Number of attributes = " + n);

            // 3) Read the next line containing n type codes.
            //    1 = int, 2 = real, 3 = string, 4 = 100D-vector
            String[] typeTokens = reader.readLine().trim().split("\\s+");
            if (typeTokens.length != n) {
                throw new IllegalArgumentException(
                    "Expected " + n + " attribute type codes, found " + typeTokens.length
                );
            }

            // Build the AttrType array for the schema
            AttrType[] attrTypes = new AttrType[n];
            int strFieldCount = 0; // track how many string fields we have (for str_sizes)
            for (int i = 0; i < n; i++) {
                int code = Integer.parseInt(typeTokens[i]);
                switch (code) {
                    case 1:
                        attrTypes[i] = new AttrType(AttrType.attrInteger);
                        break;
                    case 2:
                        attrTypes[i] = new AttrType(AttrType.attrReal);
                        break;
                    case 3:
                        attrTypes[i] = new AttrType(AttrType.attrString);
                        strFieldCount++;
                        break;
                    case 4:
                        attrTypes[i] = new AttrType(AttrType.attrVector100D);
                        break;
                    default:
                        throw new IllegalArgumentException(
                            "Unknown attribute type code: " + code
                        );
                }
            }

            // For each string field, we’ll assume some fixed maximum length.
            // You can adjust these lengths to suit your data.
            short[] strSizes = new short[strFieldCount];
            Arrays.fill(strSizes, (short)30);

            // 4) Initialize MiniBase with the DB name.
            //    (Adjust the page size, buffer pool size, etc. as you wish.)
            //    For demonstration, we use 4096 bytes, 50 pages, LRU replacement.
            SystemDefs sysdef = new SystemDefs(dbName, 4096, 50, "LRU");

            // 5) Create a heap file. You can name it arbitrarily or derive from arguments.
            Heapfile heapfile = new Heapfile("batch_insert_output.in");

            // 6) We will read tuples until EOF, each tuple has n fields, 
            //    reading line by line for each attribute’s value.
            //    (For attribute 4 => 100D-vector => read 100 integers in one line or multiple lines)
            //    The problem statement says: “The next n lines will contain values of the first tuple; 
            //    the next n lines for the second tuple; etc.”

            // Prepare a reusable Tuple instance
            Tuple t = new Tuple();
            t.setHdr((short)n, attrTypes, strSizes);

            // Keep track of the size once, so we can always re_init fields
            int size = t.size();
            t = new Tuple(size);
            t.setHdr((short)n, attrTypes, strSizes);

            int tupleCount = 0;

            while (true) {
                // Attempt to read n lines for the next tuple
                // If we cannot read all n lines, we must be at the end
                String[] fieldValues = new String[n];
                for (int i = 0; i < n; i++) {
                    String line = reader.readLine();
                    if (line == null) {
                        // No more data left
                        if (i > 0) {
                            System.err.println("Warning: partial tuple found but ignoring it.");
                        }
                        break; 
                    }
                    fieldValues[i] = line.trim();
                }
                // If the first line of the new tuple read was null, we’re done
                if (fieldValues[0] == null) {
                    break;
                }

                // Now fill in the fields:
                // Map each field to the appropriate set method.
                int attrIndex = 0;
                int stringIndex = 0; // track strings
                for (int i = 0; i < n; i++) {
                    int code = Integer.parseInt(typeTokens[i]);
                    switch (code) {
                        case 1: // int
                        {
                            int val = Integer.parseInt(fieldValues[i]);
                            t.setIntFld(i+1, val);
                            break;
                        }
                        case 2: // real (float)
                        {
                            float val = Float.parseFloat(fieldValues[i]);
                            t.setFloFld(i+1, val);
                            break;
                        }
                        case 3: // string
                        {
                            // If the line is the entire string, store it
                            // If you have strings with spaces, you'd need a different approach
                            String s = fieldValues[i];
                            t.setStrFld(i+1, s);
                            break;
                        }
                        case 4: // 100D vector
                        {
                            // The line contains 100 space-separated integers (based on the spec)
                            // If your data is spread over multiple lines, adjust the code accordingly.
                            // For simplicity, we assume it's a single line with 100 ints separated by spaces.
                            String[] vectorTokens = fieldValues[i].split("\\s+");
                            if (vectorTokens.length != 100) {
                                throw new IllegalArgumentException(
                                    "Expected 100 integers for vector, found " + vectorTokens.length
                                );
                            }
                            short[] vecData = new short[100];
                            for (int k = 0; k < 100; k++) {
                                vecData[k] = Short.parseShort(vectorTokens[k]);
                            }
                            Vector100Dtype vector = new Vector100Dtype(vecData);
                            t.set100DVectFld(i+1, vector);
                            break;
                        }
                        default:
                            // Should never happen if we validated up front
                            throw new IllegalArgumentException("Unknown attribute code: " + code);
                    }
                }

                // 7) Insert the tuple into the heap file
                byte[] record = t.getTupleByteArray();
                RID rid = heapfile.insertRecord(record);
                tupleCount++;
            }

            System.out.println("Successfully inserted " + tupleCount + " tuples.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }
}
