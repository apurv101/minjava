package tests;

import heap.Heapfile;
import heap.Tuple;
import global.AttrType;
import global.SystemDefs;
import global.RID;
import java.io.File;

public class IntStrDBTest {
    public static void main(String[] args) {
        try {
            // Define valid Windows paths.
            String dbPath = "C:\\tmp\\test_intstr_db";
            String logPath = "C:\\tmp\\test_intstr_log";

            // Ensure the base directory exists.
            File tmpDir = new File("C:\\tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            
            // Optionally delete an existing heap file to start fresh.
            File heapFile = new File("intstr.in");
            if (heapFile.exists()) {
                heapFile.delete();
            }
            
            // Initialize MiniBase.
            SystemDefs sysdef = new SystemDefs(dbPath, 4096, 100, "LRU");

            // Create a new heap file.
            Heapfile intStrFile = new Heapfile("intstr.in");

            // Define schema: field 1: integer, field 2: string.
            AttrType[] attrTypes = new AttrType[2];
            attrTypes[0] = new AttrType(AttrType.attrInteger);
            attrTypes[1] = new AttrType(AttrType.attrString);
            
            // For string fields, provide max length.
            short[] strSizes = new short[1];
            strSizes[0] = 30;
            
            // Create a tuple.
            Tuple tuple = new Tuple();
            tuple.setHdr((short)2, attrTypes, strSizes);
            tuple.setIntFld(1, 12345);
            tuple.setStrFld(2, "Hello MiniBase");

            // Insert the tuple.
            RID rid = intStrFile.insertRecord(tuple.returnTupleByteArray());
            System.out.println("Inserted integer/string tuple with RID: " + rid.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
