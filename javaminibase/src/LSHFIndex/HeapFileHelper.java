package LSHFIndex;

import global.SystemDefs;
import heap.Heapfile;
import heap.Scan;
import heap.Tuple;
import global.RID;
import global.AttrType;

import java.util.Arrays;


public class HeapFileHelper {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java LSHFIndex.HeapFileHelper <DBNAME> <HEAPFILENAME> <mode>");
            System.out.println("Modes:");
            System.out.println("  check - Check if the heap file exists.");
            System.out.println("  dump  - Dump the records from the heap file.");
            return;
        }

        String dbName = args[0];
        String heapFileName = args[1];
        String mode = args[2];


        new SystemDefs(dbName, 0, 50, "LRU");

        if (mode.equalsIgnoreCase("check")) {
            checkHeapFileExists(heapFileName);
        } else if (mode.equalsIgnoreCase("dump")) {
            dumpHeapFileRecords(heapFileName);
        } else {
            System.out.println("Invalid mode! Use 'check' or 'dump'.");
        }
    }


    public static void checkHeapFileExists(String heapFileName) {
        try {
            System.out.println("\nChecking if heap file exists in MiniBase...");
            Heapfile heapfile = new Heapfile(heapFileName);
            System.out.println("Heapfile exists: " + heapFileName);
        } catch (Exception e) {
            System.out.println("Heapfile does NOT exist in MiniBase.");
            e.printStackTrace();
        }
    }


    public static void dumpHeapFileRecords(String heapFileName) {
        try {
            Heapfile heapfile = new Heapfile(heapFileName);
            Scan scan = heapfile.openScan();
            Tuple tuple = new Tuple();
            RID rid = new RID();

            System.out.println("\nDumping records from heap file: " + heapFileName);

            int recordCount = 0;
            while ((tuple = scan.getNext(rid)) != null) {
                recordCount++;

                AttrType[] attrTypes = new AttrType[]{ new AttrType(AttrType.attrString) };
                short[] strSizes = new short[]{30}; // Modify based on your schema
                tuple.setHdr((short) attrTypes.length, attrTypes, strSizes);

                System.out.println("➡ Record #" + recordCount + ": " + Arrays.toString(tuple.getTupleByteArray()));
            }

            scan.closescan();
            System.out.println("Heap file scan complete. Total records: " + recordCount);
        } catch (Exception e) {
            System.out.println("Error reading heap file records.");
            e.printStackTrace();
        }
    }
}
