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

import Utils.Fget;
import Utils.Fput;
import Utils.OS_cmd;

import Vdb.common;

/**
 * Vdbench compare GUI
 *
 */
public class CompFrame extends JFrame implements ActionListener
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  private static boolean dirs_in_parm = false;

  private static WlComp wlcomp = null;

  public  DataModel  dm1;
  private DataModel2 dm2;

  JTable table1  = null;
  JTable table2 = null;

  public  volatile static String   search_value = "";
  private static JPanel    top_panel  = null;
  private static JTextField search     = new JTextField("");
  private static JCheckBox hide1      = new JCheckBox("Hide 0-1%");
  private static JCheckBox hide2      = new JCheckBox("Hide 0-2%");
  private static JButton   ok_button  = new JButton("OK");
  private static JButton   ext_button = new JButton("Exit");

  public  static JScrollPane avg_scroll = null;
  private static JScrollPane dtl_scroll = null;
  private static JSplitPane  split_pane = null;

  public CompFrame(WlComp wl)
  {
    wlcomp = wl;
    addWindowListener(new WindowAdapter()
                      {
                        public void windowClosing(WindowEvent e)
                        {
                          StoredParms.storeParms();
                          System.exit(0);
                        }
                      });

    setJMenuBar(new WlMenus(wlcomp));

    setCompareTitle();

    hide1.addActionListener(this);
    hide2.addActionListener(this);
    ext_button.addActionListener(this);
    ok_button.addActionListener(this);


    search.addKeyListener(new KeyListener()
                          {
                            public void keyReleased(KeyEvent key)
                            {
                              search_value  = search.getText();
                            }
                            public void keyPressed(KeyEvent key)
                            {
                              if (key.getKeyCode()==KeyEvent.VK_ENTER)
                                doCompare(true);
                            }
                            public void keyTyped(KeyEvent key)
                            {
                            }
                          });

    this.setSize(StoredParms.last_width, StoredParms.last_height);
    this.setLocation(StoredParms.last_x, StoredParms.last_y);

    buildTopPanel();
  }




  public void buildTopPanel()
  {
    if (top_panel != null)
      getContentPane().remove(top_panel);

    JLabel label = new JLabel("Search:");

    Dimension dim = new Dimension(60, 20);
    search.setMinimumSize(dim);
    search.setMaximumSize(dim);
    search.setPreferredSize(dim);

    int x = 0;
    top_panel = new JPanel();
    top_panel.setLayout(new GridBagLayout());
    top_panel.add(hide1,      new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    top_panel.add(hide2,      new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    top_panel.add(label,      new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    top_panel.add(search,     new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    top_panel.add(ok_button,  new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    top_panel.add(ext_button, new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    Delta[] deltas = Delta.getDeltas();
    for (int i = 0; i < deltas.length; i++)
      top_panel.add(deltas[i], new GridBagConstraints(x++, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    getContentPane().add(top_panel, BorderLayout.NORTH);
  }


  public void doCompare(boolean load_data)
  {
    clearTable();

    try
    {
      waitCursor(this);
      if (load_data)
        wlcomp.loadAllData();

      double hide = 0;
      if (hide1.isSelected())
        hide = 1;
      if (hide2.isSelected())
        hide = 2;

      dm1 = new DataModel();
      ArrayList <DataPair> data_pairs = WlComp.createDataPairs(hide);
      dm1.storePairs(data_pairs);

      dm2 = new DataModel2();
      dm2.setDetail(new ArrayList(0));
      table1 = new JTable(dm1);
      table2 = new JTable(dm2);
      dm1.setRenderers(table1);
      dm1.setColumnWidth(table1);
      dm2.setRenderers(table2);
      dm2.setColumnWidth(table2);


      MultiLineHeader renderer = new MultiLineHeader();
      Enumeration e = table1.getColumnModel().getColumns();
      while (e.hasMoreElements())
      {
        ((TableColumn) e.nextElement()).setHeaderRenderer(renderer);
      }


      /* Allow sorting of the table: */
      TableRowSorter <TableModel> sorter = new TableRowSorter(dm1);
      table1.setRowSorter(sorter);

      /* This code helps with the properly sorting of Strings containing */
      /* numeric unaligned values:                                       */
      sorter.setStringConverter(new TableStringConverter()
                                {
                                  public String toString(TableModel model, int row, int col)
                                  {
                                    Object value = model.getValueAt(row, col);

                                    if (value instanceof Double)
                                    {
                                      double doub = (Double) value;
                                      return String.format("%018.3f", 1000000000000. + doub);
                                    }

                                    if (value instanceof String)
                                    {
                                      String val = (String) value;
                                      val = common.replace(val, ",", "");
                                      if (common.isDouble(val))
                                      {
                                        double doub = Double.parseDouble(val);
                                        return String.format("%018.3f", 1000000000000. + doub);
                                      }
                                    }

                                    /* delta sort: wrap it around a HUGE value: */
                                    if (value instanceof DeltaValue)
                                    {
                                      DeltaValue dv = (DeltaValue) value;
                                      String result = String.format("%018.3f", 1000000000000. + dv.value);
                                      return result;
                                    }

                                    String expand = DataPair.expand(value.toString());
                                    //common.ptod("expand: " + expand);
                                    return expand;
                                  }
                                });

      table2.addMouseListener(new java.awt.event.MouseAdapter()
                              {
                                public void mousePressed(MouseEvent me)
                                {
                                  if (me.getClickCount() > 1)
                                  {
                                    int row = table2.getSelectedRow();
                                    CompRunData rd = dm2.getDetail(row);
                                    startBrowser(rd.vdbench_dir, rd.reference);
                                  }

                                  else if (me.isMetaDown()) // && named_model.showingKeys())
                                    rightClickInTable(me);
                                }
                              });

      table1.addMouseListener(new java.awt.event.MouseAdapter()
                              {
                                public void mouseClicked(MouseEvent me)
                                {
                                  rowSelected(table1, table1.convertRowIndexToModel(table1.getSelectedRow()));
                                }

                              });

      table1.addKeyListener(new KeyListener()
                            {
                              public void keyReleased(KeyEvent key)
                              {
                                if (key.getKeyText(key.getKeyCode()).equalsIgnoreCase("up"))
                                  rowSelected(table1, table1.getSelectedRow());
                                else if (key.getKeyText(key.getKeyCode()).equalsIgnoreCase("Down"))
                                  rowSelected(table1, table1.getSelectedRow());
                              }
                              public void keyPressed(KeyEvent key)
                              {
                              }
                              public void keyTyped(KeyEvent key)
                              {
                              }
                            });

      /* If we had an existing panel, remove it first:                          */
      /* (It would have been better to leave the split panel alone, and instead */
      /* just send new data to DataModel(), but this works for now)             */
      if (split_pane != null)
        getContentPane().remove(split_pane);

      avg_scroll = new JScrollPane(table1);
      dtl_scroll = new JScrollPane(table2);
      split_pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, avg_scroll, dtl_scroll);

      getContentPane().add(split_pane, BorderLayout.CENTER);
      setVisible(true);
      split_pane.setDividerLocation(.8);

      setVisible(true);
    }

    catch (CompException e)
    {
      common.ptod(e);
      common.ptod("e.getMessage(): " + e.getMessage());
      JOptionPane.showMessageDialog(this, "Error during Workload Compare: \n" +
                                    e.getMessage(),
                                    "Processing Aborted",
                                    JOptionPane.ERROR_MESSAGE);
    }
    catch (Exception e)
    {
      StackTraceElement[] stack = e.getStackTrace();
      String txt = "\nStack Trace: ";
      for (int i = 0; stack != null && i < stack.length; i++)
        txt += "\n at " + stack[i].toString();

      JOptionPane.showMessageDialog(this, "Error during Workload Compare: \n" +
                                    e.toString() + "\t" +
                                    txt,
                                    "Processing Aborted",
                                    JOptionPane.ERROR_MESSAGE);
    }

    waitCursor(this);
  }


  public void rowSelected(JTable table, int row)
  {
    ArrayList <CompRunData> detail = ((DataModel) table.getModel()).getDetail(row);
    dm2.setDetail(detail);
    dm2.setColumnWidth(table2);

    dm2.fireTableDataChanged();
  }


  public static void waitCursor(Component comp)
  {
    if (comp.getCursor().getType() != Cursor.WAIT_CURSOR)
      comp.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    else
      comp.setCursor(Cursor.getDefaultCursor());
  }



  private String getDirectory(String dir, String label)
  {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select '" + label + "' Directory");
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    /* Set the current directory to the PARENT, unless directory was given as argument: */
    if (dir != null && Fget.dir_exists(dir))
    {
      if (dirs_in_parm)
        fc.setCurrentDirectory(new File(dir));
      else
        fc.setCurrentDirectory(new File(dir).getParentFile());
    }


    /* Keep the same old directory? */
    if (fc.showOpenDialog(null) != fc.APPROVE_OPTION)
      return dir;

    /* Get new directory: */
    dir = fc.getSelectedFile().getAbsolutePath();

    if (avg_scroll != null)
    {
      getContentPane().remove(avg_scroll);
      update(getGraphics());
      setVisible(true);
    }

    return dir;
  }

  public String getFile()
  {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select file");
    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

    /* Keep the same old directory? */
    if (fc.showOpenDialog(null) != fc.APPROVE_OPTION)
      return null;

    /* Get new file: */
    return fc.getSelectedFile().getAbsolutePath();
  }

  public void actionPerformed(ActionEvent e)
  {
    String cmd = e.getActionCommand();

    if (cmd.equals(hide1.getText()))
      doCompare(false);

    else if (cmd.equals(hide2.getText()))
      doCompare(false);

    else if (cmd.equals(ok_button.getText()))
      doCompare(true);

    else if (cmd.equals(ext_button.getText()))
    {
      StoredParms.storeParms();
      System.exit(0);
    }

    setCompareTitle();
  }

  // There's a problem here: I include ALL positional parameters in the title,
  // which becomes a mess when using /* .......
  private void setCompareTitle()
  {
    String txt = "Vdbench Workload Comparator: ";
    for (int i = 1; i < WlComp.pos.size(); i++)
      txt += WlComp.pos.get(i) + " ";
    setTitle(txt);
  }

  public void clearTable()
  {
    if (avg_scroll != null)
    {
      getContentPane().remove(avg_scroll);
      update(getGraphics());
      setVisible(true);
      avg_scroll = null;
    }
  }


  public boolean askOkCancel(String text)
  {
    int rc = JOptionPane.showConfirmDialog(this, text,
                                           "Information message",
                                           JOptionPane.OK_CANCEL_OPTION);

    return rc == JOptionPane.OK_OPTION;
  }


  /**
   * Right click in table2.
   *
   * Stolen from Swat.
   */
  private void rightClickInTable(MouseEvent me)
  {

    ActionListener menuListener = new ActionListener()
    {
      public void actionPerformed(ActionEvent e)
      {
        String fname, txt;
        String cmd = e.getActionCommand();

        if (cmd.startsWith("Browse Vdbench output"))
        {
          /* Calculate the min+max timestamps: */
          long min_tod = Long.MAX_VALUE;
          long max_tod = 0;
          for (int row = 0; row < table2.getRowCount(); row++)
          {
            if (table2.isRowSelected(row))
            {
              CompRunData rd = dm2.getDetail(row);
              startBrowser(rd.vdbench_dir, rd.reference);
            }
          }
        }
      }
    };

    buildPopup(menuListener, me);
  }


  private void buildPopup(ActionListener menuListener, MouseEvent me)
  {
    ArrayList <String> items = new ArrayList(10);

    items.add("Browse Vdbench output");

    String[] array = items.toArray(new String[0]);
    Pop_list pop = new Pop_list(array, menuListener);
    pop.getPopup().show(table2, me.getX(), me.getY());
  }


  private static void startBrowser(String dir, String ref)
  {
    if (common.onWindows())
    {
      OS_cmd ocmd = new OS_cmd();
      ocmd.addQuot("start ");
      ocmd.addQuot("firefox ");
      ocmd.add(dir + "/summary.html#" + ref);
      ocmd.execute();
    }

    else
    {
      String browser  = "/usr/bin/firefox";
      if (!Fget.file_exists(browser))
        browser = "firefox";
      OS_cmd ocmd = new OS_cmd();
      ocmd.addQuot(browser);
      ocmd.add(dir + "/summary.html");
      ocmd.add("&");
      ocmd.execute();
    }
  }

}
