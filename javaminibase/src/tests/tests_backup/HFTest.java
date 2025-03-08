package tests;

import java.io.*;
import java.util.*;
import java.lang.*;
import heap.*;
import bufmgr.*;
import diskmgr.*;
import global.*;
import chainexception.*;

/** 
 * The HFDriver class provides a series of tests for the Heapfile layer.
 */
class HFDriver extends TestDriver implements GlobalConst {

  private static final boolean OK = true;
  private static final boolean FAIL = false;
  
  private int choice;
  private static final int reclen = 32;  // fixed-size record length
  
  public HFDriver() {
    super("hptest");
    choice = 100;      // big enough for file to occupy > 1 data page
    // choice = 2000;   // bigger test
    // choice = 5;      // smaller test
  }

  public boolean runTests() {
    System.out.println("\n" + "Running " + testName() + " tests...." + "\n");

    // Initialize Minibase
    SystemDefs sysdef = new SystemDefs(dbpath, 100, 100, "Clock");
   
    // Clean up anything from prior runs
    String newdbpath;
    String newlogpath;
    String remove_logcmd;
    String remove_dbcmd;
    String remove_cmd = "/bin/rm -rf ";
    
    newdbpath = dbpath;
    newlogpath = logpath;
    
    remove_logcmd = remove_cmd + logpath;
    remove_dbcmd  = remove_cmd + dbpath;
    
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } catch (IOException e) {
      System.err.println("IO error: " + e);
    }
    
    remove_logcmd = remove_cmd + newlogpath;
    remove_dbcmd  = remove_cmd + newdbpath;
    
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } catch (IOException e) {
      System.err.println("IO error: " + e);
    }
    
    // Now run the actual tests
    boolean _pass = runAllTests();
    
    // Clean up again
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } catch (IOException e) {
      System.err.println("IO error: " + e);
    }
    
    System.out.print("\n" + "..." + testName() + " tests ");
    System.out.print(_pass == OK ? "completely successfully" : "failed");
    System.out.print(".\n\n");
    
    return _pass;
  }
  
  // ------------------------------------------------------------
  // Test1
  // ------------------------------------------------------------
  protected boolean test1() {
    // 1) Reset counters
    PCounter.initialize();

    System.out.println("\n  Test 1: Insert and scan fixed-size records\n");
    boolean status = OK;
    RID rid = new RID();
    Heapfile f = null;

    System.out.println("  - Create a heap file\n");
    try {
      f = new Heapfile("file_1");
    } catch (Exception e) {
      status = FAIL;
      System.err.println("*** Could not create heap file\n");
      e.printStackTrace();
    }

    // Check that no pages are pinned
    if (status == OK && 
        SystemDefs.JavabaseBM.getNumUnpinnedBuffers() != SystemDefs.JavabaseBM.getNumBuffers()) {
      System.err.println("*** The heap file has left pages pinned\n");
      status = FAIL;
    }

    // Insert records
    if (status == OK) {
      System.out.println("  - Add " + choice + " records to the file\n");
      for (int i = 0; (i < choice) && (status == OK); i++) {
        // Construct a fixed-length record
        DummyRecord rec = new DummyRecord(reclen);
        rec.ival = i;
        rec.fval = (float) (i * 2.5);
        rec.name = "record" + i;

        try {
          rid = f.insertRecord(rec.toByteArray());
        } catch (Exception e) {
          status = FAIL;
          System.err.println("*** Error inserting record " + i + "\n");
          e.printStackTrace();
        }

        // Check pinned pages
        if (status == OK &&
            SystemDefs.JavabaseBM.getNumUnpinnedBuffers() != SystemDefs.JavabaseBM.getNumBuffers()) {
          System.err.println("*** Insertion left a page pinned\n");
          status = FAIL;
        }
      }
      
      // Confirm record count
      try {
        if (f.getRecCnt() != choice) {
          status = FAIL;
          System.err.println("*** File reports " + f.getRecCnt() + 
                             " records, not " + choice + "\n");
        }
      } catch (Exception e) {
        status = FAIL;
        e.printStackTrace();
      }
    }
    
    // Now scan them back
    Scan scan = null;
    if (status == OK) {
      System.out.println("  - Scan the records just inserted\n");
      try {
        scan = f.openScan();
      } catch (Exception e) {
        status = FAIL;
        System.err.println("*** Error opening scan\n");
        e.printStackTrace();
      }

      if (status == OK &&
          SystemDefs.JavabaseBM.getNumUnpinnedBuffers() == SystemDefs.JavabaseBM.getNumBuffers()) {
        System.err.println("*** The heap-file scan has not pinned the first page\n");
        status = FAIL;
      }
    }

    if (status == OK) {
      int len, i = 0;
      DummyRecord rec = null;
      Tuple tuple;
      
      boolean done = false;
      while (!done) {
        try {
          tuple = scan.getNext(rid);
          if (tuple == null) {
            done = true;
            break;
          }
        } catch (Exception e) {
          status = FAIL;
          e.printStackTrace();
          break;
        }

        if (!done && status == OK) {
          try {
            rec = new DummyRecord(tuple);
          } catch (Exception e) {
            System.err.println("" + e);
            e.printStackTrace();
          }

          len = tuple.getLength();
          if (len != reclen) {
            System.err.println("*** Record " + i + " had unexpected length " + len + "\n");
            status = FAIL;
            break;
          } else if (SystemDefs.JavabaseBM.getNumUnpinnedBuffers() == SystemDefs.JavabaseBM.getNumBuffers()) {
            System.err.println("On record " + i + ":\n");
            System.err.println("*** The heap-file scan has not left its page pinned\n");
            status = FAIL;
            break;
          }
          String name = "record" + i;
          
          if ((rec.ival != i)
              || (rec.fval != (float) i * 2.5)
              || (!name.equals(rec.name))) {
            System.err.println("*** Record " + i + " differs from what we inserted\n");
            System.err.println("rec.ival: " + rec.ival + " should be " + i + "\n");
            System.err.println("rec.fval: " + rec.fval + " should be " + (i * 2.5) + "\n");
            System.err.println("rec.name: " + rec.name + " should be " + name + "\n");
            status = FAIL;
            break;
          }
        }
        i++;
      }
      
      // Final check after scan
      if (status == OK) {
        if (SystemDefs.JavabaseBM.getNumUnpinnedBuffers() != SystemDefs.JavabaseBM.getNumBuffers()) {
          System.err.println("*** The heap-file scan has not unpinned its page after finishing\n");
          status = FAIL;
        } else if (i != choice) {
          status = FAIL;
          System.err.println("*** Scanned " + i + " records instead of " + choice + "\n");
        }
      }
    }
    
    if (status == OK) {
      System.out.println("  Test 1 completed successfully.\n");
    }

    // 2) Print the I/O counters
    System.out.println("Test1 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test1 - Write I/Os : " + PCounter.wcounter);

    return status;
  }
  
  // ------------------------------------------------------------
  // Test2
  // ------------------------------------------------------------
  protected boolean test2() {
    // 1) Reset counters
    PCounter.initialize();

    System.out.println("\n  Test 2: Delete fixed-size records\n");
    boolean status = OK;
    Scan scan = null;
    RID rid = new RID();
    Heapfile f = null;

    System.out.println("  - Open the same heap file as test 1\n");
    try {
      f = new Heapfile("file_1");
    } catch (Exception e) {
      status = FAIL;
      System.err.println(" Could not open heapfile");
      e.printStackTrace();
    }

    // Delete half the records (the odd ones).
    if (status == OK) {
      System.out.println("  - Delete half the records\n");
      try {
        scan = f.openScan();
      } catch (Exception e) {
        status = FAIL;
        System.err.println("*** Error opening scan\n");
        e.printStackTrace();
      }
    }
    
    if (status == OK) {
      int i = 0;
      Tuple tuple;
      boolean done = false;

      while (!done) {
        try {
          tuple = scan.getNext(rid);
          if (tuple == null) {
            done = true;
          }
        } catch (Exception e) {
          status = FAIL;
          e.printStackTrace();
          break;
        }

        if (!done && status == OK) {
          // Delete the odd-numbered records
          if (i % 2 == 1) {
            try {
              status = f.deleteRecord(rid);
            } catch (Exception e) {
              status = FAIL;
              System.err.println("*** Error deleting record " + i + "\n");
              e.printStackTrace();
              break;
            }
          }
        }
        i++;
      }
    }
    
    if (scan != null) {
      try {
        scan.closescan();
      } catch (Exception e) {
        e.printStackTrace();
        status = FAIL;
      }
    }

    // Check pinned pages again
    if (status == OK &&
        SystemDefs.JavabaseBM.getNumUnpinnedBuffers() != SystemDefs.JavabaseBM.getNumBuffers()) {
      System.err.println("*** Deletion left a page pinned\n");
      status = FAIL;
    }
    
    // Now scan the remaining records (the even ones)
    if (status == OK) {
      System.out.println("  - Scan the remaining records\n");
      try {
        scan = f.openScan();
      } catch (Exception e) {
        status = FAIL;
        System.err.println("*** Error opening scan\n");
        e.printStackTrace();
      }
    }
      
    if (status == OK) {
      int i = 0;
      DummyRecord rec;
      Tuple tuple;
      boolean done = false;

      while (!done) {
        try {
          tuple = scan.getNext(rid);
          if (tuple == null) {
            done = true;
          }
        } catch (Exception e) {
          status = FAIL;
          e.printStackTrace();
          break;
        }

        if (!done && status == OK) {
          try {
            rec = new DummyRecord(tuple);
          } catch (Exception e) {
            System.err.println("" + e);
            e.printStackTrace();
            status = FAIL;
            break;
          }
          // Because we deleted the odd ones, the records left are i, i+2, i+4, ...
          // i is incremented by 2 in the original data
          // So rec.ival should match i, rec.fval should be i*2.5
          if ((rec.ival != i) || (rec.fval != (float) i * 2.5)) {
            System.err.println("*** Record " + i + " differs from what we inserted\n");
            System.err.println("rec.ival: " + rec.ival + " should be " + i + "\n");
            System.err.println("rec.fval: " + rec.fval + " should be " + (i * 2.5) + "\n");
            status = FAIL;
            break;
          }
          i += 2; // skip to the next even number
        }
      }
    }

    if (status == OK) {
      System.out.println("  Test 2 completed successfully.\n");
    }

    // Print counters
    System.out.println("Test2 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test2 - Write I/Os : " + PCounter.wcounter);

    return status;
  }

  // ------------------------------------------------------------
  // Test3
  // ------------------------------------------------------------
  protected boolean test3() {
    // 1) Reset counters
    PCounter.initialize();

    System.out.println("\n  Test 3: Update fixed-size records\n");
    boolean status = OK;
    Scan scan = null;
    RID rid = new RID();
    Heapfile f = null; 

    System.out.println("  - Open the same heap file as tests 1 and 2\n");
    try {
      f = new Heapfile("file_1");
    } catch (Exception e) {
      status = FAIL;
      System.err.println("*** Could not create heap file\n");
      e.printStackTrace();
    }

    // We'll set rec.fval = 7*i for the i-th record (which is actually all the even records).
    if (status == OK) {
      System.out.println("  - Change the records\n");
      try {
        scan = f.openScan();
      } catch (Exception e) {
        status = FAIL;
        System.err.println("*** Error opening scan\n");
        e.printStackTrace();
      }
    }

    if (status == OK) {
      int i = 0;
      DummyRecord rec;
      Tuple tuple;
      boolean done = false;
      
      while (!done) {
        try {
          tuple = scan.getNext(rid);
          if (tuple == null) {
            done = true;
            break;
          }
        } catch (Exception e) {
          status = FAIL;
          e.printStackTrace();
          break;
        }
        
        if (!done && status == OK) {
          try {
            rec = new DummyRecord(tuple);
          } catch (Exception e) {
            System.err.println("" + e);
            e.printStackTrace();
            status = FAIL;
            break;
          }

          // rec.ival should match i (the even series), so let's set rec.fval=7*i
          rec.fval = (float) (7 * i);

          Tuple newTuple = null; 
          try {
            newTuple = new Tuple(rec.toByteArray(), 0, rec.getRecLength());
          } catch (Exception e) {
            status = FAIL;
            System.err.println("" + e);
            e.printStackTrace();
            break;
          }
          try {
            status = f.updateRecord(rid, newTuple);
          } catch (Exception e) {
            status = FAIL;
            e.printStackTrace();
            break;
          }

          if (status != OK) {
            System.err.println("*** Error updating record " + i + "\n");
            break;
          }
          i += 2;  // Because we only had even records left
        }
      }
    }
    
    // Check pinned pages after updating
    if (scan != null) {
      try {
        scan.closescan();
      } catch (Exception e) {
        e.printStackTrace();
        status = FAIL;
      }
    }

    if (status == OK && 
        SystemDefs.JavabaseBM.getNumUnpinnedBuffers() != SystemDefs.JavabaseBM.getNumBuffers()) {
      System.err.println("*** Updating left pages pinned\n");
      status = FAIL;
    }
    
    // Now check that the updates are indeed there
    if (status == OK) {
      System.out.println("  - Check that the updates are really there\n");
      try {
        scan = f.openScan();
      } catch (Exception e) {
        status = FAIL;
        e.printStackTrace();
      }
      if (status == FAIL) {
        System.err.println("*** Error opening scan\n");
      }
    }

    if (status == OK) {
      int i = 0;
      DummyRecord rec, rec2;
      Tuple tuple, tuple2;
      boolean done = false;
      
      while (!done) {
        try {
          tuple = scan.getNext(rid);
          if (tuple == null) {
            done = true;
            break;
          }
        } catch (Exception e) {
          status = FAIL;
          e.printStackTrace();
          break;
        }
        
        if (!done && status == OK) {
          try {
            rec = new DummyRecord(tuple);
          } catch (Exception e) {
            System.err.println("" + e);
            status = FAIL;
            break;
          }

          // Let's also test getRecord()
          try {
            tuple2 = f.getRecord(rid);
          } catch (Exception e) {
            status = FAIL;
            System.err.println("*** Error getting record " + i + "\n");
            e.printStackTrace();
            break;
          }
          
          rec2 = null;
          try {
            rec2 = new DummyRecord(tuple2);
          } catch (Exception e) {
            System.err.println("" + e);
            e.printStackTrace();
            status = FAIL;
            break;
          }

          // We expect rec.ival == i, rec.fval == 7*i
          // (since we only had even numbers left, i increments by 2)
          if ((rec.ival != i) || (rec.fval != (float) (i * 7))
              || (rec2.ival != i) || (rec2.fval != (float) (i * 7))) {
            System.err.println("*** Record " + i + " differs from our update\n");
            System.err.println("rec.ival: " + rec.ival + " should be " + i + "\n");
            System.err.println("rec.fval: " + rec.fval + " should be " + (i * 7.0) + "\n");
            status = FAIL;
            break;
          }
        }
        i += 2;  // Because we only had the even ones left
      }
    }
    
    if (status == OK) {
      System.out.println("  Test 3 completed successfully.\n");
    }

    // Print counters
    System.out.println("Test3 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test3 - Write I/Os : " + PCounter.wcounter);

    return status;
  }

  // ------------------------------------------------------------
  // Test4
  // ------------------------------------------------------------
  protected boolean test4() {
    // 1) Reset counters
    PCounter.initialize();

    System.out.println("\n  Test 4: Test some error conditions\n");
    boolean status = OK;
    Scan scan = null;
    RID rid = new RID();
    Heapfile f = null; 
    
    try {
      f = new Heapfile("file_1");
    } catch (Exception e) {
      status = FAIL;
      System.err.println("*** Could not create heap file\n");
      e.printStackTrace();
    }

    if (status == OK) {
      System.out.println("  - Try to change the size of a record\n");
      try {
        scan = f.openScan();
      } catch (Exception e) {
        status = FAIL;
        System.err.println("*** Error opening scan\n");
        e.printStackTrace();
      }
    }

    if (status == OK) {
      Tuple tuple;
      DummyRecord rec;
      
      // Grab the first record
      try {
        tuple = scan.getNext(rid);
        if (tuple == null) {
          status = FAIL;
          System.err.println("*** No records found in test4\n");
        } else {
          rec = new DummyRecord(tuple);
          int len = tuple.getLength();

          // Attempt to shorten the record by 1 byte
          Tuple newTuple = null;
          try {
            newTuple = new Tuple(rec.toByteArray(), 0, len - 1);
          } catch (Exception e) {
            System.err.println("" + e);
            e.printStackTrace();
          }
          try {
            status = f.updateRecord(rid, newTuple);
          } catch (ChainException e) {
            // Expecting InvalidUpdateException
            status = checkException(e, "heap.InvalidUpdateException");
            if (status == FAIL) {
              System.err.println("**** Shortening a record");
              System.out.println("  --> Failed as expected \n");
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
          
          if (status == OK) {
            status = FAIL;
            System.err.println("######The expected exception was not thrown\n");
          } else {
            status = OK;
          }

          if (status == OK) {
            // Attempt to lengthen the record by 1 byte
            try {
              newTuple = new Tuple(rec.toByteArray(), 0, len + 1);
            } catch (Exception e) {
              System.err.println("" + e);
              e.printStackTrace();
            }
            try {
              status = f.updateRecord(rid, newTuple);
            } catch (ChainException e) {
              status = checkException(e, "heap.InvalidUpdateException");
              if (status == FAIL) {
                System.err.println("**** Lengthening a record");
                System.out.println("  --> Failed as expected \n");
              }
            } catch (Exception e) {
              e.printStackTrace();
            }

            if (status == OK) {
              status = FAIL;
              System.err.println("The expected exception was not thrown\n");
            } else {
              status = OK;
            }
          }
        }
      } catch (Exception e) {
        status = FAIL;
        e.printStackTrace();
      }
    }
    
    // Insert a record that's too long
    if (scan != null) {
      try {
        scan.closescan();
      } catch (Exception e) {
        e.printStackTrace();
        status = FAIL;
      }
    }

    if (status == OK) {
      System.out.println("  - Try to insert a record that's too long\n");
      byte[] record = new byte[MINIBASE_PAGESIZE + 4]; // bigger than a page
      try {
        rid = f.insertRecord(record);
      } catch (ChainException e) {
        // Expecting SpaceNotAvailableException
        status = checkException(e, "heap.SpaceNotAvailableException");
        if (status == FAIL) {
          System.err.println("**** Inserting a too-long record");
          System.out.println("  --> Failed as expected \n");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
      
      if (status == OK) {
        status = FAIL;
        System.err.println("The expected exception was not thrown\n");
      } else {
        status = OK;
      }
    }
    
    if (status == OK) {
      System.out.println("  Test 4 completed successfully.\n");
    }

    // Print counters
    System.out.println("Test4 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test4 - Write I/Os : " + PCounter.wcounter);

    return (status == OK);
  }

  // ------------------------------------------------------------
  // Test5
  // ------------------------------------------------------------
  protected boolean test5() {
    // 1) Reset counters
    PCounter.initialize();

    // (No real logic here, just placeholders)
    boolean status = OK;
    
    // 2) Print counters
    System.out.println("Test5 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test5 - Write I/Os : " + PCounter.wcounter);

    return status;
  }
  
  // ------------------------------------------------------------
  // Test6
  // ------------------------------------------------------------
  protected boolean test6() {
    // 1) Reset counters
    PCounter.initialize();

    boolean status = OK;
    
    // 2) Print counters
    System.out.println("Test6 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test6 - Write I/Os : " + PCounter.wcounter);

    return status;
  }
  
  // ------------------------------------------------------------
  // runAllTests
  // ------------------------------------------------------------
  protected boolean runAllTests() {
    boolean _passAll = OK;
    
    if (!test1()) { _passAll = FAIL; }
    if (!test2()) { _passAll = FAIL; }
    if (!test3()) { _passAll = FAIL; }
    if (!test4()) { _passAll = FAIL; }
    if (!test5()) { _passAll = FAIL; }
    if (!test6()) { _passAll = FAIL; }
    
    return _passAll;
  }

  protected String testName() {
    return "Heap File";
  }
}

/** 
 * Simple "struct" or record-like class for fixed-length fields 
 */
class DummyRecord {
  public int    ival; 
  public float  fval;      
  public String name;  
  private int   reclen;
  private byte[] data;

  /** Default constructor */
  public DummyRecord() {}

  /** Constructor specifying record length */
  public DummyRecord(int _reclen) {
    setRecLen(_reclen);
    data = new byte[_reclen];
  }

  /** 
   * Constructs a DummyRecord from a byte array 
   */
  public DummyRecord(byte[] arecord) throws IOException {
    setIntRec(arecord);
    setFloRec(arecord);
    setStrRec(arecord);
    data = arecord;
    setRecLen(arecord.length);
  }

  /** 
   * Constructs a DummyRecord from a Tuple 
   */
  public DummyRecord(Tuple _atuple) throws IOException {
    data = new byte[_atuple.getLength()];
    data = _atuple.getTupleByteArray();
    setRecLen(_atuple.getLength());
    
    setIntRec(data);
    setFloRec(data);
    setStrRec(data);
  }

  /** Convert this object to a byte array (for insertion) */
  public byte[] toByteArray() throws IOException {
    Convert.setIntValue(ival, 0, data);
    Convert.setFloValue(fval, 4, data);
    Convert.setStrValue(name, 8, data);
    return data;
  }

  /** 
   * Sets integer field from the byte array 
   */
  public void setIntRec(byte[] _data) throws IOException {
    ival = Convert.getIntValue(0, _data);
  }

  /** 
   * Sets float field from the byte array 
   */
  public void setFloRec(byte[] _data) throws IOException {
    fval = Convert.getFloValue(4, _data);
  }

  /** 
   * Sets string field from the byte array 
   */
  public void setStrRec(byte[] _data) throws IOException {
    name = Convert.getStrValue(8, _data, reclen - 8);
  }

  /** Set the record length */
  public void setRecLen(int size) {
    reclen = size;
  }

  /** Get the record length */
  public int getRecLength() {
    return reclen;
  }
}

/** The HFTest class: main driver */
public class HFTest {

  public static void main(String argv[]) {
    HFDriver hd = new HFDriver();
    boolean dbstatus = hd.runTests();

    if (!dbstatus) {
      System.err.println("Error encountered during buffer manager tests:\n");
      Runtime.getRuntime().exit(1);
    }

    Runtime.getRuntime().exit(0);
  }
}
