package Vdb;
import java.util.HashMap;
import java.util.Vector;

/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

/**
 * This class handles proper naming of requested operations.
 */
class Operations
{
  private final static String c =
  "Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.";

  private static String[] operations =
  {
    "read",         //  0
    "write",        //  1
    "mkdir",        //  2
    "rmdir",        //  3
    "copy",         //  4
    "move",         //  5
    "create",       //  6
    "delete",       //  7
    "getattr",      //  8
    "setattr",      //  9
    "access",       // 10
    "open",         // 11
    "close",        // 12
    "put",          // 13
    "get"           // 14

    // we should add a 'backward sequential' file selection?
  };

  private static HashMap <String, Integer> operations_map = new HashMap(32);

  private static boolean[] operations_used = new boolean[64];

  public static final int READ    = addOperation("Read");       //  0
  public static final int WRITE   = addOperation("Write");      //  1
  public static final int MKDIR   = addOperation("Mkdir");      //  2
  public static final int RMDIR   = addOperation("Rmdir");      //  3
  public static final int COPY    = addOperation("Copy");       //  4
  public static final int MOVE    = addOperation("Move");       //  5
  public static final int CREATE  = addOperation("Create");     //  6
  public static final int DELETE  = addOperation("Delete");     //  7
  public static final int GETATTR = addOperation("Getattr");    //  8
  public static final int SETATTR = addOperation("Setattr");    //  9
  public static final int ACCESS  = addOperation("Access");     // 10
  public static final int OPEN    = addOperation("Open");       // 11
  public static final int CLOSE   = addOperation("Close");      // 12
  public static final int PUT     = addOperation("Put");        // 13
  public static final int GET     = addOperation("Get");        // 14


  private static int addOperation(String operation)
  {
    int opnumber = operations_map.size();
    operation    = operation.toLowerCase();
    operations_map.put(operation, opnumber);
    return opnumber;
  }

  /**
   * Translate operation String to an integer.
   * Since this call is made very early on, also keep track of which
   * operations are really used.
   */
  public static int getOperationIdentifier(String operation)
  {
    operation = operation.toLowerCase();

    Integer opnumber = operations_map.get(operation);
    if (opnumber == null)
      return -1;
    else
    {
      operations_used [ opnumber ] = true;
      return opnumber;
    }
  }


  public static String getOperationText(int op)
  {
    if (op == -1)
      return "n/a";
    return operations[op];
  }

  /**
   * This method is meant for some infrequently used operations to allow them
   * ONLY to be included in the output when used.
   * BTW: flatfile is left alone.
   */
  public static boolean isOperationUsed(int op)
  {
    return operations_used [ op ];
  }

  public static int getOperationCount()
  {
    return operations.length;
  }

}
