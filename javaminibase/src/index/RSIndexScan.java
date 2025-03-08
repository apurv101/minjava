package index;

import global.AttrType;
import global.IndexType;
import global.Vector100Dtype;
import global.Vector100DKey;
import global.RID;
import index.IndexException;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFEntry;
import iterator.FldSpec;
import iterator.CondExpr;
import iterator.Iterator;
import java.io.IOException;
import java.util.List;

public class RSIndexScan extends Iterator {
    private IndexType indexType;
    private String relName;
    private String indName;
    private AttrType[] types;
    private short[] str_sizes;
    private int noInFlds;
    private int noOutFlds;
    private FldSpec[] outFlds;
    private CondExpr[] selects;
    private int fldNum;
    private Vector100Dtype query;
    private int distance;  // threshold distance
    
    private LSHFIndex lshfIndex;
    private List<LSHFEntry> resultList;
    private int current;
    
    public RSIndexScan(IndexType index,
                       String relName,
                       String indName,
                       AttrType[] types,
                       short[] str_sizes,
                       int noInFlds,
                       int noOutFlds,
                       FldSpec[] outFlds,
                       CondExpr[] selects,
                       int fldNum,
                       Vector100Dtype query,
                       int distance)
            throws IOException, IndexException {
        this.indexType = index;  // should be IndexType.LSHFIndex
        this.relName = relName;
        this.indName = indName;
        this.types = types;
        this.str_sizes = str_sizes;
        this.noInFlds = noInFlds;
        this.noOutFlds = noOutFlds;
        this.outFlds = outFlds;
        this.selects = selects;
        this.fldNum = fldNum;
        this.query = query;
        this.distance = distance;
        this.current = 0;
        
        // Create (or open) an LSHFIndex instance.
        // In a full implementation, this index would be persistent.
        lshfIndex = new LSHFIndex(5, 10);
        
        // Assume the index is already populated.
        resultList = lshfIndex.rangeSearch(new Vector100DKey(query), distance);
    }
    
    public Tuple get_next() throws IOException, IndexException {
        if (current >= resultList.size())
            return null;
        LSHFEntry entry = resultList.get(current++);
        Tuple t = new Tuple();
        // For demonstration, output a tuple with two fields:
        // 1) attrVector100D (the key)
        // 2) an integer representing RID's page number.
        AttrType[] retTypes = new AttrType[2];
        retTypes[0] = new AttrType(AttrType.attrVector100D);
        retTypes[1] = new AttrType(AttrType.attrInteger);
        short[] retStrSizes = new short[1];
        retStrSizes[0] = 0;
        try {
            t.setHdr((short)2, retTypes, retStrSizes);
            t.set100DVectFld(1, entry.key.key);
            t.setIntFld(2, entry.rid.pageNo.pid);
        } catch (Exception e) {
            throw new IndexException(e, "RSIndexScan: error building tuple");
        }
        return t;
    }
    
    public void close() throws IOException, IndexException {
        // No resources to close in this in-memory simulation.
    }
}
