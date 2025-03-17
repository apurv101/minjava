package global;

import java.io.Serializable;

public class Vector100Dtype implements Serializable {
    // Each short can range from -10000 to 10000.
    private short[] values;

    public Vector100Dtype() {
        values = new short[100];
    }

    // Possibly a constructor that accepts an existing array of shorts
    public Vector100Dtype(short[] arr) {
        if (arr.length != 100) {
            throw new IllegalArgumentException("Must have 100 elements");
        }
        values = new short[100];
        System.arraycopy(arr, 0, values, 0, 100);
    }

    public short getValue(int index) {
        return values[index];
    }

    public void setValue(int index, short val) {
        values[index] = val;
    }

    public short[] getValues() {
        return values;
    }


}
