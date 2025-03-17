package LSHFIndex;

import global.*;
import global.RID;
import btree.KeyClass;
import index.IndexException;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static LSHFIndex.Query.computeDistance;

public class LSHFIndex implements Serializable {
    private int h;  // number of hash functions per layer
    private int L;  // number of layers
    // For simplicity, we store all entries in an in‑memory list.
    private List<LSHFEntry> entries;
    
    public LSHFIndex(int h, int L) throws IOException, ClassNotFoundException {
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
System.out.println("count : " + count);
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




    public void loadIndexFromFile(String filename) throws IOException, ClassNotFoundException {
        File indexFile = new File(filename);
        if (!indexFile.exists()) {
            System.out.println("DEBUG: Index file not found, starting fresh." + filename);
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(indexFile))) {
            LSHFIndex loadedIndex = (LSHFIndex) ois.readObject();
            this.entries = loadedIndex.entries; // Load saved entries
            System.out.println("DEBUG: Loaded " + entries.size() + " entries from index file.");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("ERROR: Failed to load index from file.");
            e.printStackTrace();
            throw e;
        }
    }

}
