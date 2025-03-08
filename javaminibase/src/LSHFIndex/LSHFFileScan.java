package LSHFIndex;

import java.io.IOException;
import btree.KeyClass;
import global.RID;
import index.IndexFileScan;

/**
 * A simple scan that might just iterate over all index entries in the LSH-Forest
 * or handle a limited range, depending on constructor parameters.
 */
public class LSHFFileScan implements IndexFileScan {

  private LSHFIndex lshIndex; 
  private KeyClass loKey;
  private KeyClass hiKey;

  // Possibly store an in-memory list of results or a pointer to pages, etc.

  public LSHFFileScan(LSHFIndex lsh, KeyClass lo, KeyClass hi) {
    this.lshIndex = lsh;
    this.loKey = lo;
    this.hiKey = hi;
  }

  /**
   * get_next() returns the next key,RID pair from the scan.
   */
  @Override
  public KeyClass get_next(RID rid) throws IOException {
    // If no more entries, return null
    // Otherwise, fill 'rid' and return the key
    return null;
  }

  @Override
  public void delete_current() throws IOException {
    // optionally allow deletion during scan
    // or do nothing
  }

  @Override
  public void close() throws IOException {
    // free up resources
  }
}
