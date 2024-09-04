package Vdb;

/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.File;
import java.util.ArrayList;

import Utils.Fput;
import Utils.Getopt;
import Utils.OS_cmd;


/**
 * Program to look for Vdbench Master JVMs with an options to shutdown.
 *
 * Dependency: need to run from a JDK to find 'jps'.
 *
 *
 * Usage:  ./vdbench kill         for listing
 *         ./vdbench kill all     kill all master JVMs
 *         ./vdbench kill 12345   kill master JVM 12345
 *
 *         ./vdbench kill -j /dir/where/jps/is/
 *
 */
public class VdbenchKill
{
  private final static String c =
  "Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.";

  private static String sep = File.separator;

  private static String tmpdir       = (common.onWindows()) ? Fput.getTmpDir() : "/tmp";
  private static String tmp_shutdown = new File(tmpdir, "vdbench.shutdown.").getAbsolutePath();

  public static void main(String[] args)
  {
    String current_process = common.getProcessIdString();
    File   jps_ptr    = null;
    String jps_target = System.getProperty("java.home");

    Getopt getopt = new Getopt(args, "j:-d", 1);
    if (!getopt.isOK())
      common.failure("Parameter parsing failed");

    /* Did user tell me where JPS is? */
    if (getopt.check('j'))
    {
      jps_target = getopt.get_string();
      jps_ptr = new File(jps_target);
    }

    /* We expect jps in the current java home: */
    else
    {
      if (common.onWindows())
      {
        if (jps_target.endsWith("jre"))
          jps_target = jps_target.replace("\\jre", "");

        jps_ptr = new File(jps_target + sep + "bin", "jps.exe");
        if (!jps_ptr.exists())
        {
          System.out.printf("Can not find file '%s'\n", jps_ptr.getAbsolutePath());
          common.failure("VdbenchKill only works when run from a JDK");
        }
      }

      else
      {
        if (jps_target.endsWith("jre"))
          jps_target = jps_target.replace("/jre", "");

        jps_ptr = new File(jps_target + sep + "bin", "jps");
        if (!jps_ptr.exists())
        {
          System.out.printf("Can not find file '%s'\n", jps_ptr.getAbsolutePath());
          common.failure("VdbenchKill only works when run from a JDK");
        }
      }
    }

    //if (jps_target.contains(" "))
    //  common.failure("VdbenchKill alas does not handle a jps '%s' with embedded blanks",
    //                 jps_target);

    /* Get process ids of Vdbmain instances: */
    ArrayList <String> masters = new ArrayList(8);
    ArrayList <String> slaves  = new ArrayList(8);
    OS_cmd ocmd = new OS_cmd();
    ocmd.addQuot(jps_ptr.getAbsolutePath());
    ocmd.execute(false);

    if (!ocmd.getRC())
    {
      printOutput(ocmd);
      common.failure("'%s' command failed", jps_target);
    }

    /* Loop through all we found: */
    for (String line : ocmd.getStdout())
    {
      String[] split = line.trim().split(" +");
      if (split.length != 2)
      {
        printOutput(ocmd);
        common.failure("Expecting two data fields in the outout");
      }

      //if (split[1].equals("SlaveJvm"))
      //  slaves.add(split[0]);
      if (!split[1].equals("Vdbmain"))
        continue;
      if (split[0].equals(current_process))
        continue;

      masters.add(split[0]);
    }

    if (masters.size() == 0)
    {
      System.out.println("No Vdbench master JVMs found");
      return;
    }


    /* No input, just list: */
    if (getopt.get_positionals().size() == 0)
    {
      System.out.println("\n");
      for (String pid : masters)
        System.out.printf("Vdbench Master JVM: %s\n", pid);
      System.out.println("");
      for (String pid : slaves)
        System.out.printf("Vdbench Slave JVM:  %s (Do not use this pid to kill) \n", pid);
      System.out.println("\n");
      return;
    }


    /* One input, not 'all': */
    String kill = getopt.get_positionals().get(0);
    if (!kill.equalsIgnoreCase("all"))
    {
      boolean found = false;
      for (String pid : masters)
      {
        if (pid.equals(kill))
          found = true;
      }

      if (!found)
      {
        System.out.printf("Can not find process-id %s to kill \n", kill);
        return;
      }

      String kill_file = tmp_shutdown + kill;
      Fput fp = new Fput(kill_file);
      fp.close();
      System.out.println("Sent 'shutdown' request to: " + kill_file);
      return;

    }

    /* One input, 'all': */
    for (String pid : masters)
    {
      String kill_file = tmp_shutdown + pid;
      Fput fp = new Fput(kill_file);
      fp.close();
      System.out.println("Sent 'shutdown' request to: " + kill_file);
    }

  }

  private static void printOutput(OS_cmd ocmd)
  {
    for (String line : ocmd.getStderr())
      System.out.println("stderr: " + line);

    for (String line : ocmd.getStdout())
      System.out.println("stdout: " + line);
  }

}
