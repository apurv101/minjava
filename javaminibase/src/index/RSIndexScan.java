package index;

import global.*;
import heap.*;
import iterator.*;
import LSHFIndex.LSHFIndex;      // your new package
import LSHFIndex.LSHFFileRangeScan; // specialized range scan
import btree.KeyClass;

/**
 * RSIndexScan (Range Search Index Scan)
 * This class acts like an iterator that returns tuples from
 * a heapfile that lie within a certain "distance" of a given query vector.
 */
public class RSIndexScan extends Iterator {

  private AttrType[] _types;
  private short[] _str_sizes;
  private int _noInFlds;
  private int _noOutFlds;
  private FldSpec[] _outFlds;
  private CondExpr[] _selects;
  private int _fldNum;
  private Vector100Dtype _query;
  private int _distance;

  private Heapfile _hf;
  private LSHFIndex _lshIndex;
  private LSHFFileRangeScan _rangeScan;
  
  private Tuple _tuple;
  private boolean _done;

  /**
   * RSIndexScan constructor
   *
   * @param index      The type of index (should be LSHFIndex).
   * @param relName    The heapfile name for the underlying relation.
   * @param indName    The filename for the index itself (the LSHF file).
   * @param types      The attribute types of the relation
   * @param str_sizes  The string sizes for the relation
   * @param noInFlds   The number of input fields
   * @param noOutFlds  The number of output fields
   * @param outFlds    The projection for the output
   * @param selects    Any selection conditions to check
   * @param fldNum     The field number that the index is built on
   * @param query      The 100D vector used as the query
   * @param distance   The distance threshold
   * @throws Exception on errors
   */
  public RSIndexScan(
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
      int       distance
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
    _distance  = distance;
    _done      = false;

    // Open the heapfile for the relation
    _hf = new Heapfile(relName);

    // Confirm the index type is your LSHFIndex
    if (index.indexType != IndexType.LSHFIndex) {
      throw new UnsupportedOperationException("RSIndexScan only supports LSHFIndex for now.");
    }

    // Open or create the LSHFIndex for the given filename
    _lshIndex = new LSHFIndex(indName, /* h= */ 0, /* L= */0); 
    // ^ Typically you'd keep track of h, L, or store them in the index header.
    //   This constructor signature might differ from your code.

    // Then open a range-based scan on that index
    // The LSHFFileRangeScan returns RIDs of entries within 'distance' of _query.
    _rangeScan = _lshIndex.rangeSearch(
        new LSHFIndex.Vector100DKey(_query),
        _distance
    );
  }

  /**
   * Returns the next matching tuple, or null if done.
   */
  @Override
  public Tuple get_next() throws Exception {
    if (_done) return null;

    // The rangeScan returns (KeyClass, RID) pairs.
    RID rid = new RID();
    KeyClass k = _rangeScan.get_next(rid);
    if (k == null) {
      // means no more entries
      _done = true;
      return null;
    }

    // We have the rid => retrieve the corresponding tuple from the heapfile
    Tuple tmp = _hf.getRecord(rid);
    // Now we can apply any select conditions in _selects
    if (PredEval.Eval(_selects, tmp, null, _types, null)) {
      // We also might do projection
      // Return the projected fields
      Tuple res = new Tuple();
      res.setHdr((short)_noOutFlds, _types, _str_sizes); // might need adjusting
      Projection.Project(tmp, _types, res, _outFlds, _noOutFlds);
      return res;
    } else {
      // If it doesn't pass the selection, skip it => get_next() again
      return get_next();  
    }
  }

  @Override
  public void close() throws Exception {
    if (!_done) {
      if (_rangeScan != null) {
        _rangeScan.close();
      }
      _done = true;
    }
  }
}
