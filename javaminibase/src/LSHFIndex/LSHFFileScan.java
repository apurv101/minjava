package lshfindex;

import index.Iterator;
import global.*;
import heap.*;
import java.io.IOException;
import java.util.List;

public class LSHFFileScan extends Iterator {
    private LSHFIndex index;
    private List<LSHFEntry> entries;
    private int current;
    
    public LSHFFileScan(LSHFIndex index) throws IOException, IndexException {
        this.index = index;
        // For a full scan, use a range search with a very large distance.
        entries = index.rangeSearch(new Vector100DKey(new Vector100Dtype()), Integer.MAX_VALUE);
        current = 0;
    }
    
    public Tuple get_next() throws IOException, IndexException {
        if (current >= entries.size()) return null;
        LSHFEntry entry = entries.get(current++);
        Tuple t = new Tuple();
        AttrType[] types = new AttrType[2];
        types[0] = new AttrType(AttrType.attrVector100D);
        types[1] = new AttrType(AttrType.attrInteger); // For RID (simplified)
        short[] strSizes = new short[1];
        strSizes[0] = 0;
        try {
            t.setHdr((short)2, types, strSizes);
            t.set100DVectFld(1, entry.key.key);
            // For demonstration, we use the page number of RID as an integer key.
            t.setIntFld(2, entry.rid.pageNo.pid);
        } catch (Exception e) {
            throw new IndexException(e, "Error building tuple in LSHFFileScan");
        }
        return t;
    }
    
    public void close() throws IOException, IndexException { }
}
