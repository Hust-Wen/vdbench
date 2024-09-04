package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.File;
import java.util.*;

import Utils.Format;
import Utils.OS_cmd;


/**
 * This class handles all the setup of FWD workloads.
 */
class FwgRun
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  private static Vector threads_started = null;

  private static HashMap first_time_map = new HashMap(16);


  public static Vector getThreads()
  {
    return threads_started;
  }

  public static void startFwg(Work work)
  {
    threads_started = new Vector(256);
    Vector fwgs_for_slave = work.fwgs_for_slave;
    FormatCounter mkdir_threads_running  = null;
    FormatCounter create_threads_running = null;
    FormatCounter write_threads_running  = null;

    Blocked.resetCounters();

    /* Replace the anchor name with a (possibly) unix2windows name: */
    for (int i = 0; i < fwgs_for_slave.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs_for_slave.elementAt(i);
      fwg.anchor.swapAnchorName();
    }

    /* Do a forced cleanup (-c execution parameter) the first time we get anchor: */
    for (int i = 0; i < fwgs_for_slave.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs_for_slave.elementAt(i);
      if (first_time_map.get(fwg.anchor.getAnchorName()) == null)
      {
        if (work.force_fsd_cleanup)
        {
          common.ptod("Deleting old file structure because of forced cleanupOldFiles ('-c').");
          fwg.anchor.setDeletePending(true);
        }
      }
      first_time_map.put(fwg.anchor.getAnchorName(), fwg.anchor.getAnchorName());
    }


    /* If we are going to do a format run, first delete all old junk: */
    for (int i = 0; i < fwgs_for_slave.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs_for_slave.elementAt(i);

      if (!fwg.anchor.exist())
        common.failure("Anchor directory does not exist yet: " + fwg.anchor.getAnchorName());

      /* Delete everything that's left in an anchor directory: */
      if (work.format_run && !work.format_flags.format_restart)
      {
        common.ptod("Deleting old file structure.");
        fwg.anchor.setDeletePending(true);
      }

      else if (work.format_run && work.format_flags.format_clean)
      {
        common.ptod("Deleting old file structure. (format=clean)");
        fwg.anchor.setDeletePending(true);
      }

      fwg.anchor.trackXfersizes(fwg.xfersizes);
      fwg.anchor.trackFileSizes(fwg.filesizes);
    }

    /* Send message to master telling him we're building this file structure: */
    /* It can take very long and with this we can keep the user informed.     */
    SlaveJvm.getMasterSocket().putMessage(new SocketMessage(SocketMessage.STARTING_FILE_STRUCTURE));

    /* Now initialize the anchor, which, if there was no format to be done */
    /* simply can reuse the previous content of that anchor:               */
    for (int i = 0; i < fwgs_for_slave.size(); i++)
    {
      long start = System.currentTimeMillis();
      FwgEntry fwg = (FwgEntry) fwgs_for_slave.elementAt(i);
      common.ptod("Starting initializeFileAnchor for rd=%s anchor=%s",
                  SlaveWorker.work.work_rd_name, fwg.fsd_name);
      //SlaveJvm.sendMessageToConsole("Starting initializeFileAnchor for %s", fwg.fsd_name);
      fwg.anchor.initializeFileAnchor(fwg);

      /* If there is a target anchor for copy/move, initialize that also: */
      if (fwg.target_anchor != null)
        fwg.target_anchor.initializeFileAnchor(fwg);

      common.ptod("Completed initializeFileAnchor for %s: %.2f",
                  fwg.fsd_name, (System.currentTimeMillis() - start) / 1000.);
      //SlaveJvm.sendMessageToConsole("Completed initializeFileAnchor for %s: %.2f",
      //                              fwg.fsd_name, (System.currentTimeMillis() - start) / 1000.);
    }

    /* Send message to master telling him we're done building file structure: */
    SlaveJvm.getMasterSocket().putMessage(new SocketMessage(SocketMessage.ENDING_FILE_STRUCTURE));

    /* Activate all Kstat stuff we need: */
    FsdEntry.markKstatActive();

    /* Set the proper skew for each FWG: */
    calcSkew(fwgs_for_slave);

    /* Make sure run does not prematurily terminate because of sequential eof: */
    //WG_entry.sequentials_count(rd.wgs_for_rd);

    /* When rate is negative, use x% of previous: */
    // this must go to the master!  Not sure if it is even used.
    if (work.fwd_rate < 0)
      work.fwd_rate = Vdbmain.observed_iorate * -1 * work.fwd_rate / 100;

    if (work.fwd_rate <= 0)
    {
      common.ptod("");
      common.ptod("Filesystem Workload (fwd) requested");
      common.ptod("but no 'fwdrate' parameter is used");
      common.failure("missing 'fwdrate' parameter");
    }

    /* Format must keep track of the status of ALL it's threads to make */
    /* sure that we can switch between mkdir, create, and write together: */
    if (work.format_run)
      setupFormatCounters(fwgs_for_slave);

    /* Setup the FwgWaiter: */
    FwgWaiter waiter = new FwgWaiter(new Task_num("FwgWaiter"), fwgs_for_slave,
                                     work.fwd_rate, work.distribution);
    waiter.start();


    /* Create a new thread for each FWG: */
    int starts = 0;
    ArrayList <File> cleanup_list = null;
    for (int i = 0; i < fwgs_for_slave.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs_for_slave.elementAt(i);
      fwg.setShutdown(false);

      /* For cleanup we need to tell each thread which level1 directory to delete: */
      if (work.format_run)
        cleanup_list = getDirCleanupList(fwg.anchor);


      /* Start multiple threads: */
      ArrayList <OpFormat> started_formats = new ArrayList(8);
      for (int j = 0; j < fwg.threads; j++)
      {
        FwgThread fwgthread  = null;
        int operation = fwg.getOperation();
        String name = (work.format_run) ? "OpFormat" :
                      Operations.getOperationText(operation);
        Task_num task = new Task_num("FwgThread " + name +
                                     " " + fwg.anchor.getAnchorName());

        if (work.format_run)
        {
          fwgthread = new OpFormat(task, fwg);
          started_formats.add((OpFormat) fwgthread);
          ((OpFormat) fwgthread).setFormatThreadNumber(j);
        }

        /* This one must be before read+write: */
        else if (fwg.readpct >= 0)                fwgthread = new OpReadWrite(task, fwg);
        else if (operation == Operations.MKDIR)   fwgthread = new OpMkdir(task,     fwg);
        else if (operation == Operations.CREATE)  fwgthread = new OpCreate(task,    fwg);
        else if (operation == Operations.READ)    fwgthread = new OpRead(task,      fwg);
        else if (operation == Operations.WRITE)   fwgthread = new OpWrite(task,     fwg);
        else if (operation == Operations.GETATTR) fwgthread = new OpGetAttr(task,   fwg);
        else if (operation == Operations.SETATTR) fwgthread = new OpSetAttr(task,   fwg);
        else if (operation == Operations.ACCESS)  fwgthread = new OpAccess(task,    fwg);
        else if (operation == Operations.OPEN)    fwgthread = new OpOpen(task,      fwg);
        else if (operation == Operations.CLOSE)   fwgthread = new OpClose(task,     fwg);
        else if (operation == Operations.DELETE)  fwgthread = new OpDelete(task,    fwg);
        else if (operation == Operations.RMDIR)   fwgthread = new OpRmdir(task,     fwg);
        else if (operation == Operations.COPY)    fwgthread = new OpCopy(task,      fwg);
        else if (operation == Operations.MOVE)    fwgthread = new OpMove(task,      fwg);
        else if (operation == Operations.PUT)     fwgthread = new OpPutCloud(task,  fwg);
        else if (operation == Operations.GET)     fwgthread = new OpGetCloud(task,  fwg);

        else
          common.failure("Operation not supported (yet?): " + operation);

        starts++;
        fwgthread.start();
        threads_started.add(fwgthread);
      }

      if (!work.format_run)
      {
        common.ptod("Started " + fwg.threads + " threads for " +
                    "fwd=" + fwg.getName() + ",fsd=" + fwg.fsd_name +
                    ",operation=" +
                    Operations.getOperationText(fwg.getOperation()));
      }
      else
      {
        common.ptod("Started " + fwg.threads + " threads for " +
                    "fwd=" + fwg.getName() + ",fsd=" + fwg.fsd_name);
      }

      /* Format runs are told which level1 directory to delete: */
      if (work.format_run)
        giveDirnamesToClean(started_formats, cleanup_list);
    }

    common.plog("Started " + starts + " FwgThreads for rd=" + work.work_rd_name);
  }


  /**
   * Calculate skew and/or spread unrequested skew around if no skew defined
   */
  public static void calcSkew(Vector fwgs_for_rd)
  {
    double tot_skew = 0;
    double remainder;
    int    no_skews = 0;

    /* Go through all FWD entries: */
    for (int i = 0; i < fwgs_for_rd.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs_for_rd.elementAt(i);

      /* For those who have skew, determine the total skew: */
      if (fwg.skew != 0)
        tot_skew += fwg.skew;
      else
        no_skews++;
    }

    /* If any FWGs did not specify skew, spread the remainder around: */
    if (no_skews != 0)
    {
      remainder = (100.0 - tot_skew) / no_skews;
      for (int i = 0; i < fwgs_for_rd.size(); i++)
      {
        FwgEntry fwg = (FwgEntry) fwgs_for_rd.elementAt(i);

        if (fwg.skew == 0)
        {
          fwg.skew = remainder;
          tot_skew += fwg.skew;
        }
      }
    }


    for (int i = 999990; i < fwgs_for_rd.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs_for_rd.elementAt(i);
      common.plog("Skew for fwd=" + fwg.getName() +
                  ",fsd=" + fwg.fsd_name +
                  Format.f(",operation=%-8s ", Operations.getOperationText(fwg.getOperation()) +
                           ":") + fwg.skew);
    }

    /* Skew must be close to 100% (floating point allows just a little off): */
    if (fwgs_for_rd.size() != 0)
    {
      if ((tot_skew < 99.9999) || (tot_skew > 100.0001))
        common.failure("Total skew must add up to 100: " + tot_skew);
    }

  }


  /**
   * Create one FormatCounter for each anchor that is included.
   */
  private static void setupFormatCounters(Vector fwgs)
  {
    /* It is understood at this point that there is only ONE fwg for each anchor */
    /* Check that first: */
    HashMap anchor_map = new HashMap(32);
    for (int i = 0; i < fwgs.size(); i++)
    {
      FwgEntry fwg   = (FwgEntry) fwgs.elementAt(i);
      anchor_map.put(fwg.anchor, new Integer(fwg.threads));
    }
    if (fwgs.size() != anchor_map.size())
      common.ptod("FwgEntry count does not match FileAnchor count: " +
                  fwgs.size() + "/" + anchor_map.size());

    /* Format must keep track of the status of ALL it's threads to make */
    /* sure that we can switch between mkdir, create, and write together: */
    FileAnchor[] anchors = (FileAnchor[]) anchor_map.keySet().toArray(new FileAnchor[0]);
    Integer[]    threads = (Integer[])    anchor_map.values().toArray(new Integer[0]);
    for (int i = 0; i < anchors.length; i++)
    {
      anchors[i].mkdir_threads_running  = new FormatCounter(threads[i].intValue());
      anchors[i].create_threads_running = new FormatCounter(threads[i].intValue());
    }
  }


  /**
   * Create a list of all level1 directories.
   * This list will be passed on to separate OpFormat threads for cleanup.
   */
  private static ArrayList <File> getDirCleanupList(FileAnchor anchor)
  {
    ArrayList <File> dirs = new ArrayList(8);
    for (File dirptr : new File(anchor.getAnchorName()).listFiles())
    {
      if (dirptr.getName().startsWith("vdb") &&  dirptr.isDirectory())
        dirs.add(dirptr);
    }

    return dirs;
  }


  /**
   * Round-robin all level1 directories to all OpFormat threads
   */
  private static void giveDirnamesToClean(ArrayList <OpFormat> started_formats,
                                          ArrayList <File>     dir_list)
  {
    for (int dir = 0; dir < dir_list.size(); dir++)
    {
      int      round_robin = dir % started_formats.size();
      OpFormat fmt_thread  = started_formats.get(round_robin);
      fmt_thread.storeCleanDir(dir_list.get(dir));
    }
  }


  // debugging
  public static void endOfRun(Work work)
  {
    for (FwgEntry fwg : work.fwgs_for_slave)
    {
      fwg.anchor.endOfRun();
    }

  }
}
