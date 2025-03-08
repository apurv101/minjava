package global;

import index.KeyClass;

public class Vector100DKey implements KeyClass {
    public Vector100Dtype key;
    
    public Vector100DKey(Vector100Dtype key) {
        this.key = key;
    }
    
    public Vector100DKey() {
        this.key = new Vector100Dtype();
    }
    
    public int compareTo(Object obj) {
        if (!(obj instanceof Vector100DKey))
            throw new ClassCastException("Not a Vector100DKey");
        Vector100DKey other = (Vector100DKey) obj;
        // For simplicity, compare based on the sum of squares of components.
        int sum1 = 0, sum2 = 0;
        for (int i = 0; i < 100; i++) {
            sum1 += key.getValue(i) * key.getValue(i);
            sum2 += other.key.getValue(i) * other.key.getValue(i);
        }
        return Integer.compare(sum1, sum2);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < 100; i++) {
            sb.append(key.getValue(i));
            if (i < 99)
                sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
