package tests;

import index.*;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFEntry;
import global.*;
import heap.Tuple;
import iterator.CondExpr;
import iterator.FldSpec;
import iterator.RelSpec;
import iterator.Iterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple test program that:
 * 1) Creates an in-memory LSHFIndex and inserts some 100D vectors.
 * 2) Runs a RSIndexScan (range search).
 * 3) Runs a NNIndexScan (nearest neighbors).
 */
public class RSNNTest {

    public static void main(String[] args) {
        System.out.println("Starting RSNNTest...");

        // Step 1: Create an in-memory LSHFIndex and populate it
        // Typically you'd open a persistent index on disk, 
        // but here we just demonstrate the logic with a brand-new index.
        LSHFIndex index = new LSHFIndex(5, 10);

        // We'll store a handful of 100D vectors with trivial data for demonstration.
        // For real tests, you'd use actual 100D data.
        // We'll do 3 vectors:
        short[] v1_arr = new short[100];
        short[] v2_arr = new short[100];
        short[] v3_arr = new short[100];
        for(int i=0; i<100; i++){
            v1_arr[i] = (short)(i);
            v2_arr[i] = (short)(i+2);
            v3_arr[i] = (short)(i+4);
        }
        Vector100Dtype v1 = new Vector100Dtype(v1_arr);  // distance ~ 0 from itself
        Vector100Dtype v2 = new Vector100Dtype(v2_arr);  // slightly different
        Vector100Dtype v3 = new Vector100Dtype(v3_arr);

        // We'll store them in the LSHFIndex with different RIDs, say:
        // rid1.pageNo.pid = 1, rid2.pageNo.pid = 2, rid3.pageNo.pid = 3
        RID rid1 = new RID();
        rid1.pageNo = new PageId(1);
        rid1.slotNo = 0;

        RID rid2 = new RID();
        rid2.pageNo = new PageId(2);
        rid2.slotNo = 0;

        RID rid3 = new RID();
        rid3.pageNo = new PageId(3);
        rid3.slotNo = 0;

        try {
            index.insert(new Vector100DKey(v1), rid1);
            index.insert(new Vector100DKey(v2), rid2);
            index.insert(new Vector100DKey(v3), rid3);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Step 2: Let's test RSIndexScan
        // We'll define a trivial set of parameters for the constructor
        IndexType idxType = new IndexType(IndexType.LSHFIndex);
        String relName = "MyRel";
        String indName = "MyLSHIndex"; // typically the on-disk index name
        // We'll have a schema of 1 attribute if we only care about the vector
        AttrType[] myTypes = new AttrType[1];
        myTypes[0] = new AttrType(AttrType.attrVector100D);

        short[] str_sizes = new short[0]; // no strings
        int noInFlds = 1;  // we have just 1 attribute
        int noOutFlds = 1; // or 2? We'll see
        FldSpec[] outFlds = new FldSpec[1];
        outFlds[0] = new FldSpec(new RelSpec(RelSpec.outer), 1);

        CondExpr[] selects = null; // not used
        int fldNum = 1;  // field number of our vector attribute

        // We'll do a "query" vector ~ v2, and pick distance = 10
        Vector100Dtype query = v2;
        int distance = 10;

        // We can create an RSIndexScan with these parameters
        RSIndexScan rangeScan = null;
        try {
            rangeScan = new RSIndexScan(
                idxType, relName, indName,
                myTypes, str_sizes,
                noInFlds, noOutFlds, 
                outFlds, selects,
                fldNum, query, distance
            );
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("\n-- Testing RSIndexScan with distance=10 around vector2 --");
        try {
            Tuple t = null;
            while( (t = rangeScan.get_next()) != null ) {
                // We stored 2 fields in the code: 
                // Field1: vector
                // Field2: integer for rid's pageno
                // But in the constructor we said outFlds was 1. Actually, we are retrieving 2 fields.
                // We'll just read them out.
                try {
                    Vector100Dtype vect = t.get100DVectFld(1);
                    int pagePid = t.getIntFld(2);
                    System.out.println("Got a tuple: RID(pageNo=" + pagePid + ")"
                            + " vector first dim = " + vect.getValue(0));
                } catch(Exception ee) {
                    ee.printStackTrace();
                }
            }
        } catch (IOException|IndexException e) {
            e.printStackTrace();
        }

        // Step 3: Test NNIndexScan
        // We pick top 2 nearest neighbors to v1
        int kCount = 2;
        NNIndexScan nnScan = null;
        try {
            nnScan = new NNIndexScan(
                idxType, relName, indName,
                myTypes, str_sizes,
                noInFlds, noOutFlds,
                outFlds, selects,
                fldNum, v1, kCount
            );
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("\n-- Testing NNIndexScan for top-2 nearest to vector1 --");
        try {
            Tuple t = null;
            while( (t = nnScan.get_next()) != null ) {
                try {
                    Vector100Dtype vect = t.get100DVectFld(1);
                    int pagePid = t.getIntFld(2);
                    System.out.println("Got a neighbor: RID(pageNo=" + pagePid + ")"
                             + " vector first dim = " + vect.getValue(0));
                } catch(Exception ee) {
                    ee.printStackTrace();
                }
            }
        } catch (IOException|IndexException e) {
            e.printStackTrace();
        }

        // close
        try {
            rangeScan.close();
            nnScan.close();
        } catch(Exception e) {
            e.printStackTrace();
        }

        System.out.println("\nRSNNTest done.");
    }
}
