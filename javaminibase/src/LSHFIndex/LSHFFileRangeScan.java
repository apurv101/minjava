package lshfindex;

import index.IndexException;
import global.AttrType;
import global.Vector100Dtype;
import heap.Tuple;
import heap.RID;
import iterator.Iterator;
import java.io.IOException;
import java.util.List;

public class LSHFFileRangeScan extends Iterator {
    private List<LSHFEntry> entries;
    private int current;
    
    public LSHFFileRangeScan(LSHFIndex index, global.KeyClass key, int distance) throws IOException, IndexException {
        if (!(key instanceof global.Vector100DKey))
            throw new IndexException("Key must be of type Vector100DKey");
        entries = index.rangeSearch(key, distance);
        current = 0;
    }
    
    public Tuple get_next() throws IOException, IndexException {
        if (current >= entries.size()) return null;
        LSHFEntry entry = entries.get(current++);
        Tuple t = new Tuple();
        AttrType[] types = new AttrType[2];
        types[0] = new AttrType(global.AttrType.attrVector100D);
        types[1] = new AttrType(global.AttrType.attrInteger);
        short[] strSizes = new short[1];
        strSizes[0] = 0;
        try {
            t.setHdr((short)2, types, strSizes);
            t.set100DVectFld(1, entry.key.key);
            t.setIntFld(2, entry.rid.pageNo.pid);
        } catch (Exception e) {
            throw new IndexException(e, "Error building tuple in LSHFFileRangeScan");
        }
        return t;
    }
    
    public void close() throws IOException, IndexException { }
}
