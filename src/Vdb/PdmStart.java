package Vdb;

/*
 * Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.util.ArrayList;

import Utils.Fget;
import Utils.Fput;




/**
 * This class handles '*pdm' comments found in the parameter file.
 *
 *
 *  *pdm type        henk_test
 *  *pdm output      /var/tmp/output.tod
 *  *pdm keywords    key1 key2 key3
 *  *pdm clock_check 5
 *  *pdm server      a.b
 *  *pdm client      c.d
 *
 *
 * If any *pdm is found, but no servers or clients are defined, abort.
 * none found in the stub, abort.
 *
 * The runfile will be placed in the output directory.
 *
 *
 * Once all successful, insert a startcmd= and endcmd call to 'pdm start'.
 *
 *
 */
public class PdmStart
{
  private final static String c =
  "Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.";

  private static ArrayList <String> pdm_lines = new ArrayList(16);
  private static ArrayList <String> servers   = new ArrayList(16);
  private static ArrayList <String> clients   = new ArrayList(16);
  private static String keywords     = "";
  private static String type         = null;
  private static String output       = null;
  private static String clock_check  = "5";
  private static String system_check = "y";

  /* I don't like having this hardcoded, but its the best I can do. */
  /* As override I'll allow '*pdm swat /x/y/z/swat' */
  private static String swat_home = "/net/sbm-240a.us.oracle.com/export/swat/swat";


  /**
   * The output directory name MAY have been changed because of output+ or
   * output.tod
   */
  public static void setOutputDir(String dir)
  {
    output = dir;
  }

  public static void setup()
  {
    if (pdm_lines.size() == 0)
      return;

    for (String line : pdm_lines)
    {
      String[] split = line.split(" +");
      if (split.length < 2)
        common.failure("PdmStart: unexpected input: " + line);

      if (split[0].equals("client"))
      {
        for (int i = 1; i < split.length; i++)
          clients.add(split[i]);
      }

      else if (split[0].equals("server"))
      {
        for (int i = 1; i < split.length; i++)
          servers.add(split[i]);
      }

      else if (split[0].equals("keywords"))
      {
        split = line.split(" +", 2);
        keywords += split[1] + " ";
      }

      else if (split[0].equals("type"))
        type = split[1];

      /* Output was determined earlier, see lookForOutputDir() */
      else if (split[0].equals("output"))
      {
      }

      else if (split[0].equals("clock_check"))
        clock_check = split[1];

      else if (split[0].equals("system_check"))
        system_check = split[1];

      else if (split[0].equals("swat"))
        swat_home = split[1];

      else
        common.failure("PdmStart: unexpected *pdm input: " + line);
    }

    if (type == null)
      common.failure("PdmStart: 'type' parameter required. ");

    //for (String line : servers)
    //  common.ptod("line: >>>%s<<<", line);
    //for (String line : clients)
    //  common.ptod("line: >>>%s<<<", line);

    /* We must have at least one server/client: */
    if (clients.size() + servers.size() == 0)
      common.failure("PDM start requested, but no servers or clients specified");

    createSimpleRunfile();
  }


  /**
   * Us all available info to create a runfile inside of the output directory .
   */
  private static void createSimpleRunfile()
  {
    /* First create the runfile: */
    Fput fp = new Fput(output, "runfile");

    fp.println("%-12s %s", "type",         type);
    fp.println("%-12s %s", "keywords",     keywords);
    fp.println("%-12s %s", "output",       output);
    fp.println("%-12s %s", "clock_check",  clock_check);
    fp.println("%-12s %s", "system_check", system_check);

    for (String server : servers)
      fp.println("%-12s %s", "server", server);
    for (String client : clients)
      fp.println("%-12s %s", "client", client);
    fp.close();
    //fp.printFile("runfile");

    /* And now insert startcmd= and endcmd=:     */
    /* 'abort' tells Vdbench to abort if failed. */
    String pdmstart = swat_home;
    pdmstart += " pdm start -r " + fp.getName();
    Debug_cmds.starting_command.storeCommands(new String[] { pdmstart, "master", "cons", "abort"});

    String pdmend = swat_home;
    pdmend += " pdm end -r " + fp.getName();
    Debug_cmds.ending_command.storeCommands(new String[] { pdmend, "master", "cons", "abort"});
  }


  /**
   * Since working with Artis is a nightmare we've got to do the job ourselves.
   * Look for '*pdm output xxx' in the first parameter file and use that output
   * directory name for further processing.
   * The '-o' then will be ignored.
   *
   * At this time ONLY the first parmfile is scanned. If we find out that the
   * info can be in a second/third file we'll deal with that.
   */
  public static String lookForOutputDir(String[] args)
  {
    /* Look for the '-f parmfile'  or '-fparmfile': */
    String parmfile = null;
    for (int i = 0; i < args.length; i++)
    {
      String arg = args[i];
      if (!arg.startsWith("-f"))
        continue;
      if (arg.length() > 2)
      {
        parmfile = arg.substring(2);
        break;
      }

      if (args.length > i+1)
      {
        parmfile = args[i+1];
        break;
      }
    }

    /* If we did not find what we need, go do normal processing (which will fail later) */
    if (parmfile == null)
      return null;

    /* Same if the file does not exist: */
    if (!Fget.file_exists(parmfile))
      return null;

    /* Read the parameter file looking for what we need: */
    for (String line : Fget.readFileToArray(parmfile))
    {
      line = line.trim();
      if (!line.startsWith("*pdm"))
        continue;

      /* Remember for later use: */
      line = line.substring(5).trim();

      String[] split = line.split(" +");
      if (split.length < 2)
        continue;

      if (split[0].equals("output"))
        output = split[1];

      /* All lines (besides 'output') are stored for later use.            */
      /* Bypassing output allows me to specify an output directory WITHOUT */
      /* calling PDM!                                                      */
      else
        pdm_lines.add(line);
    }

    /* No luck: */
    return output;
  }

  public static void main(String[] args)
  {
    String output = lookForOutputDir(args);
    common.ptod("output: " + output);
  }
}

