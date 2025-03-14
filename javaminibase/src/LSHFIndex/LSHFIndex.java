package LSHFIndex;

import global.*;
import global.RID;
import btree.KeyClass;
import index.IndexException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class LSHFIndex implements Serializable {
    private int h;  // number of hash functions per layer
    private int L;  // number of layers
    // For simplicity, we store all entries in an in‑memory list.
    private List<LSHFEntry> entries;
    
    public LSHFIndex(int h, int L) {
        this.h = h;
        this.L = L;
        entries = new ArrayList<>();
    }
    
    // Insert a new entry into the index.
    public void insert(KeyClass key, RID rid) throws IndexException, IOException {
        if (!(key instanceof Vector100DKey))
            throw new IndexException("Key must be of type Vector100DKey");
        LSHFEntry entry = new LSHFEntry((Vector100DKey) key, rid);
        entries.add(entry);
    }
    
    // Delete an entry from the index.
    public void delete(KeyClass key, RID rid) throws IndexException, IOException {
        if (!(key instanceof Vector100DKey))
            throw new IndexException("Key must be of type Vector100DKey");
        Vector100DKey vKey = (Vector100DKey) key;
        for (int i = 0; i < entries.size(); i++) {
            LSHFEntry entry = entries.get(i);
            if(entry.key.toString().equals(vKey.toString()) && entry.rid.equals(rid)) {
                entries.remove(i);
                return;
            }
        }
        throw new IndexException("Entry not found");
    }
    
    // Range search: return all entries whose key is within 'distance' of the given key.
    public List<LSHFEntry> rangeSearch(KeyClass key, int distance) throws IndexException, IOException {
        if (!(key instanceof Vector100DKey))
            throw new IndexException("Key must be of type Vector100DKey");
        Vector100DKey vKey = (Vector100DKey) key;
        List<LSHFEntry> result = new ArrayList<>();
        for (LSHFEntry entry : entries) {
            int dist = computeDistance(vKey.key, entry.key.key);
            if (dist <= distance)
                result.add(entry);
        }
        return result;
    }
    
    // Nearest neighbor search: return the top 'count' entries nearest to the key.
    public List<LSHFEntry> nnSearch(KeyClass key, int count) throws IndexException, IOException {
        if (!(key instanceof Vector100DKey))
            throw new IndexException("Key must be of type Vector100DKey");
        Vector100DKey vKey = (Vector100DKey) key;
        List<LSHFEntry> result = new ArrayList<>(entries);
        result.sort((e1, e2) -> {
            int d1 = computeDistance(vKey.key, e1.key.key);
            int d2 = computeDistance(vKey.key, e2.key.key);
            return Integer.compare(d1, d2);
        });
        if (count > result.size())
            count = result.size();
        return result.subList(0, count);
    }

    public void writeIndexToFile(String filename) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(this); // Serialize the entire index object
            System.out.println("LSHF Index successfully written to file: " + filename);
        } catch (IOException e) {
            System.err.println("Error writing LSHF Index to file: " + e.getMessage());
            throw e;
        }
    }

    
    // Helper method: compute Euclidean distance between two vectors (rounded to int).
    private int computeDistance(Vector100Dtype v1, Vector100Dtype v2) {
        float dist = 0;
        for (int i = 0; i < 100; i++){
            int diff = v1.getValue(i) - v2.getValue(i);
            dist += diff * diff;
        }
        return (int) Math.sqrt(dist);
    }
}
