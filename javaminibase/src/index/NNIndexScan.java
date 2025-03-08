package index;

import global.*;
import heap.*;
import iterator.*;
import LSHFIndex.LSHFIndex;
import LSHFIndex.LSHFFileNNScan;
import btree.KeyClass;

/**
 * NNIndexScan (Nearest-Neighbor Index Scan)
 * Returns up to 'count' nearest neighbors to 'query'.
 */
public class NNIndexScan extends Iterator {

  private AttrType[] _types;
  private short[] _str_sizes;
  private int _noInFlds;
  private int _noOutFlds;
  private FldSpec[] _outFlds;
  private CondExpr[] _selects;
  private int _fldNum;
  private Vector100Dtype _query;
  private int _count;

  private Heapfile _hf;
  private LSHFIndex _lshIndex;
  private LSHFFileNNScan _nnScan;
  private boolean _done;

  public NNIndexScan(
      IndexType index,
      String    relName,
      String    indName,
      AttrType[] types,
      short[]   str_sizes,
      int       noInFlds,
      int       noOutFlds,
      FldSpec[] outFlds,
      CondExpr[]selects,
      int       fldNum,
      Vector100Dtype query,
      int       count
  ) throws Exception
  {
    _types     = types;
    _str_sizes = str_sizes;
    _noInFlds  = noInFlds;
    _noOutFlds = noOutFlds;
    _outFlds   = outFlds;
    _selects   = selects;
    _fldNum    = fldNum;
    _query     = query;
    _count     = count;
    _done      = false;

    _hf = new Heapfile(relName);

    if (index.indexType != IndexType.LSHFIndex) {
      throw new UnsupportedOperationException("NNIndexScan only supports LSHFIndex for now.");
    }

    _lshIndex = new LSHFIndex(indName, /* h= */ 0, /* L= */0);

    // Open an LSHFFileNNScan that yields up to 'count' nearest neighbors
    _nnScan = _lshIndex.nnSearch(
        new LSHFIndex.Vector100DKey(_query),
        _count
    );
  }

  @Override
  public Tuple get_next() throws Exception {
    if (_done) return null;

    RID rid = new RID();
    KeyClass k = _nnScan.get_next(rid); 
    if (k == null) {
      _done = true;
      return null;
    }

    Tuple tmp = _hf.getRecord(rid);
    if (PredEval.Eval(_selects, tmp, null, _types, null)) {
      Tuple res = new Tuple();
      res.setHdr((short)_noOutFlds, _types, _str_sizes);
      Projection.Project(tmp, _types, res, _outFlds, _noOutFlds);
      return res;
    } else {
      return get_next();
    }
  }

  @Override
  public void close() throws Exception {
    if (!_done) {
      if (_nnScan != null) {
        _nnScan.close();
      }
      _done = true;
    }
  }
}
