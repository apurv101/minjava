package LSHFIndex;

import global.Vector100DKey;
import global.RID;

public class LSHFEntry {
    public Vector100DKey key;
    public RID rid;
    
    public LSHFEntry(Vector100DKey key, RID rid) {
        this.key = key;
        this.rid = rid;
    }
    
    public String toString() {
        return "<" + key.toString() + ", " + rid.toString() + ">";
    }
}


