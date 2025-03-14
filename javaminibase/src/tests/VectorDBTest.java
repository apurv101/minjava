package tests;

import heap.Heapfile;
import heap.Tuple;
import global.AttrType;
import global.SystemDefs;
import global.Vector100Dtype;
import global.RID;

public class VectorDBTest {
    public static void main(String[] args) {
        try {
            // Initialize MiniBase. Adjust the parameters as needed.
            // Here we use:
            //    dbPath = "/tmp/test_vector_db"
            //    logPath = "/tmp/test_vector_log"
            //    pageSize = 4096 bytes, number of pages = 4096, buffer pool size = 4000 frames
            String dbPath = "/tmp/test_vector_db";
            String logPath = "/tmp/test_vector_log";
            SystemDefs sysdef = new SystemDefs(dbPath, 4096, 4000, "Clock");

            // Create (or open) a heap file for storing vector tuples.
            // In MiniBase, a heap file is simply a table.
            Heapfile vectorFile = new Heapfile("vector.in");

            // Create a new tuple that has one field (the vector field).
            // We assume that the new attribute type for 100D vectors is registered in AttrType.
            AttrType[] attrTypes = new AttrType[1];
            attrTypes[0] = new AttrType(AttrType.attrVector100D);
            // (No string sizes needed for non-string attributes.)
            Tuple tuple = new Tuple();
            tuple.setHdr((short)1, attrTypes, null);

            // Create a sample 100D vector.
            Vector100Dtype vector = new Vector100Dtype();
            // For example, set each dimension to i*10 (you can change this as needed)
            for (int i = 0; i < 100; i++) {
                vector.setValue(i, (short)(i * 10));
            }

            // Set the tuple's first field to the created vector.
            tuple.set100DVectFld(1, vector);

            // Insert the tuple into the heap file.
            RID rid = vectorFile.insertRecord(tuple.returnTupleByteArray());
            System.out.println("Inserted vector tuple into 'vector.in' with RID: " + rid.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
