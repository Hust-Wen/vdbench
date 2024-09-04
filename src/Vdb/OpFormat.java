package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;

import Utils.Format;


/**
 * Format all directories and/or all files.
 */
class OpFormat extends FwgThread
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  private OpMkdir   mkdir  = null;
  private OpCreate  create = null;

  private static Signal signal;

  private int    format_thread_number;

  private ArrayList <File> dirs_to_clean = null;


  public OpFormat(Task_num tn, FwgEntry fwg)
  {
    super(tn, fwg);
    signal = new Signal(15);


    /* If 'format' is requested, we just fake it by creating                   */
    /* some separate threads (threads won't run).                              */
    /* These thread instances then are used to call doOperation() to do the work. */
    mkdir  = new OpMkdir  (null, fwg);
    create = new OpCreate (null, fwg);
  }


  public void storeCleanDir(File dirptr)
  {
    if (dirs_to_clean == null)
      dirs_to_clean = new ArrayList(8);
    dirs_to_clean.add(dirptr);
    //common.ptod("storeCleanDir: %2d %s", format_thread_number, dirptr.getAbsolutePath());
  }

  public void setFormatThreadNumber(int no)
  {
    format_thread_number = no;
  }


  /**
   * Format all directories and files
   */
  protected boolean doOperation() throws InterruptedException
  {
    /* If we only did deletes, exit now: */
    if (SlaveWorker.work.format_flags.format_clean)
      return false;

    // Format no longer uses FwgWaiter for all of its operations.
    // This has been done because some FSDs were just much faster than others.
    // We still have the mkdir/create synchronization.
    // Because of tis we should do the stuff below as a 'while true' loop, and
    // remove the 'format' check in FwgWaiter.
    // With this we then no longer will need (I think) the FWG suspension!
    //  TBD.

    /* Do 'mkdir' as long as needed: */
    if (fwg.anchor.mkdir_threads_running.notZero())
    {
      /* Don't create directories if we have more threads than width: */

      // We were suspending this FwgEntry (in waitFor) even BEFORE any work was done.
      // While suspended, we never allowed the suspended threads to say that they were done.
      //
      //
      //if (format_thread_number >= fwg.anchor.width)
      //  waitForAllOtherThreads(fwg, fwg.anchor.mkdir_threads_running, "mkdir");
      //
      //else
      {
        if (!mkdir.doOperation() || !fwg.anchor.moreDirsToFormat())
          waitForAllOtherThreads(fwg, fwg.anchor.mkdir_threads_running, "mkdir");
      }

      return true;
    }

    /* If we only format directories, stop now: */
    if (SlaveWorker.work.format_flags.format_dirs_requested)
      return false;


    /* Do 'create' as long as needed: */
    if (fwg.anchor.create_threads_running.notZero())
    {
      /* (This reports info for every X:) */
      reportStuff("Created", fwg.anchor.getFileCount(), fwg.anchor.getExistingFileCount());
      if (!create.doOperation() || !fwg.anchor.anyFilesToFormat())
      {
        waitForAllOtherThreads(fwg, fwg.anchor.create_threads_running, "create");
        fwg.work_done = true;

        return false;
      }
      return true;
    }

    //else
    {
      //common.ptod("fwg.anchor.getFullFileCount(): " + fwg.anchor.getFullFileCount());
      reportStuff("Formatted", fwg.anchor.getFileCount(), fwg.anchor.getFullFileCount());
    }

    return true;
  }

  /**
   * This is a little primitive, but it works somewhat.
   * Want to rewrite this some day.
   */
  private static Object duplicate_lock = new Object();
  private void reportStuff(String task, long total, long count)
  {
    /* Prevent multiple threads from giving the same message: */
    synchronized (duplicate_lock)
    {
      int one_pct = (int) total / 100;
      if (one_pct != 0 && count % one_pct == 0 && signal.go())
      {
        double pct = count * 100. / total;

        /* Prevent multiple threads reporting the same threshold: */
        if (fwg.anchor.last_format_pct <= one_pct)
        {
          SlaveJvm.sendMessageToConsole("anchor=%s: %s %,d of %,d files (%.2f%%)",
                                        fwg.anchor.getAnchorName(),
                                        task, count, total, pct);
          fwg.anchor.last_format_pct = one_pct;
        }
      }
    }
  }


  /**
   * Wait for either 'mkdir', 'create' or 'write' to complete for all threads.
   *
   * The end result is that if you have multiple anchors and therefore multiple
   * Fwg's and multiple OpFormat threads running, they all wait for each other
   * for any of the three mkdir/create/write steps, regardles of any anchor
   * having less files than others and therefore finishing earlier.
   */
  private void waitForAllOtherThreads(FwgEntry      fwg,
                                      FormatCounter counter,
                                      String        txt) throws InterruptedException
  {
    /* Suspend FwgWaiter logic for this FWG: */
    FwgWaiter.getMyQueue(fwg).suspendFwg();


    synchronized (counter)
    {
      /* Lower 'count of threads': */
      counter.counter--;
      //common.plog("Format: One thread '" + txt + "' complete for anchor=" + fwg.anchor.getAnchorName());

      /* Make sure round robin starts at the beginning of the file list: */
      //fwg.anchor.startRoundRobin();

      /* If all threads are done: */
      if (counter.counter == 0)
      {
        SlaveJvm.sendMessageToConsole("anchor=" + fwg.anchor.getAnchorName() +
                                      " " + txt + " complete.");
        //Blocked.printAndResetCounters();

        /* Sleep a bit. This allows one second interval reporting to */
        /* complete its last interval. This is for debugging only */
        //common.sleep_some(1000);

        /* Wake up everybody else: */
        counter.notifyAll();

        /* Make sure round robin starts at the beginning of the file list: */
        fwg.anchor.startRoundRobin();

        /* Tell FwgWaiter to start using this FWG again: */
        FwgWaiter.getMyQueue(fwg).restartFwg();

        return;
      }

      /* Wait until all threads are done: */
      while (counter.counter > 0)
      {
        counter.wait(100); // without the wait time it hung again???
      }

      /* When we exit here, the threads pick up the next operation, */
      /* either 'create' or 'write'. */

      //common.ptod("waitForAllOtherThreads2: %-12s %-15s %d", txt, fwg.anchor.getAnchorName(), counter.counter);
    }
    //common.plog("exit: '" + txt + "' complete for anchor=" + fwg.anchor.getAnchorName());
  }


  /**
   * 'format' cleanup: start deleting directories and files existing under the
   * first level of directories.
   * Doing it this way allows multi-threaded delete.
   *
   * Of course, if we use width=1 then there will NOT be multi-threading!
   */
  public void deletePendingLevel1Directories()
  {
    if (dirs_to_clean == null || dirs_to_clean.size() == 0)
      return;

    for (File dirptr : dirs_to_clean)
    {
      /* This check is here in case a monitor file tells us to shut down: */
      if (SlaveJvm.isWorkloadDone())
        return;

      long start = System.currentTimeMillis();
      deleteOldStuff(dirptr, fwg);

      long seconds = (System.currentTimeMillis() - start) / 1000;
      if (seconds > 30)
        common.ptod("deletePendingLevel1Directories: it took %4d seconds to clean up %s ",
                    seconds, dirptr.getName());

    }
  }


  /**
   * Recursively list the anchor directory.
   * As soon as you find a file, delete it. Directories are stored in a Vector
   * so that they can be deleted later.
   */
  private void deleteOldStuff(File dirptr, FwgEntry fwg)
  {
    String dirname    = dirptr.getAbsolutePath();
    FileAnchor anchor = fwg.anchor;

    /* Start the recusrive directory search: */
    Signal signal     = new Signal(30);

    /* Go delete files and directories: */
    readDirsAndDelete(fwg, dirptr, signal);


    /* Finally, clean up the control file: */
    // File fptr = new File(getAnchorName(), ControlFile.CONTROL_FILE);
    // if (fptr.exists())
    // {
    //   if (!fptr.delete())
    //     common.failure("Unable to delete control file: " + fptr.getAbsolutePath());
    // }
    //
    // existing_dirs = 0;
  }


  /**
   * Get a directory listing and while doing that, delete all files, followed
   * by the delete of the directory.
   */
  private void readDirsAndDelete(FwgEntry fwg,
                                 File     dirptr_in,
                                 Signal   signal)
  {
    FileAnchor anchor = fwg.anchor;

    /* Go through a list of all files and directories of this parent: */
    File[] dirptrs = dirptr_in.listFiles();

    /* This is likely caused by the use of 'shared=yes' with an other slave */
    /* deleting this directory.                                             */
    if (dirptrs == null)
    {
      common.ptod("readDirsAndDelete(): directory not found and ignored: " + dirptr_in.getAbsolutePath());
      return;
    }

    for (File dirptr : dirptrs)
    {
      /* This check is here in case a monitor file tells us to shut down: */
      if (SlaveJvm.isWorkloadDone())
        return;

      String name = dirptr.getName();
      //common.ptod("readDirsAndDelete: " + dirptr.getAbsolutePath());

      if (name.endsWith(InfoFromHost.NO_DISMOUNT_FILE))
        continue;


      /* Only return our own directory and file names. If any other files */
      /* are left in a directory the directory delete will fail anyway if */
      /* other files are left.                                            */
      if (!name.startsWith("vdb"))
        continue;

      /* Leave the control file around a little bit, but remove debug */
      /* backup copies:                                               */
      if (name.endsWith(ControlFile.CONTROL_FILE))
        continue;
      if (name.indexOf(ControlFile.CONTROL_FILE) == -1)
      {
        if (!name.endsWith(".dir") &&
            (!name.endsWith(".file") && !name.endsWith(".file.gz")))
          continue;
      }

      /* If this is a file, delete it: */
      if (name.endsWith("file"))
      {
        long begin_delete = Native.get_simple_tod();
        if (!dirptr.delete())
        {
          if (!fwg.shared)
            common.failure("Unable to delete file: " + dirptr.getAbsolutePath());
          else
            fwg.blocked.count(Blocked.FILE_DELETE_SHARED);
        }
        else
        {
          //common.ptod("deleted1: " + dirptr.getAbsolutePath());
          FwdStats.count(Operations.DELETE, begin_delete);
          fwg.blocked.count(Blocked.FILE_DELETES);
          anchor.countFileDeletes();
        }
        continue;
      }


      /* Directories get a recursive call before they are deleted: */
      readDirsAndDelete(fwg, dirptr, signal);
    }

    /* Once back here the directory should be empty and can be deleted: */
    long begin_delete = Native.get_simple_tod();
    if (!dirptr_in.delete())
    {
      if (!fwg.shared)
      {
        common.ptod("");
        common.ptod("Unable to delete directory: " + dirptr_in.getAbsolutePath());
        common.ptod("Are there any files left that were not created by Vdbench?");
        common.failure("Unable to delete directory: " + dirptr_in.getAbsolutePath() +
                       ". \n\t\tAre there any files left that were not created by Vdbench?");
      }
      else
      {
        anchor.countDirDeletes();
        fwg.blocked.count(Blocked.DIR_DELETE_SHARED);
      }
    }
    else
    {
      //common.ptod("deletedd: " + dirptr_in.getAbsolutePath());
      FwdStats.count(Operations.RMDIR, begin_delete);
      fwg.blocked.count(Blocked.DIRECTORY_DELETES);
    }
  }
}



class FormatCounter
{
  int counter;

  public FormatCounter(int count)
  {
    counter = count;
  }

  public boolean notZero()
  {
    synchronized (this)
    {
      return counter > 0;
    }
  }
}
