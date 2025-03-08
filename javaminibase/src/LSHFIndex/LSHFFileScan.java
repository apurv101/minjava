package LSHFIndex;

import index.IndexException;
import global.AttrType;
import global.Vector100Dtype;
import heap.Tuple;
import global.RID;
import iterator.Iterator;
import java.io.IOException;
import java.util.List;

public class LSHFFileScan extends Iterator {
    private LSHFIndex index;
    private List<LSHFEntry> entries;
    private int current;
    
    public LSHFFileScan(LSHFIndex index) throws IOException, IndexException {
        this.index = index;
        // For a full scan, we use a range search with a very high threshold.
        entries = index.rangeSearch(new global.Vector100DKey(new Vector100Dtype()), Integer.MAX_VALUE);
        current = 0;
    }
    
    public Tuple get_next() throws IOException, IndexException {
        if (current >= entries.size()) return null;
        LSHFEntry entry = entries.get(current++);
        Tuple t = new Tuple();
        AttrType[] types = new AttrType[2];
        types[0] = new AttrType(global.AttrType.attrVector100D);
        types[1] = new AttrType(global.AttrType.attrInteger); // for demonstration
        short[] strSizes = new short[1];
        strSizes[0] = 0;
        try {
            t.setHdr((short)2, types, strSizes);
            t.set100DVectFld(1, entry.key.key);
            t.setIntFld(2, entry.rid.pageNo.pid);  // simplified RID conversion
        } catch (Exception e) {
            throw new IndexException(e, "Error building tuple in LSHFFileScan");
        }
        return t;
    }
    
    public void close() throws IOException, IndexException { }
}
