package LSHFIndex;

import global.Vector100DKey;
import global.RID;

import java.io.Serializable;

public class LSHFEntry implements Serializable {
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


