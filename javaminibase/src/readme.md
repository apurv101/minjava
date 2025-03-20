
# README: High-Level Explanation of the Main Logic

This project builds upon the MiniBase educational database system, extending it to store and query 100-dimensional integer vectors. The main logic covers several tasks:

## 1. Adjusting MiniBase Parameters
We modified global constants in `GlobalConst.java`:
- **Page size** (e.g. from 1024 to 4096 or 8192 bytes),
- **Max space** (e.g. 4096 or 8192),
- **Buffer pool size** (e.g. 4000 frames),
- ...
These changes allow MiniBase to handle larger data more comfortably.

## 2. New Vector100Dtype
We introduced a **new attribute type** (`attrVector100D`) to represent a 100-dimensional vector of short integers (each dimension in the range −10000 to 10000). This required:
- Adding **Vector100Dtype.java**, a class containing an array of 100 shorts and getters/setters,
- Hooking into the core `Tuple.java` logic to support `get100DVectFld(...)` and `set100DVectFld(...)`, and
- Extending `Convert.java` to handle reading/writing 100D vectors in byte arrays.

## 3. Distance-based Condition Expressions
We extended the system so that if a condition involves a vector field, instead of a simple equality comparison, the system can compute the **distance** between two vectors. For instance:
- `CompareTupleWithTuple` now returns an **integer distance** if the field type is `attrVector100D`.
- A new `distance` field was added to `CondExpr`, and operators like `aopLT`, `aopGE`, etc., are interpreted in terms of “distance < threshold,” “distance >= threshold,” etc.

## 4. LSH-Forest Index
We created a new index type, **LSHFIndex**, for approximate similarity search on vectors. This is inspired by standard Locality-Sensitive Hashing. Our implementation has:
- **LSHFIndex.java** – an in-memory structure that stores a list of `(key, RID)` pairs (where the key is a 100D vector). We also have methods:
  - `insert(...)`
  - `delete(...)`
  - `rangeSearch(key, distance)`
  - `nnSearch(key, count)`
- **LSHFEntry.java** – a small class to hold a `(Vector100DKey, RID)` pair.
- **LSHFFileScan**, **LSHFFileRangeScan**, **LSHFFileNNScan** – classes that wrap these index operations in a more standard “scan” interface.

## 5. Index-based Access Methods
We added:
- **RSIndexScan** – a new iterator for “range search” queries (`rangeSearch(...)`).
- **NNIndexScan** – a new iterator for “nearest neighbor” queries (`nnSearch(...)`).
They open an **LSH-Forest** index file, search it with the user’s parameters, and yield matching tuples.

## 6. Batch Insert Utility
`BatchInsert.java` automates database creation and index building. Command-line usage is:
```
java LSHFIndex.BatchInsert h L DATAFILENAME DBNAME
```
- **h** = number of hash functions per layer,
- **L** = number of layers,
- **DATAFILENAME** = file with tuples to load,
- **DBNAME** = used for naming the database files.

It:
1. Reads the data file, which specifies how many attributes and what types they are (int, real, string, 100D-vector).
2. Inserts each tuple into a heap file.
3. For each 100D-vector attribute, it builds an LSH-Forest index (with the given h and L) and writes it to disk so it can be used in subsequent queries.

## 7. Query Execution
We also support queries via a program called `Query` (if implemented fully). The query spec file can contain lines like:
- `Range(QA, T, D, ...)`
- `NN(QA, T, K, ...)`
Here:
- **QA** is the attribute number of the vector field,
- **T** is a file containing the 100-dimensional target vector,
- **D** is a distance threshold,
- **K** is how many nearest neighbors to request,
- The final “...” is which fields to project in the output.

When the query runs:
1. It loads the indexes from disk (if `INDEXOPTION == "Y"`).
2. Uses the relevant `RSIndexScan` or `NNIndexScan` to find matches.
3. Prints results and I/O statistics (via `PCounter`).

## 8. Testing & Demonstration
We included several test classes in `/tests/`:
- **LSHFIndexTest**: Basic demonstration of inserting three vectors, range-searching, nearest-neighbor searching, and deletion.
- **RSNNTest**: Demonstration of `RSIndexScan` and `NNIndexScan` usage.
- Various smaller classes (e.g. `IntStringVector100DTupleTest`) show how to store and retrieve tuples that contain both normal attributes (like int/string) plus the 100D vector.

## 9. Example Commands

Below are sample commands for a typical workflow:

```
cd minjava/javaminibase/src

java LSHFIndex/BatchInsert.java 5 10 sample_data_1.txt myDB

java LSHFIndex/BatchInsert.java 5 10 sample_data_2.txt myDB

java LSHFIndex.Query MyDB rquery1.txt Y 50

java LSHFIndex.Query MyDB rquery2.txt Y 50

java LSHFIndex.Query MyDB nquery1.txt Y 50

java LSHFIndex.Query MyDB nquery2.txt Y 50

java LSHFIndex.Query MyDB nquery3.txt Y 50
```

