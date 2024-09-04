package VdbComp;

/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

import Utils.*;

/**
 * Workload Comparator menu options.
 */
public class WlMenus extends JMenuBar implements ActionListener
{
  private final static String c =
  "Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.";

  private WlComp wlcomp;


  private JMenu             file_menu  = new JMenu("File");
  private JMenu             options    = new JMenu("Options");

  private JMenuItem         ranges     = new JMenuItem("Modify Color Ranges");
  private JMenuItem         browse_old = new JMenuItem("Browse old");
  private JMenuItem         browse_new = new JMenuItem("Browse new");
  private JMenuItem         save       = new JMenuItem("Export ");
  private JMenuItem         exit       = new JMenuItem("Exit");

  public WlMenus(WlComp wl)
  {
    wlcomp = wl;

    add(file_menu);
    add(options);

    ranges.addActionListener(this);
    exit.addActionListener(this);
    save.addActionListener(this);
    browse_old.addActionListener(this);
    browse_new.addActionListener(this);

    options.add(ranges);
    file_menu.add(save);
    //file_menu.add(browse_old);
    //file_menu.add(browse_new);
    file_menu.add(exit);
  }


  public void actionPerformed(ActionEvent e)
  {
    String cmd = e.getActionCommand();

    if (cmd.equals(exit.getText()))
    {
      StoredParms.storeParms();
      System.exit(0);
    }

    else if (cmd.equals(ranges.getText()))
    {
      ChangeRanges cr = new ChangeRanges(wlcomp);
      cr.setSize(400, 300);
      Utils.Message.centerscreen(cr);
      cr.setVisible(true);
    }

    else if (cmd.equals(save.getText()))
    {
      String csv = wlcomp.frame.getFile();
      if (csv == null)
        return;

      wlcomp.batchExport(csv);
    }

  }
}

