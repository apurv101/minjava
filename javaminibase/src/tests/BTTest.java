package tests;

import java.io.*;
import java.util.*;
import java.lang.*;
import heap.*;
import bufmgr.*;
import diskmgr.*;
import global.*;
import btree.*;

/** Note: in JAVA, methods can't be overridden to be more private.
    Therefore, the declaration of all private functions are now declared
    protected as opposed to the private type in C++.
*/

class BTDriver implements GlobalConst {

  public BTreeFile file;
  public int postfix = 0;
  public int keyType;
  public BTFileScan scan;
  
  protected String dbpath;  
  protected String logpath;
  public int deleteFashion;

  public void runTests() {
    Random random = new Random();
    dbpath = "BTREE" + random.nextInt() + ".minibase-db";
    logpath = "BTREE" + random.nextInt() + ".minibase-log";

    // Initialize the database
    SystemDefs sysdef = new SystemDefs(dbpath, 5000, 5000, "Clock");
    System.out.println("\nRunning tests....\n");

    keyType = AttrType.attrInteger;

    // Kill anything that might be hanging around
    String newdbpath;
    String newlogpath;
    String remove_logcmd;
    String remove_dbcmd;
    String remove_cmd = "/bin/rm -rf ";

    newdbpath = dbpath;
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

    // Run the interactive tests via menu
    runAllTests();

    // Clean up again
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } catch (IOException e) {
      System.err.println("IO error: " + e);
    }

    System.out.print("\n... Finished.\n\n");
  }

  private void menu() {
    System.out.println("-------------------------- MENU ------------------");
    System.out.println("\n\n[0]   Naive delete (new file)");
    System.out.println("[1]   Full delete(Default) (new file)");

    System.out.println("\n[2]   Print the B+ Tree Structure");
    System.out.println("[3]   Print All Leaf Pages");
    System.out.println("[4]   Choose a Page to Print");

    System.out.println("\n           ---Integer Key (for choices [6]-[14]) ---");
    System.out.println("\n[5]   Insert a Record");
    System.out.println("[6]   Delete a Record");
    System.out.println("[7]   Test1 (new file): insert n records in order");
    System.out.println("[8]   Test2 (new file): insert n records in reverse order");
    System.out.println("[9]   Test3 (new file): insert n records in random order");
    System.out.println("[10]  Test4 (new file): insert n records in random order and delete m records randomly");
    System.out.println("[11]  Delete some records");

    System.out.println("\n[12]  Initialize a Scan");
    System.out.println("[13]  Scan the next Record");
    System.out.println("[14]  Delete the just-scanned record");

    System.out.println("\n           ---String Key (for choice [15]) ---");
    System.out.println("\n[15]  Test5 (new file): insert n records in random order and delete m records randomly.");

    System.out.println("\n[16]  Close the file");
    System.out.println("[17]  Open which file (input an integer for the file name)");
    System.out.println("[18]  Destroy which file (input an integer for the file name)");

    System.out.println("\n[19]  Quit!");
    System.out.print("Hi, make your choice :");
  }

  protected void runAllTests() {
    PageId pageno = new PageId();
    int key, n, m, num, choice, lowkeyInt, hikeyInt;
    KeyClass lowkey, hikey;
    KeyDataEntry entry;
    RID rid;
    choice = 1;
    deleteFashion = 1; // full delete by default

    try {
      System.out.println(" ********** The file name is: " + "AAA" + postfix + "  **********");
      file = new BTreeFile("AAA" + postfix, keyType, 4, 1); // full delete
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    postfix = 0;

    while (choice != 19) {
      menu();
      try {
        choice = GetStuff.getChoice();

        switch (choice) {
          case 0:
            postfix++;
            deleteFashion = 0; // naive delete
            System.out.println(" ********** The file name is: " + "AAA" + postfix + "  **********");
            file = new BTreeFile("AAA" + postfix, keyType, 100, 0);
            break;

          case 1:
            postfix++;
            deleteFashion = 1; // full delete
            System.out.println(" ********** The file name is: " + "AAA" + postfix + "  **********");
            file = new BTreeFile("AAA" + postfix, keyType, 100, 1);
            break;

          case 2:
            BT.printBTree(file.getHeaderPage());
            break;

          case 3:
            BT.printAllLeafPages(file.getHeaderPage());
            break;

          case 4:
            System.out.println("Please input the page number: ");
            num = GetStuff.getChoice();
            if (num < 0) break;
            BT.printPage(new PageId(num), keyType);
            break;

          case 5:
            keyType = AttrType.attrInteger;
            System.out.println("Please input the integer key to insert: ");
            key = GetStuff.getChoice();
            if (key < 0) break;
            pageno.pid = key;
            rid = new RID(pageno, key);
            file.insert(new IntegerKey(key), rid);
            break;

          case 6:
            keyType = AttrType.attrInteger;
            System.out.println("Please input the integer key to delete: ");
            key = GetStuff.getChoice();
            if (key < 0) break;
            pageno.pid = key;
            rid = new RID(pageno, key);
            file.Delete(new IntegerKey(key), rid);
            break;

          case 7:
            // test1
            file.close();
            postfix++;
            keyType = AttrType.attrInteger;
            System.out.println("Please input the number of keys to insert: ");
            n = GetStuff.getChoice();
            if (n <= 0) break;
            test1(n);
            break;

          case 8:
            // test2
            file.close();
            postfix++;
            keyType = AttrType.attrInteger;
            System.out.println("Please input the number of keys to insert: ");
            n = GetStuff.getChoice();
            if (n <= 0) break;
            test2(n);
            break;

          case 9:
            // test3
            file.close();
            postfix++;
            keyType = AttrType.attrInteger;
            System.out.println("Please input the number of keys to insert: ");
            n = GetStuff.getChoice();
            if (n <= 0) break;
            test3(n);
            break;

          case 10:
            // test4
            file.close();
            postfix++;
            keyType = AttrType.attrInteger;
            System.out.println("Please input the number of keys to insert: ");
            n = GetStuff.getChoice();
            System.out.println("Please input the number of keys to delete: ");
            m = GetStuff.getChoice();
            if (n <= 0 || m < 0) break;
            if (m > n) m = n;
            test4(n, m);
            break;

          case 11:
            // range delete
            keyType = AttrType.attrInteger;
            System.out.println("Please input the LOWER integer key(>=0): ");
            lowkeyInt = GetStuff.getChoice();
            System.out.println("Please input the HIGHER integer key(>=0): ");
            hikeyInt = GetStuff.getChoice();
            if (hikeyInt < 0 || lowkeyInt < 0) break;
            for (key = lowkeyInt; key <= hikeyInt; key++) {
              pageno.pid = key;
              rid = new RID(pageno, key);
              file.Delete(new IntegerKey(key), rid);
            }
            break;

          case 12:
            // new scan
            keyType = AttrType.attrInteger;
            System.out.println("Please input the LOWER integer key (null if -3): ");
            lowkeyInt = GetStuff.getChoice();
            lowkey = new IntegerKey(lowkeyInt);
            System.out.println("Please input the HIGHER integer key (null if -2): ");
            hikeyInt = GetStuff.getChoice();
            hikey = new IntegerKey(hikeyInt);
            if (lowkeyInt == -3) lowkey = null;
            if (hikeyInt == -2) hikey = null;
            if (hikeyInt == -1 || lowkeyInt == -1) break;
            scan = file.new_scan(lowkey, hikey);
            break;

          case 13:
            entry = scan.get_next();
            if (entry != null) {
              System.out.println("SCAN RESULT: " + entry.key + " " + entry.data);
            } else {
              System.out.println("AT THE END OF SCAN!");
            }
            break;

          case 14:
            scan.delete_current();
            break;

          case 15:
            // test5
            file.close();
            postfix++;
            keyType = AttrType.attrString;
            System.out.println("Please input the number of keys to insert: ");
            n = GetStuff.getChoice();
            System.out.println("Please input the number of keys to delete: ");
            m = GetStuff.getChoice();
            if (n <= 0 || m < 0) break;
            if (m > n) m = n;
            test5(n, m);
            break;

          case 16:
            file.close();
            System.out.println(" ********** You closed the file: " + "AAA" + postfix + " **********");
            break;

          case 17:
            file.close();
            n = GetStuff.getChoice();
            System.out.println(" ********** You open the file: " + "AAA" + n + " **********");
            file = new BTreeFile("AAA" + n);
            break;

          case 18:
            file.close();
            n = GetStuff.getChoice();
            System.out.println(" ********** You destroy the file: " + "AAA" + n + " **********");
            file = new BTreeFile("AAA" + n);
            file.destroyFile();
            break;

          case 19:
            // quit
            break;
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("       !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.out.println("       !!         Something is wrong                    !!");
        System.out.println("       !!  Is your DB full? Then exit and rerun it!     !!");
        System.out.println("       !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      }
    }
  }

  // ---------------------------------------------------------------------------
  // test1
  // ---------------------------------------------------------------------------
  void test1(int n) throws Exception {
    // Reset counters at start
    PCounter.initialize();

    try {
      System.out.println(" ********** The file name is: " + "AAA" + postfix + " **********");
      file = new BTreeFile("AAA" + postfix, keyType, 4, deleteFashion);
      file.traceFilename("TRACE");

      KeyClass key;
      RID rid = new RID();
      PageId pageno = new PageId();

      for (int i = 0; i < n; i++) {
        key = new IntegerKey(i);
        pageno.pid = i;
        rid = new RID(pageno, i);
        file.insert(key, rid);
      }

      System.out.println("test1 completed inserting " + n + " ordered keys.");
    } catch (Exception e) {
      throw e;
    }

    // Print counters at the end
    System.out.println("test1 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("test1 - Write I/Os : " + PCounter.wcounter);
  }

  // ---------------------------------------------------------------------------
  // test2
  // ---------------------------------------------------------------------------
  void test2(int n) throws Exception {
    // Reset counters
    PCounter.initialize();

    try {
      System.out.println(" ********** The file name is: " + "AAA" + postfix + " **********");
      file = new BTreeFile("AAA" + postfix, keyType, 4, deleteFashion);
      file.traceFilename("TRACE");

      KeyClass key;
      RID rid = new RID();
      PageId pageno = new PageId();

      // Insert keys in reverse order
      for (int i = 0; i < n; i++) {
        key = new IntegerKey(n - i);
        pageno.pid = n - i;
        rid = new RID(pageno, n - i);
        file.insert(key, rid);
      }

      System.out.println("test2 completed inserting " + n + " reverse-ordered keys.");
    } catch (Exception e) {
      throw e;
    }

    // Print counters
    System.out.println("test2 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("test2 - Write I/Os : " + PCounter.wcounter);
  }

  // ---------------------------------------------------------------------------
  // test3
  // ---------------------------------------------------------------------------
  void test3(int n) throws Exception {
    // Reset counters
    PCounter.initialize();

    try {
      System.out.println(" ********** The file name is: " + "AAA" + postfix + " **********");
      file = new BTreeFile("AAA" + postfix, keyType, 4, deleteFashion);
      file.traceFilename("TRACE");

      // Create a random permutation of keys 0..n-1
      int[] k = new int[n];
      for (int i = 0; i < n; i++) {
        k[i] = i;
      }
      Random ran = new Random();
      int random;
      int tmp;

      // Shuffle array multiple times
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }

      KeyClass key;
      RID rid = new RID();
      PageId pageno = new PageId();

      // Insert in random order
      for (int i = 0; i < n; i++) {
        key = new IntegerKey(k[i]);
        pageno.pid = k[i];
        rid = new RID(pageno, k[i]);
        file.insert(key, rid);
      }

      System.out.println("test3 completed inserting " + n + " random-ordered keys.");
    } catch (Exception e) {
      throw e;
    }

    // Print counters
    System.out.println("test3 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("test3 - Write I/Os : " + PCounter.wcounter);
  }

  // ---------------------------------------------------------------------------
  // test4
  // ---------------------------------------------------------------------------
  void test4(int n, int m) throws Exception {
    // Reset counters
    PCounter.initialize();

    try {
      System.out.println(" ********** The file name is: " + "AAA" + postfix + " **********");
      file = new BTreeFile("AAA" + postfix, keyType, 4, deleteFashion);
      file.traceFilename("TRACE");

      // Create random permutation of keys
      int[] k = new int[n];
      for (int i = 0; i < n; i++) {
        k[i] = i;
      }
      Random ran = new Random();
      int random;
      int tmp;

      // Shuffle
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }

      // Insert
      KeyClass key;
      RID rid = new RID();
      PageId pageno = new PageId();
      for (int i = 0; i < n; i++) {
        key = new IntegerKey(k[i]);
        pageno.pid = k[i];
        rid = new RID(pageno, k[i]);
        file.insert(key, rid);
      }

      // Shuffle again
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }

      // Delete first m keys from the shuffled array
      for (int i = 0; i < m; i++) {
        key = new IntegerKey(k[i]);
        pageno.pid = k[i];
        rid = new RID(pageno, k[i]);
        if (!file.Delete(key, rid)) {
          System.out.println("*********************************************************");
          System.out.println("*  Your delete method might have a bug!                *");
          System.out.println("*  Inserted a record but failed to delete it.          *");
          System.out.println("*********************************************************");
        }
      }

      System.out.println("test4 completed: inserted " + n + " keys, deleted " + m + " of them.");
    } catch (Exception e) {
      throw e;
    }

    // Print counters
    System.out.println("test4 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("test4 - Write I/Os : " + PCounter.wcounter);
  }

  // ---------------------------------------------------------------------------
  // test5
  // ---------------------------------------------------------------------------
  void test5(int n, int m) throws Exception {
    // Reset counters
    PCounter.initialize();

    try {
      System.out.println(" ********** The file name is: " + "AAA" + postfix + " **********");
      file = new BTreeFile("AAA" + postfix, keyType, 20, deleteFashion);
      file.traceFilename("TRACE");

      int[] k = new int[n];
      for (int i = 0; i < n; i++) {
        k[i] = i;
      }
      Random ran = new Random();
      int random;
      int tmp;

      // Shuffle
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }

      KeyClass key;
      RID rid = new RID();
      PageId pageno = new PageId();

      // Insert string keys like "**X"
      for (int i = 0; i < n; i++) {
        key = new StringKey("**" + k[i]);
        pageno.pid = k[i];
        rid = new RID(pageno, k[i]);
        file.insert(key, rid);
      }

      // Shuffle again
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }
      for (int i = 0; i < n; i++) {
        random = Math.abs(ran.nextInt() % n);
        tmp = k[i];
        k[i] = k[random];
        k[random] = tmp;
      }

      // Delete first m keys
      for (int i = 0; i < m; i++) {
        key = new StringKey("**" + k[i]);
        pageno.pid = k[i];
        rid = new RID(pageno, k[i]);

        if (!file.Delete(key, rid)) {
          System.out.println("*********************************************************");
          System.out.println("*  Your delete method might have a bug!                *");
          System.out.println("*  Inserted a record but failed to delete it.          *");
          System.out.println("*********************************************************");
        }
      }

      System.out.println("test5 completed: inserted " + n + " string keys, deleted " + m + " of them.");
    } catch (Exception e) {
      throw e;
    }

    // Print counters
    System.out.println("test5 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("test5 - Write I/Os : " + PCounter.wcounter);
  }
}

/**
 * Simple helper class to get integer input from the console
 */
class GetStuff {
  GetStuff() {}

  public static int getChoice() {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    int choice = -1;
    try {
      choice = Integer.parseInt(in.readLine());
    } catch (NumberFormatException e) {
      return -1;
    } catch (IOException e) {
      return -1;
    }
    return choice;
  }

  public static void getReturn() {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    try {
      String ret = in.readLine();
    } catch (IOException e) {}
  }
}

public class BTTest implements GlobalConst {
  public static void main(String[] argvs) {
    try {
      BTDriver bttest = new BTDriver();
      bttest.runTests();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Error encountered during buffer manager tests:\n");
      Runtime.getRuntime().exit(1);
    }
  }
}
