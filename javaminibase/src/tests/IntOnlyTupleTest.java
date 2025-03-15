package tests;

import heap.Heapfile;
import heap.Tuple;
import global.AttrType;
import global.SystemDefs;
import global.RID;
import java.io.File;

public class IntOnlyTupleTest {
    public static void main(String[] args) {
        try {
            // Define valid Windows paths.
            String dbPath = "C:\\tmp\\test_intonly_tuple_db";
            String logPath = "C:\\tmp\\test_intonly_tuple_log";

            // Ensure the base directory exists.
            File tmpDir = new File("C:\\tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            
            // Delete an existing heap file to start fresh.
            File heapFile = new File("intonly_tuple.in");
            if (heapFile.exists()) {
                heapFile.delete();
            }
            
            // Initialize MiniBase.
            // Using 1024 for the page size to honor GlobalConst.MINIBASE_PAGESIZE.
            SystemDefs sysdef = new SystemDefs(dbPath, 1024, 50, "LRU");

            // Create a new heap file.
            Heapfile intHeapFile = new Heapfile("intonly_tuple.in");

            // Create a new tuple.
            Tuple tuple = new Tuple();

            // Define the schema: a single integer field.
            AttrType[] attrTypes = new AttrType[1];
            attrTypes[0] = new AttrType(AttrType.attrInteger);

            // Call setHdr to set up the tuple header.
            tuple.setHdr((short)1, attrTypes, null);
            
            // Set the integer field.
            tuple.setIntFld(1, 42);

            // Get the tuple's byte array. This should now be small.
            byte[] rec = tuple.getTupleByteArray();
            System.out.println("Tuple length after setHdr: " + rec.length);

            // Insert the tuple record into the heap file.
            RID rid = intHeapFile.insertRecord(rec);
            System.out.println("Inserted tuple with RID: " + rid.toString());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

