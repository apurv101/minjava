package tests;

import heap.Heapfile;
import heap.Tuple;
import global.AttrType;
import global.SystemDefs;
import global.RID;
import java.io.File;

public class IntStringTupleTest {
    public static void main(String[] args) {
        try {
            // Define valid Windows paths.
            String dbPath = "C:\\tmp\\test_intstring_tuple_db";
            String logPath = "C:\\tmp\\test_intstring_tuple_log";

            // Ensure the base directory exists.
            File tmpDir = new File("C:\\tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            
            // Delete an existing heap file to start fresh.
            File heapFile = new File("intstring_tuple.in");
            if (heapFile.exists()) {
                heapFile.delete();
            }
            
            // Initialize MiniBase.
            // Using 1024 for the page size to honor GlobalConst.MINIBASE_PAGESIZE.
            SystemDefs sysdef = new SystemDefs(dbPath, 1024, 50, "LRU");

            // Create a new heap file.
            Heapfile intStringHeapFile = new Heapfile("intstring_tuple.in");

            // Create a new tuple.
            Tuple tuple = new Tuple();

            // Define the schema: an integer field and a string field.
            AttrType[] attrTypes = new AttrType[2];
            attrTypes[0] = new AttrType(AttrType.attrInteger);
            attrTypes[1] = new AttrType(AttrType.attrString);

            // Define the string sizes. Since there is one string field, we specify its maximum length.
            short[] strSizes = new short[1];
            strSizes[0] = 30;  // Maximum length of 30 characters.

            // Set the header for the tuple with two fields.
            tuple.setHdr((short)2, attrTypes, strSizes);
            
            // Set the integer field (first field).
            tuple.setIntFld(1, 42);

            // Set the string field (second field).
            tuple.setStrFld(2, "Hello MiniBase");

            // Get the tuple's byte array.
            byte[] rec = tuple.getTupleByteArray();
            System.out.println("Tuple length after setHdr: " + rec.length);

            // Insert the tuple record into the heap file.
            RID rid = intStringHeapFile.insertRecord(rec);
            System.out.println("Inserted tuple with RID: " + rid.toString());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
