package VdbComp;

/*
 *
 * Copyright (c) 2000-2008 Sun Microsystems, Inc. All Rights Reserved.
 *
 */

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

/**
 * Popup menus.
 * Some are created using brandnew menu items, others are reusing existing real
 * menus, in this case MenuCharts().
 * The latter to eliminate having to keep them in-sync.
 */
public class Pop_list extends JPanel implements MouseListener
{
  private final static String c = "Copyright (c) 2000-2008 Sun Microsystems, Inc. " +
                                  "All Rights Reserved.";

  private ActionListener original_action;
  private Component[]    original_items;
  private JPopupMenu popup;



  /**
   * Regular new popup menu.
   */
  public Pop_list(String[] items, ActionListener action)
  {
    popup = new JPopupMenu();
    JMenuItem item;

    for (int i = 0; i < items.length; i++)
    {
      popup.add(item = new JMenuItem(items[i]));
      item.setHorizontalTextPosition(JMenuItem.RIGHT);
      item.addActionListener(action);
    }
  }


  public JPopupMenu getPopup()
  {
    return popup;
  }


  /**
   * An inner class to check whether mouse events are the popup trigger
   */
  class MousePopupListener extends MouseAdapter
  {
    public void mousePressed(MouseEvent e)
    {
    }
    public void mouseClicked(MouseEvent e)
    {
    }
    public void mouseReleased(MouseEvent e)
    {
      /* Popup menus apparently do not honor 'isSelected()': */
      if (!((JMenuItem) e.getSource()).isEnabled())
        return;

      /* If this is a checkbox, we need to stay in sync with the original: */
      if (e.getSource() instanceof JCheckBoxMenuItem)
      {
        JCheckBoxMenuItem source = (JCheckBoxMenuItem) e.getSource();
        for (int i = 0; i < original_items.length; i++)
        {
          if (((JMenuItem) original_items[i]).getText() == source.getText())
            ((JCheckBoxMenuItem) original_items[i]).setSelected(source.isSelected());
        }
      }

      /* Shortcut here: use the ORIGINAL ActionListener: */
      original_action.actionPerformed(new ActionEvent(null, 0,
                                                      ((JMenuItem) e.getSource()).getText()));
    }

    private void checkPopup(MouseEvent e)
    {
      if (e.isPopupTrigger())
      {
        popup.show(Pop_list.this, e.getX(), e.getY());
      }
    }
  }

  public void mouseEntered(MouseEvent e)
  {
    //common.ptod("this entered: " + e.getComponent().getClass().getName());
  }
  public void mouseExited(MouseEvent e)
  {
    //common.ptod("this exited:  " + e.getComponent().getClass().getName());
  }
  public void mouseReleased(MouseEvent e)
  {
    //common.where();
  }
  public void mousePressed(MouseEvent e)
  {
    //common.where();
  }
  public void mouseClicked(MouseEvent e)
  {
    //common.where();
  }
}
