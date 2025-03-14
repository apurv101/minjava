import global.*;
import heap.*;
import java.io.IOException;
import java.util.Random;

public class SimpleVectorDB {
    public static void main(String[] args) {
        try {
            // Step 1: Initialize MiniBase
            SystemDefs sysdef = new SystemDefs("minibaseDB", 4096, 4000, "Clock");

            // Step 2: Define the schema (Vector100Dtype)
            AttrType[] attrTypes = new AttrType[1];
            attrTypes[0] = new AttrType(AttrType.attrVector100D); // New Vector Attribute Type

            short[] strSizes = new short[0]; // No string attributes
            Tuple t = new Tuple();
            t.setHdr((short) 1, attrTypes, strSizes);
            int tupleSize = t.size();

            // Step 3: Create a heap file
            Heapfile heapfile = new Heapfile("vectorTable");

            // Step 4: Create a sample 100D vector
            Vector100Dtype vector = new Vector100Dtype();
            Random rand = new Random();
            for (int i = 0; i < 100; i++) {
                vector.setDimension(i, rand.nextInt(20001) - 10000); // Values between -10000 and 10000
            }

            // Step 5: Insert the vector into a tuple
            Tuple newTuple = new Tuple(tupleSize);
            newTuple.setHdr((short) 1, attrTypes, strSizes);
            newTuple.set100DVectFld(1, vector);

            // Step 6: Insert the tuple into the heap file
            RID rid = heapfile.insertRecord(newTuple.returnTupleByteArray());

            System.out.println("Vector inserted into database.");

            // Step 7: Retrieve and display the stored vector
            Scan scan = heapfile.openScan();
            Tuple retrievedTuple;
            RID retrievedRid = new RID();
            while ((retrievedTuple = scan.getNext(retrievedRid)) != null) {
                Vector100Dtype retrievedVector = retrievedTuple.get100DVectFld(1);
                System.out.println("Retrieved Vector: " + retrievedVector);
            }
            scan.closescan();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
