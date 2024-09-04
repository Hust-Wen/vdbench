package Vdb;

/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.File;
import java.util.*;
import Utils.*;


public class ParseFlat
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  private static boolean quiet          = false;
  private static boolean comma          = true;
  private static String  input_file     = null;
  private static String  output_file    = "-";
  private static String  label_file     = null;

  private static boolean average        = false;
  private static Vector  <String[]> split_data     = new    Vector(256);
  private static Vector  <String> column_names   = new    Vector(36);
  private static Vector  <String> filter_columns = new    Vector(36);
  private static Vector  <String> filter_values  = new    Vector(36);
  private static Vector  <String> output_cols    = new    Vector(36);

  private static boolean all_columns = false;

  /**
  * Usage: ParseFlat flatfile.html
  *     output.txt col1 xxx col2 yyy .... data rate resp
   */
  public static void main(String[] args) throws Exception
  {
    /* Parse all input parameters: */
    scanParms(args);

    /* debugging: */
    for (int i = 90; i < filter_columns.size(); i++)
      common.ptod("filter_columns: " + filter_columns.elementAt(i));
    for (int i = 90; i < filter_values.size(); i++)
      common.ptod("filter_values:  " + filter_values.elementAt(i));
    for (int i = 90; i < output_cols.size(); i++)
      common.ptod("output_cols:    " + output_cols.elementAt(i));

    /* Read the possible column names from flatfile: */
    readColumnHeadersAndData();

    if (output_cols.size() == 1 && output_cols.get(0).equals("all"))
    {
      output_cols.clear();
      for (String col : column_names)
        output_cols.add(col);
    }

    /* Make sure the ones requested exist: */
    checkColumnNames(filter_columns);
    checkColumnNames(output_cols);

    /* Now create the output file: */
    createOutput();

    common.ptod("ParseFlat completed successfully.");
  }

  private static void readColumnHeadersAndData()
  {
    /* Find the first line that starts with a blank: */
    Fget fg = new Fget(input_file);
    String line = null;
    while ((line = fg.get()) != null)
    {
      //common.ptod("line: " + line);
      if (line.trim().length() == 0)
        continue;
      if (line.startsWith(" "))
      {
        /* That line contains all the proper column names in the right order: */
        String[] split = line.trim().split(" +");
        for (int i = 0; i < split.length; i++)
          column_names.add(split[i]);
        break;
      }
    }

    /* Now copy (as split) all the data rows into the 'data' Vector: */
    while ((line = fg.get()) != null)
    {
      if (!line.startsWith("*"))
      {
        String[] split = line.trim().split(" +");

        if (split.length != column_names.size())
        {
          common.ptod("line: " + line);
          common.ptod("ParseFlat: data line incomplete, expecting %d columns, getting %d. Line ignored.",
                      column_names.size(), split.length);
          common.ptod("This is acceptable for the last line in the file.");
        }
        else
          split_data.add(split);
      }
    }

    fg.close();
  }

  private static void checkColumnNames(Vector list)
  {
    loop:
    for (int i = 0; i < list.size(); i++)
    {
      String col = (String) list.elementAt(i);
      findColumnNo(col);
    }
  }

  private static int findColumnNo(String col)
  {
    for (int j = 0; j < column_names.size(); j++)
    {
      String name = (String) column_names.elementAt(j);
      if (col.equalsIgnoreCase(name))
        return j;
    }

    common.failure("Column '" + col + "' unknown'");
    return 0;
  }

  private static void createOutput()
  {
    int count = 0;
    Fput fp = new Fput(output_file);

    /* Start with writing column headers: */
    String txt = "";
    for (int j = 0; j < output_cols.size(); j++)
    {
      String colname = (String) output_cols.elementAt(j);
      if (j > 0)
        txt += (comma) ? "," : "\t";
      txt += colname;
    }
    fp.println(txt);

    /* Remove all 'format=' runs: */
    // Removed 9/12/17. Did not remember why I did this in the first place.
    // Having ONLY a format caused parse to abort 'no data'.
    //removeFormat();

    /* Remove/keep 'avg_' line: */
    checkAverage();

    /* Go through each data line: */
    loop:
    for (int i = 0; i < split_data.size(); i++)
    {
      try
      {

        String[] split = (String[]) split_data.elementAt(i);

        /* Look for a match with all 'columns' and 'filters': */
        boolean match = true;
        for (int j = 0; j < filter_columns.size(); j++)
        {
          String col = (String) filter_columns.elementAt(j);
          int colno = findColumnNo(col);
          if (!split[colno].equalsIgnoreCase((String) filter_values.elementAt(j)))
            match = false;
        }

        /* If all the filters match, use this data: */
        if (match)
        {
          txt = "";
          for (int j = 0; j < output_cols.size(); j++)
          {
            String colname = (String) output_cols.elementAt(j);
            int colno      = findColumnNo(colname);
            String data    = split[colno];
            if (data.equals("?"))
              data = "0";

            if (j > 0)
              txt += (comma) ? "," : "\t";
            txt += data;

            /* Decide whether to use this interval: */
            if (colname.equalsIgnoreCase("interval"))
            {
              if (!average && data.startsWith("avg"))
                continue loop;
              if (average && !data.startsWith("avg"))
                continue loop;
            }
          }

          count++;
          fp.println(txt);
        }
      }

      catch (Exception e)
      {
        for (int l = 0; l < split_data.get(i).length; l++)
          common.ptod("Failed column: %2d: ", l, split_data.get(i)[l]);

        common.failure(e);
      }
    }

    fp.close();

    if (count == 0)
      common.failure("No output written. Check column names and filters");
  }


  private static void removeFormat()
  {
    int count = 0;
    /* Remove all 'format=' runs: */
    for (int i = 0; i < split_data.size(); i++)
    {
      String[] split = (String[]) split_data.elementAt(i);
      int run = findColumnNo("run");
      if (split[run].startsWith(RD_entry.FSD_FORMAT_RUN))
      {
        count++;
        split_data.removeElementAt(i--);
      }
    }

    if (count > 0)
      common.ptod("Flatfile parser removed all Run Definitions starting with '%s'",
                  RD_entry.FSD_FORMAT_RUN);
  }

  private static void checkAverage()
  {
    /* Remove all 'format=' runs: */
    for (int i = 0; i < split_data.size(); i++)
    {
      String[] split = (String[]) split_data.elementAt(i);
      int interval = findColumnNo("interval");
      String data = split[interval];
      if (!average && data.startsWith("avg"))
      {
        split_data.removeElementAt(i--);
        continue;
      }
      if (average && !data.startsWith("avg"))
      {
        split_data.removeElementAt(i--);
        continue;
      }
    }
  }


  private static void usage(String txt)
  {
    common.ptod("");
    common.ptod("");
    common.ptod("");
    common.ptod("Usage:");
    common.ptod("./vdbench parseflat -i flatfile.html -o output.csv [-c col1 col2 ..] ");
    common.ptod("                 [-t][-a] [-f col1 value1 col2 value2 .. ..] [-q]");
    common.ptod("");
    common.ptod("-i input flatfile, e.g. output/flatfile.html");
    common.ptod("-o output csv file name (default stdout)");
    common.ptod("-c which column to write to output. Columns are written in the order specified.");
    common.ptod("-f filters: 'if (colX == valueX) ... ...' (Alphabetic compare)");
    common.ptod("-a include only the 'avg' data. Default: include only non-avg data.");
    common.ptod("-a Creat tab-separated file, not comma-separated");
    common.ptod("");
    common.ptod("");
    common.ptod("");
    common.failure(txt);
  }


  private static void scanParms(String[] argsin)
  {
    Vector parms = new Vector(64);

    /* Take all parameters and replace possible '-frun' with '-f run': */
    for (int i = 1; i < argsin.length; i++)
    {
      String arg = argsin[i];
      if (arg.startsWith("-") && arg.length() > 2)
      {
        parms.add(arg.substring(0,2));
        parms.add(arg.substring(2));
      }
      else if (arg.startsWith("-q"))
        quiet = true;
      else if (arg.startsWith("-t"))
        comma = false;
      else
        parms.add(arg);
    }

    String[] args = (String[]) parms.toArray(new String[0]);

    /* Print arguments: */
    if (!quiet)
    {
      System.err.println("vdbench parseflat arguments:");
      for (int i = 0; i < args.length; i++)
        System.err.println("Argument " + i + ": " + args[i]);
    }

    /* Now do the parsing: */
    for (int i = 0; i < args.length; i++)
    {
      //common.ptod("args: " + args[i]);
      /* Input file: */
      if (args[ i ].startsWith("-i"))
        input_file = args[++i];

      /* Output file: */
      else if (args[ i ].startsWith("-o"))
        output_file = args[++i];

      /* Filters: */
      else if (args[i].startsWith("-f"))
      {
        for (i++; i < args.length && !args[i].startsWith("-"); )
        {
          filter_columns.add(args[ i++ ]);
          //common.ptod("columns: " + filter_columns.lastElement());

          if (i == args.length)
            usage("'-f' filters must be specified in pairs");

          filter_values.add (args[ i++ ]);
          //common.ptod("values:  " + filter_values.lastElement());
        }

        /* Back off one: */
        i--;
      }

      /* Data columns: */
      else if (args[i].startsWith("-c"))
      {
        for (int j = i+1; j < args.length && !args[j].startsWith("-"); j++)
        {
          if (args[j].equals("all"))
            all_columns = true;
          output_cols.add(args[++i]);
        }
      }

      /* 'avg' lines? */
      else if (args[i].startsWith("-a"))
        average = true;

      /* Quiet? */
      else if (args[i].startsWith("-q"))
        quiet = true;

      /* 'label' file? */
      else if (args[i].startsWith("-l"))
        label_file = args[++i];

      else
        usage("invalid parameter: " + args[i]);
    }

    /* Checks: */
    if (input_file == null)
      usage("No input file specified '-f xxx'");
    if (output_cols.size() == 0)
      usage("No data columns specified '-c xxx yyy zzz'");

    /* if the input is a directory, try flatfile: */
    if (Fget.dir_exists(input_file))
    {
      if (Fget.file_exists(input_file, "flatfile.html"))
        input_file = new File(input_file, "flatfile.html").getAbsolutePath();
    }
  }
}
