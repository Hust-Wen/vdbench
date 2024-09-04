package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.util.*;

import Utils.Format;
import Utils.Fput;


/**
 * This class handles File system reports
 */
public class FwdReport extends Report
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  private static int total_header_lines = 1;

  /**
   * Report FWD statistics for this interval for all hosts and slaves.
   */
  public static void reportFwdInterval()
  {
    /* Report on Host reports: */
    for (int i = 0; i < Host.getDefinedHosts().size(); i++)
    {
      Host host = (Host) Host.getDefinedHosts().elementAt(i);
      FwdStats host_fwd = new FwdStats();

      Kstat_cpu kstat_cpu = host.getSummaryReport().getData().getIntervalCpuStats();

      /* Report on Slave reports: */
      for (int j = 0; j < host.getSlaves().size(); j++)
      {
        Slave slave = (Slave) host.getSlaves().elementAt(j);
        if (slave.getCurrentWork() == null)
          continue;

        Report.getReport(slave).getData().reportInterval(kstat_cpu);

        /* Create all slave FSD reports: */
        if (slave_detail)
        {
          for (String fsdname : slave.getFsdsUsed())
            slave.getReport(fsdname).getData().reportInterval(kstat_cpu);
        }
      }

      Report.getReport(host).getData().reportInterval(kstat_cpu);
    }

    Kstat_cpu kc_total = Report.getSummaryReport().getData().getIntervalCpuStats();
    FwdStats total_fwd = Report.getSummaryReport().getData().getIntervalFwdStats();

    total_fwd.printLine(Report.getSummaryReport(), kc_total);
    if (!Vdbmain.kstat_console)
      total_fwd.printLine(Report.getStdoutReport(), kc_total);

    /* Report all FSDs: */
    for (int i = 0; i < FsdEntry.getFsdList().size(); i++)
    {
      FsdEntry fsd  = (FsdEntry) FsdEntry.getFsdList().elementAt(i);
      Report.getReport(fsd.name).getData().reportInterval(kc_total);
    }

    /* Report all Fwds: */
    for (int i = 0; i < FwdEntry.getFwdList().size(); i++)
    {
      FwdEntry fwd  = (FwdEntry) FwdEntry.getFwdList().elementAt(i);
      Report.getReport(fwd.fwd_name).getData().reportInterval(kc_total);

      /* There is a bug: when running multiple formats there are multiple    */
      /* format FWDs and the code is writing a line for each possible format */
      /* on the same file. Just end it after the first.                      */
      if (fwd.fwd_name.equals("format"))
        break;
    }

    if (isKstatReporting())
      Report.reportKstat();

    if (common.get_debug(common.PRINT_FS_COUNTERS))
      Blocked.printCountersToLog();

    if (CpuStats.isCpuReporting())
      writeFlatCpu(kc_total);
    total_fwd.writeFlat("" + Report.getInterval(), kc_total);
    Flat.printInterval();

    total_fwd.writeBinFile();
  }


  /**
   * Report run-level totals.
   */
  public static FwdStats reportRunTotals()
  {
    String avg = Report.getAvgLabel();

    /* Look at all hosts: */
    for (int i = 0; i < Host.getDefinedHosts().size(); i++)
    {
      Host host           = (Host) Host.getDefinedHosts().elementAt(i);
      FwdStats fwd_host   = new FwdStats();
      Kstat_cpu kstat_cpu = host.getSummaryReport().getData().getTotalCpuStats();

      /* Look at all slaves: */
      for (int j = 0; j < host.getSlaves().size(); j++)
      {
        Slave slave  = (Slave) host.getSlaves().elementAt(j);
        if (slave.getCurrentWork() == null)
          continue;

        Report.getReport(slave).getData().reportFwdTotal(kstat_cpu, avg);

        /* Create all slave FSD reports: */
        if (slave_detail)
        {
          for (String fsdname : slave.getFsdsUsed())
            slave.getReport(fsdname).getData().reportFwdTotal(kstat_cpu, avg);
        }
      }

      Report.getReport(host).getData().reportFwdTotal(kstat_cpu, avg);

      if (NfsStats.areNfsReportsNeeded())
        host.PrintNfsstatTotals(avg);
    }

    Kstat_cpu kstat_cpu = Report.getSummaryReport().getData().getTotalCpuStats();
    FwdStats fwd_total  = Report.getSummaryReport().getData().getTotalFwdStats();

    /* Now report these numbers: */
    fwd_total.printLineL(Report.getSummaryReport(), kstat_cpu, avg);
    fwd_total.printStdLine(Report.getSummaryReport(), kstat_cpu, avg);
    fwd_total.printMaxLine(Report.getSummaryReport(), kstat_cpu, avg);
    if (!Vdbmain.kstat_console)
    {
      fwd_total.printLineL(Report.getStdoutReport(), kstat_cpu, avg);
      fwd_total.printStdLine(Report.getStdoutReport(), kstat_cpu,avg);
      fwd_total.printMaxLine(Report.getStdoutReport(), kstat_cpu,avg);
    }

    /* Write the run total also in the totals file: */
    if (total_header_lines++ % 10 == 1)
      fwd_total.printHeaders(Report.getTotalReport());
    fwd_total.printLineL(Report.getTotalReport(), kstat_cpu, avg);

    fwd_total.writeFlat(avg, kstat_cpu);
    if (CpuStats.isCpuReporting())
      Report.writeFlatCpu(kstat_cpu);

    /* Report FSD and FWD statistics: */
    ReportData.reportFwdTotals(FsdEntry.getFsdNames(), kstat_cpu, avg);
    ReportData.reportFwdTotals(FwdEntry.getFwdNames(), kstat_cpu, avg);

    /* Report summary histogram: */
    Report report = Report.getReport("histogram");
    String title = "Total of all requested operations since warmup: ";
    report.println(fwd_total.getReqstdHistogram().printHistogram(title));

    for (int i = 0; i < FsdEntry.getFsdList().size(); i++)
    {
      FsdEntry fsd = (FsdEntry) FsdEntry.getFsdList().elementAt(i);

      /* Report summary histogram: */
      report        = Report.getReport(fsd, "histogram");
      ReportData rs = report.getData();
      report.println(rs.getTotalFwdStats().getReqstdHistogram().printHistogram(title));
    }

    for (int i = 0; i < FwdEntry.getFwdList().size(); i++)
    {
      FwdEntry fwd = (FwdEntry) FwdEntry.getFwdList().elementAt(i);

      /* Report summary histogram: */
      report        = Report.getReport(fwd, "histogram");
      ReportData rs = report.getData();
      report.println(rs.getTotalFwdStats().getReqstdHistogram().printHistogram(title));

      /* There is a bug: when running multiple formats there are multiple    */
      /* format FWDs and the code is writing a line for each possible format */
      /* on the same file. Just end it after the first.                      */
      if (fwd.fwd_name.equals("format"))
        break;
    }

    return fwd_total;
  }


  /**
   * Create Slave SD reports for a specific host
   */
  public static void createSlaveFsdReports(Host host)
  {
    /* Create all slave SD reports: */
    for (Slave slave : host.getSlaves())
    {
      for (String fsdname : slave.getFsdsUsed())
      {
        Report report = new Report(slave, fsdname,
                                   "Slave FSD report for fsd=" + fsdname, slave_detail);
        slave.addReport(fsdname, report);

        if (slave_detail)
        {
          String txt = "fsd=" + fsdname;
          slave.getSummaryReport().printHtmlLink("Links to slave FSD reports",
                                                 report.getFileName(), txt);
        }
      }
    }
  }


  /**
   * Create SD reports for all hosts.
   * (Only done when there is more than one host. Avoids duplication of reports)
   */
  public static void createHostFsdReports()
  {
    for (Host host : Host.getDefinedHosts())
    {
      String[] fsds = getFsdsUsedForHost(host);
      Arrays.sort(fsds, new SdSort());

      /* Create all host SD reports: */
      //if (host_detail)
      {
        for (String fsdname : fsds)
        {
          String txt = "Host FSD report for fsd=" + fsdname + ",host=" + host.getLabel();
          Report report = new Report(host, fsdname, txt, host_detail);
          host.addReport(fsdname, report);

          txt = "fsd=" + fsdname;
          host.getSummaryReport().printHtmlLink("Link to host FSD reports",
                                                report.getFileName(), txt);
        }
      }

      createSlaveFsdReports(host);
    }
  }



  /**
   * Return Fsds used for a host
   */
  public static String[] getFsdsUsedForHost(Host host)
  {
    HashMap <String, String> map = new HashMap(16);

    for (RD_entry rd : Vdbmain.rd_list)
    {
      for (FwgEntry fwg : rd.fwgs_for_rd)
      {
        if (fwg.host_name.equals(host.getLabel()))
          map.put(fwg.fsd_name, fwg.fsd_name);
      }
    }

    return map.keySet().toArray(new String[0]);
  }


  /**
   * This is a shortcut: I need to do this better, looking only for the SLAVE,
   * not the host.
   * For now this will have to do.
   */
  public static String[] getFsdsUsedForSlave(Slave slave)
  {
    HashMap <String, String> map = new HashMap(16);

    for (RD_entry rd : Vdbmain.rd_list)
    {
      for (FwgEntry fwg : rd.fwgs_for_rd)
      {
        if (fwg.host_name.equals(slave.getHost().getLabel()))
          map.put(fwg.fsd_name, fwg.fsd_name);
      }
    }

    return map.keySet().toArray(new String[0]);
  }
}


