package LSHFIndex;

import btree.KeyClass;
import global.Vector100Dtype;

public class Vector100DKey extends KeyClass {
  
  private Vector100Dtype value;

  public Vector100DKey(Vector100Dtype val) {
    value = val;
  }

  public Vector100Dtype getValue() {
    return value;
  }

  @Override
  public String toString() {
    // Could print or summarize the 100D vector
    return "[Vector100DKey: " + value + "]";
  }
}
