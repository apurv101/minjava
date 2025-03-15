package batch;

import global.AttrType;
import global.Convert;
import global.GlobalConst;
import global.RID;
import global.SystemDefs;
import global.Vector100Dtype;
import heap.Heapfile;
import heap.Tuple;
import iterator.FldSpec;
import iterator.RelSpec;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;

public class batchinsert {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }

        // ------------------------------------------------------------------
        // 1. Parse command-line arguments
        // ------------------------------------------------------------------
        int h = Integer.parseInt(args[0]);            // not specifically used in this example
        int L = Integer.parseInt(args[1]);            // not specifically used in this example
        String dataFileName = args[2];
        String dbName       = args[3];

        System.out.println("h = " + h + ", L = " + L);
        System.out.println("DATAFILENAME = " + dataFileName);
        System.out.println("DBNAME       = " + dbName);

        BufferedReader br = null;

        try {
            // ------------------------------------------------------------------
            // 2. Create or initialize the MiniBase DB using DBNAME
            //    For example, we use 4096 byte pages and 50 pages in the DB.
            //    Adjust these to suit your environment or Task requirements.
            // ------------------------------------------------------------------
            // Delete the db file if it already exists (so we start fresh)
            File dbFile = new File(dbName);
            if (dbFile.exists()) {
                dbFile.delete();
            }
            SystemDefs sysDef = new SystemDefs(dbName,       // db path
                                               4096,         // page size
                                               50,           // number of pages
                                               "LRU");       // replacement policy

            // ------------------------------------------------------------------
            // 3. Open the data file for reading
            // ------------------------------------------------------------------
            br = new BufferedReader(new FileReader(dataFileName));

            // Read the number of attributes
            int numAttrs = Integer.parseInt(br.readLine().trim());
            System.out.println("Number of attributes = " + numAttrs);

            // Read the attribute-type line
            // e.g. "1 3 4" means (integer, string, 100D-vector)
            String[] attrTokens = br.readLine().trim().split("\\s+");
            if (attrTokens.length != numAttrs) {
                throw new IOException("Mismatch between numAttrs and the attribute type line length.");
            }

            // For string attributes, we’ll store the short-lens in an array.
            // We'll do a single pass to figure out how many string fields we have
            // (so we know how large to make strSizes).
            int stringCount = 0;
            for (String t : attrTokens) {
                int tType = Integer.parseInt(t);
                if (tType == 3) { // 3 indicates "string" from your project description
                    stringCount++;
                }
            }

            // Build the array of AttrType, also build strSizes
            AttrType[] attrTypes = new AttrType[numAttrs];
            short[] strSizes = new short[stringCount];
            // Suppose we define a default maximum string length = 30
            // You might want to parse the data file or pass in a separate param
            // for string sizes. For simplicity, we’ll just use 30 for all strings.
            for (int i = 0; i < stringCount; i++) {
                strSizes[i] = 30;
            }

            int strIndex = 0;
            for (int i = 0; i < numAttrs; i++) {
                int tType = Integer.parseInt(attrTokens[i]);
                switch(tType) {
                    case 1:
                        // integer
                        attrTypes[i] = new AttrType(AttrType.attrInteger);
                        break;
                    case 2:
                        // real / float
                        attrTypes[i] = new AttrType(AttrType.attrReal);
                        break;
                    case 3:
                        // string
                        attrTypes[i] = new AttrType(AttrType.attrString);
                        break;
                    case 4:
                        // 100D-vector
                        attrTypes[i] = new AttrType(AttrType.attrVector100D);
                        break;
                    default:
                        throw new IOException("Unknown attribute type code: " + tType);
                }
            }

            // ------------------------------------------------------------------
            // 4. Create a Heapfile to store the tuples
            // ------------------------------------------------------------------
            Heapfile hf = new Heapfile("batch_insert_heap.in");

            // ------------------------------------------------------------------
            // 5. Repeatedly read each tuple’s data, build a Tuple, insert it
            //    Each tuple has numAttrs lines. For type=4, read 100 integers on one line
            // ------------------------------------------------------------------
            // We read until we exhaust the file. A single "batch" of data might
            // be large. Each tuple requires reading n lines, unless an attribute
            // is type=4 => then we read 100 numbers on that line instead of 1.
            // However, your instructions say: "The next n lines for first tuple,
            // the next n lines for second, etc." The exception is that a 100D
            // vector line contains 100 integers. 
            //
            // This sample code reads n lines per tuple, except for vector lines
            // which we parse carefully.
            // ------------------------------------------------------------------

            while (true) {
                // We'll attempt to read n attribute-values. If we can't, we break.
                String[] fieldValues = new String[numAttrs];
                for (int i = 0; i < numAttrs; i++) {
                    // For each attribute line, read the line from the file
                    String line = br.readLine();
                    if (line == null) {
                        // We reached end of file while reading this tuple => done
                        break;
                    }
                    fieldValues[i] = line.trim();
                }
                // If any of them was null, it means we didn't get a complete tuple
                boolean incomplete = false;
                for (String v : fieldValues) {
                    if (v == null) {
                        incomplete = true;
                        break;
                    }
                }
                if (incomplete) {
                    // no more complete tuples
                    break;
                }

                // Now we have n lines read. Let’s build a Tuple
                Tuple t = new Tuple();
                try {
                    t.setHdr((short) numAttrs, attrTypes, strSizes);
                } catch (Exception e) {
                    System.err.println("Could not setHdr for the tuple: " + e.getMessage());
                    continue;
                }

                // Fill the fields
                int strCounter = 0; // to keep track of which strSizes index to use
                for (int i = 0; i < numAttrs; i++) {
                    int fieldType = attrTypes[i].attrType;
                    switch(fieldType) {
                        case AttrType.attrInteger:
                            {
                                int intVal = Integer.parseInt(fieldValues[i]);
                                t.setIntFld(i+1, intVal);
                            }
                            break;
                        case AttrType.attrReal:
                            {
                                float fVal = Float.parseFloat(fieldValues[i]);
                                t.setFloFld(i+1, fVal);
                            }
                            break;
                        case AttrType.attrString:
                            {
                                // Possibly you want to ensure it does not exceed strSizes[strCounter]
                                // e.g. substring if length is too big
                                t.setStrFld(i+1, fieldValues[i]);
                                strCounter++;
                            }
                            break;
                        case AttrType.attrVector100D:
                            {
                                // The line for a 100D-vector is supposed to have 100 integers
                                // space-separated. Parse them all
                                String[] vectorTokens = fieldValues[i].split("\\s+");
                                if (vectorTokens.length != 100) {
                                    throw new IOException("Expected 100 integers for 100D-vector, found "
                                                          + vectorTokens.length);
                                }
                                short[] vectArr = new short[100];
                                for (int idx = 0; idx < 100; idx++) {
                                    int iv = Integer.parseInt(vectorTokens[idx]);
                                    vectArr[idx] = (short) iv; 
                                }
                                Vector100Dtype vecVal = new Vector100Dtype(vectArr);
                                t.set100DVectFld(i+1, vecVal);
                            }
                            break;
                        default:
                            throw new IOException("Unknown field type: " + fieldType);
                    }
                }

                // Insert the tuple
                byte[] record = t.getTupleByteArray();
                RID rid = hf.insertRecord(record);
                System.out.println("Inserted tuple with RID: " + rid);
            }

            System.out.println("All tuples inserted successfully!");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); } catch(IOException ignored) {}
            }
        }
    }
}
