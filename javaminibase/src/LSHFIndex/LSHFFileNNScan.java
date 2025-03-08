package LSHFIndex;

import java.io.IOException;
import btree.KeyClass;
import global.RID;
import index.IndexFileScan;

public class LSHFFileNNScan implements IndexFileScan {

  private LSHFIndex lshIndex;
  private KeyClass queryKey;
  private int k;

  public LSHFFileNNScan(LSHFIndex idx, KeyClass key, int k) {
    this.lshIndex = idx;
    this.queryKey = key;
    this.k = k;
    // Possibly gather the top-k neighbors
  }

  @Override
  public KeyClass get_next(RID rid) throws IOException {
    // Return the next-nearest entry, or null if done
    return null;
  }

  @Override
  public void delete_current() throws IOException {
  }

  @Override
  public void close() throws IOException {
  }
}
