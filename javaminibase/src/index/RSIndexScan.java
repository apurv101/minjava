package index;

import global.*;
import heap.Heapfile;
import iterator.*;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFFileRangeScan;
import LSHFIndex.Vector100DKey;

/**
 * RSIndexScan implements a new range-search index scan.
 */
public class RSIndexScan extends Iterator {
  
  private AttrType[] types;
  private short[] str_sizes;
  private int noInFlds;
  private int noOutFlds;
  private FldSpec[] outFlds;
  private CondExpr[] selects;
  private int fldNum;
  private Vector100Dtype query;
  private int distance;
  
  private Heapfile hf;
  private LSHFIndex lshIndex;
  private LSHFFileRangeScan rangeScan;
  
  private boolean done;
  private Tuple op_buf;
  
  public RSIndexScan(
      IndexType index,
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
      int distance
  ) throws Exception {
    this.types = types;
    this.str_sizes = str_sizes;
    this.noInFlds = noInFlds;
    this.noOutFlds = noOutFlds;
    this.outFlds = outFlds;
    this.selects = selects;
    this.fldNum = fldNum;
    this.query = query;
    this.distance = distance;
    this.done = false;
    
    hf = new Heapfile(relName);
    
    if(index.indexType != IndexType.LSHFIndex)
      throw new UnsupportedOperationException("RSIndexScan only supports LSHFIndex.");
    
    // Open the LSHF index
    lshIndex = new LSHFIndex(indName, 3, 4); // Example: h=3, L=4; adjust as needed.
    
    // Open a range scan on the LSHF index.
    rangeScan = lshIndex.rangeSearch(new Vector100DKey(query), distance);
    
    op_buf = new Tuple();
    // Set header for op_buf as needed
    op_buf.setHdr((short)noOutFlds, types, str_sizes);
  }
  
  @Override
  public Tuple get_next() throws Exception {
    if(done) return null;
    
    RID rid = new RID();
    KeyClass key = rangeScan.get_next(rid);
    if(key == null) {
      done = true;
      return null;
    }
    
    Tuple t = hf.getRecord(rid);
    // Evaluate condition (if any)
    if(PredEval.Eval(selects, t, null, types, null)) {
      Tuple proj = new Tuple();
      proj.setHdr((short)noOutFlds, types, str_sizes);
      Projection.Project(t, types, proj, outFlds, noOutFlds);
      return proj;
    } else {
      return get_next();
    }
  }
  
  @Override
  public void close() {
    if(!done) {
      try {
        rangeScan.close();
      } catch(Exception e) { /* handle exception */ }
      done = true;
    }
  }
}
