package Vdb;

/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

class OpRmdir extends FwgThread
{
  private final static String c =
  "Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.";


  private boolean rmdir_max = (SlaveWorker.work.fwd_rate == RD_entry.MAX_RATE);

  public OpRmdir(Task_num tn, FwgEntry fwg)
  {
    super(tn, fwg);
  }

  /**
   * Create a directory.
   * Search for a directory who is missing a parent, and then
   * create the highest level missing parent.
   */
  protected boolean doOperation()
  {
    Directory dir = null;

    while (true)
    {
      if (SlaveJvm.isWorkloadDone())
        return false;

      // rmdir will NEVER be done during a format!!!!!! -:)
      dir = fwg.anchor.getDir(fwg.select_random, format || rmdir_max);

      if (dir == null)
        return false;

      if (!dir.setBusy(true))
      {
        //common.ptod("dir1: " + dir.getFullName());
        block(Blocked.DIR_BUSY_RMDIR);
        continue;
      }

      if (!dir.exist())
      {
        dir.setBusy(false);

        if (!canWeGetMoreDirectories(msg2))
          return false;

        //common.ptod("dir2: " + dir.getFullName());
        block(Blocked.DIR_DOES_NOT_EXIST, dir.getFullName());
        continue;
      }

      /* No locking required, since this (the parent) is still busy: */
      if (rmdir_max)
        deleteChildren(dir.getChildren());

      if (dir.anyExistingChildren())
      {
        dir.setBusy(false);
        block(Blocked.DIR_STILL_HAS_CHILD, dir.getFullName());
        continue;
      }

      /* Can't delete directory if it still has some files: */
      if (dir.countFiles(0, null) != 0)
      {
        dir.setBusy(false);
        block(Blocked.DIR_STILL_HAS_FILES, dir.getFullName());

        if (!canWeGetMoreFiles(msg))
        {
          common.where();
          return false;
        }

        if (!canWeExpectFileDeletes(msg))
        {
          common.where();
          return false;
        }

        continue;
      }

      break;
    }


    //  /* No locking required, since this (the parent) is still busy: */
    //  if (format || rmdir_max)
    //    deleteChildren(dir.getChildren());

    /* Now do the work: */
    dir.deleteDir(fwg);
    dir.setBusy(false);

    return true;
  }


  private void deleteChildren(Directory[] children)
  {
    if (children == null)
      return;

    for (Directory dir : children)
    {

      /* Lock this new child. The loop is in case an other thread is trying      */
      /* to delete this directory also, but he'll fail since the parent is busy: */
      while (!dir.setBusy(true));

      if (!dir.exist())
      {
        dir.setBusy(false);
        continue;
      }

      deleteChildren(dir.getChildren());

      if (dir.countFiles(0, null) != 0)
      {
        block(Blocked.DIR_STILL_HAS_FILES, dir.getFullName());
        dir.setBusy(false);
        continue;
      }

      dir.deleteDir(fwg);

      /* Free up this child now: */
      dir.setBusy(false);
    }
  }


  private String[] msg =
  {
    "Anchor: " + fwg.anchor.getAnchorName(),
    "Vdbench is trying to delete a directory, but the directory that we are",
    "trying to delete is not empty, and there are no threads currently",
    "active that can delete those files."
  };

  private String[] msg2 =
  {
    "Anchor: " + fwg.anchor.getAnchorName(),
    "Vdbench is trying to delete a directory, but the directory that we are",
    "trying to delete does not exist, and there are no threads currently",
    "active that create new directories."
  };

}
