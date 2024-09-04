package VdbComp;

/*
 *
 * Copyright (c) 2000-2008 Sun Microsystems, Inc. All Rights Reserved.
 *
 */

import java.awt.Component;
import java.util.Vector;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;


/**
 * Multi-line table header.
 * Creates one line for each blank delimited field in the column header.
 */
class MultiLineHeader extends JList implements TableCellRenderer
{
  private final static String c = "Copyright (c) 2000-2008 Sun Microsystems, Inc. " +
                                  "All Rights Reserved.";

  public MultiLineHeader()
  {
    setOpaque(true);
    setForeground(UIManager.getColor("TableHeader.foreground"));
    setBackground(UIManager.getColor("TableHeader.background"));
    setBorder(UIManager.getBorder("TableHeader.cellBorder"));
    ListCellRenderer renderer = getCellRenderer();
    ((JLabel)renderer).setHorizontalAlignment(JLabel.CENTER);
    setCellRenderer(renderer);
  }


  /**
   * Column header: one line per blank-delimited piece of the name.
   * If the title ends on (xxx), separate the (xxx) also.
   */
  public Component getTableCellRendererComponent(JTable  table,
                                                 Object  value,
                                                 boolean isSelected,
                                                 boolean hasFocus,
                                                 int     row,
                                                 int     column)
  {
    setFont(table.getFont());
    String string  = (value == null) ? "" : value.toString();

    setListData(getColumnHeaderLines(string));
    return this;
  }

  public static Vector getColumnHeaderLines(String string)
  {
    String[] split = string.trim().split(" +");

    Vector <String> vector = new Vector();
    for (int i = 0; i < split.length; i++)
      vector.add(split[i]);

    String last = split[split.length-1];
    int left    = last.lastIndexOf("(");
    int right   = last.lastIndexOf(")");
    if (left != -1 && right != -1 && !last.startsWith("("))
    {
      vector.set(split.length-1, last.substring(0, left));
      vector.add(last.substring(left));
    }

    return vector;
  }
}



