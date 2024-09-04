package VdbComp;

/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.table.*;

import Utils.Format;

import Vdb.RD_entry;
import Vdb.common;

/**
 * JTable data model for the workload comparator.
 * This model supports the 'average' display of data.
 */
public class DataModel extends AbstractTableModel
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  private ArrayList <String>               column_names = null;
  private ArrayList <FractionCellRenderer> renderers = null;
  private ArrayList <DataPair>             data_pairs;

  public DataModel()
  {
    //createcolumn_names();
  }

  public void storePairs(ArrayList <DataPair> pairs)
  {
    //for (DataPair pair : pairs)
    //  common.ptod("pair: " + pair.rd_name);
    data_pairs = pairs;
    Collections.sort(data_pairs);

    createcolumn_names();
  }


  /**
   * Create column headers.
   */
  public void createcolumn_names()
  {
    column_names = new ArrayList(32);
    renderers    = new ArrayList(32);

    /* Fixed column names: */
    addColumn("Run",          0, 0, SwingConstants.LEFT);

    /* Data column names: */
    addColumn("Old resp",      8, 3, SwingConstants.RIGHT);
    addColumn("New resp",      8, 3, SwingConstants.RIGHT);
    addColumn("Delta resp",    8, 3, SwingConstants.RIGHT);
    addColumn("Old iops",      8, 0, SwingConstants.RIGHT);
    addColumn("New iops",      8, 0, SwingConstants.RIGHT);
    addColumn("Delta iops",    8, 0, SwingConstants.RIGHT);

    addColumn("Old mbs",       8, 0, SwingConstants.RIGHT);
    addColumn("New mbs",       8, 0, SwingConstants.RIGHT);
    addColumn("Delta mbs",   8, 0, SwingConstants.RIGHT);

    addColumn("Old  max",      8, 2, SwingConstants.RIGHT);
    addColumn("New  max",      8, 2, SwingConstants.RIGHT);

    /* Special columns for Roch and Blaise: */
    if (WlComp.getopt.check('v'))
    {
      addColumn("Old std",       8, 2, SwingConstants.RIGHT);
      addColumn("New std",       8, 2, SwingConstants.RIGHT);
      addColumn("Old rstd",      8, 3, SwingConstants.RIGHT);
      addColumn("New rstd",      8, 3, SwingConstants.RIGHT);
      addColumn("Regression",    8, 3, SwingConstants.RIGHT);
    }

    /* Get ONE set of CompRunData to help find out if a certain column is needed: */
    CompRunData cd = null;
    if (data_pairs.size() == 0)
      return;

    if (data_pairs.get(0).old_rd != null)
      cd = data_pairs.get(0).old_rd;
    else if (data_pairs.get(0).new_rd != null)
      cd = data_pairs.get(0).new_rd;

    /* Some fields are there only for SDs or FSDs: */
    if (cd != null && cd.flatfile_data.get("queue_depth") != null)
    {
      addColumn("Old qd",      8, 0, SwingConstants.RIGHT);
      addColumn("New qd",      8, 0, SwingConstants.RIGHT);
      addColumn("Delta qd",    8, 0, SwingConstants.RIGHT);
    }

    if (cd != null && cd.flatfile_data.get("read%") != null)
    {
      addColumn("Old rdpct",   6, 1, SwingConstants.RIGHT);
      addColumn("New rdpct",   6, 1, SwingConstants.RIGHT);
    }
  }


  /**
   * Add a column, together with renderer information.
   */
  private void addColumn(String col, int width, int dec, int align)
  {
    column_names.add(col);
    renderers.add(new FractionCellRenderer(width, dec, align));
  }


  /**
   * Set display renders for easch column
   */
  public void setRenderers(JTable table)
  {
    TableColumnModel model = table.getColumnModel();
    for (int i = 0; i < column_names.size(); i++)
      model.getColumn(i).setCellRenderer(renderers.get(i));
  }


  /**
   * Set the minimum column width for each column
   */
  public void setColumnWidth(JTable table)
  {
    TableColumnModel model = table.getColumnModel();
    for (int i = 0; i < column_names.size(); i++)
      TableWidth.sizeColumn(i, table);
  }


  public ArrayList <CompRunData> getDetail(int row)
  {
    DataPair dp = data_pairs.get(row);
    ArrayList <CompRunData> detail = new ArrayList(8);

    if (dp.old_rd != null)
    {
      for (CompRunData rd : dp.old_rd.detail_list)
        detail.add(rd);
    }
    if (dp.new_rd != null)
    {
      for (CompRunData rd : dp.new_rd.detail_list)
        detail.add(rd);
    }

    return detail;
  }

  public Object getValueAt(int row, int col)
  {
    DataPair dp = data_pairs.get(row);
    CompRunData rd = null;
    String colname = column_names.get(col);

    try
    {
      if (colname.equalsIgnoreCase("Run"))
        return dp.rd_name;

      if (dp.old_rd != null && dp.new_rd != null)
      {
        if (colname.equals("Regression"))
        {
          double res = dp.old_rd.rate_mean - dp.new_rd.rate_mean;
          res = res * 100 / dp.old_rd.rate_mean;
          return res;
        }
      }


      /* Decide what data we want, old or new: */
      if (colname.toLowerCase().startsWith("old"))
        rd = dp.old_rd;
      else
        rd = dp.new_rd;


      if (colname.equalsIgnoreCase("Delta resp"))
        return new DeltaValue(getDelta(dp.old_rd, dp.new_rd, "Resp"));

      if (colname.equalsIgnoreCase("Delta iops"))
        return new DeltaValue(getDelta(dp.old_rd, dp.new_rd, "Rate"));

      if (colname.equalsIgnoreCase("Delta mbs"))
        return new DeltaValue(getDelta(dp.old_rd, dp.new_rd, "MB/sec"));

      if (colname.equalsIgnoreCase("Delta qd"))
        return new DeltaValue(getDelta(dp.old_rd, dp.new_rd, "queue_depth"));


      if (rd == null)
        return "n/a";


      if (colname.endsWith("resp"))
        return  rd.getValue("resp");

      if (colname.endsWith("iops"))
        return String.format("%,.0f", rd.getValue("rate"));

      if (colname.endsWith("mbs"))
        return  rd.getValue("mb/sec");

      if (colname.endsWith("max"))
        return  rd.resp_max;

      if (colname.endsWith("qd"))
        return  rd.getValue("queue_depth");

      if (colname.endsWith("rdpct"))
        return  rd.getValue("read%");

      if (colname.endsWith("rstd"))
        return  rd.relative_std;

      if (colname.endsWith("std"))
        return  rd.rate_std;

      else

        return "DataModel n/a";
    }
    catch (Exception e)
    {
      common.ptod("colname: " + colname);
      common.ptod("dp.old_rd: " + dp.old_rd);
      common.ptod("dp.new_rd: " + dp.new_rd);
      //common.ptod("dp: " + dp.old_rd.vdbench_dir);
      common.failure(e);
    }
    return null;
  }


  /**
   * Get the delta percentage between two values
   */
  public static double getDelta(CompRunData old_run, CompRunData new_run, String label)
  {
    /* If we don't have both old and new, just return 0: */
    if (old_run == null || new_run == null)
      return 0;

    if (old_run.getValue(label) == null)
      return 0;
    //common.failure("can not find label: " + label);
    double old_val = ((Double) old_run.getValue(label)).doubleValue();
    double new_val = ((Double) new_run.getValue(label)).doubleValue();
    double delta    = old_val - new_val;

    /* For response time of course less is good, not bad: */
    if (label.equalsIgnoreCase("Resp"))
      return delta * 100. / old_val;
    else
      return delta * 100. / old_val * -1;
  }

  public int getRowCount()
  {
    return data_pairs.size();
  }

  public int getColumnCount()
  {
    return column_names.size();
  }
  public String getColumnName(int col)
  {
    return column_names.get(col);
  }

  public String getLabels(int row)
  {
    DataPair dp = data_pairs.get(row);
    if (dp.old_rd != null && dp.new_rd != null)
      return dp.old_rd.label + " - " + dp.new_rd.label;
    else if (dp.old_rd != null)
      return dp.old_rd.label;
    else
      return dp.new_rd.label;
  }
}
