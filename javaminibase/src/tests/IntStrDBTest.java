package tests;

import heap.Heapfile;
import heap.Tuple;
import global.AttrType;
import global.SystemDefs;
import global.RID;

public class IntStrDBTest {
    public static void main(String[] args) {
        try {
            // Initialize MiniBase with chosen parameters.
            // For example: page size 4096 bytes, 4096 pages, 4000 buffer frames.
            String dbPath = "/tmp/test_intstr_db";
            String logPath = "/tmp/test_intstr_log";
            SystemDefs sysdef = new SystemDefs(dbPath, 4096, 4000, "Clock");

            // Create (or open) a heap file. Here the file is named "intstr.in"
            Heapfile intStrFile = new Heapfile("intstr.in");

            // Define the schema for a tuple with two fields:
            // field 1: integer, field 2: string.
            AttrType[] attrTypes = new AttrType[2];
            attrTypes[0] = new AttrType(AttrType.attrInteger);
            attrTypes[1] = new AttrType(AttrType.attrString);
            
            // For string fields, provide the maximum length (here we use 30)
            short[] strSizes = new short[1];
            strSizes[0] = 30;
            
            // Create a new tuple and set its header.
            Tuple tuple = new Tuple();
            tuple.setHdr((short)2, attrTypes, strSizes);

            // Set field 1 (integer) to a sample value.
            tuple.setIntFld(1, 12345);

            // Set field 2 (string) to a sample value.
            tuple.setStrFld(2, "Hello MiniBase");

            // Insert the tuple into the heap file.
            RID rid = intStrFile.insertRecord(tuple.returnTupleByteArray());
            System.out.println("Inserted integer/string tuple into 'intstr.in' with RID: " + rid.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
