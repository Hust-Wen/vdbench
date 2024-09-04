package Vdb;

/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.util.*;

import Utils.Format;
import Utils.Fput;
import Utils.printf;
import Utils.OS_cmd;

/**
 */
public class WdReport extends Report
{
  private final static String c =
  "Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.";

  /**
   *  Create WD reports showing the the run numbers.
   */
  public static void createRunWdReports()
  {
    if (Vdbmain.wd_list.size() < 2)
      return;

    /* Count how many reports we need: */
    int reports = 0;
    for (WD_entry wd : Vdbmain.wd_list)
    {
      if (wd.wd_is_used)
        reports++;
    }

    if (reports == 0)
      return;

    /* Create all WD reports: */
    Report.getSummaryReport().println("");
    for (WD_entry wd : Vdbmain.wd_list)
    {
      if (!wd.wd_is_used)
        continue;

      String wdname = wd.wd_name;
      Report report = new Report(wdname, "Workload Report for wd=" + wdname);

      Report.getSummaryReport().printHtmlLink("Link to workload report",
                                       report.getFileName(), wdname);

      Report hist = new Report(wdname, "histogram", "wd=" + wdname + " response time histogram.");
      report.printHtmlLink("Link to response time histogram",
                           hist.getFileName(), "histogram");
    }

  }


  /**
   * Report SD statistics on:
   * - Slave SD report
   * - Slave SD summary report
   * - Host SD report
   * - Host summary report
   * - SD report
   * - Summary report
   */
  public static void reportWdStats()
  {
    if (Vdbmain.wd_list.size() < 2)
      return;

    /* Get total cpu info: */
    Kstat_cpu kc_total = Report.getSummaryReport().getData().getIntervalCpuStats();

    /* Report on the WD report: */
    for (WD_entry wd : Vdbmain.wd_list)
    {
      if (!wd.wd_is_used || !wd.isWdUsedThisRd())
        continue;

      String wdname = wd.wd_name;
      SdStats stats = Report.getReport(wdname).getData().getIntervalSdStats();
      getReport(wdname).reportDetail(stats, kc_total);
    }
  }



  /**
   * Report WD total statistics
   */
  public static void reportWdTotalStats()
  {
    if (Vdbmain.wd_list.size() < 2)
      return;

    String avg   = Report.getAvgLabel();

    /* Get cpu totals: */
    Kstat_cpu kc_total = Report.getSummaryReport().getData().getTotalCpuStats();

    /* Report WD totals: */
    for (WD_entry wd : Vdbmain.wd_list)
    {
      if (!wd.wd_is_used || !wd.isWdUsedThisRd())
        continue;

      SdStats stats = getReport(wd.wd_name).getData().getTotalSdStats();
      getReport(wd.wd_name).reportDetail(stats, kc_total, avg);
      getReport(wd.wd_name, "histogram").println(stats.printHistograms());
    }
  }
}
