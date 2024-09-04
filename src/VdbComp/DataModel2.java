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
 * JTable data model for the workload comparator
 *
 * Thought: I could show, in color, the delta of these values, the delta between
 * the CURRENT value and the average as displayed in DataModel().
 *
 * Basically, I can also just calculate the info and store it in DataPair so
 * that here allI need to do is display, not other logic?
 */
public class DataModel2 extends AbstractTableModel
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  private ArrayList <String> column_names = null;


  private ArrayList <FractionCellRenderer> renderers = null;

  private ArrayList <CompRunData>  detail;

  public DataModel2()
  {
    column_names = new ArrayList(32);
    renderers    = new ArrayList(32);

  }
  /**
 * Add a column, together with renderer information.
 */
  private void addColumn(String col, int width, int dec, int align)
  {
    column_names.add(col);
    renderers.add(new FractionCellRenderer(width, dec, align));
  }


  public void setDetail(ArrayList <CompRunData> dtl)
  {
    detail = dtl;

    column_names = new ArrayList(32);
    renderers    = new ArrayList(32);

    /* Get ONE set of CompRunData to help find out if a certain column is needed: */
    CompRunData cd = null;
    if (detail.size() > 0)
      cd = detail.get(0);

    addColumn("Label (double click on row)", 0, 0, SwingConstants.LEFT);
    addColumn("IOPS",                        8, 3, SwingConstants.RIGHT);
    addColumn("Resp",                        8, 3, SwingConstants.RIGHT);
    addColumn("Resp max",                    8, 3, SwingConstants.RIGHT);
    addColumn("Resp stddev",                 8, 3, SwingConstants.RIGHT);

    /* Some fields are there only for SDs or FSDs: */
    // Even if these were added, there is some discrepancy shoiwng qdepth in
    // the mb/sec column. Removed for now.
    //if (cd != null && cd.flatfile_data.get("queue_depth") != null)
    //{
    //  addColumn("Qdepth",                      8, 3, SwingConstants.RIGHT);
    //}

    addColumn("MB/sec",                      8, 3, SwingConstants.RIGHT);
  }
  public CompRunData getDetail(int row)
  {
    return detail.get(row);
  }


  public Object getValueAt(int row, int col)
  {
    CompRunData rd = detail.get(row);
    String colname = column_names.get(col);

    /************************************************************************/
    /* Note that any new field here must also be added ParseData.keep_list! */
    /************************************************************************/

    if (colname.toLowerCase().startsWith("label"))
      return  rd.vdbench_dir; //  rd.label;

    if (colname.equalsIgnoreCase("resp"))
      return  String.format("%,.3f", rd.getValue("resp"));

    if (colname.equalsIgnoreCase("iops"))
      return  String.format("%,.3f", rd.getValue("rate"));

    if (colname.equalsIgnoreCase("resp max"))
      return  String.format("%,.3f", rd.getValue("resp_max"));

    if (colname.equalsIgnoreCase("Resp stddev"))
      return  String.format("%,.3f", rd.getValue("resp_std"));

    if (colname.equalsIgnoreCase("MB/sec"))
      return  String.format("%,.0f", rd.getValue("MB/sec"));

    if (colname.equalsIgnoreCase("qdepth"))
      return  String.format("%,.0f", rd.getValue("queue_depth"));

    else
      return colname;
  }


  /**
   * Get the delta percentage between two values
   */
  public double getDelta(CompRunData old_run, CompRunData new_run, String label)
  {
    if (old_run.getValue(label) == null)
      common.failure("can not find label: " + label);
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
    return detail.size();
  }

  public int getColumnCount()
  {
    return column_names.size();
  }
  public String getColumnName(int col)
  {
    return column_names.get(col);
  }

  /**
   * Set the minimum column width for each column
   */
  public void setColumnWidth(JTable table)
  {
    TableColumnModel model = table.getColumnModel();
    for (int i = 0; i < model.getColumnCount(); i++)
      TableWidth.sizeColumn(i, table);
  }

  /**
   * Set display renders for easch column
   */
  public void setRenderers(JTable table)
  {
    TableColumnModel model = table.getColumnModel();
    for (int i = 0; i < model.getColumnCount(); i++)
      model.getColumn(i).setCellRenderer(renderers.get(i));
  }
}
