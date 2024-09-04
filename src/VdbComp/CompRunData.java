package VdbComp;

/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.util.*;
//import Utils.common;
import Utils.Fget;
import Vdb.common;

/**
 * Contains data for each run
 */
public class CompRunData implements Comparable
{
  private final static String c =
  "Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.";

  String  label;
  String  rd_name;
  String  rd_short;
  String  run_description;
  String  reference;
  String  vdbench_dir;
  String  parent;
  boolean average = false;

  double  delta_resp;
  double  delta_rate;
  double  delta_mb;

  double  resp_max;
  double  rate_mean;
  double  rate_std;
  double  relative_std;

  HashMap <String, Object> flatfile_data = null;
  HashMap <String, String> forxx_values;

  ArrayList <CompRunData> detail_list = null;

  public CompRunData(String lbl, HashMap map, String dir)
  {
    flatfile_data   = map;
    label           = lbl;
    vdbench_dir     = dir;

    if (vdbench_dir != null)
      parent = new File(vdbench_dir).getName();

    /* Pick up the run name: */
    rd_short = (String) flatfile_data.get("run");
    if (WlComp.add_parent)
      rd_name = parent + "/" + "rdxyz";
      //rd_name = parent + "/" + rd_short;
    else
      rd_name = (String) flatfile_data.get("run");

    if (rd_name == null)
      new CompException("No proper 'Run' column found in file " + label);
  }


  /**
   * Parse for 'forxx' values, returning a HashMap with each found value
   */
  public void parseForxxValues(HashMap all_keywords)
  {
    forxx_values = new HashMap();
    if (run_description == null)
      return;

    if (run_description.indexOf("For loops: None") != -1)
      return;

    if (run_description.indexOf("For loops:") == -1)
      return;
      //throw new CompException("Unable to find proper 'loops:' value: \n" + run_description);

    /* Skip until 'loops'.                                            */
    StringTokenizer st = new StringTokenizer(run_description, " <");
    while (!st.nextToken().contains("loops:"));

    /* Starting Vdbench 5.00 there are TWO occurences of 'for loops:' */
    //if (run_description.startsWith("<"))
    //  while (st.hasMoreTokens() && !st.nextToken().contains("loops:"));

    /* We now have the 'forxxx=nnnn' pairs: */
    while (st.hasMoreTokens())
    {
      String pair = st.nextToken();
      if (pair.startsWith("/b"))
        break;

      if (pair.indexOf("=") == -1)
        throw new CompException("No '=' value inside of forxx pair: \n" + run_description);
      String keyword = pair.substring(0, pair.indexOf("="));
      String value   = pair.substring(pair.indexOf("=") + 1);
      //common.ptod("keyword: " + keyword + " " + value);

      try
      {
        double number;
        if (value.endsWith("k"))
          number = 1024 * Double.parseDouble(value.substring(0, value.length() - 1));
        else if (value.endsWith("m"))
          number = 1024 * 1024 * Double.parseDouble(value.substring(0, value.length() - 1));
        else if (value.endsWith("g"))
          number = 1024 * 1024 * 1024 * Double.parseDouble(value.substring(0, value.length() - 1));
        else if (keyword.equals("iorate") && value.equals("max"))
          number = 999988;
        else
          number = Double.parseDouble(value);

        /* '-i threads' ignores the 'threads=' parameter: */
        if (WlComp.getopt.check('i'))
        {
          if (keyword.equalsIgnoreCase(WlComp.getopt.get_string()))
            continue;
        }

        //common.ptod("keyword: " + keyword + "    " + number);
        forxx_values.put(keyword, value);
        all_keywords.put(keyword, keyword);

        rd_name += "," + keyword + "=" + value;
      }
      catch (NumberFormatException e)
      {
        common.ptod(e);
        throw new CompException("Invalid numerics in forxx pair: \n" + run_description);
      }
    }
  }


  /**
   * Get data value from the HashMap using the column label.
   * There have been changes (yes, I screwed up) where a label was changed in
   * the flatfile to start with an UpperCase, while the code did not properly
   * support that (had forgotten about VdbComp.
   * The result was that we had a mix of 'Case/case' flatfiles and a new need to
   * handle both.
   *
   * This should take care of that.
   */
  private static HashMap <String, String> warning_map = new HashMap(32);
  public Object getValue(String label)
  {
    /* First look for the a 'normal case' field: */
    Object value = flatfile_data.get(label);
    if (value != null)
      return value;

    /* Then try 'lowercase' field: */
    value = flatfile_data.get(label.toLowerCase());
    if (value != null)
      return value;

    /* Field does not exists, give a one-time warning: */
    if (warning_map.put(label, label) == null)
    {
      common.ptod("Unknown getValue for " + label);
      common.where(8);
    }
    return null;
  }


  public int compareTo(Object obj)
  {
    CompRunData rd1 = (CompRunData) this;
    CompRunData rd2 = (CompRunData) obj;

    return rd1.rd_name.compareToIgnoreCase(rd2.rd_name);
  }
}

