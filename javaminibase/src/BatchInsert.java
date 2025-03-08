import java.io.*;
import java.util.*;

// (Assuming you have these classes already implemented in your project)
import global.Vector100Dtype;
import global.Vector100DKey;
import heap.Tuple;
import LSHFIndex.LSHFIndex;

public class BatchInsert {
    
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: batchinsert h L DATAFILENAME DBNAME");
            System.exit(1);
        }
        
        int h = Integer.parseInt(args[0]);
        int L = Integer.parseInt(args[1]);
        String dataFilename = args[2];
        String dbName = args[3];
        
        // Disk I/O simulation counters:
        int diskPagesRead = 0;
        int diskPagesWritten = 0;
        
        // Map to hold LSHFIndex for each vector attribute (type 4).
        Map<Integer, LSHFIndex> vectorIndexes = new HashMap<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(dataFilename))) {
            
            // Read number of attributes
            int nAttrs = Integer.parseInt(br.readLine().trim());
            
            // Read attribute types (as integers: 1, 2, 3, or 4)
            String[] typeTokens = br.readLine().trim().split("\\s+");
            int[] attrTypes = new int[nAttrs];
            for (int i = 0; i < nAttrs; i++) {
                attrTypes[i] = Integer.parseInt(typeTokens[i]);
                // For every vector attribute (type 4), create a new LSHFIndex.
                if (attrTypes[i] == 4) {
                    // Create the LSHFIndex instance with parameters h and L.
                    LSHFIndex index = new LSHFIndex(h, L);
                    vectorIndexes.put(i, index);
                }
            }
            
            // Process tuples: Each tuple has nAttrs lines (one per attribute).
            String line;
            List<Tuple> tuples = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                String[] tupleValues = new String[nAttrs];
                // The first attribute’s value is already read.
                tupleValues[0] = line;
                // Read the remaining (nAttrs - 1) attribute values.
                for (int i = 1; i < nAttrs; i++) {
                    tupleValues[i] = br.readLine();
                }
                
                // Create a Tuple from the array of string values.
                Tuple t = createTuple(tupleValues, attrTypes);
                tuples.add(t);
                
                // Simulate insertion into the database.
                // (Here, you would normally write the tuple to a disk page.)
                diskPagesWritten++;  // (Increment as per your simulation)
                
                // For every vector attribute, update the corresponding index.
                for (int i = 0; i < nAttrs; i++) {
                    if (attrTypes[i] == 4) {
                        short[] vectorData = parseVector(tupleValues[i]);
                        // Wrap the integer array in a Vector100Dtype (adjust constructor as needed)
                        Vector100Dtype vector = new Vector100Dtype(vectorData);
                        Vector100DKey key = new Vector100DKey(vector);
                        // Insert key and a dummy RID (or your record pointer) into the index.
                        vectorIndexes.get(i).insert(key, null);
                    }
                }
                
                diskPagesRead++;  // For each tuple, simulate reading a page.
            }
            
            // Write out each vector index to a file.
            for (Map.Entry<Integer, LSHFIndex> entry : vectorIndexes.entrySet()) {
                int attrNo = entry.getKey();
                LSHFIndex index = entry.getValue();
                String indexFileName = dbName + "_" + attrNo + "_" + h + "_" + L;
                index.writeIndexToFile(indexFileName);
            }
            
            // Output disk I/O statistics.
            System.out.println("Disk Pages Read: " + diskPagesRead);
            System.out.println("Disk Pages Written: " + diskPagesWritten);
            
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    // Converts an array of strings (one per attribute) into a Tuple,
    // converting each field based on its type.
    private static Tuple createTuple(String[] values, int[] attrTypes) {
        Tuple t = new Tuple();
        // Set up tuple header, allocate fields, etc.
        // For each attribute:
        //   - For type 1: convert to integer.
        //   - For type 2: convert to real (float/double).
        //   - For type 3: leave as string.
        //   - For type 4: convert the line into a 100D-vector (see parseVector).
        // (Implement this based on your Tuple class and project requirements.)
        return t;
    }
    
    // Parses a line containing 100 integers (separated by whitespace)
    // and returns them as an int array.
    private static short[] parseVector(String line) {
        String[] tokens = line.trim().split("\\s+");
        short[] vector = new short[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            vector[i] = (short) Integer.parseInt(tokens[i]);
        }
        return vector;
    }
}
