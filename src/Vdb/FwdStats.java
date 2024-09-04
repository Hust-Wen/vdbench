package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

//import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import Utils.*;

/**
 * This class handles statistics for all Filesystem Workloads.
 *
 * On a slave, all workloads and all anchors are accumulated in one set
 * of counters for each FwgThread.
 */
public class FwdStats implements java.io.Serializable
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  public FwdCounter read    = new FwdCounter(this, "read   " );
  public FwdCounter write   = new FwdCounter(this, "write  " );
  public FwdCounter mkdir   = new FwdCounter(this, "mkdir  " );
  public FwdCounter create  = new FwdCounter(this, "create " );
  public FwdCounter getattr = new FwdCounter(this, "getattr" );
  public FwdCounter setattr = new FwdCounter(this, "setattr" );
  public FwdCounter access  = new FwdCounter(this, "access " );
  public FwdCounter open    = new FwdCounter(this, "open   " );
  public FwdCounter close   = new FwdCounter(this, "close  " );
  public FwdCounter copy    = new FwdCounter(this, "copy   " );
  public FwdCounter move    = new FwdCounter(this, "move   " );
  public FwdCounter delete  = new FwdCounter(this, "delete " );
  public FwdCounter rmdir   = new FwdCounter(this, "rmdir  " );

  // get/put could possible all removed, but I have not figured
  // out how to include get/put counts in 'requested ops'.
  // Hold off for now removing.
  public FwdCounter put     = new FwdCounter(this, "put    " );
  public FwdCounter get     = new FwdCounter(this, "get    " );

  public long       r_bytes = 0;
  public long       w_bytes = 0;

  public long       permit_time = 0;
  public long       permit_count = 0;

  private long elapsed;

  public boolean work_done;


  private static Bin fwd_bin_file;
  private static String all_fields;

  private static FwdPrint inp  = new FwdPrint("Interval",   "",      "12.0");
  private static FwdPrint acp  = new FwdPrint("ReqstdOps.", "rate",  "6.1", "resp",  "6.3");
  private static FwdPrint cpup = new FwdPrint("cpu%",       "total", "5.1", "sys",   "4.2");
  private static FwdPrint rdp  = new FwdPrint("read",       "rate",  "6.1", "resp",  "6.3");
  private static FwdPrint wrp  = new FwdPrint("write",      "rate",  "6.1", "resp",  "6.3");
  private static FwdPrint pctp = new FwdPrint(" read",      "pct",   "5.1");
  private static FwdPrint mbp  = new FwdPrint("mb/sec",     "read",  "5.2", "write", "5.2");
  private static FwdPrint mbt  = new FwdPrint("mb/sec",     "total", "6.2");
  private static FwdPrint xfp  = new FwdPrint("xfer",       "size",  "7.0");
  private static FwdPrint mdp  = new FwdPrint("mkdir",      "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint ddp  = new FwdPrint("rmdir",      "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint crp  = new FwdPrint("create",     "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint opp  = new FwdPrint("open",       "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint clp  = new FwdPrint("close",      "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint delp = new FwdPrint("delete",     "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint getp = new FwdPrint("getattr",    "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint setp = new FwdPrint("setattr",    "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint accs = new FwdPrint("access",     "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint copp = new FwdPrint("copy",       "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint movp = new FwdPrint("move",       "rate",  "5.1", "resp",  "6.3");
  private static FwdPrint putp = new FwdPrint("put",        "mb",  "5.1");
  private static FwdPrint ggtp = new FwdPrint("get",        "mb",  "5.1");

  private static int time_travel_window = checkTimeTravel();
  private static int time_travel_count  = 0;

  private static boolean skip_response_times = common.get_debug(common.NO_RESPONSE_TIMES);

  public FwdStats()
  {
    //common.where(8);
  }

  /**
   * Count Operation workload and performance statistics.
   */
  public static void count(int operation, long start)
  {
    //common.where();
    //start += 200000;

    long end = Native.get_simple_tod();

    /* This is a way around too much timetravel, just setting all response times to zero: */
    if (skip_response_times)
      end = start;

    if (start > end)
      start = timeTravel(start, end);

    FwgThread thread = (FwgThread) Thread.currentThread();
    thread.per_thread_stats.add(operation, end - start, 0);
  }

  public static void countXfer(int operation, long start, int xfersize)
  {
    //common.where();
    //start += 200000;

    long end = Native.get_simple_tod();

    /* This is a way around too much timetravel, just setting all response times to zero: */
    if (skip_response_times)
      end = start;

    if (start > end)
      start = timeTravel(start, end);

    FwgThread thread = (FwgThread) Thread.currentThread();
    thread.per_thread_stats.add(operation, end - start, xfersize);
  }


  //
  // We could save some time here by having the CALLER specify instead of
  // operation=read the actual 'read' FwdCounter
  //
  private void add(int operation, long resp, int xfersize)
  {
    /* These are in order of expected frequency. 'switch' does not let me */
    /* use these non-constants:                                           */
    //if (     operation == Operations.CREATE    )
    //  common.where(8);

    if (     operation == Operations.READ    ) read    .addResp(resp);
    else if (operation == Operations.WRITE   ) write   .addResp(resp);
    else if (operation == Operations.GETATTR ) getattr .addResp(resp);
    else if (operation == Operations.SETATTR ) setattr .addResp(resp);
    else if (operation == Operations.ACCESS  ) access  .addResp(resp);
    else if (operation == Operations.CREATE  ) create  .addResp(resp);
    else if (operation == Operations.MKDIR   ) mkdir   .addResp(resp);
    else if (operation == Operations.OPEN    ) open    .addResp(resp);
    else if (operation == Operations.CLOSE   ) close   .addResp(resp);
    else if (operation == Operations.DELETE  ) delete  .addResp(resp);
    else if (operation == Operations.RMDIR   ) rmdir   .addResp(resp);
    else if (operation == Operations.COPY    ) copy    .addResp(resp);
    else if (operation == Operations.MOVE    ) move    .addResp(resp);
    else if (operation == Operations.PUT     ) put     .addResp(resp);
    else if (operation == Operations.GET     ) get     .addResp(resp);

    else
      common.failure("FwdStats.add(): unknown operation: " + operation);


    if (operation == Operations.READ) // || operation == Operations.GET)
      r_bytes += xfersize;

    else if (operation == Operations.WRITE) // || operation == Operations.PUT)
      w_bytes += xfersize;
  }

  public void accum(FwdStats old, boolean add_elapsed)
  {
    read    .accum(old.read    );
    write   .accum(old.write   );
    mkdir   .accum(old.mkdir   );
    create  .accum(old.create  );
    getattr .accum(old.getattr );
    setattr .accum(old.setattr );
    access  .accum(old.access  );
    open    .accum(old.open    );
    close   .accum(old.close   );
    delete  .accum(old.delete  );
    rmdir   .accum(old.rmdir   );
    copy    .accum(old.copy    );
    move    .accum(old.move    );
    put     .accum(old.put     );
    get     .accum(old.get     );
    r_bytes += old.r_bytes;
    w_bytes += old.w_bytes;
    permit_count += old.permit_count;
    permit_time  += old.permit_time;

    if (add_elapsed)
      elapsed += old.elapsed ;
    else if (old.elapsed != 0)
      elapsed  = old.elapsed ;
  }


  public void delta(FwdStats nw, FwdStats old)
  {
    read    .delta(nw.read    , old.read    );
    write   .delta(nw.write   , old.write   );
    mkdir   .delta(nw.mkdir   , old.mkdir   );
    create  .delta(nw.create  , old.create  );
    getattr .delta(nw.getattr , old.getattr );
    setattr .delta(nw.setattr , old.setattr );
    access  .delta(nw.access  , old.access  );
    open    .delta(nw.open    , old.open    );
    close   .delta(nw.close   , old.close   );
    delete  .delta(nw.delete  , old.delete  );
    rmdir   .delta(nw.rmdir   , old.rmdir   );
    copy    .delta(nw.copy    , old.copy    );
    move    .delta(nw.move    , old.move    );
    put     .delta(nw.put     , old.put     );
    get     .delta(nw.get     , old.get     );

    r_bytes = nw.r_bytes - old.r_bytes;
    w_bytes = nw.w_bytes - old.w_bytes;
    permit_count = nw.permit_count - old.permit_count;
    permit_time  = nw.permit_time  - old.permit_time;
  }


  public void copyStats(FwdStats old)
  {
    read    .copy(old.read    );
    write   .copy(old.write   );
    mkdir   .copy(old.mkdir   );
    create  .copy(old.create  );
    getattr .copy(old.getattr );
    setattr .copy(old.setattr );
    access  .copy(old.access  );
    open    .copy(old.open    );
    close   .copy(old.close   );
    delete  .copy(old.delete  );
    rmdir   .copy(old.rmdir   );
    copy    .copy(old.copy    );
    move    .copy(old.move    );
    put     .copy(old.put     );
    get     .copy(old.get     );
    r_bytes = old.r_bytes;
    w_bytes = old.w_bytes;
    permit_count = old.permit_count;
    permit_time  = old.permit_time;
  }


  public static void printHeaders(Report report)
  {
    DateFormat df = new SimpleDateFormat( "MMM dd, yyyy" );
    String now = df.format(new Date());

    String aux0 = "";
    String aux1 = "";
    if (report == Report.getSummaryReport() || report == Report.getStdoutReport())
    {
      if (Report.getAuxReport() != null)
      {
        String[] aux = Report.getAuxReport().getSummaryHeaders();
        aux0 = aux[0];
        aux1 = aux[1];
      }
    }

    String permit1 = "";
    String permit2 = "";
    if (common.get_debug(common.REPORT_FWG_PERMITS))
    {
      permit1 = " thread";
      permit2 = "  busy ";
    }

    report.println("");
    report.println(now + getHeader1(CpuStats.isCpuReporting()) + aux0 + permit1);
    report.println("            " + getHeader2(CpuStats.isCpuReporting()) + aux1 + permit2);
  }

  public static String getShortHeader1()
  {
    String line = getHeader1(false);

    return line.substring(inp .getHeader1().length());
  }
  public static String getHeader1(boolean cpu)
  {
    String line = "";
    line += inp .getHeader1();
    line += acp .getHeader1();
    line += (cpu) ? cpup.getHeader1() : "";
    line += pctp.getHeader1();
    line += rdp .getHeader1();
    line += wrp .getHeader1();
    line += mbp .getHeader1();
    line += mbt .getHeader1();
    line += xfp .getHeader1();
    if (Operations.isOperationUsed(Operations.MKDIR))  line += mdp .getHeader1();
    if (Operations.isOperationUsed(Operations.RMDIR))  line += ddp .getHeader1();
    if (Operations.isOperationUsed(Operations.CREATE)) line += crp .getHeader1();
    line += opp .getHeader1();
    line += clp .getHeader1();
    if (Operations.isOperationUsed(Operations.DELETE)) line += delp.getHeader1();

    if (Operations.isOperationUsed(Operations.GETATTR)) line += getp.getHeader1();
    if (Operations.isOperationUsed(Operations.SETATTR)) line += setp.getHeader1();
    if (Operations.isOperationUsed(Operations.ACCESS))  line += accs.getHeader1();
    if (Operations.isOperationUsed(Operations.COPY))    line += copp.getHeader1();
    if (Operations.isOperationUsed(Operations.MOVE))    line += movp.getHeader1();
    //if (Operations.isOperationUsed(Operations.GET))     line += ggtp.getHeader1();
    //if (Operations.isOperationUsed(Operations.PUT))     line += putp.getHeader1();

    return line;
  }

  public static String getShortHeader2()
  {
    String line = getHeader2(false);

    return line.substring(inp .getHeader2().length());
  }

  public static String getHeader2(boolean cpu)
  {
    String line = "";
    line += inp .getHeader2();
    line += acp .getHeader2();
    line += (cpu) ? cpup.getHeader2() : "";
    line += pctp.getHeader2();
    line += rdp .getHeader2();
    line += wrp .getHeader2();
    line += mbp .getHeader2();
    line += mbt .getHeader2();
    line += xfp .getHeader2();
    if (Operations.isOperationUsed(Operations.MKDIR))  line += mdp .getHeader2();
    if (Operations.isOperationUsed(Operations.RMDIR))  line += ddp .getHeader2();
    if (Operations.isOperationUsed(Operations.CREATE)) line += crp .getHeader2();
    line += opp .getHeader2();
    line += clp .getHeader2();
    if (Operations.isOperationUsed(Operations.DELETE)) line += delp.getHeader2();

    if (Operations.isOperationUsed(Operations.GETATTR)) line += getp.getHeader2();
    if (Operations.isOperationUsed(Operations.SETATTR)) line += setp.getHeader2();
    if (Operations.isOperationUsed(Operations.ACCESS))  line += accs.getHeader2();
    if (Operations.isOperationUsed(Operations.COPY))    line += copp.getHeader2();
    if (Operations.isOperationUsed(Operations.MOVE))    line += movp.getHeader2();
    //if (Operations.isOperationUsed(Operations.GET))     line += ggtp.getHeader2();
    //if (Operations.isOperationUsed(Operations.PUT))     line += putp.getHeader2();

    return line;
  }

  private static double MB = 1024 * 1024;
  public void printLine(Report report, Kstat_cpu kstat_cpu)
  {
    if (Reporter.needHeaders())
      printHeaders(report);

    printLineL(report, kstat_cpu, Format.f("%d", Report.getInterval()));
  }

  /**
   * Retrun the same line, but now without the interval in front.
   */
  public String printShortLine(Report report, Kstat_cpu kc, String lbl)
  {
    String line = printLineL(report, kc, lbl);
    return line.substring(inp .getData(lbl).length());
  }
  public String printLineL(Report report, Kstat_cpu kc, String lbl)
  {
    double r_mb = r_bytes * 1000000. / elapsed / MB;
    double w_mb = w_bytes * 1000000. / elapsed / MB;
    long   xfersize = 0;
    double rdpct    = 0;

    //if (RD_entry.isOperationUsed(Operations.GET) ||
    //    RD_entry.isOperationUsed(Operations.PUT))
    //{
    //  r_mb = NwStats.in_bytes * 1000000. / elapsed / MB;
    //  w_mb = NwStats.ot_bytes * 1000000. / elapsed / MB;
    //}


    if (read.operations + write.operations > 0)
    {
      xfersize = (r_bytes + w_bytes) / (read.operations + write.operations);
      rdpct    = read.operations * 100. / (read.operations + write.operations);
    }

    String line = "";
    line += inp .getData(lbl);
    line += acp .getData(getReqstdRate(), getReqstdlResp());

    if (CpuStats.isCpuReporting() && kc != null)
      line += cpup.getData(kc.user_pct() + kc.kernel_pct(), kc.kernel_pct());

    line += pctp.getData(rdpct);
    line += rdp .getData(read   .rate(),    read   .resp()    );
    line += wrp .getData(write  .rate(),    write  .resp()    );
    line += mbp .getData(r_mb,              w_mb              );
    line += mbt .getData(r_mb + w_mb);
    line += xfp .getData(xfersize);
    if (Operations.isOperationUsed(Operations.MKDIR))
      line += mdp .getData(mkdir  .rate(),    mkdir  .resp()    );
    if (Operations.isOperationUsed(Operations.RMDIR))
      line += ddp .getData(rmdir  .rate(),    rmdir  .resp()    );
    if (Operations.isOperationUsed(Operations.CREATE))
      line += crp .getData(create .rate(),    create .resp()    );
    line += opp .getData(open   .rate(),    open   .resp()    );
    line += clp .getData(close  .rate(),    close  .resp()    );
    if (Operations.isOperationUsed(Operations.DELETE))
      line += delp.getData(delete .rate(),    delete .resp()    );

    if (Operations.isOperationUsed(Operations.GETATTR))
      line += getp.getData(getattr.rate(), getattr.resp());
    if (Operations.isOperationUsed(Operations.SETATTR))
      line += setp.getData(setattr.rate(), setattr.resp());
    if (Operations.isOperationUsed(Operations.ACCESS))
      line += accs.getData(access. rate(), access .resp());
    if (Operations.isOperationUsed(Operations.COPY))
      line += copp.getData(copy   .rate(), copy   .resp());
    if (Operations.isOperationUsed(Operations.MOVE))
      line += movp.getData(move   .rate(), move   .resp());
    //if (Operations.isOperationUsed(Operations.PUT))
    //  line += putp.getData(put    .rate(), put    .resp());
    //if (Operations.isOperationUsed(Operations.GET))
    //  line += ggtp.getData(get    .rate(), get    .resp());


    if (permit_count > 0 && common.get_debug(common.REPORT_FWG_PERMITS) &&
        !lbl.contains("avg"))
    {
      long msecs_busy = permit_time / permit_count / elapsed;
      double pct_busy = (1000. - msecs_busy) * 100 / 1000.;
      pct_busy = Math.max(0., pct_busy);
      pct_busy = Math.min(100., pct_busy);
      line += String.format(" %5.1f", pct_busy);
    }


    if (report == null)
      return line;

    if (report == Report.getSummaryReport() || report == Report.getStdoutReport())
    {
      if (Report.getAuxReport() != null)
        line += Report.getAuxReport().getSummaryData();
    }

    report.println(common.tod() + line);

    return null;
  }

  public String printMaxLine(Report report, Kstat_cpu kc, String lbl)
  {
    lbl         = lbl.replace("avg", "max");
    double r_mb = r_bytes * 1000000. / elapsed / MB;
    double w_mb = w_bytes * 1000000. / elapsed / MB;
    long   xfersize = 0;
    double rdpct    = 0;

    if (read.operations + write.operations > 0)
    {
      xfersize = (r_bytes + w_bytes) / (read.operations + write.operations);
      rdpct    = read.operations * 100. / (read.operations + write.operations);
    }

    String line = "";
    line += inp .getData(lbl);

    /* std and max for reqstdops will only be reported if there is only ONE */
    /* REQUESTED operation. Combinations can not be done, results would be invalid. */
    FwdCounter single = getSingleCounter();
    if (single == null)
      line += acp .getDataZ(0, 0);
    else
      line += acp .getDataZ(single.rateMax(), single.respMax());


    if (CpuStats.isCpuReporting() && kc != null)
      line += cpup.getDataZ(0, 0);

    line += pctp.getDataZ(0);
    line += rdp .getDataZ(read.rateMax(),   read   .respMax() );
    line += wrp .getDataZ(write.rateMax(),  write  .respMax() );
    line += mbp .getDataZ(0,                0      );
    line += mbt .getDataZ(0);
    line += xfp .getDataZ(0);
    if (Operations.isOperationUsed(Operations.MKDIR))
      line += mdp .getDataZ(mkdir.rateMax(),  mkdir  .respMax() );
    if (Operations.isOperationUsed(Operations.RMDIR))
      line += ddp .getDataZ(rmdir.rateMax(),  rmdir  .respMax() );
    if (Operations.isOperationUsed(Operations.CREATE))
      line += crp .getDataZ(create.rateMax(), create .respMax() );
    line += opp .getDataZ(open.rateMax(),   open   .respMax() );
    line += clp .getDataZ(close.rateMax(),  close  .respMax() );
    if (Operations.isOperationUsed(Operations.DELETE))
      line += delp.getDataZ(delete.rateMax(), delete .respMax() );

    if (Operations.isOperationUsed(Operations.GETATTR))
      line += getp.getDataZ(getattr.rateMax(), getattr.respMax());
    if (Operations.isOperationUsed(Operations.SETATTR))
      line += setp.getDataZ(setattr.rateMax(), setattr.respMax());
    if (Operations.isOperationUsed(Operations.ACCESS))
      line += accs.getDataZ(access.rateMax(), access .respMax());
    if (Operations.isOperationUsed(Operations.COPY))
      line += copp.getDataZ(copy.rateMax(), copy   .respMax());
    if (Operations.isOperationUsed(Operations.MOVE))
      line += movp.getDataZ(move.rateMax(), move   .respMax());
    //if (Operations.isOperationUsed(Operations.PUT))
    //  line += putp.getDataZ(put.rateMax(), put   .respMax());
    //if (Operations.isOperationUsed(Operations.GET))
    //  line += ggtp.getDataZ(get.rateMax(), get   .respMax());

    if (report == null)
      return line;

    if (report == Report.getSummaryReport() || report == Report.getStdoutReport())
    {
      if (Report.getAuxReport() != null)
        line += Report.getAuxReport().getSummaryData();
    }

    report.println(common.tod() + line);

    return null;
  }


  /**
   * Report standard deviation for those columns that I have info for.
   */
  public String printStdLine(Report report, Kstat_cpu kc, String lbl)
  {
    lbl         = lbl.replace("avg", "std");
    double r_mb = r_bytes * 1000000. / elapsed / MB;
    double w_mb = w_bytes * 1000000. / elapsed / MB;
    long   xfersize = 0;
    double rdpct    = 0;

    if (read.operations + write.operations > 0)
    {
      xfersize = (r_bytes + w_bytes) / (read.operations + write.operations);
      rdpct    = read.operations * 100. / (read.operations + write.operations);
    }

    String line = "";
    line += inp .getData(lbl);

    /* std and max for reqstdops will only be reported if there is only ONE */
    /* REQUESTED operation. Combinations can not be done, results would be invalid. */
    FwdCounter single = getSingleCounter();
    if (single == null)
      line += acp .getDataZ(0, 0);
    else
      line += acp .getDataZ(single.rateStd(), single.respStd());


    if (CpuStats.isCpuReporting() && kc != null)
      line += cpup.getDataZ(0, 0);

    line += pctp.getDataZ(0);
    line += rdp .getDataZ(read.rateStd(),   read   .respStd() );
    line += wrp .getDataZ(write.rateStd(),  write  .respStd() );
    line += mbp .getDataZ(0,                0      );
    line += mbt .getDataZ(0);
    line += xfp .getDataZ(0);
    if (Operations.isOperationUsed(Operations.MKDIR))
      line += mdp .getDataZ(mkdir.rateStd(),  mkdir  .respStd() );
    if (Operations.isOperationUsed(Operations.RMDIR))
      line += ddp .getDataZ(rmdir.rateStd(),  rmdir  .respStd() );
    if (Operations.isOperationUsed(Operations.CREATE))
      line += crp .getDataZ(create.rateStd(), create .respStd() );
    line += opp .getDataZ(open.rateStd(),   open   .respStd() );
    line += clp .getDataZ(close.rateStd(),  close  .respStd() );
    if (Operations.isOperationUsed(Operations.DELETE))
      line += delp.getDataZ(delete.rateStd(), delete .respStd() );

    if (Operations.isOperationUsed(Operations.GETATTR))
      line += getp.getDataZ(getattr.rateStd(), getattr.respStd());
    if (Operations.isOperationUsed(Operations.SETATTR))
      line += setp.getDataZ(setattr.rateStd(), setattr.respStd());
    if (Operations.isOperationUsed(Operations.ACCESS))
      line += accs.getDataZ(access.rateStd(), access .respStd());
    if (Operations.isOperationUsed(Operations.COPY))
      line += copp.getDataZ(copy.rateStd(), copy   .respStd());
    if (Operations.isOperationUsed(Operations.MOVE))
      line += movp.getDataZ(move.rateStd(), move   .respStd());
    //if (Operations.isOperationUsed(Operations.PUT))
    //  line += putp.getDataZ(put.rateStd(), put   .respStd());
    //if (Operations.isOperationUsed(Operations.GET))
    //  line += ggtp.getDataZ(get.rateStd(), get   .respStd());

    if (report == null)
      return line;

    if (report == Report.getSummaryReport() || report == Report.getStdoutReport())
    {
      if (Report.getAuxReport() != null)
        line += Report.getAuxReport().getSummaryData();
    }

    report.println(common.tod() + line);

    return null;
  }

  public long getElapsed()
  {
    return elapsed;
  }
  public void setElapsed(long el)
  {
    elapsed = el;
  }

  // not used.
  private String conditional(FwdPrint fp, FwdCounter ctr, int operation)
  {
    if (Operations.isOperationUsed(operation))
      return fp.getData(ctr.rate(), ctr.resp());
    return "";
  }

  /**
   * The totals include ONLY those operations that are requested in the
   * current RD_entry.
   */
  public double getReqstdRate()
  {
    double total = 0;

    if (RD_entry.next_rd.rd_name.startsWith(RD_entry.FSD_FORMAT_RUN))
      return write.rate();
    else
    {
      if (isOperationRequested(Operations.READ    )) total += read    .rate();
      if (isOperationRequested(Operations.WRITE   )) total += write   .rate();
      if (isOperationRequested(Operations.MKDIR   )) total += mkdir   .rate();
      if (isOperationRequested(Operations.CREATE  )) total += create  .rate();
      if (isOperationRequested(Operations.OPEN    )) total += open    .rate();
      if (isOperationRequested(Operations.CLOSE   )) total += close   .rate();
      if (isOperationRequested(Operations.DELETE  )) total += delete  .rate();
      if (isOperationRequested(Operations.RMDIR   )) total += rmdir   .rate();
      if (isOperationRequested(Operations.GETATTR )) total += getattr .rate();
      if (isOperationRequested(Operations.SETATTR )) total += setattr .rate();
      if (isOperationRequested(Operations.ACCESS  )) total += access  .rate();
      if (isOperationRequested(Operations.COPY    )) total += copy    .rate();
      if (isOperationRequested(Operations.MOVE    )) total += move    .rate();
      if (isOperationRequested(Operations.PUT     )) total += put     .rate();
      if (isOperationRequested(Operations.GET     )) total += get     .rate();
    }

    return total;
  }

  /**
   * Get the TOTAL rate, whether requested or not.
   */
  public double getTotalRate()
  {
    double total = 0;

    total += removeTrash(read    .rate());
    total += removeTrash(write   .rate());
    total += removeTrash(mkdir   .rate());
    total += removeTrash(create  .rate());
    total += removeTrash(open    .rate());
    total += removeTrash(close   .rate());
    total += removeTrash(delete  .rate());
    total += removeTrash(rmdir   .rate());
    total += removeTrash(getattr .rate());
    total += removeTrash(setattr .rate());
    total += removeTrash(access  .rate());
    total += removeTrash(copy    .rate());
    total += removeTrash(move    .rate());
    total += removeTrash(put     .rate());
    total += removeTrash(get     .rate());

    return total;
  }
  private static double removeTrash(double in)
  {
    //    //if (in == 0.0)
    //    //  return 0;
    //    //if (in == Double.POSITIVE_INFINITY)
    //    //  return 0;
    //    //if (in == Double.NEGATIVE_INFINITY)
    //    //  return 0;
    //    if (Double.isInfinite(in))
    //      return 0;
    //    if (Double.isNaN(in))
    //      return 0;
    //    //common.ptod("xxxxxx: %16.8f %16d", in, (long) in);
    return in;
  }


  /**
   * For requestedops we need to know if there is indeed only ONE requested
   * operation, this for std+max
   */
  public FwdCounter getSingleCounter()
  {
    int operations = 0;

    if (RD_entry.next_rd.rd_name.startsWith(RD_entry.FSD_FORMAT_RUN))
      return write;
    else
    {
      if (isOperationRequested(Operations.READ    )) operations++;
      if (isOperationRequested(Operations.WRITE   )) operations++;
      if (isOperationRequested(Operations.MKDIR   )) operations++;
      if (isOperationRequested(Operations.CREATE  )) operations++;
      if (isOperationRequested(Operations.OPEN    )) operations++;
      if (isOperationRequested(Operations.CLOSE   )) operations++;
      if (isOperationRequested(Operations.DELETE  )) operations++;
      if (isOperationRequested(Operations.RMDIR   )) operations++;
      if (isOperationRequested(Operations.GETATTR )) operations++;
      if (isOperationRequested(Operations.SETATTR )) operations++;
      if (isOperationRequested(Operations.ACCESS  )) operations++;
      if (isOperationRequested(Operations.COPY    )) operations++;
      if (isOperationRequested(Operations.MOVE    )) operations++;
      if (isOperationRequested(Operations.PUT     )) operations++;
      if (isOperationRequested(Operations.GET     )) operations++;
    }

    if (operations == 0)
      common.failure("getSingleCounter(): unexpected operations count");

    else if (operations > 1)
      return null;

    else
    {
      if (isOperationRequested(Operations.READ    )) return read    ;
      if (isOperationRequested(Operations.WRITE   )) return write   ;
      if (isOperationRequested(Operations.MKDIR   )) return mkdir   ;
      if (isOperationRequested(Operations.CREATE  )) return create  ;
      if (isOperationRequested(Operations.OPEN    )) return open    ;
      if (isOperationRequested(Operations.CLOSE   )) return close   ;
      if (isOperationRequested(Operations.DELETE  )) return delete  ;
      if (isOperationRequested(Operations.RMDIR   )) return rmdir   ;
      if (isOperationRequested(Operations.GETATTR )) return getattr ;
      if (isOperationRequested(Operations.SETATTR )) return setattr ;
      if (isOperationRequested(Operations.ACCESS  )) return access  ;
      if (isOperationRequested(Operations.COPY    )) return copy    ;
      if (isOperationRequested(Operations.MOVE    )) return move    ;
      if (isOperationRequested(Operations.PUT     )) return put     ;
      if (isOperationRequested(Operations.GET     )) return get     ;
    }

    common.failure("getSingleCounter(): unexpected operations count");
    return null;
  }




  public long getTotalBytes()
  {
    return r_bytes + w_bytes;
  }
  public long getTotalBytesRead()
  {
    return r_bytes;
  }
  public long getTotalBytesWritten()
  {
    return w_bytes;
  }


  /**
   * The totals include ONLY those operations that are requested in the
   * current RD_entry.
   */
  public double getReqstdlResp()
  {
    double total = 0;

    if (RD_entry.next_rd.rd_name.startsWith(RD_entry.FSD_FORMAT_RUN))
      return write.resp();
    else
    {
      if (isOperationRequested(Operations.READ))    total += read    .response;
      if (isOperationRequested(Operations.WRITE))   total += write   .response;
      if (isOperationRequested(Operations.MKDIR))   total += mkdir   .response;
      if (isOperationRequested(Operations.CREATE))  total += create  .response;
      if (isOperationRequested(Operations.OPEN))    total += open    .response;
      if (isOperationRequested(Operations.CLOSE))   total += close   .response;
      if (isOperationRequested(Operations.DELETE))  total += delete  .response;
      if (isOperationRequested(Operations.RMDIR))   total += rmdir   .response;
      if (isOperationRequested(Operations.GETATTR)) total += getattr .response;
      if (isOperationRequested(Operations.SETATTR)) total += setattr .response;
      if (isOperationRequested(Operations.ACCESS))  total += access  .response;
      if (isOperationRequested(Operations.COPY))    total += copy    .response;
      if (isOperationRequested(Operations.MOVE))    total += move    .response;
    }

    if (total == 0)
      return 0;

    return(double) total / getReqstdRate() / (elapsed / 1000.);
  }


  public Histogram getReqstdHistogram()
  {
    Histogram hist = new Histogram("default");

    if (RD_entry.next_rd.rd_name.startsWith(RD_entry.FSD_FORMAT_RUN))
      hist.accumBuckets(write   .getHistogram());
    else
    {
      if (isOperationRequested(Operations.READ    )) hist.accumBuckets(read    .getHistogram());
      if (isOperationRequested(Operations.WRITE   )) hist.accumBuckets(write   .getHistogram());
      if (isOperationRequested(Operations.MKDIR   )) hist.accumBuckets(mkdir   .getHistogram());
      if (isOperationRequested(Operations.CREATE  )) hist.accumBuckets(create  .getHistogram());
      if (isOperationRequested(Operations.OPEN    )) hist.accumBuckets(open    .getHistogram());
      if (isOperationRequested(Operations.CLOSE   )) hist.accumBuckets(close   .getHistogram());
      if (isOperationRequested(Operations.DELETE  )) hist.accumBuckets(delete  .getHistogram());
      if (isOperationRequested(Operations.RMDIR   )) hist.accumBuckets(rmdir   .getHistogram());
      if (isOperationRequested(Operations.GETATTR )) hist.accumBuckets(getattr .getHistogram());
      if (isOperationRequested(Operations.SETATTR )) hist.accumBuckets(setattr .getHistogram());
      if (isOperationRequested(Operations.ACCESS  )) hist.accumBuckets(access  .getHistogram());
      if (isOperationRequested(Operations.COPY    )) hist.accumBuckets(copy    .getHistogram());
      if (isOperationRequested(Operations.MOVE    )) hist.accumBuckets(move    .getHistogram());
    }

    return hist;
  }


  /**
   * Help determine which operations should be included in the total rate
   * and response time.*
   */
  private boolean isOperationRequested(int operation)
  {
    Vector fwgs = RD_entry.next_rd.fwgs_for_rd;
    for (int i = 0; i < fwgs.size(); i++)
    {
      FwgEntry fwg = (FwgEntry) fwgs.elementAt(i);
      //common.ptod("operation: " + Operations.getOperationText(operation) +
      //            " " + fwg.getOperation() + " " +
      //            Operations.getOperationText(fwg.getOperation()) +
      //            " " + RD_entry.next_rd);
      if (fwg.getOperation() == operation)
        return true;

      /* A mix of read+write? */
      if (fwg.readpct >= 0 &&
          (operation == Operations.READ || operation == Operations.WRITE))
        return true;
    }

    return false;
  }



  private static String title(int operation, int width)
  {
    String lbl = Operations.getOperationText(operation);
    int extra = width - lbl.length();
    String dots1 = "..................".substring(0, extra / 2);
    String dots2 = "..................".substring(0, extra - dots1.length());

    return dots1 + lbl + dots2;
  }
  private static String title(String lbl, int width)
  {
    int extra = width - lbl.length();
    String dots1 = "..................".substring(0, extra / 2);
    String dots2 = "..................".substring(0, extra - dots1.length());

    return dots1 + lbl + dots2;
  }

  public void writeFlat(String title, Kstat_cpu kc)
  {
    double r_mb = r_bytes * 1000000. / elapsed / MB;
    double w_mb = w_bytes * 1000000. / elapsed / MB;
    long   xfersize = 0;

    if (read.operations + write.operations > 0)
      xfersize = (r_bytes + w_bytes) / (read.operations + write.operations);

    double compratio = Validate.getCompressionRatio();

    Flat.put_col("Run",          RD_entry.next_rd.rd_name);
    Flat.put_col("Interval",     title);   // reqrate is filled in somehwere in RD_entry.

    Flat.put_col("rate",         getReqstdRate());
    FwdCounter single = getSingleCounter();
    if (single != null)
    {
      Flat.put_col("Rate_std",         single.rateStd());
      Flat.put_col("Rate_max",         single.rateMax());
    }

    Flat.put_col("resp",         getReqstdlResp());
    if (single != null)
    {
      Flat.put_col("Resp_std",         single.respStd());
      Flat.put_col("Resp_max",         single.respMax());
    }

    Flat.put_col("MB/sec",            r_mb + w_mb);
    Flat.put_col("MB_read",           r_mb);
    Flat.put_col("MB_write",          w_mb);
    Flat.put_col("Xfersize",          xfersize);

    Flat.put_col("Read_rate",         read.rate());
    Flat.put_col("Read_rate_std",     read.rateStd());
    Flat.put_col("Read_rate_max ",    read.rateMax());
    Flat.put_col("Read_resp",         read.resp());
    Flat.put_col("Read_resp_std",     read.respStd());
    Flat.put_col("Read_resp_max ",    read.respMax());

    Flat.put_col("Write_rate",        write.rate());
    Flat.put_col("Write_rate_std",    write.rateStd());
    Flat.put_col("Write_rate_max ",   write.rateMax());
    Flat.put_col("Write_resp",        write.resp());
    Flat.put_col("Write_resp_std",    write.respStd());
    Flat.put_col("Write_resp_max ",   write.respMax());

    Flat.put_col("Mkdir_rate",        mkdir.rate());
    Flat.put_col("Mkdir_rate_std",    mkdir.rateStd());
    Flat.put_col("Mkdir_rate_max ",   mkdir.rateMax());
    Flat.put_col("Mkdir_resp",        mkdir.resp());
    Flat.put_col("Mkdir_resp_std",    mkdir.respStd());
    Flat.put_col("Mkdir_resp_max ",   mkdir.respMax());

    Flat.put_col("Rmdir_rate",        rmdir.rate());
    Flat.put_col("Rmdir_rate_std",    rmdir.rateStd());
    Flat.put_col("Rmdir_rate_max ",   rmdir.rateMax());
    Flat.put_col("Rmdir_resp",        rmdir.resp());
    Flat.put_col("Rmdir_resp_std",    rmdir.respStd());
    Flat.put_col("Rmdir_resp_max ",   rmdir.respMax());


    Flat.put_col("Create_rate",       create.rate());
    Flat.put_col("Create_rate_std",   create.rateStd());
    Flat.put_col("Create_rate_max ",  create.rateMax());
    Flat.put_col("Create_resp",       create.resp());
    Flat.put_col("Create_resp_std",   create.respStd());
    Flat.put_col("Create_resp_max ",  create.respMax());

    Flat.put_col("Open_rate",         open.rate());
    Flat.put_col("Open_rate_std",     open.rateStd());
    Flat.put_col("Open_rate_max ",    open.rateMax());
    Flat.put_col("Open_resp",         open.resp());
    Flat.put_col("Open_resp_std",     open.respStd());
    Flat.put_col("Open_resp_max ",    open.respMax());

    Flat.put_col("Close_rate",        close.rate());
    Flat.put_col("Close_rate_std",    close.rateStd());
    Flat.put_col("Close_rate_max ",   close.rateMax());
    Flat.put_col("Close_resp",        close.resp());
    Flat.put_col("Close_resp_std",    close.respStd());
    Flat.put_col("Close_resp_max ",   close.respMax());

    Flat.put_col("Delete_rate",       delete.rate());
    Flat.put_col("Delete_rate_std",   delete.rateStd());
    Flat.put_col("Delete_rate_max ",  delete.rateMax());
    Flat.put_col("Delete_resp",       delete.resp());
    Flat.put_col("Delete_resp_std",   delete.respStd());
    Flat.put_col("Delete_resp_max ",  delete.respMax());

    Flat.put_col("Getattr_rate",      getattr.rate());
    Flat.put_col("Getattr_rate_std",  getattr.rateStd());
    Flat.put_col("Getattr_rate_max ", getattr.rateMax());
    Flat.put_col("Getattr_resp",      getattr.resp());
    Flat.put_col("Getattr_resp_std",  getattr.respStd());
    Flat.put_col("Getattr_resp_max ", getattr.respMax());

    Flat.put_col("Setattr_rate",      setattr.rate());
    Flat.put_col("Setattr_rate_std",  setattr.rateStd());
    Flat.put_col("Setattr_rate_max ", setattr.rateMax());
    Flat.put_col("Setattr_resp",      setattr.resp());
    Flat.put_col("Setattr_resp_std",  setattr.respStd());
    Flat.put_col("Setattr_resp_max ", setattr.respMax());

    Flat.put_col("Access_rate",       access.rate());
    Flat.put_col("Access_rate_std",   access.rateStd());
    Flat.put_col("Access_rate_max ",  access.rateMax());
    Flat.put_col("Access_resp",       access.resp());
    Flat.put_col("Access_resp_std",   access.respStd());
    Flat.put_col("Access_resp_max ",  access.respMax());

    //Flat.put_col("Copy_rate",       copy.rate());
    //Flat.put_col("Copy_resp",       copy.resp());
    //Flat.put_col("Move_rate",       move.rate());
    //Flat.put_col("Move_resp",       move.resp());

    if (compratio < 0)
      Flat.put_col("compratio", "n/a");
    else
      Flat.put_col("compratio", compratio);
  }


  /**
   * Define all the field names for NamedData.
   */
  public static void defineNamedData(String output)
  {
    LookupAnchor anchor = new FwdNamedData().getAnchor();

    /*
      NOTE: any changes here must also be made below and
            in /swat_mon/Utils/FwdNamedData.java
      IN OTHER WORDS: you must run both versions at the same time.
     */
    new Lookup(anchor, "Read_rate"   );
    new Lookup(anchor, "Read_resp"   );
    new Lookup(anchor, "Write_rate"  );
    new Lookup(anchor, "Write_resp"  );
    new Lookup(anchor, "MB_read"     );
    new Lookup(anchor, "MB_write"    );
    new Lookup(anchor, "MB_total"    );
    new Lookup(anchor, "Xfersize"    );
    new Lookup(anchor, "Mkdir_rate"  );
    new Lookup(anchor, "Mkdir_resp"  );
    new Lookup(anchor, "Rmdir_rate"  );
    new Lookup(anchor, "Rmdir_resp"  );
    new Lookup(anchor, "Create_rate" );
    new Lookup(anchor, "Create_resp" );
    new Lookup(anchor, "Open_rate"   );
    new Lookup(anchor, "Open_resp"   );
    new Lookup(anchor, "Close_rate"  );
    new Lookup(anchor, "Close_resp"  );
    new Lookup(anchor, "Delete_rate" );
    new Lookup(anchor, "Delete_resp" );
    new Lookup(anchor, "Getattr_rate");
    new Lookup(anchor, "Getattr_resp");
    new Lookup(anchor, "Setattr_rate");
    new Lookup(anchor, "Setattr_resp");
    new Lookup(anchor, "Access_rate" );
    new Lookup(anchor, "Access_resp" );
    new Lookup(anchor, "Copy_rate"   );
    new Lookup(anchor, "Copy_resp"   );
    new Lookup(anchor, "Move_rate"   );
    new Lookup(anchor, "Move_resp"   );
    new Lookup(anchor, "Get_rate"    );
    new Lookup(anchor, "Get_resp"    );
    new Lookup(anchor, "Put_rate"    );
    new Lookup(anchor, "Put_resp"    );
    anchor.setDoubles();

    all_fields = "";
    String[] fields = anchor.getFieldNames();
    for (int i = 0; i < fields.length; i++)
      all_fields += fields[i] + " ";

    fwd_bin_file = new Bin(output, "swat_mon.bin");
    fwd_bin_file.output();

    /* Place poper timezone into bin file: */
    Date now = new Date();
    new Date_record(now, now).export(fwd_bin_file);

    fwd_bin_file.put_array(anchor.getAnchorName(),  Bin.NAMED_HEADER);
    fwd_bin_file.put_array(anchor.getFieldTitles(), Bin.NAMED_FIELDS);
  }


  /**
   * Put the current interval's data into the binary file.
   */
  public void writeBinFile()
  {
    long tod        = System.currentTimeMillis();
    Utils.FwdNamedData fd = new Utils.FwdNamedData();
    fd.setElapsed(elapsed);
    fd.setTime(tod);

    double r_mb = r_bytes * 1000000. / elapsed / MB;
    double w_mb = w_bytes * 1000000. / elapsed / MB;
    long   xfersize = 0;

    if (read.operations + write.operations > 0)
      xfersize = (r_bytes + w_bytes) / (read.operations + write.operations);

    /* First translate all values into a concatenated String: */
    String data = "";
    data += Format.f("%.3f ", read.rate()    * elapsed / 1000);
    data += Format.f("%.3f ", read.resp()    * elapsed / 1000);
    data += Format.f("%.3f ", write.rate()   * elapsed / 1000);
    data += Format.f("%.3f ", write.resp()   * elapsed / 1000);
    data += Format.f("%.3f ", r_mb           * elapsed / 1000);
    data += Format.f("%.3f ", w_mb           * elapsed / 1000);
    data += Format.f("%.3f ", ((r_mb + w_mb)  * elapsed / 1000));
    data += Format.f("%.3f ", xfersize       * elapsed / 1000);
    data += Format.f("%.3f ", mkdir.rate()   * elapsed / 1000);
    data += Format.f("%.3f ", mkdir.resp()   * elapsed / 1000);
    data += Format.f("%.3f ", rmdir.rate()   * elapsed / 1000);
    data += Format.f("%.3f ", rmdir.resp()   * elapsed / 1000);
    data += Format.f("%.3f ", create.rate()  * elapsed / 1000);
    data += Format.f("%.3f ", create.resp()  * elapsed / 1000);
    data += Format.f("%.3f ", open.rate()    * elapsed / 1000);
    data += Format.f("%.3f ", open.resp()    * elapsed / 1000);
    data += Format.f("%.3f ", close.rate()   * elapsed / 1000);
    data += Format.f("%.3f ", close.resp()   * elapsed / 1000);
    data += Format.f("%.3f ", delete.rate()  * elapsed / 1000);
    data += Format.f("%.3f ", delete.resp()  * elapsed / 1000);
    data += Format.f("%.3f ", getattr.rate() * elapsed / 1000);
    data += Format.f("%.3f ", getattr.resp() * elapsed / 1000);
    data += Format.f("%.3f ", setattr.rate() * elapsed / 1000);
    data += Format.f("%.3f ", setattr.resp() * elapsed / 1000);
    data += Format.f("%.3f ", access.rate()  * elapsed / 1000);
    data += Format.f("%.3f ", access.resp()  * elapsed / 1000);
    data += Format.f("%.3f ", copy.rate()    * elapsed / 1000);
    data += Format.f("%.3f ", copy.resp()    * elapsed / 1000);
    data += Format.f("%.3f ", move.rate()    * elapsed / 1000);
    data += Format.f("%.3f ", move.resp()    * elapsed / 1000);
    data += Format.f("%.3f ", get.rate()     * elapsed / 1000);
    data += Format.f("%.3f ", get.resp()     * elapsed / 1000);
    data += Format.f("%.3f ", put.rate()     * elapsed / 1000);
    data += Format.f("%.3f ", put.resp()     * elapsed / 1000);

    fd.parseNamedData(all_fields + " * " + data);

    fwd_bin_file.put_array(fd.getAnchorName(), Bin.NAMED_HEADER);
    fwd_bin_file.put_array(fd.export(), Bin.NAMED_LONGS);

    /* This is needed because the channel buffer flush is not done at close */
    fwd_bin_file.flush();
  }

  private static int checkTimeTravel()
  {
    if (!Fget.file_exists(ClassPath.classPath(), "timetravel.txt"))
      return 0;
    String[] lines = Fget.readFileToArray(ClassPath.classPath(), "timetravel.txt");
    if (lines.length == 1)
    {
      common.ptod("checkTimeTravel: " + lines[0]);
      return Integer.parseInt(lines[0].trim());
    }
    return 0;
  }

  private static synchronized long timeTravel(long start, long end)
  {
    long early = start - end;
    if (time_travel_window  == 0)
      common.failure("FwdStats.count(): start time greater than end time: " +
                     start + " " + end + " " + (start - end));

    if (time_travel_window < early)
      common.failure("FwdStats.count(): start time greater than end time: " +
                     start + " " + end + " " + (start - end) +
                     "; greater than allowed time travel window");
    if (time_travel_count == 0)
      common.ptod("FwdStats.count(): start time greater than end time. " +
                  "Only 1000 occurrences are allowed.");

    if (time_travel_count++ < 100)
      common.ptod("FwdStats.count(): start time greater than end time: " +
                  start + " " + end + " " + (start - end));

    if (time_travel_count > 1000)
      common.failure("FwdStats.count(): start time greater than end time: " +
                     start + " " + end + " " + (start - end) + "; Maximum 1000 allowed");


    return end;
  }
}

