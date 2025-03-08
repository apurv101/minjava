package tests3;

import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFEntry;
import global.Vector100DKey;
import global.Vector100Dtype;
import global.RID;
import global.PageId; // import the PageId class
import index.IndexException;

import java.io.IOException;
import java.util.List;

public class LSHFIndexTest {
    
    // Dummy implementation of a page identifier for testing purposes.
    // Now DummyPageId extends PageId so it can be used with RID.
    public static class DummyPageId extends PageId {
        public DummyPageId(int pid) {
            super(pid); // Assuming PageId has a constructor that accepts an int
        }
        
        @Override
        public String toString() {
            return "DummyPageId(" + this.pid + ")";
        }
    }
    
    // Dummy implementation of Vector100Dtype.
    // This creates a 100-dimensional vector with values starting at a given offset.
    public static class DummyVector100D extends Vector100Dtype {
        private int[] values;
        
        public DummyVector100D(int offset) {
            values = new int[100];
            for (int i = 0; i < 100; i++) {
                values[i] = i + offset;
            }
        }
        
        @Override
        public short getValue(int i) {
            return (short) values[i];
        }
        
        @Override
        public String toString() {
            return "DummyVector100D(" + values[0] + ", ...)";
        }
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("Starting LSHFIndex tests...");

            // Create an instance of LSHFIndex with arbitrary parameters h and L.
            LSHFIndex index = new LSHFIndex(4, 3);
            
            // Create dummy vectors.
            // vec1: [0, 1, 2, ..., 99]
            // vec2: [1, 2, 3, ..., 100]
            // vec3: [2, 3, 4, ..., 101]
            DummyVector100D vec1 = new DummyVector100D(0);
            DummyVector100D vec2 = new DummyVector100D(1);
            DummyVector100D vec3 = new DummyVector100D(2);
            
            // Create corresponding keys.
            Vector100DKey key1 = new Vector100DKey(vec1);
            Vector100DKey key2 = new Vector100DKey(vec2);
            Vector100DKey key3 = new Vector100DKey(vec3);
            
            // Create dummy RID objects using the new DummyPageId.
            RID rid1 = new RID(new DummyPageId(1), 0);
            RID rid2 = new RID(new DummyPageId(2), 0);
            RID rid3 = new RID(new DummyPageId(3), 0);
            
            // --- Test Insertion ---
            index.insert(key1, rid1);
            index.insert(key2, rid2);
            index.insert(key3, rid3);
            System.out.println("Insertion test passed.");

            // --- Test Range Search ---
            // Expected distances:
            // Distance(key1, key1) = 0.
            // Distance(key1, key2) = sqrt(100*1^2) = 10.
            // Distance(key1, key3) = sqrt(100*2^2) = 20.
            // Using a distance threshold of 10 should return key1 and key2.
            List<LSHFEntry> rangeResults = index.rangeSearch(key1, 10);
            if (rangeResults.size() != 2) {
                System.err.println("Range search test failed: expected 2 entries, got " + rangeResults.size());
            } else {
                System.out.println("Range search test passed.");
            }
            
            // --- Test Nearest Neighbor Search ---
            // Requesting the top 2 nearest neighbors for key1 should return key1 (distance 0) and key2.
            List<LSHFEntry> nnResults = index.nnSearch(key1, 2);
            if (nnResults.size() != 2) {
                System.err.println("Nearest neighbor search test failed: expected 2 entries, got " + nnResults.size());
            } else {
                if (!nnResults.get(0).key.toString().equals(key1.toString())) {
                    System.err.println("Nearest neighbor search test failed: first entry is not key1.");
                } else {
                    System.out.println("Nearest neighbor search test passed.");
                }
            }
            
            // --- Test Deletion ---
            // Delete key2 and then perform a range search with threshold 10.
            index.delete(key2, rid2);
            rangeResults = index.rangeSearch(key1, 10);
            // After deletion, only key1 should be within the distance threshold.
            if (rangeResults.size() != 1) {
                System.err.println("Deletion test failed: expected 1 entry after deletion, got " + rangeResults.size());
            } else {
                System.out.println("Deletion test passed.");
            }
            
            System.out.println("All tests completed.");
            
        } catch (IOException | IndexException e) {
            e.printStackTrace();
        }
    }
}
