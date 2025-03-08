package tests;

import java.io.*;
import java.util.*;
import java.lang.*;
import chainexception.*;
import diskmgr.*;

//    Major Changes:
//    1. Change the return type of test() functions from 'int' to 'boolean'
//       to avoid defining static int TRUE/FALSE, which makes it easier for
//       derived functions to return the right type.
//    2. Function runTest is not implemented to avoid dealing with function
//       pointers.  Instead, it's flattened in runAllTests() function.
//    3. Change
//          Status TestDriver::runTests()
//            Status TestDriver::runAllTests()
//       to
//          public boolean runTests();
//          protected boolean runAllTests();

/** 
 * TestDriver class is a base class for various test driver
 * objects.
 * <br>
 * Note that the code written so far is very machine dependent.  It assumes
 * the users are on UNIX system.  For example, in function runTests, a UNIX
 * command is called to clean up the working directories.
 */

public class TestDriver {

  public final static boolean OK   = true; 
  public final static boolean FAIL = false; 

  protected String dbpath;  
  protected String logpath;
  

  /** 
   * TestDriver Constructor 
   *
   * @param nameRoot The name of the test being run
   */
  protected TestDriver (String nameRoot) {
    // To port to a different platform, "user.name" should
    // still work well because it is not UNIX-specific.
    dbpath = "/tmp/" + nameRoot + System.getProperty("user.name") + ".minibase-db"; 
    logpath = "/tmp/" + nameRoot + System.getProperty("user.name") + ".minibase-log"; 
  }

  /**
   * Default Constructor
   */
  protected TestDriver () {}

  /**
   * The following test methods each do the following:
   * 1) Initialize PCounter
   * 2) Perform (dummy) test logic (currently always pass)
   * 3) Print read/write I/Os
   * 4) Return pass/fail
   */

  protected boolean test1 () {
    // 1. Reset counters
    PCounter.initialize();

    // 2. Do actual test logic (currently trivial)
    boolean pass = true; // set to false if the test fails

    // 3. Print out read/write counters
    System.out.println("Test1 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test1 - Write I/Os : " + PCounter.wcounter);

    // 4. Return pass/fail
    return pass;
  }

  protected boolean test2 () {
    PCounter.initialize();
    boolean pass = true;

    System.out.println("Test2 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test2 - Write I/Os : " + PCounter.wcounter);

    return pass;
  }

  protected boolean test3 () {
    PCounter.initialize();
    boolean pass = true;

    System.out.println("Test3 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test3 - Write I/Os : " + PCounter.wcounter);

    return pass;
  }

  protected boolean test4 () {
    PCounter.initialize();
    boolean pass = true;

    System.out.println("Test4 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test4 - Write I/Os : " + PCounter.wcounter);

    return pass;
  }

  protected boolean test5 () {
    PCounter.initialize();
    boolean pass = true;

    System.out.println("Test5 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test5 - Write I/Os : " + PCounter.wcounter);

    return pass;
  }

  protected boolean test6 () {
    PCounter.initialize();
    boolean pass = true;

    System.out.println("Test6 - Read I/Os  : " + PCounter.rcounter);
    System.out.println("Test6 - Write I/Os : " + PCounter.wcounter);

    return pass;
  }

  /** 
   * @return <code>String</code> object which contains the name of the test
   */
  protected String testName() { 
    // A little reminder to subclassers 
    return "*** unknown ***"; 
  }

  /**
   * This function does the preparation/cleaning work for the
   * running tests.
   *
   * @return a boolean value indicates whether ALL the tests have passed
   */
  public boolean runTests ()  {
    
    System.out.println ("\n" + "Running " + testName() + " tests...." + "\n");
    
    // Kill anything that might be hanging around
    String newdbpath;
    String newlogpath;
    String remove_logcmd;
    String remove_dbcmd;
    String remove_cmd = "/bin/rm -rf ";

    newdbpath = dbpath;
    newlogpath = logpath;

    remove_logcmd = remove_cmd + logpath;
    remove_dbcmd = remove_cmd + dbpath;

    // Commands here is very machine dependent.  We assume
    // user are on UNIX system here
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } 
    catch (IOException e) {
      System.err.println (""+e);
    }
    
    remove_logcmd = remove_cmd + newlogpath;
    remove_dbcmd = remove_cmd + newdbpath;

    //This step seems redundant for me.  But it's in the original
    //C++ code.  So I am keeping it as of now, just in case
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } 
    catch (IOException e) {
      System.err.println (""+e);
    }

    //Run the tests. Return type different from C++
    boolean _pass = runAllTests();

    //Clean up again
    try {
      Runtime.getRuntime().exec(remove_logcmd);
      Runtime.getRuntime().exec(remove_dbcmd);
    } 
    catch (IOException e) {
      System.err.println (""+e);
    }
    
    System.out.println ("\n" + "..." + testName() + " tests ");
    System.out.print (_pass==OK ? "completely successfully" : "failed");
    System.out.println (".\n\n");
    
    return _pass;
  }

  protected boolean runAllTests() {

    boolean _passAll = OK;

    // Running test1() to test6()
    if (!test1()) { _passAll = FAIL; }
    if (!test2()) { _passAll = FAIL; }
    if (!test3()) { _passAll = FAIL; }
    if (!test4()) { _passAll = FAIL; }
    if (!test5()) { _passAll = FAIL; }
    if (!test6()) { _passAll = FAIL; }

    return _passAll;
  }

  /**
   * Used to verify whether the exception thrown from
   * the bottom layer is the one expected.
   */
  public boolean checkException (ChainException e, String expectedException) {

    boolean notCaught = true;
    while (true) {
      String exception = e.getClass().getName();
      if (exception.equals(expectedException)) {
        return (!notCaught);
      }
      if (e.prev == null) {
        return notCaught;
      }
      e = (ChainException)e.prev;
    }
  }
}
