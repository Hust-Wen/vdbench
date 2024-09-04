package VdbComp;

/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.File;
import java.util.*;

import Utils.Fget;
import Utils.Fput;
import Utils.Getopt;

import Vdb.common;

/**
 * Vdbench workload comparator.
 *
 * It obtains a set of 'old' and 'new' directories and reads all summary.html
 * and flatfile.html files. It compares several numbers and displays
 * them and the delta percentages in a JTable. Delta percentages are color
 * coded: green is good, red is bad.
 *
 */
public class WlComp
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  private static boolean dirs_in_parm = false;

  public static CompFrame frame  = null;

  public static Vector  <CompRunData>    old_rds     = null;
  public static Vector  <CompRunData>    new_rds     = null;
  public HashMap <String, String> all_keywords = null;

  public static  Getopt getopt       = null;
  public static  Vector <String> pos = null;

  public static String mondir = "http://sbm-240a.us.oracle.com/shares/export/swat/swat_monitor_data";

  public static ArrayList <String>   old_files = null;
  public static ArrayList <String>   new_files = null;

  public static ArrayList <String>   old_labels = null;
  public static ArrayList <String>   new_labels = null;

  public static ArrayList <FlatFile> old_flats = null;
  public static ArrayList <FlatFile> new_flats = null;


  /* RD names across Vdbench output directories used for 'old' or 'new' are */
  /* expected to be unique. That's how we do the match and merge.           */
  /* When using PDM types, this will be a requirement.                      */
  /* to be continued.... */


  /* When we can count on Run Definitions having a unique name we can just use */
  /* the RD name for match/merge old/new. */
  public static boolean add_parent = false;


  public static void main(String[] args) //throws ClassNotFoundException
  {
    getopt = new Getopt(args, "hvpo:d:i:", 999);
    getopt.print("Compare");
    if (!getopt.isOK())
      usage("Parameter scan errors");
    if (getopt.check('h'))
      usage("Help requested");
    if (getopt.get_positionals().size() < 2)
      usage("Expecting a minimum of two positional parameters");

    /* Read saved parameters: */
    StoredParms.loadParms();

    add_parent = getopt.check('p');

    /* For debugging or future option? */
    pos = getopt.get_positionals();
    if (pos.size() > 1)
      dirs_in_parm = true;

    WlComp    wlcomp = null;
    if (getopt.check('o'))
    {
      wlcomp = new WlComp();
      wlcomp.loadAllData();
      wlcomp.batchExport(getopt.get_string());
      return;
    }

    /* Set the frame to the same place and size as before: */
    wlcomp = new WlComp();
    frame  = new CompFrame(wlcomp);
    frame.setSize(StoredParms.last_width, StoredParms.last_height);
    frame.setLocation(StoredParms.last_x, StoredParms.last_y);

    /* If we already have directories immediately display: */
    frame.setVisible(true);
    if (dirs_in_parm)
      frame.doCompare(true);
  }

  private static void usage(String txt)
  {
    common.ptod("");
    common.ptod("");
    common.ptod(txt);
    common.ptod("");
    common.ptod("Usage: ./vdbench compare dir_old dir_new [-o tab_sep_file]");
    common.ptod("");
    common.ptod("       ./vdbench compare old olddir1 olddir2 ..  new newdir1 newdir2 ... [-o tab_sep_file]");
    common.ptod("");
    common.ptod("");
    common.failure(txt);
  }


  /**
   */
  private static String getFlatFile(String type, String number)
  {
    String topframe = String.format("%s/pdm/complete/%s/%05d/topframe.html",
                                    mondir, type,
                                    Integer.parseInt(number));

    String output = getOutput(topframe);
    if (output.endsWith("/summary.html"))
      output.replaceAll("/summary.html", "");

    if (!output.startsWith("http://"))
    {
      common.ptod("PDM %s/%s", type, number);
      common.ptod("Output directory name does not start with 'http': " + output);
      common.failure("Output directory either does not exist, or is improperly "+
                     "defined in the PDM translation table");
    }

    return output;
  }
  private static String getOutput(String topframe)
  {
    String[] lines = Fget.readFileToArray(topframe);

    for (String line : lines)
    {
      if (!line.contains("Output:"))
        continue;
      String output = line.substring(line.indexOf("<a href=") + 9);
      output = output.substring(0, output.indexOf("\""));
      if (output.endsWith("/summary.html"))
        output = output.substring(0, output.lastIndexOf("/"));
      return output;
    }

    if (lines == null)
      common.failure("File '%s' does not contain valid output directory", topframe);
    return null;
  }

  public void doCompare()
  {
    frame.waitCursor(frame);

    loadAllData();

    frame.waitCursor(frame);

    //frame.refresh();
  }



  /**
   * Load all data belonging to both directories
   */
  public void loadAllData()
  {
    old_files  = new ArrayList(8);
    new_files  = new ArrayList(8);

    old_labels = new ArrayList(8);
    new_labels = new ArrayList(8);

    old_flats  = new ArrayList(8);
    new_flats  = new ArrayList(8);

    /* PDM will pick up all it needs here: */
    /* vdbench compare old pdmtype output001 output002 new [pdmtype] output003 output004 */
    if (usePdmInfo())
    {
    }

    /* vdbench compare just.one.output: */
    else if (pos.size() == 2)
    {
      dirs_in_parm = true;
      old_files.add(pos.get(1));
      new_files.add(pos.get(1));
      old_labels.add(pos.get(1));
      new_labels.add(pos.get(1));
    }

    /* vdbench compare output001 output002 */
    else if (pos.size() == 3)
    {
      dirs_in_parm = true;
      old_files.add(pos.get(1));
      new_files.add(pos.get(2));

      old_labels.add(pos.get(1));
      new_labels.add(pos.get(2));
    }

    /* vdbench compare old output001 output002 new output003 output004 */
    else if (pos.size() > 3)
    {
      dirs_in_parm = true;
      boolean old_found = false;
      boolean new_found = false;
      int i = 1;
      for (; i < pos.size(); i++)
      {
        if (pos.get(i).equals("old"))
        {
          old_found = true;
          break;
        }
      }
      if (!old_found)
        common.failure("'old' argument not found");

      for (i++; i < pos.size(); i++)
      {
        if (pos.get(i).equals("new"))
        {
          new_found = true;
          break;
        }
        old_files.add(pos.get(i));
        old_labels.add(pos.get(i));
      }

      if (!new_found)
        common.failure("'new' argument not found");


      for (i++; i < pos.size(); i++)
      {
        new_files.add(pos.get(i));
        new_labels.add(pos.get(i));
      }
    }


    /* Sort the files to make sure we match old/new correctly: */
    Collections.sort(old_files);
    Collections.sort(new_files);

    common.ptod("old_files: " + old_files.size());
    common.ptod("new_files: " + new_files.size());

    /* Load all run data: */
    old_rds = new Vector(8, 0);
    for (int i = 0; i < old_files.size(); i++)
    {
      FlatFile ff = new FlatFile(old_labels.get(i), old_files.get(i));
      if (ParseData.parseFlatFile(ff, old_rds))
        old_flats.add(ff);
    }

    new_rds = new Vector (8, 0);
    for (int i = 0; i < new_files.size(); i++)
    {
      FlatFile ff = new FlatFile(new_labels.get(i), new_files.get(i));
      if (ParseData.parseFlatFile(ff, new_rds))
        new_flats.add(ff);
    }

    /* Pick up summary.html run descriptions for all flatfiles: */
    ParseData.parseSummary(old_flats);
    ParseData.parseSummary(new_flats);

    parseDescriptions();

    /* blank-separate arguments must ALL be in the RD name: */
    String search = CompFrame.search_value;
    String[] args = search.trim().split(" +");
    if (search.length() > 0)
    {
      for (int i = 0; i < old_rds.size(); i++)
      {
        CompRunData cd = old_rds.get(i);
        for (String arg : args)
        {
          if (!cd.rd_name.contains(arg))
            old_rds.set(i, null);
        }
      }
      while (old_rds.remove(null));
    }

    for (int i = 0; i < new_rds.size(); i++)
    {
      CompRunData cd = new_rds.get(i);
      for (String arg : args)
      {
        if (!cd.rd_name.contains(arg))
          new_rds.set(i, null);
      }
    }
    while (new_rds.remove(null));


    if (old_rds.size() == 0)
      throw new CompException("No valid runs found in flatfile.html");


    /* I now have 'n' old and new runs. Insert an 'average': */
    //if (old_rds.size() + new_rds.size() > 2)
    {
      insertAverages(old_rds);
      insertAverages(new_rds);
    }

    Collections.sort(old_rds);
    Collections.sort(new_rds);

    // This was written to help identify cases where the RD names are being reused.
    //countShortRdNames(old_rds);
    //countShortRdNames(new_rds);
  }


  private static void countShortRdNames(Vector <CompRunData> rds)
  {
    HashMap <String, Long> count_map = new HashMap(32);

    for (CompRunData rd : rds)
    {
      if (rd.average)
        continue;
      Long count = count_map.get(rd.rd_short);
      if (count == null)
        count = new Long(0);
      count_map.put(rd.rd_short, ++count);
    }

    String[] names = count_map.keySet().toArray(new String[0]);
    Arrays.sort(names);
    for (String name : names)
      common.ptod("Count: %-20s %3d", name, count_map.get(name));

  }

  /**
   * Pick up possible PDM info:
   * ./vdbench compare old pdm_type1 11 22 33 new [pdm_type2] 44 55 66
   */
  private static boolean usePdmInfo()
  {
    String type_old = null;
    String type_new = null;

    if (pos.size() > 3)
    {
      String index = String.format("%s/pdm/complete/%s/index.html", mondir, pos.get(2));
      String[] lines = Fget.readFileToArray(index);
      if (lines == null)
        return false;
      type_old = type_new = pos.get(2);
    }
    else
      return false;

    /*               0   1       2         3
    /* vdbench compare old pdmtype output001 output002 new pdmtype output003 output004 */
    dirs_in_parm = true;
    boolean old_found = false;
    boolean new_found = false;
    int i = 1;
    for (; i < pos.size(); i++)
    {
      if (pos.get(i).equals("old"))
      {
        old_found = true;
        break;
      }
    }
    if (!old_found)
      common.failure("'old' argument not found");

    for (i+=2; i < pos.size(); i++)
    {
      if (pos.get(i).equals("new"))
      {
        new_found = true;
        break;
      }
      String flat = getFlatFile(type_old, pos.get(i));
      old_files.add(flat);
      old_labels.add(String.format("%s/%05d", type_old, Integer.parseInt(pos.get(i))));
    }

    if (!new_found)
      common.failure("'new' argument not found");


    String index = String.format("%s/pdm/complete/%s/index.html", mondir, pos.get(i+1));

    String[] lines = Fget.readFileToArray(index);
    if (lines != null)
    {
      type_new = pos.get(i+1);
      i++;
    }

    for (i++; i < pos.size(); i++)
    {
      String flat = getFlatFile(type_new, pos.get(i));
      new_files.add(flat);
      new_labels.add(String.format("%s/%05d", type_new, Integer.parseInt(pos.get(i))));
    }

    return true;

  }

  private void insertAverages(Vector <CompRunData> rds)
  {
    /* First get a list of unique RD names: */
    HashMap <String, String> name_map = new HashMap(32);

    /* RD names either just are RD names, or have their parent directory */
    /* name prefixed, e.g. run10-1mseqwr/rd1 (using '-p' parameter)      */
    for (CompRunData rd : rds)
      name_map.put(rd.rd_name, rd.rd_name);


    for (String name : name_map.keySet())
    {
      ArrayList <CompRunData> detail = new ArrayList(8);
      CompRunData first = null;
      double iops  = 0;
      double resp  = 0;
      double mb    = 0;
      double sum_sq = 0;
      double resp_max    = 0;
      int    count = 0;
      String label = "";
      for (CompRunData rd : rds)
      {
        if (!rd.rd_name.equals(name))
          continue;
        if (first == null)
          first = rd;
        count++;
        double rate = (Double) rd.flatfile_data.get("rate");
        iops += (Double) rate;
        //common.ptod("iops: %12.3f %12.3f", rate, iops);
        resp += (Double) rd.flatfile_data.get("resp");
        mb   += (Double) rd.flatfile_data.get("mb/sec");
        sum_sq += (rate*rate);
        label += rd.label + " ";

        resp_max = Math.max(resp_max, (Double) rd.flatfile_data.get("resp_max"));

        detail.add(rd);
      }

      if (first == null)
        common.failure("not expecting a null rd");

      HashMap <String, Object> cloned = (HashMap <String, Object>) first.flatfile_data.clone();
      CompRunData avg  = new CompRunData("", cloned, null);
      avg.average      = true;
      avg.rd_name      = name;
      avg.flatfile_data.put("rate",   iops / count);
      avg.flatfile_data.put("resp",   resp / count);
      avg.flatfile_data.put("mb/sec", mb   / count);

      avg.rate_mean = iops / count;
      avg.rate_std  = Vdb.ownmath.stddev(count, sum_sq, iops);
      avg.relative_std  = avg.rate_std * 100 / avg.rate_mean;
      avg.resp_max      = resp_max;

      avg.detail_list = detail;
      rds.add(avg);
    }
  }

  private void matchDirectories(ArrayList <String> old_files,
                                ArrayList <String> new_files)
  {
    /* Sort the files to make sure we match old/new correctly: */
    Collections.sort(old_files);
    Collections.sort(new_files);

    if (old_files.size() + new_files.size() == 2)
      return;

    HashMap <String, String> old_parents = new HashMap(64);
    HashMap <String, String> new_parents = new HashMap(64);

    /* Create a parent entry for each directory: */
    for (int i = 0; i < old_files.size(); i++)
      old_parents.put(new File(old_files.get(i)).getParentFile().getName(), "x");
    for (int i = 0; i < new_files.size(); i++)
      new_parents.put(new File(new_files.get(i)).getParentFile().getName(), "x");


    for (int i = 0; i < old_files.size(); i++)
      common.ptod("old_files: " + old_files.get(i));
    for (int i = 0; i < new_files.size(); i++)
      common.ptod("new_files: " + new_files.get(i));

    common.ptod("");

    /* If the OLD directory is not in NEW, remove: */
    for (int i = 0; i < old_files.size(); i++)
    {
      String parent = new File(old_files.get(i)).getParentFile().getName();
      if (new_parents.get(parent) == null)
      {
        common.ptod("No new matching directory found, removing: " + old_files.get(i));
        old_files.set(i, null);
      }
    }


    /* if the NEW directory is not in OLD, remove: */
    for (int i = 0; i < new_files.size(); i++)
    {
      String parent = new File(new_files.get(i)).getParentFile().getName();
      if (old_parents.get(parent) == null)
      {
        common.ptod("No old matching directory found, removing: " + new_files.get(i));
        new_files.set(i, null);
      }
    }

    while (old_files.remove(null));
    while (new_files.remove(null));
  }


  /**
   * Parse run descriptions for each run.
   * Return a list of unique forxx values.
   */
  private HashMap parseDescriptions()
  {
    all_keywords = new HashMap();
    for (int i = 0; i < old_rds.size(); i++)
    {
      CompRunData run = old_rds.elementAt(i);
      run.parseForxxValues(all_keywords);
    }

    for (int i = 0; i < new_rds.size(); i++)
    {
      CompRunData run = new_rds.elementAt(i);
      run.parseForxxValues(all_keywords);
    }

    return all_keywords;
  }



  public static ArrayList <DataPair> createDataPairs(double hide)
  {

    ArrayList <DataPair> data_pairs = new ArrayList(1024);

    common.ptod("old_rds.size(): " + old_rds.size());
    common.ptod("new_rds.size(): " + new_rds.size());
    //for (CompRunData rd : old_rds)
    //  common.ptod("old: %-32s %b", rd.rd_name, rd.average);
    //for (CompRunData rd : new_rds)
    //  common.ptod("new: %-32s %b", rd.rd_name, rd.average);

    /* Create a HashMap, merging both old and new. */
    /* First add all 'old':                        */
    HashMap <String, DataPair> both_map = new HashMap(512);
    for (CompRunData rd : old_rds)
    {
      if (!rd.average)
        continue;
      DataPair dp = both_map.get(rd.rd_name + rd.label);
      dp          = new DataPair();
      dp.old_rd   = rd;
      dp.rd_name  = rd.rd_name;
      both_map.put(rd.rd_name + rd.label, dp);
      //common.ptod("dp.old_rd: " + rd.rd_name + rd.label);
    }

    /* Now add 'new', but merge old+new data: */
    for (CompRunData rd : new_rds)
    {
      if (!rd.average)
        continue;
      DataPair dp = both_map.get(rd.rd_name + rd.label);
      if (dp == null)
      {
        dp         = new DataPair();
        dp.new_rd  = rd;
        dp.rd_name = rd.rd_name;
        both_map.put(rd.rd_name + rd.label, dp);
      }
      else
        dp.new_rd = rd;
    }

    /* We now have a map with individual 'pairs' of data. */
    /* Change that into a list: */
    String[] keys = both_map.keySet().toArray(new String[0]);
    Arrays.sort(keys);
    for (String key : keys)
    {
      DataPair dp = both_map.get(key);
      data_pairs.add(dp);
    }

    /* Hide old+new+average runs with delta < 1: */
    if (hide != 0)
    {
      for (DataPair dp : data_pairs)
      {
        if (dp == null)
          continue;

        double delta_resp = DataModel.getDelta(dp.old_rd, dp.new_rd, "resp");
        double delta_rate = DataModel.getDelta(dp.old_rd, dp.new_rd, "rate");

        if (Math.abs(delta_resp) < hide && Math.abs(delta_rate) < hide)
          data_pairs.set(data_pairs.indexOf(dp), null);
      }
    }

    while (data_pairs.remove(null));

    common.ptod("Amount of data pairs: %d", data_pairs.size());

    return data_pairs;
  }



  /**
   * Batch Export, used both for batch and GUI.
   *
   * When file ends with 'csv', Excel thinks it is a CSV file. Duh!
   * So no longer depend on ending with csv which would be dangerous.
   */
  public void batchExport(String outfile)
  {
    ArrayList <DataPair> data_pairs = createDataPairs(0);
    Collections.sort(data_pairs);

    Fput fp = new Fput(outfile);
    String tmp = fp.println("rd\t" +
                            "old_resp\t" +
                            "new_resp\t" +
                            "delta_resp\t" +
                            "old_rate\t" +
                            "new_rate\t" +
                            "delta_rate\t" +
                            "old_mb\t" +
                            "new_mb\t" +
                            "delta_mb\t" +

                            "old_max\t" +
                            "new_max\t" +
                            "old_std\t" +
                            "new_std\t" +
                            "old_rstd\t" +
                            "new_rstd\t" +
                            "regress\t");

    for (DataPair dp : data_pairs)
    {
      CompRunData old_rd = dp.old_rd;
      CompRunData new_rd = dp.new_rd;

      String line = dp.rd_name;

      if (old_rd == null || new_rd == null)
      {
        common.ptod("Missing '%s' data for %s; skipped",
                    (old_rd == null) ? "old" : "new", dp.rd_name);
        continue;
      }

      line += "\t" + String.format("%.3f", old_rd.flatfile_data.get("resp"));
      line += "\t" + String.format("%.3f", new_rd.flatfile_data.get("resp"));
      line += "\t" + String.format("%s",   getDelta(old_rd, new_rd, "resp"));
      line += "\t" + String.format("%.3f", old_rd.flatfile_data.get("rate"));
      line += "\t" + String.format("%.3f", new_rd.flatfile_data.get("rate"));
      line += "\t" + String.format("%s",   getDelta(old_rd, new_rd, "rate"));
      line += "\t" + String.format("%.3f", old_rd.flatfile_data.get("mb/sec"));
      line += "\t" + String.format("%.3f", new_rd.flatfile_data.get("mb/sec"));
      line += "\t" + String.format("%s",   getDelta(old_rd, new_rd, "mb/sec"));

      line += "\t" + String.format("%s",   old_rd.resp_max);
      line += "\t" + String.format("%s",   new_rd.resp_max);
      line += "\t" + String.format("%.3f", old_rd.rate_std);
      line += "\t" + String.format("%.3f", new_rd.rate_std);
      line += "\t" + String.format("%.3f", old_rd.relative_std);
      line += "\t" + String.format("%.3f", new_rd.relative_std);

      if (old_rd != null && new_rd != null)
      {
        double res = old_rd.rate_mean - new_rd.rate_mean;
        res = res * 100 / old_rd.rate_mean;
        line += "\t" + String.format("%.3f", res);
      }
      else
        line += "\t";

      fp.println(line);
    }

    fp.close();
  }


  private static String getDelta(CompRunData old_run, CompRunData new_run, String label)
  {
    if (old_run.flatfile_data.get(label) == null)
      common.failure("can not find label: " + label);
    double old_val = ((Double) old_run.flatfile_data.get(label)).doubleValue();
    double new_val = ((Double) new_run.flatfile_data.get(label)).doubleValue();
    double delta    = old_val - new_val;

    if (label.equals("resp"))
      return String.format("%.1f%%", delta * 100. / old_val);
    else
      return String.format("%.1f%%", delta * 100. / old_val * -1);

  }
}

