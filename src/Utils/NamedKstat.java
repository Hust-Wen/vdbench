package Utils;

/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.util.*;

/**
 * All native functions.
 */
public class NamedKstat
{
  private final static String c =
  "Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.";

  /**
   * Get a kstat_ctl_t pointer to Kstat
   */
  public static native long kstat_open();


  /**
   * Close kstat kstat_ctl_t pointer
   */
  public static native long kstat_close(long kstat_ctl_t);

  /**
   * Using named Kstat data, return a String with
   * label number label number .....
   *
   * Can return with String: "JNI failure: ...."
   */
  public static native String kstat_lookup_stuff(long   kstat_ctl_t,
                                                 String module,
                                                 String name);



  public static void main(String[] args)
  {
  }

}
