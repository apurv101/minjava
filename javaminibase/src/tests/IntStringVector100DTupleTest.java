package tests;

import heap.Heapfile;
import heap.Tuple;
import global.AttrType;
import global.SystemDefs;
import global.RID;
import global.Vector100Dtype;
import java.io.File;

public class IntStringVector100DTupleTest {
    public static void main(String[] args) {
        try {
            // Define valid Windows paths.
            String dbPath = "C:\\tmp\\test_int_string_vector100d_tuple_db";
            String logPath = "C:\\tmp\\test_int_string_vector100d_tuple_log";

            // Ensure the base directory exists.
            File tmpDir = new File("C:\\tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            
            // Delete an existing heap file to start fresh.
            File heapFile = new File("int_string_vector100d_tuple.in");
            if (heapFile.exists()) {
                heapFile.delete();
            }
            
            // Initialize MiniBase.
            // Here we use a page size of 4096 bytes (as set in GlobalConst.java).
            SystemDefs sysdef = new SystemDefs(dbPath, 4096, 50, "LRU");

            // Create a new heap file.
            Heapfile heap = new Heapfile("int_string_vector100d_tuple.in");

            // Create a new tuple.
            Tuple tuple = new Tuple();

            // Define the schema: an integer, a string, and a vector100D.
            AttrType[] attrTypes = new AttrType[3];
            attrTypes[0] = new AttrType(AttrType.attrInteger);
            attrTypes[1] = new AttrType(AttrType.attrString);
            attrTypes[2] = new AttrType(AttrType.attrVector100D);

            // Define the maximum length for the string field.
            short[] strSizes = new short[1];
            strSizes[0] = 30;  // maximum length for the string field

            // Set the header for the tuple with three fields.
            tuple.setHdr((short)3, attrTypes, strSizes);
            
            // Set the integer field (first field).
            tuple.setIntFld(1, 42);
            
            // Set the string field (second field).
            tuple.setStrFld(2, "Hello MiniBase Vector");
            
            // Create a sample vector for the third field.
            // For example, we fill the vector with values 0, 1, 2, ..., 99.
            short[] vectorData = new short[100];
            for (int i = 0; i < 100; i++) {
                vectorData[i] = (short)i;
            }
            Vector100Dtype vector = new Vector100Dtype(vectorData);
            tuple.set100DVectFld(3, vector);

            // Get the tuple's byte array.
            byte[] rec = tuple.getTupleByteArray();
            System.out.println("Tuple length after setHdr: " + rec.length);

            // Insert the tuple record into the heap file.
            RID rid = heap.insertRecord(rec);
            System.out.println("Inserted tuple with RID: " + rid.toString());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
