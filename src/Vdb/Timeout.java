package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import Utils.*;


/**
 * Timeout mechanism.
 * Check each slave every interval looking for consecutive intervals where ZERO
 * operations were reported by the slave for each SD or FSD.
 *
 * Call a user specified script every 'timeout' seconds, and when that call
 * responds with "xxxxx tbd" abort Vdbench.
 */
public class Timeout
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";


  private static long    timeout_secs        = 0;
  private static long    timeout_msecs       = 0;

  private static long    timeout_max         = 0;
  private static String  timeout_script      = null;
  private static long    report_secs         = 0;
  private static long    last_notification   = 0;
  private static OS_cmd  ocmd                = null;
  private static Object  timeout_lock        = new Object();

  private static ArrayList <String> messages = new ArrayList(16);

  private static SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss-zzz");

  public static void reportLazyBums()
  {
    /* If we don't look for timeouts and/or don't report, don't bother: */
    if (timeout_secs == 0 && report_secs == 0)
      return;

    if (Vdbmain.isWdWorkload())
    {
      for (SD_entry sd : Vdbmain.sd_list)
      {
        for (Slave slave : SlaveList.getSlaveList())
        {
          if (!slave.isUsingSd(sd))
            continue;

          if (sd.concatenated_sd)
            continue;

          ReportData rdata = slave.getReport(sd.sd_name).getData();

          if (rdata.longest_idle / 1000 > report_secs)
            common.plog("Longest idle period for sd=%s from slave=%-12s %3d seconds "+
                        "between %s and %s",
                        sd.sd_name, slave.getLabel(),
                        rdata.longest_idle / 1000,
                        df.format(new Date(rdata.longest_end.getTime() - rdata.longest_idle)),
                        df.format(rdata.longest_end));

          /* No matter what: clear fields for the next run: */
          rdata.longest_idle   = 0;
          rdata.last_real_work = 0;
        }
      }
    }

    else
    {
      for (Host host : Host.getDefinedHosts())
      {
        for (Slave slave : host.getSlaves())
        {
          for (String fsdname : slave.getFsdsUsed())
          {
            ReportData rdata = slave.getReport(fsdname).getData();

            if (rdata.longest_idle / 1000 > report_secs)
              common.plog("Longest idle period for fsd=%s from slave=%-12s %3d seconds "+
                          "between %s and %s",
                          fsdname, slave.getLabel(),
                          rdata.longest_idle / 1000,
                          df.format(new Date(rdata.longest_end.getTime() - rdata.longest_idle)),
                          df.format(rdata.longest_end));

            /* No matter what: clear fields for the next run: */
            rdata.longest_idle   = 0;
            rdata.last_real_work = 0;
          }
        }
      }
    }
  }


  /**
   * Scan through all slaves and all SDs/Fsds, looking for Slave+SD/Fsd
   * combination from which we have not seen any i/o completions. Method is NOT
   * called during warmup.
   *
   *
   * Problem: this all looks good for slave+sd, but more work is needed if we
   * want to report host+sd or total+sd.
   */
  public static void lookForTimeouts()
  {
    /* If we don't look for timeouts and/or don't report, don't bother: */
    if (timeout_secs == 0 && report_secs == 0)
      return;

    /* If we have a pending and now completed script call, get results: */
    synchronized (timeout_lock)
    {
      if (ocmd != null && !ocmd.stillRunning())
      {
        // TBD
        //for (String stderr : ocmd.getStderr())
        //  common.ptod("stderr: " + stderr);
        //for (String stdout : ocmd.getStdout())
        //  common.ptod("stdout: " + stdout);

        String[] stdout = ocmd.getStdout();
        if (stdout.length > 0 && stdout[0].trim().startsWith("abort"))
        {
          reportLazyBums();
          ErrorLog.plog("Timeout: abort requested by 'timeout=(%d,%s)'",
                        timeout_msecs / 1000, timeout_script);;
          common.failure("Timeout: abort requested by 'timeout=(%d,%s)'",
                         timeout_msecs / 1000, timeout_script);;
        }

        ocmd.removeKillAtEnd();
        ocmd = null;
      }
    }


    /* This will contain the max late value for this interval only: */
    long max_late_ms = 0;
    messages.clear();



    /* Technically, the warmup period is excluded from run totals.  */
    /* Timeouts therefore also should be ignored during the warmup? */
    /* Indeed, this is no longer called during warmup.              */

    /* Check all SDs: */
    long max_time = 0;
    for (SD_entry sd : Vdbmain.sd_list)
    {
      for (Slave slave : SlaveList.getSlaveList())
      {
        if (!slave.isUsingSd(sd))
          continue;

        /* Problem: seek=eof and format=yes. We need a signal from the slave */
        /* telling us the work for this sd/fsd is done.....                  */
        if (sd.concatenated_sd || sd.work_done)
          continue;

        ReportData rdata = slave.getReport(sd.sd_name).getData();

        /* Interesting question: if there never was any work done                */
        /* it could be that we got stuck immediately, and that is an error also! */
        /* This therefore means that we need to initialize the field             */
        /* with the START of the run, and not even wait for end of warmup!       */
        /* However, since we don't really want to record info during warmup we   */
        /* have to accept that problem. 'idle' time therefore is only idle time  */
        /* AFTER warmup, meaning that REAL idle time can be longer.              */
        if (rdata.last_real_work == 0)
          rdata.last_real_work = Reporter.run_start_time.getTime();

        long  no_work_for_ms = System.currentTimeMillis() - rdata.last_real_work;
        max_late_ms          = Math.max(max_late_ms, no_work_for_ms);
        if (no_work_for_ms > timeout_msecs)
        {
          if (no_work_for_ms > rdata.longest_idle)
          {
            rdata.longest_idle = no_work_for_ms;
            rdata.longest_end = new Date();
          }

          if (timeout_secs > 0)
            messages.add(String.format("timeout=%d value exceeded for sd=%s on slave=%s for %3d seconds.",
                                       timeout_msecs / 1000,
                                       sd.sd_name, slave.getLabel(), no_work_for_ms / 1000));
          max_time = Math.max(no_work_for_ms, max_time);
        }
      }
    }


    /* Check all FSDs: */
    for (Host host : Host.getDefinedHosts())
    {
      for (Slave slave : host.getSlaves())
      {
        for (String fsdname : slave.getFsdsUsed())
        {
          /* If work for this FSD is complete, don't look for timeouts: */
          FsdEntry fsd = FsdEntry.findFsd(fsdname);
          if (!fsd.in_use || fsd.work_done)
            continue;

          ReportData rdata = slave.getReport(fsdname).getData();

          /* Interesting question: if there never was any work done                */
          /* it could be that we got stuck immediately, and that is an error also! */
          /* This therefore means that we need to initialize the field             */
          /* with the START of the run, and not even wait for end of warmup!       */
          if (rdata.last_real_work == 0)
            rdata.last_real_work = Reporter.run_start_time.getTime();

          long  no_work_for = System.currentTimeMillis() - rdata.last_real_work;
          max_late_ms = Math.max(max_late_ms, no_work_for);
          if (no_work_for > timeout_msecs)
          {
            if (no_work_for > rdata.longest_idle)
            {
              rdata.longest_idle = no_work_for;
              rdata.longest_end = new Date();
            }

            // idea: just report '260 seconds between x and y.  !!!!
            if (timeout_secs > 0)
              messages.add(String.format("timeout=%d value exceeded for fsd=%s on slave=%s for %3d seconds.",
                                         timeout_msecs / 1000,
                                         fsdname, slave.getLabel(), no_work_for / 1000));
            max_time = Math.max(no_work_for, max_time);
          }
        }
      }
    }

    /* Without timeout parameter we still do our 'max idle' stuff, but don't time out: */
    if (timeout_secs == 0)
      return;


    /* If we found anything we did not like, record it: */
    if (messages.size() > 0)
    {
      Status.printOne(String.format("timeout=%d value exceeded. See errorlog.html",
                                    timeout_msecs / 1000));

      if (timeout_script != null && timeout_script.equals("abort"))
      {
        for (String msg : messages)
          ErrorLog.plog(msg);
        ErrorLog.plog("'timeout=(%d,abort)' requested", timeout_msecs / 1000);
        common.failure("'timeout=(%d,abort)' requested", timeout_msecs / 1000);
      }

      /* Don't do anything for 'timeout' seconds, of course the first one is free: */
      if (System.currentTimeMillis() > last_notification + timeout_msecs)
      {
        last_notification = System.currentTimeMillis();

        for (String msg : messages)
          ErrorLog.plog(msg);

        /* if the user wants to be notified, tell him: */
        if (timeout_script != null)
          callUserScript(max_late_ms);

        common.ptod("timeout=%d value exceeded. See errorlog.html",
                    timeout_msecs / 1000);
      }
    }

    /* See if 'max' was reached: */
    if (timeout_max > 0 && max_time > timeout_max)
    {
      ErrorLog.plog("Maximum timeout=(%d,%d) has been reached. ",
                    timeout_msecs / 1000, timeout_max / 1000);
      common.failure("Maximum timeout=(%d,%d) has been reached. ",
                     timeout_msecs / 1000, timeout_max / 1000);
    }
  }


  /**
   * We found slow SDs or FSDs. Don't report more than once every 'timeout'
   * seconds.
   */
  private static void callUserScript(long max_late_ms)
  {
    /* there is still a notify pending: */
    if (ocmd != null)
      return;

    /* If we have not heard from the last async notification, or, if we */
    /* heard back but have not picked up the data, wait until next time: */
    synchronized (timeout_lock)
    {
      /* Notify: */
      ocmd = new OS_cmd();
      ocmd.addText(common.replace(timeout_script, "$output", Vdbmain.output_dir));
      ocmd.addText(String.format("maxlate %d", max_late_ms / 1000));
      ocmd.addText("&");

      /* Start the command, but don't look for the response until the next interval: */
      ocmd.execute(false);
    }
  }


  /**
   * This method is called when a slave times out during termination.
   */
  public static void callScriptForShutdown()
  {
    if (timeout_script == null)
      return;

    ocmd = new OS_cmd();
    ocmd.addText(common.replace(timeout_script, "$output", Vdbmain.output_dir));

    /* Start the command and wait for its completion.                       */
    /* If the script never returns then we can find the command in logfile: */
    ocmd.execute(false);

    common.plog("callScriptForShutdown: " + ocmd.getCmd());

    for (String line : ocmd.getStderr())
      common.plog("callScriptForShutdown stderr: " + line);
    for (String line : ocmd.getStdout())
      common.plog("callScriptForShutdown: " + line);
  }


  /**
   * timeout=report
   * timeout=(report,5)
   * timeout=60
   * timeout=(60,abort)
   * timeout=(60,600)
   * timeout=(60,script)
   */
  public static void scanTimeoutParameter(ArrayList <String> raw_values)
  {
    /* Handle timeout=report and timeout=(report,5): */
    if (raw_values.get(0).equals("report"))
    {
      report_secs = 1;
      if (raw_values.size() == 2)
        report_secs = Integer.parseInt(raw_values.get(1));
      else if (raw_values.size() != 1)
        common.failure("timeout=(report,nn) has more values than expected: %d",
                       raw_values.size());
      return;
    }

    /* The FIRST one is always the initial timeout value: */
    String raw = raw_values.get(0);
    if (!common.isNumeric(raw))
      common.failure("The first timeout= parameter must be numeric");
    timeout_secs  = Integer.parseInt(raw);
    timeout_msecs = timeout_secs * 1000;

    /* If this is all, done: */
    if (raw_values.size() == 1)
      return;

    if (raw_values.size() > 2)
      common.failure("The timeout= parameter may contain only two values");


    /* The second parameter either is 'abort', 'script.sh' or numeric: */
    raw = raw_values.get(1);
    if (common.isNumeric(raw))
      timeout_max = Integer.parseInt(raw) * 1000;
    else
      timeout_script = raw;
  }
}


