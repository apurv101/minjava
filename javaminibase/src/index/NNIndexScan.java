package index;

import global.*;
import heap.Heapfile;
import iterator.*;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFFileNNScan;
import LSHFIndex.Vector100DKey;

/**
 * NNIndexScan implements a new nearest-neighbor index scan.
 */
public class NNIndexScan extends Iterator {
  
  private AttrType[] types;
  private short[] str_sizes;
  private int noInFlds;
  private int noOutFlds;
  private FldSpec[] outFlds;
  private CondExpr[] selects;
  private int fldNum;
  private Vector100Dtype query;
  private int count;  // number of nearest neighbors to return
  
  private Heapfile hf;
  private LSHFIndex lshIndex;
  private LSHFFileNNScan nnScan;
  
  private boolean done;
  private Tuple op_buf;
  
  public NNIndexScan(
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
      int count
  ) throws Exception {
    this.types = types;
    this.str_sizes = str_sizes;
    this.noInFlds = noInFlds;
    this.noOutFlds = noOutFlds;
    this.outFlds = outFlds;
    this.selects = selects;
    this.fldNum = fldNum;
    this.query = query;
    this.count = count;
    this.done = false;
    
    hf = new Heapfile(relName);
    
    if(index.indexType != IndexType.LSHFIndex)
      throw new UnsupportedOperationException("NNIndexScan only supports LSHFIndex.");
    
    lshIndex = new LSHFIndex(indName, 3, 4); // Example parameters
    nnScan = lshIndex.nnSearch(new Vector100DKey(query), count);
    
    op_buf = new Tuple();
    op_buf.setHdr((short)noOutFlds, types, str_sizes);
  }
  
  @Override
  public Tuple get_next() throws Exception {
    if(done) return null;
    
    RID rid = new RID();
    KeyClass key = nnScan.get_next(rid);
    if(key == null) {
      done = true;
      return null;
    }
    
    Tuple t = hf.getRecord(rid);
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
        nnScan.close();
      } catch(Exception e) { /* handle exception */ }
      done = true;
    }
  }
}
