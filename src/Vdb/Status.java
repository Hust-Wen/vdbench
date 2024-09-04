package Vdb;

/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A report file that tells the user, usually for automation purposes, what the
 * current state is of a Vdbench run.
 *
 * There is logic in here to make sure that a specific message text is only
 * displayed ONCE, e.g. 'i/o error', 'corruption'. That keeps the status file
 * nice and small and cozy.
 *
 * Report: status.html
 *
 *
 * Note: the flush() only tells java to send the data to the OS, there is no
 * guarantee that the OS indeed then flushes the data immediately to disk.
 * http://yongkunphd.blogspot.com/2013/12/how-fsync-works-in-java.html
 *
 * The link above shows an option to also do that, but at this time I am not
 * willing to take a risk messing up how Vdbench operates: I don't want a thread
 * to have to physically WAIT for a possible slow disk write.
 * fsflush() 'should' do a physical write within 30 seconds though.
 */
public class Status
{
  private final static String c =
  "Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.";

  private static Report status_report = null;
  private static ArrayList <String> history = new ArrayList(4);



  public Status()
  {
    if (status_report != null)
      common.failure("Recursive creation of 'status.html'");

    status_report = new Report("status", "* Vdbench status");

    Report.getSummaryReport().printHtmlLink("Vdbench status", "status", "status");

    status_report.println("* The objective of this file is to contain easily parseable " +
                          "information about the current state of Vdbench.");
    status_report.println("* This then can serve as an 'official' interface for any software " +
                          "monitoring Vdbench.");
    status_report.println("* Each line of output will be immediately flushed to the file system, " +
                          "making its content accessible by any monitoring program.");
    status_report.println("* The values below are all tab-delimited.");
    status_report.println("");

    status_report.getWriter().flush();
  }


  public static void clear()
  {
    history.clear();
  }

  /**
   * This print a status using the CURRENT Run Definition in its message.
   */
  public static void printRdStatus(String format, Object ... args)
  {
    if (Vdbmain.simulate)
      return;

    /* This situation may exist with one of the many vdbench utilities: */
    if (status_report == null)
      return;

    RD_entry         rd = RD_entry.next_rd;
    SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss-zzz ");
    String          tod = df.format(new Date());
    String          txt = String.format(format, args);

    status_report.println("%s\t%s\trd=%s\t%s", tod, txt, rd.rd_name, rd.current_override.getText());

    status_report.getWriter().flush();
  }


  public static void printStatus(String format, Object ... args)
  {
    /* This situation may exist with one of the many vdbench utilities: */
    if (status_report == null)
      return;

    SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss-zzz ");
    String          tod = df.format(new Date());
    String          txt = String.format(format, args);

    status_report.println("%s\t%s", tod, txt);

    status_report.getWriter().flush();
  }


  /**
   * Print a message only ONCE per RD.
   */
  public static void printOne(String txt)
  {
    /* This situation may exist with one of the many vdbench utilities: */
    if (status_report == null)
      return;

    if (history.contains(txt))
      return;
    history.add(txt);

    SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss-zzz ");
    String          tod = df.format(new Date());

    status_report.println("%s\t%s", tod, txt);

    status_report.getWriter().flush();
  }
}

