package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

import Utils.Format;

/**
 * Functionality related to the creation of a Flat parsable file
 */
public class Flat
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  private String label;                /* Label assigned to this column */

  private String strval;
  private double dblval;
  private long   longval;
  private int    type;                 /* 0: No value entered                         */
  /* 1: Long entered                             */
  /* 2: Double entered                           */
  /* 3: String entered                           */
  private String text;

  private static Vector flat_list = new Vector(32,0);
  private static PrintWriter flatfile_html = null;
  private static boolean first_print = true;



  public static void createFlatFile()
  {
    flatfile_html = Report.createHmtlFile("flatfile.html");
  }

  /**
   * Disallow spaces in column header or data contents
   */
  private static void blanks(String value)
  {
    if (value.trim().indexOf(" ") != -1)
      common.failure("No embedded blanks allowed in flat file labels or data: '"
                     + value + "'");
  }


  /**
   * Add a new column to the report:
   */
  private static void add_col(String label_in, String text_in)
  {
    blanks(label_in);
    Flat flt = new Flat();
    flat_list.addElement(flt);
    flt.label = label_in.trim();
    flt.type  = 0;
    flt.text  = text_in;
  }


  /**
   * Find a column in the report
   */
  private static Flat find_col(String label_in, int type_in)
  {
    blanks(label_in);
    for (int i = 0; i < flat_list.size(); i++)
    {
      Flat flt = (Flat) flat_list.elementAt(i);
      if (label_in.trim().toLowerCase().compareTo(flt.label.toLowerCase()) == 0)
      {
        flt.type = type_in;
        return flt;
      }
    }
    common.failure("Flatfile column reporting: column '" + label_in + "' not found");

    return null;
  }

  /**
   * Store data in column.
   * Data is stored until the print() function is called.
   * Once the data is printed, the contents for a column is retained.
   * This allows one-time information like RUN1 to be stored without
   * it having to call put_col() for each next line.
   * This means however that you should not leave old data around!!
   * If you want to clear that old data and that column printed as "n/a"
   * use Flat.put_col("label");
   */
  public static void put_col(String label_in, long input)
  {
    find_col(label_in, 1).longval = input;
  }

  public static void put_col(String label_in, double input)
  {
    find_col(label_in, 2).dblval = input;
  }

  public static void put_col(String label_in, String input)
  {
    //blanks(input);
    //if (input.trim().indexOf(" ") != -1)
    //  common.failure("No embedded blanks allowed in flat file labels or data: '"
    //                 + input + "'");

    find_col(label_in, 3).strval = input;
  }

  public static void put_col(String label_in)
  {
    find_col(label_in, 0);
  }


  /**
   * Print column headers
   */
  private static void print_col_headers()
  {
    /* Note: */
    /* Note: */
    /* Note: the first byte in this line must be blank for ParseFlat to recognize it.*/
    String line = String.format("%12s %23s ", "tod", "timestamp");

    println("*");
    println("* 'flatfile.html' contains Vdbench generated information in a column by column ASCII format. ");
    println("* The first line in the file contains a one word 'column header name'; the rest of the file ");
    println("* contains data that belongs to each column. The objective of this file format is to allow ");
    println("* easy transfer of information to a spreadsheet and therefore the creation of performance ");
    println("* charts different from the performance charts that can be created by Sun StorageTek Workload ");
    println("* Analysis Tool (Swat) available for download from Sun.");
    println("* See also 'Selective flatfile parsing' in the documentation. ");
    println("*");

    /* Print comments: */
    for (int i = 0; i < flat_list.size(); i++)
    {
      Flat flt = (Flat) flat_list.elementAt(i);
      flatfile_html.println("* " + Format.f("%-16s: ", flt.label) + flt.text);
    }
    flatfile_html.println("* 'n/a'           : Data not available, or conflicting data. eg. multiple different xfersize parameters used.");
    flatfile_html.println("* ");


    /* Print column headers: */
    for (int i = 0; i < flat_list.size(); i++)
    {
      Flat flt = (Flat) flat_list.elementAt(i);
      line = line + Format.f("%s ", flt.label);
    }

    flatfile_html.println(line);
  }


  public static void println(String txt)
  {
    if (first_print)
    {
      first_print = false;
      print_col_headers();
    }

    flatfile_html.println(txt);
  }

  /**
   * Print a column of data.
   * This can be improved by defining a format string for each column.
   */
  public static void printInterval()
  {
    String line = "";

    if (first_print)
    {
      first_print = false;
      print_col_headers();
    }

    for (int i = 0; i < flat_list.size(); i++)
    {
      Flat flt = (Flat) flat_list.elementAt(i);
      if (flt.type == 1)
        line = line + Format.f("%d ", flt.longval);
      else if (flt.type == 2)
        line = line + removeTrail(flt.dblval) + " ";
      else if (flt.type == 3)
        line = line + Format.f("%s ", flt.strval);
      else
        line = line + Format.f("%s ", "n/a");
    }

    Date now = new Date();
    SimpleDateFormat time      = new SimpleDateFormat("HH:mm:ss.SSS");
    SimpleDateFormat date_time = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss-zzz ");
    String line2 = String.format("%12s %24s%s",
                                 time.format(now),
                                 date_time.format(now), line);

    flatfile_html.println(line2);
    //common.ptod(line, flatfile_html);
  }

  /**
   * When printing with 4 decimals, when they are all zeros they are just
   * wasting space. Remove them.
   */
  private static String removeTrail(double value)
  {
    /* The '6' here assures we get at least one number before the decimal */
    String out = Format.f("%6.4f", value);

    //common.ptod("out1: " + out);
    while (out.endsWith("0"))
    {
      out = out.substring(0, out.length() - 1);
    }
    while (out.endsWith("."))
    {
      out = out.substring(0, out.length() - 1);
    }
    //common.ptod("out2: " + out);

    return out;
  }

  /**
   * Standard columns
   */
  public static void define_column_headers()
  {
    if (Vdbmain.isWdWorkload())
    {
      // Don't include these two lines. That then adds two columns also.
      //add_col("tod",         "Timestamp, hh:mm:ss.SSS");
      //add_col("timestamp",   "Timestamp, mm/dd/yyy-hh:mm:ss.SSS");
      add_col("Run",         "Name of run from RD=");
      add_col("Interval",    "Reporting interval number");
      add_col("reqrate",     "Requested i/o rate");
      add_col("rate",        "Observed i/o rate");
      add_col("MB/sec",      "Megabytes per second (MB=1024*1024)");
      add_col("bytes/io",    "Average data transfersize");
      add_col("read%",       "Observed read percentage");
      add_col("resp",        "Observed response time");
      add_col("read_resp",   "Observed read response time");
      add_col("write_resp",  "Observed write response time");
      add_col("resp_max",    "Observed maximum response time");
      add_col("read_max",    "Observed maximum read response time");
      add_col("write_max",   "Observed maximum write response time");
      add_col("resp_std",    "Standard deviation response time");
      add_col("read_std",    "Standard deviation reads");
      add_col("write_std",   "Standard deviation writes");
      add_col("xfersize",    "data transfer size requested");
      add_col("threads",     "number of threads requested");
      add_col("rdpct",       "read% requested");
      add_col("rhpct",       "readhit% requested");
      add_col("whpct",       "writehit% requested");
      add_col("seekpct",     "seek% requested");
      add_col("lunsize",     "Total amount of Gigabytes of all luns (GB= 1024*1024*1024)");
      add_col("version",     "Vdbench version identifier");
      add_col("compratio",   "Requested compression ratio");
      add_col("dedupratio",  "Requested dedup ratio");
      add_col("queue_depth", "Vdbench calculated average i/o queue depth");
    }

    else
    {
      add_col("Run",               "Name of run from RD=");
      add_col("Interval",          "Reporting interval number");
      add_col("Xfersize",          "data transfer size requested");
      add_col("Threads",           "number of threads requested");
      add_col("Reqrate",           "Requested FWD rate");

      add_col("Rate",              "Requested operations per second");
      add_col("Rate_std",          "Requested operations per second standard deviation");
      add_col("Rate_max",          "Requested operations per second max");

      add_col("Resp",              "Requested response time");
      add_col("Resp_std",          "Requested response time standard deviation");
      add_col("Resp_max",          "Requested response time max");

      add_col("MB/sec",            "Megabytes per second (MB=1024*1024)");
      add_col("MB_read",           "Megabytes read per second");
      add_col("MB_write",          "Megabytes written per second");
      //add_col("Xfersize",          "Average transfer size");

      add_col("Read_rate",         "Reads per second");
      add_col("Read_rate_std",     "Reads per second stddev");
      add_col("Read_rate_max",     "Reads per second max");
      add_col("Read_resp",         "Read response time");
      add_col("Read_resp_std",     "Read response time stddev");
      add_col("Read_resp_max",     "Read response time max");

      add_col("Write_rate",        "Writes per second");
      add_col("Write_rate_std",    "Writes per second stddev");
      add_col("Write_rate_max",    "Writes per second max");
      add_col("Write_resp",        "Write response time");
      add_col("Write_resp_std",    "Write response time stddev");
      add_col("Write_resp_max",    "Write response time max");

      add_col("Mkdir_rate",        "Mkdirs per second");
      add_col("Mkdir_rate_std",    "Mkdirs per second stddev");
      add_col("Mkdir_rate_max",    "Mkdirs per second max");
      add_col("Mkdir_resp",        "Mkdir response time");
      add_col("Mkdir_resp_std",    "Mkdir response time stddev");
      add_col("Mkdir_resp_max",    "Mkdir response time max");

      add_col("Rmdir_rate",        "Rmdirs per second");
      add_col("Rmdir_rate_std",    "Rmdirs per second stddev");
      add_col("Rmdir_rate_max",    "Rmdirs per second max");
      add_col("Rmdir_resp",        "Rmdir response time");
      add_col("Rmdir_resp_std",    "Rmdir response time stddev");
      add_col("Rmdir_resp_max",    "Rmdir response time max");

      add_col("Create_rate",       "Creates per second");
      add_col("Create_rate_std",   "Creates per second stddev");
      add_col("Create_rate_max",   "Creates per second max");
      add_col("Create_resp",       "Create response time");
      add_col("Create_resp_std",   "Create response time stddev");
      add_col("Create_resp_max",   "Create response time max");

      add_col("Open_rate",         "Opens per second");
      add_col("Open_rate_std",     "Opens per second stddev");
      add_col("Open_rate_max",     "Opens per second max");
      add_col("Open_resp",         "Open response time");
      add_col("Open_resp_std",     "Open response time stddev");
      add_col("Open_resp_max",     "Open response time max");

      add_col("Close_rate",        "Closes per second");
      add_col("Close_rate_std",    "Closes per second stddev");
      add_col("Close_rate_max",    "Closes per second max");
      add_col("Close_resp",        "Close response time");
      add_col("Close_resp_std",    "Close response time stddev");
      add_col("Close_resp_max",    "Close response time max");

      add_col("Delete_rate",       "Deletes per second");
      add_col("Delete_rate_std",   "Deletes per second stddev");
      add_col("Delete_rate_max",   "Deletes per second max");
      add_col("Delete_resp",       "Delete response time");
      add_col("Delete_resp_std",   "Delete response time stddev");
      add_col("Delete_resp_max",   "Delete response time max");

      add_col("Getattr_rate",      "Getattrs per second");
      add_col("Getattr_rate_std",  "Getattrs per second stddev");
      add_col("Getattr_rate_max",  "Getattrs per second max");
      add_col("Getattr_resp",      "Getattr response time");
      add_col("Getattr_resp_std",  "Getattr response time stddev");
      add_col("Getattr_resp_max",  "Getattr response time max");

      add_col("Setattr_rate",      "Setattrs per second");
      add_col("Setattr_rate_std",  "Setattrs per second stddev");
      add_col("Setattr_rate_max",  "Setattrs per second max");
      add_col("Setattr_resp",      "Setattr response time");
      add_col("Setattr_resp_std",  "Setattr response time stddev");
      add_col("Setattr_resp_max",  "Setattr response time max");

      add_col("Access_rate",       "Accesses per second");
      add_col("Access_rate_std",   "Accesses per second stddev");
      add_col("Access_rate_max",   "Accesses per second max");
      add_col("Access_resp",       "Access response time");
      add_col("Access_resp_std",   "Access response time stddev");
      add_col("Access_resp_max",   "Access response time max");

      //add_col("Copy_rate",         "Copies per second");
      //add_col("Copy_resp",         "Copy response time");
      //add_col("Move_rate",         "Moves per second");
      //add_col("Move_resp",         "Move response time");

      add_col("Compratio",         "Requested compression ratio");
      add_col("Dedupratio",        "Requested dedup ratio");
    }
  }


  /**
   * KSTAT only columns
   */
  public static void define_column_headers_kstat()
  {
    add_col("ks_rate",     "kstat: i/o rate" );
    add_col("ks_resp",     "kstat: response time" );
    add_col("ks_wait",     "kstat: host wait time" );
    add_col("ks_svct",     "kstat: service time" );
    add_col("ks_mb",       "kstat: megabytes per second (MB=1024*1024)" );
    add_col("ks_read%",    "kstat: read percentage" );
    add_col("ks_busy%",    "kstat: busy percentage" );
    add_col("ks_avwait",   "kstat: #i/o's waiting in host" );
    add_col("ks_avact",    "kstat: #i/o's active" );
    add_col("ks_bytes",    "kstat: average transfer size" );
  }

  public static void define_column_headers_cpu()
  {
    add_col("cpu_used",    "kstat: cpu% user+sys" );
    add_col("cpu_user",    "kstat: cpu% user" );
    add_col("cpu_kernel",  "kstat: cpu% sys " );
    add_col("cpu_wait",    "kstat: cpu% wait" );
    add_col("cpu_idle",    "kstat: cpu% idle" );
  }



  /**
   * Report on the flatfile those parameters that are identical in each workload
   */
  public static void reportWgParameters(RD_entry rd)
  {
    ArrayList <WG_entry> wg_list = rd.getAllWorkloads();
    WG_entry wg     = wg_list.get(0);
    int    xfersize = (wg.getXfersizes().length > 0) ? (int) wg.getXfersizes()[0] : 0;
    double rhpct    = wg.rhpct;
    double whpct    = wg.whpct;
    double readpct  = wg.readpct;
    double seekpct  = wg.seekpct;

    for (int i = 1; i < wg_list.size(); i++)
    {
      wg = wg_list.get(i);
      if (wg.getXfersizes().length > 0 && xfersize != wg.getXfersizes()[0])
        xfersize = -1;
      if (rhpct != wg.rhpct)
        rhpct = -1;
      if (whpct != wg.whpct)
        whpct = -1;
      if (readpct != wg.readpct)
        readpct = -1;
      if (seekpct != wg.seekpct)
        seekpct = -1;
    }

    /* Clear columns in case I have no info: */
    Flat.put_col("xfersize");
    Flat.put_col("rhpct");
    Flat.put_col("whpct");
    Flat.put_col("rdpct");
    Flat.put_col("threads");
    Flat.put_col("seekpct");

    /* Fill in the blanks, if we have any info: */
    if (xfersize != -1)
      Flat.put_col("xfersize", xfersize);
    if (rhpct != -1)
      Flat.put_col("rhpct", rhpct);
    if (whpct != -1)
      Flat.put_col("whpct", whpct);
    if (readpct != -1)
      Flat.put_col("rdpct", readpct);
    if (seekpct != -1)
      Flat.put_col("seekpct", seekpct);

    /* Keep track of #threads in SD parameter. If they're all the same, report */
    /* that count, otherwise report -1: */
    long threads_in_sds = 0;

    long lunsize = 0;
    for (int i = 0; i < Vdbmain.sd_list.size(); i++)
    {
      SD_entry sd = (SD_entry) Vdbmain.sd_list.elementAt(i);
      if (sd.isActive())
      {
        if (threads_in_sds == 0)
          threads_in_sds = sd.threads;
        if (threads_in_sds != sd.threads)
          threads_in_sds = -1;

        lunsize += sd.end_lba;
      }
    }

    if (rd.current_override.getThreads() != For_loop.NOVALUE)
      Flat.put_col("threads", rd.current_override.getThreads());
    else
      Flat.put_col("threads", threads_in_sds);
    Flat.put_col("lunsize", (double) lunsize / Report.GB);
  }



  public static void reportFwgParameters(RD_entry rd)
  {
    /* Clear columns in case I have no info: */
    Flat.put_col("xfersize");
    Flat.put_col("threads");

    if (rd.current_override.getThreads() != For_loop.NOVALUE)
      Flat.put_col("Threads", rd.current_override.getThreads());

    if (rd.current_override.getXfersize() != For_loop.NOVALUE)
      Flat.put_col("Xfersize", rd.current_override.getXfersize());
  }
}

