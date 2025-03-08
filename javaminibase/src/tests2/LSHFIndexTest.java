package tests;

import global.*;
import lshfindex.*;
import heap.RID;

public class LSHFIndexTest {
    public static void main(String[] args) {
        try {
            // Create an LSHFIndex with h = 4 and L = 3 (sample parameters)
            LSHFIndex index = new LSHFIndex(4, 3);
            
            // Create sample vectors (100-dimensional)
            short[] a = new short[100];
            short[] b = new short[100];
            short[] c = new short[100];
            for (int i = 0; i < 100; i++) {
                a[i] = (short) i;            // vector increasing from 0 to 99
                b[i] = (short) (100 - i);      // vector decreasing from 100 to 1
                c[i] = (short) (i % 10);       // repeating pattern 0-9
            }
            Vector100Dtype vA = new Vector100Dtype(a);
            Vector100Dtype vB = new Vector100Dtype(b);
            Vector100Dtype vC = new Vector100Dtype(c);
            
            // Create keys for these vectors
            Vector100DKey keyA = new Vector100DKey(vA);
            Vector100DKey keyB = new Vector100DKey(vB);
            Vector100DKey keyC = new Vector100DKey(vC);
            
            // Create dummy RIDs. (Assuming PageId is in the global package.)
            RID ridA = new RID(new PageId(1), 1);
            RID ridB = new RID(new PageId(2), 2);
            RID ridC = new RID(new PageId(3), 3);
            
            // Insert the entries into the LSHFIndex.
            index.insert(keyA, ridA);
            index.insert(keyB, ridB);
            index.insert(keyC, ridC);
            
            // Now perform a range search:
            // Find all entries within a threshold distance of keyA.
            int threshold = 50;  // Adjust this threshold based on expected distances.
            System.out.println("Range search results (distance <= " + threshold + "):");
            for (LSHFEntry entry : index.rangeSearch(keyA, threshold)) {
                System.out.println(entry);
            }
            
            // Now perform a nearest neighbor search:
            // Find the top 2 nearest neighbors to keyA.
            int nnCount = 2;
            System.out.println("Nearest neighbor search results (top " + nnCount + "):");
            for (LSHFEntry entry : index.nnSearch(keyA, nnCount)) {
                System.out.println(entry);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
