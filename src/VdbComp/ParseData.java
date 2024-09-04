package VdbComp;

/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.util.*;
import Utils.Fget;
import Vdb.common;

/**
 * Parse flatfile.html files look for 'avg_' intervals.
 */
public class ParseData
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  private static String[] keep_list = new String[]
  {
    "reqrate",
    "interval",
    "mb/sec",
    "resp_max",
    "resp_std",
    "resp",
    "queue_depth",
    "read%",
    "rate"
  };

  /**
   * Parse a flatfile.html
   *
   * @param fname  Complete file name for flatfile.html
   *
   * @return
   */
  public static boolean parseFlatFile(FlatFile ff, Vector all_rds)
  {
    String fname = ff.vdbench_dir + "/flatfile.html";
    String[] lines = Fget.readFileToArray(fname);
    if (lines == null)
    {
      common.ptod("label: %s", ff.label);
      common.ptod("File: %s", fname);
      common.ptod("File ignored, file is either empty or does not exist");
      return false;
    }
    String line     = null;
    int    comments = 0;

    /* Look for the first header: */
    int i = 0;
    for (i = 0; i < lines.length; i++)
    {
      line = lines[i].trim();
      if (line.startsWith("tod"))
        break;
      if (line.startsWith("*"))
        comments++;
    }

    /* Flatfile.html alas is also created in the multi-jvm h1,h2 subdirectory */
    /* we can recognize this because those files will not contain comments */
    //if (line == null && comments == 0)
    //  return;

    if (!line.startsWith("tod"))
      throw new CompException("Missing 'tod' label in file " + fname);

    /* Pick up all column headers: */
    StringTokenizer st = new StringTokenizer(line);
    Vector <String> headers = new Vector(st.countTokens());
    while (st.hasMoreTokens())
      headers.add(st.nextToken().toLowerCase());

    /* Now pick up data for each 'avg_' run from the flatfile: */
    CompRunData last_run = null;
    for (i++; i < lines.length; i++)
    {
      line = lines[i].trim();

      HashMap flatfile_data = new HashMap();
      st = new StringTokenizer(line);
      int col = 0;
      while (st.hasMoreTokens())
      {
        String value = st.nextToken();
        //if (headers.elementAt(col).equals("Run"))
        //  common.ptod("value: " + value + " " + headers.elementAt(col));
        try
        {
          double number = Double.parseDouble(value);
          String column = headers.get(col++);
          if (Arrays.asList(keep_list).contains(column))
            flatfile_data.put(column, new Double(number));
        }
        catch (NumberFormatException e)
        {
          flatfile_data.put(headers.get(col++), value);
        }
      }

      if (col != headers.size())
      {
        String txt = "\nlast line: " + line + "\nheaders: " + headers.size() +
                     "\ndata: " + col;
        throw new CompException("Not enough data for all columns in file " + fname + txt);
      }

      /* Only save data for a run average: */
      Object interval = flatfile_data.get("interval");
      if (interval == null)
        throw new CompException("Missing 'Interval' column in file " + fname);


      if ((interval instanceof Double) &&
          ((Double) interval).doubleValue() == 1 && last_run != null)
      {
        all_rds.add(last_run);
        ff.rds.add(last_run);
      }

      last_run = new CompRunData(ff.label, flatfile_data, ff.vdbench_dir);
    }

    /* Add the last process run at eof. This assumes that the last run was   */
    /* complete!! There is no way to check, but the only consequence will be */
    /* that the values of the last running interval will be seen as run      */
    /* averages Fine for now.                                                */
    if (last_run != null)
    {
      all_rds.add(last_run);
      ff.rds.add(last_run);
    }

    if (all_rds.size() == 0)
      throw new CompException("No valid run averages found in file " + fname);

    return true;
  }


  /**
   * 'Temporary' functionality to parse summary.html for those files
   * where the 'Starting RD=' data is not in flatfile.html yet.
   */
  public static void parseSummary(ArrayList <FlatFile> all_flats)
  {
    /* If we already have any run description, exit: */
    for (int i = 0; i < all_flats.size(); i++)
    {
      FlatFile ff = (FlatFile) all_flats.get(i);

      for (int j = 0; j < ff.rds.size(); j++)
      {
        CompRunData run = ff.rds.get(j);
        if (run.run_description != null)
        {
          common.failure("not expected");
          return;
        }
      }
    }


    /* Parse the summary.html files in the flatfile directories one at the time: */
    for (int i = 0; i < all_flats.size(); i++)
    {
      FlatFile ff = (FlatFile) all_flats.get(i);

      /* Get all "Starting RD" lines for this one summary.html: */
      String summ = ff.vdbench_dir+ "/summary.html";

      Vector descriptions = new Vector(8, 0);
      for (String line : Fget.readFileToArray(summ))
      {
        if (line.indexOf("Starting RD") != -1)
          descriptions.add(line);
      }


      /* We now have all these 'Starting RD' and must put them back in the runs: */
      /* (loop only for the amount of descriptions, an active run may not be complete) */
      //common.ptod("descriptions.size(): " + descriptions.size());
      for (int j = 0; j < descriptions.size(); j++)
      {
        CompRunData rd = ff.rds.get(j);
        String description = (String) descriptions.get(j);
        StringTokenizer st = new StringTokenizer(description, "=; ");

        boolean found = false;
        while (st.hasMoreTokens())
        {
          String token = st.nextToken();
          if (token.equals("RD"))
          {
            found = true;

            String[] split = description.split("\"+");
            String ref = split[1];
            //common.ptod("ref: " + ref);

            /* Remove html info if needed: */
            String name = st.nextToken();
            if (name.indexOf("</b>") != -1)
              name = name.substring(0, name.indexOf("</b>"));

            if (WlComp.add_parent)
              name = rd.parent + "/" + name;

            if (!WlComp.add_parent)
            {
            if (!name.equals(rd.rd_name))
              throw new CompException("Unmatched RD names in file " + summ +
                                      ": " + rd.rd_name + "/" + name);
            }

            rd.run_description = description;
            rd.reference       = ref;
            break;
          }
        }

        if (!found)
          throw new CompException("No proper RD name found in " + description);

      }
    }
  }

  public static void main(String[] args)
  {
    //Vector all_runs = new Vector(8, 0);
    //String fname = "C:\\wlcomp\\dir1\\flatfile.html";
    //parseFile(fname, all_runs);
  }
}

class FlatFile
{
  String label;
  String vdbench_dir;
  Vector <CompRunData> rds = new Vector(8, 0);

  public FlatFile(String lbl, String name)
  {
    this.label       = lbl;
    this.vdbench_dir = name;
  }
}
