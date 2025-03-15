package batch;

import global.*;
import heap.Heapfile;
import heap.Tuple;
import index.IndexType;  // adjust if needed
import LSHFIndex.*;     // hypothetical package for your LSH-forest classes
import java.io.*;
import java.util.*;

/**
 * A command-line program for batch insertion into a MiniBase heap file
 * and building an LSH-forest index (for attrVector100D attributes).
 *
 * Usage:
 *   java batchinsert h L DATAFILENAME DBNAME
 */
public class batchinsert {
    public static void main(String[] args) {

        // ---------------------------------------------------
        // 1) Parse Command-line Arguments
        // ---------------------------------------------------
        if (args.length != 4) {
            System.err.println("Usage: java batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }

        int h = Integer.parseInt(args[0]);
        int L = Integer.parseInt(args[1]);
        String dataFileName = args[2];
        String dbName = args[3];

        // Example debugging output:
        System.out.println("h: " + h);
        System.out.println("L: " + L);
        System.out.println("Data File: " + dataFileName);
        System.out.println("DB Name: " + dbName);

        // ---------------------------------------------------
        // 2) Read the Data File
        // ---------------------------------------------------
        // The file format is described as:
        //   (1) first line: number of attributes (n)
        //   (2) second line: n numbers describing attribute types (1=int,2=real,3=string,4=vector)
        //   (3) next n lines = first tuple’s values
        //   (4) next n lines = second tuple’s values
        //   ...
        // And lines for 100D-vector contain 100 integers each.
        //

        int numAttrs;
        AttrType[] attrTypes;  // array of AttrType
        short[] strSizes;      // for storing size info of any strings
        List<TupleData> allTuples = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(dataFileName))) {

            // (1) Read number of attributes
            String line = br.readLine();
            if (line == null) {
                throw new RuntimeException("Data file is empty or invalid format");
            }
            numAttrs = Integer.parseInt(line.trim());

            // (2) Read attribute type definitions
            line = br.readLine();
            if (line == null) {
                throw new RuntimeException("Data file missing attribute type definition line");
            }
            String[] typeStrs = line.trim().split("\\s+");
            if (typeStrs.length != numAttrs) {
                throw new RuntimeException("Attribute count mismatch in type definition");
            }

            // Create our AttrType array (and note which are strings, for strSizes).
            attrTypes = new AttrType[numAttrs];
            List<Integer> stringFieldIndexes = new ArrayList<>();

            for (int i = 0; i < numAttrs; i++) {
                int typeVal = Integer.parseInt(typeStrs[i]);
                switch (typeVal) {
                    case 1: // integer
                        attrTypes[i] = new AttrType(AttrType.attrInteger);
                        break;
                    case 2: // real/float
                        attrTypes[i] = new AttrType(AttrType.attrReal);
                        break;
                    case 3: // string
                        attrTypes[i] = new AttrType(AttrType.attrString);
                        stringFieldIndexes.add(i);
                        break;
                    case 4: // Vector100D
                        attrTypes[i] = new AttrType(AttrType.attrVector100D);
                        break;
                    default:
                        throw new RuntimeException("Unrecognized attribute type: " + typeVal);
                }
            }

            // We must fill in short[] strSizes for any string fields.
            // Suppose each string field has max length 30 (or your choice).
            // If you have multiple string fields, you'll need an entry in strSizes for each of them.
            strSizes = new short[stringFieldIndexes.size()];
            for (int k = 0; k < stringFieldIndexes.size(); k++) {
                // Hardcode 30 or parse from somewhere else
                strSizes[k] = 30;
            }

            // (3) Read the tuples in sets of numAttrs lines
            while (true) {
                // For each tuple, read numAttrs lines
                String[] tupleValues = new String[numAttrs];
                for (int i = 0; i < numAttrs; i++) {
                    line = br.readLine();
                    if (line == null) {
                        // No more data -> break out of outer while loop
                        // We only store partial tuple if we have all attributes
                        if (i != 0) {
                            System.err.println("WARNING: Data file ended mid-tuple, ignoring partial tuple");
                        }
                        break;
                    }

                    // If this attribute is type 4 (vector), then line has 100 integers
                    // We'll store the entire line for now, parse later when building the actual Tuple.
                    tupleValues[i] = line.trim();
                }
                // If line is null, we break from while
                if (line == null) {
                    break;
                }
                // We read 1 full tuple
                allTuples.add(new TupleData(tupleValues));
            }

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // For reference, we have now:
        //   numAttrs
        //   attrTypes
        //   strSizes
        //   allTuples (each item is an object that holds the raw string for each field)

        // Quick debug
        System.out.println("Finished reading data. Number of tuples: " + allTuples.size());

        // ---------------------------------------------------
        // 3) Create a MiniBase Database
        // ---------------------------------------------------
        // e.g., DBName is a path
        // We can specify the pageSize, num_pages, replacementPolicy, etc.
        // Typically, in your tasks, you might store DBName as the full name or path.
        // For example:
        try {
            // Adjust page size / num_pages to match your usage:
            // The 4th argument is buffer replacement policy: "Clock", "LRU", etc.
            SystemDefs sysdef = new SystemDefs(dbName, 4096, 4000, "LRU");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // ---------------------------------------------------
        // 4) Create a Heapfile for the table
        // ---------------------------------------------------
        // Suppose we name it the same as the DB but with .in extension:
        Heapfile heapFile = null;
        String heapFileName = dbName + ".in";
        try {
            heapFile = new Heapfile(heapFileName);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // ---------------------------------------------------
        // 5) Potentially Create LSH-forest Index for each Vector Attribute
        // ---------------------------------------------------
        // If an attribute is type attrVector100D, then we create an index
        // named, for example, DBNAME + "_" + attrNum + "_" + h + "_" + L (or similar).
        // Let’s store these in a small structure if multiple vector attributes exist.
        //
        // We'll pretend you have something like:
        //   LSHFIndex index = new LSHFIndex(indexName, h, L, <other params>...);
        // The constructor might create pages, root pointer, etc.
        // Then, as you insert tuples, you'd do index.insert(vectorValue, rid).

        class IndexInfo {
            int attrPos;         // which field
            LSHFIndex lshfIndex; // your LSH-forest index class
        }
        List<IndexInfo> lshIndexes = new ArrayList<>();

        for (int i = 0; i < numAttrs; i++) {
            if (attrTypes[i].attrType == AttrType.attrVector100D) {
                // Construct an index for this attribute
                String indexName = dbName + "_attr" + (i + 1) + "_h" + h + "_L" + L;
                System.out.println("Creating LSH-forest index: " + indexName);

                // Hypothetical constructor; adjust to your actual LSHFIndex constructor:
                try {
                    // e.g., IndexType.LSHFIndex might be a constant enumerating the index type
                    //       or simply pass a string "LSHFIndex"
                    LSHFIndex lshfIndex = new LSHFIndex(indexName, h, L /*, maybe other args */);

                    IndexInfo ii = new IndexInfo();
                    ii.attrPos = i;
                    ii.lshfIndex = lshfIndex;
                    lshIndexes.add(ii);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Failed to create LSHFIndex for attribute " + (i + 1));
                }
            }
        }

        // ---------------------------------------------------
        // 6) Insert All Tuples into the Heap and Index(es)
        // ---------------------------------------------------
        // We build a "template" Tuple with the same schema (numAttrs, attrTypes, strSizes).
        Tuple tuple = new Tuple();
        try {
            tuple.setHdr((short) numAttrs, attrTypes, strSizes);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        int tupleSize = tuple.size();

        // Now we loop over allTuples, set each field, then insert.
        for (TupleData td : allTuples) {
            // Reuse the same tuple object each time, but remember to do tuple.setHdr if needed
            // or to be safe re-create a new Tuple. We'll just re-use but careful to set all fields:

            for (int i = 0; i < numAttrs; i++) {
                switch (attrTypes[i].attrType) {
                    case AttrType.attrInteger:
                        int ival = Integer.parseInt(td.fieldValues[i]);
                        try {
                            tuple.setIntFld(i + 1, ival);
                        } catch (Exception e) { e.printStackTrace(); }
                        break;

                    case AttrType.attrReal:
                        float rval = Float.parseFloat(td.fieldValues[i]);
                        try {
                            tuple.setFloFld(i + 1, rval);
                        } catch (Exception e) { e.printStackTrace(); }
                        break;

                    case AttrType.attrString:
                        // Note: The raw line is the entire string. If you must handle spaces, you can do so
                        // because we read entire lines from the file. Or parse otherwise.
                        // If your line might have multiple tokens, handle that as well. For now we assume the entire line is the string.
                        try {
                            tuple.setStrFld(i + 1, td.fieldValues[i]);
                        } catch (Exception e) { e.printStackTrace(); }
                        break;

                    case AttrType.attrVector100D:
                        // The line for this field has 100 integers separated by spaces.
                        // Parse them into a short[] of length 100.
                        short[] vectData = new short[100];
                        String[] vectTokens = td.fieldValues[i].split("\\s+");
                        if (vectTokens.length != 100) {
                            throw new RuntimeException("Expected 100 integers for Vector100D, got " + vectTokens.length);
                        }
                        for (int v = 0; v < 100; v++) {
                            vectData[v] = Short.parseShort(vectTokens[v]);
                        }
                        Vector100Dtype vectVal = new Vector100Dtype(vectData);
                        try {
                            tuple.set100DVectFld(i + 1, vectVal);
                        } catch (Exception e) { e.printStackTrace(); }
                        break;

                    default:
                        throw new RuntimeException("Unrecognized type in insertion loop");
                }
            }

            // Now get a byte[] representation for insertion
            byte[] rec;
            try {
                rec = tuple.getTupleByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            // Insert into heap
            RID rid = null;
            try {
                rid = heapFile.insertRecord(rec);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            // For each LSH-forest index, insert if it corresponds to this attribute.
            // i.e., we loop over each IndexInfo in lshIndexes, get the attrPos,
            // and call something like lshfIndex.insert(vectVal, rid).
            // We'll need to re-fetch the vector from the tuple or from our local "vectVal" we just parsed.
            for (IndexInfo ii : lshIndexes) {
                int vecPos = ii.attrPos;  // 0-based
                // We already processed the parse in the loop above. Let's do a quick check:
                if (attrTypes[vecPos].attrType == AttrType.attrVector100D) {
                    // we can get the value from tuple
                    try {
                        Vector100Dtype thisVect = tuple.get100DVectFld(vecPos + 1);
                        ii.lshfIndex.insert(thisVect, rid);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // ---------------------------------------------------
        // 7) Output # of Disk Pages Read / Written
        // ---------------------------------------------------
        // Your version of MiniBase might have a PCounter or a method in SystemDefs to get R/W counts.
        // For instance (hypothetically):
        int reads = 0;
        int writes = 0;
        try {
            // If you have a PCounter class:
            //    reads = PCounter.getRCounter();
            //    writes = PCounter.getWCounter();
            //
            // Or if your SystemDefs environment tracks them:
            //    reads = SystemDefs.JavabaseDB.getSomething();
            //    writes = SystemDefs.JavabaseDB.getSomethingElse();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Number of disk pages read: " + reads);
        System.out.println("Number of disk pages written: " + writes);

        System.out.println("batchinsert completed successfully.");
    }

    /**
     * A small helper class to store the raw string for each field in one tuple,
     * exactly as read from the file. We’ll parse them later.
     */
    static class TupleData {
        String[] fieldValues;
        TupleData(String[] vals) {
            this.fieldValues = vals;
        }
    }
}
