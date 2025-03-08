package index;

import global.AttrType;
import global.IndexType;
import global.Vector100Dtype;
import global.Vector100DKey;
import heap.Tuple;
import heap.RID;
import index.IndexException;
import lshfindex.LSHFIndex;
import lshfindex.LSHFEntry;
import iterator.FldSpec;
import iterator.CondExpr;
import iterator.Iterator;
import java.io.IOException;
import java.util.List;

/**
 * NNIndexScan performs a nearest neighbor search on an LSH-forest index.
 * It returns the top 'count' tuples nearest to the given query vector.
 */
public class NNIndexScan extends Iterator {
    // parameters
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
    private int count;  // number of nearest neighbors to return

    // Our new LSHF index instance
    private LSHFIndex lshfIndex;
    // List of results obtained by a nearest neighbor search
    private List<LSHFEntry> resultList;
    private int current;
    
    public NNIndexScan(IndexType index,
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
                       int count)
            throws IOException, IndexException {
        this.indexType = index; // should be IndexType.LSHFIndex
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
        this.count = count;
        this.current = 0;
        
        // Open the LSHF index.
        lshfIndex = new LSHFIndex(5, 10);
        
        // Assume the index is already populated.
        // Perform a nearest neighbor search using the query vector:
        resultList = lshfIndex.nnSearch(new Vector100DKey(query), count);
    }
    
    public Tuple get_next() throws IOException, IndexException {
        if (current >= resultList.size()) 
            return null;
        LSHFEntry entry = resultList.get(current++);
        Tuple t = new Tuple();
        // For demonstration, we return a tuple with two fields:
        // 1) the vector key (attrVector100D)
        // 2) an integer (e.g., the RID's page number)
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
            throw new IndexException(e, "NNIndexScan: error building tuple");
        }
        return t;
    }
    
    public void close() throws IOException, IndexException {
        // Nothing to close for our in-memory simulation.
    }
}
