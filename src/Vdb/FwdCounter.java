package Vdb;

/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import Utils.*;


/**
 * This class contains all stuff needed to accumulate FWD statistics.
 * There is one instance for each operation: read, write, etc.
 */
class FwdCounter implements java.io.Serializable
{
  private final static String c =
  "Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.";

  private String    type;
  private FwdStats  fstats;   /* Needed to find elapsed time */
  public  long      operations;
  public  long      response;
  public  long      response2;
  public  long      maxresp;
  private Histogram histogram =   new Histogram("default");


  public  long      op_count;
  public  long      op_rate;
  public  long      op_rate2;
  public  long      op_max;

  public FwdCounter(FwdStats st, String tp)
  {
    fstats = st;
    type = tp.trim();
  }

  public Histogram getHistogram()
  {
    return histogram;
  }

  public void addResp(long resp)
  {
    operations ++;

    response  += resp;
    response2 += (resp * resp);
    maxresp    = Math.max(maxresp, resp);
    histogram.addToBucket(resp);
  }

  public void accum(FwdCounter old)
  {
    if (type.equals("xread"))
    {
      common.ptod("maxresp0: %-25s, %5d %5d", this, maxresp, old.maxresp);
      common.where(4);
    }


    operations += old.operations;
    response   += old.response;
    response2  += old.response2;
    maxresp     = Math.max(maxresp, old.maxresp);
    histogram.accumBuckets(old.histogram);

    op_count   ++;
    if (old.fstats.getElapsed() != 0 && old.operations != 0)
    {
      op_rate    += old.rate();
      op_rate2   += (old.rate() * old.rate());

      op_max      = Math.max(op_max, (long) old.rate());
      //common.ptod("op_max: %6d ", op_max);
    }


    //if (op_max == Long.MAX_VALUE)
    //{
    //  common.ptod("fstats.getElapsed(): " + old.fstats.getElapsed());
    //  common.ptod("old.operations: " + old.operations);
    //  common.ptod("old.rate(): " + old.rate());
    //  common.ptod("old.rate(): " + (long) old.rate());
    //  common.where(8);
    //}
  }

  public void delta(FwdCounter nw, FwdCounter old)
  {
    operations = nw.operations - old.operations;
    response   = nw.response   - old.response;
    response2  = nw.response2  - old.response2;
    maxresp    = Math.max(nw.maxresp, old.maxresp);
    histogram.deltaBuckets(nw.histogram, old.histogram);
  }

  public void copy(FwdCounter old)
  {
    operations = old.operations;
    response   = old.response;
    response2  = old.response2;
    maxresp    = Math.max(maxresp, old.maxresp);
    op_max     = old.op_max;
    op_rate    = old.op_rate;
    op_rate2   = old.op_rate2;
    op_count   = old.op_count;
    histogram  = (Histogram) old.histogram.clone();

    if (type.equals("xread"))
    {
      common.ptod("maxresp1: " + maxresp);
      common.where(4);
    }
  }

  public double rate()
  {
    double ret = operations * 1000000. / fstats.getElapsed();
    //if (operations > 0)
   // if (type.equals("read") && fstats.getElapsed() == 0)
   // {
   //    common.ptod("operations: %-8s %6d %7.2f %6d", type, operations, ret, fstats.getElapsed());
   //    common.where(4);
   // }
    return ret;
  }
  public double resp()
  {
    if (operations == 0)
      return 0;
    return(double) response / operations / 1000.;
  }

  public double respMax()
  {
    if (type.equals("xread"))
    {
      common.ptod("maxresp2: " + maxresp);
      common.where(4);
    }
    return(double) maxresp / 1000.;
  }
  public double rateMax()
  {
    return(double) op_max;
  }

  public double respStd()
  {
    if (operations <= 1 || response == 0)
      return 0;

    return Math.sqrt( ( (operations * (double) response2) -
                        ( (double) response * (double) response) ) /
                      (operations * (operations - 1) ) ) / 1000.0 ;
  }

  public double rateStd()
  {
    if (op_count <= 1 || op_rate == 0)
      return 0;

    // here I need to count the number of intervals, not the operations?

    //common.ptod("op_count: " + op_count + " " + op_rate);

    return Math.sqrt( ( (op_count * (double) op_rate2) -
                        ( (double) op_rate * (double) op_rate) ) /
                      (op_count * (op_count - 1) ) )  ;
  }
}
