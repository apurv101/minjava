package LSHFIndex;

import java.io.IOException;
import btree.KeyClass;
import global.RID;
import index.IndexFileScan;

public class LSHFFileRangeScan implements IndexFileScan {

  private LSHFIndex lshIndex;
  private KeyClass queryKey;
  private int distance;

  // Possibly store precomputed list of RIDs or a specialized pointer.

  public LSHFFileRangeScan(LSHFIndex idx, KeyClass key, int dist) {
    this.lshIndex = idx;
    this.queryKey = key;
    this.distance = dist;
    // do the LSH-forest logic to find all buckets that match
    // so that we only yield those vectors within 'distance'.
  }

  @Override
  public KeyClass get_next(RID rid) throws IOException {
    // return the next (key, rid) that’s within 'distance' of queryKey
    // or null if no more
    return null;
  }

  @Override
  public void delete_current() throws IOException {
  }

  @Override
  public void close() throws IOException {
  }
}
