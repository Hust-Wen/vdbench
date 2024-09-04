package Vdb;

/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.util.*;

import Utils.*;

/**
 * Network statistics to support curl get/put workload reporting.
 *
 * This for now is a hack. This will run locally on the Vdbench master because I
 * don't have the time right now to collect and accumulate the data when/if
 * received from multiple slaves.
 */
public class NwStats
{
  private final static String c =
  "Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.";

  private static ArrayList <NwAdapter> adapters = null;
  private static long                kstat_ctl_t = 0;

  public static long in_bytes = 7777;
  public static long ot_bytes = 7777;


  public static ArrayList <NwAdapter> getData()
  {
    return adapters;
  }

  /**
   * Find adapter names.
   * Cheap: just run kstat command, it is only called once anyway.
   */
  public static void getAdapters()
  {
    if (adapters != null)
      return;

    if (!common.onSolaris())
      common.failure("Network monitoring only available on Solaris at this time");

    adapters = new ArrayList(8);

    common.get_shared_lib();
    kstat_ctl_t = Utils.NamedKstat.kstat_open();

    /* Cheap: just run kstat command, it is only called once anyway. */
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("/usr/bin/kstat -m link -c net -s obytes64 -p");
    ocmd.execute(false);

    if (!ocmd.getRC())
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("No network adapters found");
    }

    NamedData.newInstance("Utils.NamedData/link");


    /* Scan through the 'kstat' output and pick up the adapter name. */
    /* At some time it may be nice to pick up the IP address somehow (ifconfig?) */
    for (String line : ocmd.getStdout())
    {
      String[] split = line.split(":");
      NwAdapter adap   = new NwAdapter();
      adap.name      = split[2];
      adapters.add(adap);


      /* Get the first set of data: */
      loadStatistics();
      //String data = Utils.NamedKstat.kstat_lookup_stuff(kstat_ctl_t, "link", adap.name);
      //if (data.startsWith("JNI"))
      //{
      //  common.ptod("data: " + data);
      //  common.failure("NwStats: no data found for network adapter " + adap.name);
      //}
      //else
      //  adap.old_data.parseNamedData(data);

      /* Only when we have at least one instance can we ask for index: */
      adap.index_ib  = adap.old_data.getAnchor().getIndexForField("rbytes64");
      adap.index_ob  = adap.old_data.getAnchor().getIndexForField("obytes64");

    }

    if (adapters.size() == 0)
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("No network adapters found");
    }
  }


  public static void loadStatistics()
  {
    for (NwAdapter adp : adapters)
    {
      String    data = NamedKstat.kstat_lookup_stuff(kstat_ctl_t, "link", adp.name);
      adp.cur_data   = NamedData.newInstance("Utils.NamedData/link");
      adp.cur_data.parseNamedData(data);
      adp.cur_data.setTime(System.currentTimeMillis());

      adp.dlt_data.delta(adp.cur_data, adp.old_data);

      adp.old_data = adp.cur_data;

      //common.ptod("bytes: %-8s %,12d %6d ", adp.name, adp.dlt_data.getCounters()[ adp.index_ob ],
      //            adp.dlt_data.getElapsed());
    }
  }

  public static long getOtbytes(ArrayList <NwAdapter> adapters)
  {
    long bytes = 0;

    for (NwAdapter adp : adapters)
      bytes += adp.dlt_data.getCounters()[ adp.index_ob ];

    return bytes;
  }

  public static long getInbytes(ArrayList <NwAdapter> adapters)
  {
    long bytes = 0;

    for (NwAdapter adp : adapters)
      bytes += adp.dlt_data.getCounters()[ adp.index_ib ];

    return bytes;
  }

  public static void main (String[] args)
  {
    getAdapters();

    for (int i = 0; i < 10; i++)
    {
      common.sleep_some(1000);
      loadStatistics();
    }

  }
}

class NwAdapter implements Serializable
{
  String name;
  long   prev_in  = 0;
  long   prev_ot  = 0;
  int    index_ib;
  int    index_ob;
  transient NamedData old_data  = NamedData.newInstance("Utils.NamedData/link");
  transient NamedData cur_data  = NamedData.newInstance("Utils.NamedData/link");
  NamedData dlt_data  = NamedData.newInstance("Utils.NamedData/link");

}

