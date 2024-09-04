package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

import Utils.CommandOutput;
import Utils.Fget;
import Utils.OS_cmd;


/**
 * This class handles the parmfile start_cmd= and end_cmd= parameters
 */
class Debug_cmds implements Serializable, Cloneable
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  private Vector <String> commands = new Vector(1);
  private String target   = "log";
  private boolean master  = false;
  private boolean abort   = false;
  private HashMap <String, String>  hosts   = new HashMap(8);

  public static Debug_cmds starting_command = new Debug_cmds();
  public static Debug_cmds ending_command   = new Debug_cmds();


  public Debug_cmds storeCommands(String[] parms)
  {
    /* rd=default must CLEAR the list of commands, making this a REPLACE, not an ADD: */
    if (this == RD_entry.dflt.start_cmd || this == RD_entry.dflt.end_cmd)
      commands.clear();

    for (int i = 0; i < parms.length; i++)
    {
      String parm = parms[i];
      if (parm.startsWith("cons") || parm.startsWith("sum") || parm.startsWith("log"))
        target = parm;
      else if (parm.equalsIgnoreCase("master"))
        master = true;
      else if (parm.equalsIgnoreCase("slave"))
        master = false;
      else if (parm.equalsIgnoreCase("abort"))
        abort = true;
      else if (Host.isHostKnown(parm))
        hosts.put(parm, parm);
      else
      {
        parm = common.replace_string(parm, "$output", Vdbmain.output_dir);
        commands.add(parm);
      }
    }

    return this;
  }


  public Object clone()
  {
    try
    {
      Debug_cmds dc = (Debug_cmds) super.clone();
      dc.commands   = (Vector) commands.clone();
      return dc;
    }
    catch (Exception e)
    {
      common.failure(e);
    }
    return null;
  }


  /**
   * Run the requested command.
   *
   * When the command issued does not write stderr/stdout, for instance it pipes
   * its output to a file, the process started here will not react properly to the
   * OS_cmd.killCommand() method.
   *
   * Maybe we should redirect it to a new file in here instead of writing it to
   * either of the cons/sum/log files?
   * Maybe introduce an extra 'else' branch assuming that this is a file name that
   * can be opened at the first newLine()????
   * This file name then in turn can be linked to from summary.html.
   *
   * Done. 10/14/08
   */
  public boolean run_command()
  {
    /* We execute on slave, unless specifically requested to run on master: */
    if (SlaveJvm.isThisSlave() && master)
      return true;

    for (String use_command : commands)
    {
      String[] split      = use_command.split(" +", 2);
      String   prefix     = split[0];
      String   suffix     = (split.length > 1) ? split[1] : "";
      String   found_file = common.findscript(prefix);

      /* If the command exists, leave it alone, otherwise use what we found:    */
      /* If it does not exist, then just execute and have the user deal with it */
      if (!Fget.file_exists(prefix) && found_file != null)
        prefix = found_file;

      /* We can now rebuild the command: */
      use_command = prefix + " " + suffix;

      common.ptod("Start/end command: executing '" + use_command + "'");
      if (SlaveJvm.isThisSlave())
        SlaveJvm.sendMessageToConsole("Start/end command: executing '" + use_command + "'");

      OS_cmd ocmd = new OS_cmd();
      ocmd.addText(use_command);

      ocmd.setOutputMethod(new CommandOutput()
                           {
                             public boolean newLine(String line, String type, boolean more)
                             {
                               /* Note that the ':' here causes output to go to master's console: */
                               /* (not longer sure what that means.... */
                               if (type.equals("stdout"))
                               {
                                 if (target.startsWith("cons"))
                                 {
                                   if (SlaveJvm.isThisSlave())
                                     SlaveJvm.sendMessageToConsole("Cmd: %s", line);
                                   else
                                     common.ptod("Cmd: %s", line);
                                 }

                                 else if (target.startsWith("sum"))
                                   common.psum("Cmd: %s ", line);

                                 else if (target.startsWith("log"))
                                   common.plog("Cmd: %s", line);

                                 else
                                   common.failure("Invalid target: " + target);
                               }
                               else
                               {
                                 if (target.startsWith("cons"))
                                 {
                                   if (SlaveJvm.isThisSlave())
                                     SlaveJvm.sendMessageToConsole("Cmd: stderr %s", line);
                                   else
                                     common.ptod("Cmd: stderr %s", line);
                                 }

                                 else if (target.startsWith("sum"))
                                   common.psum("Cmd: stderr %s", line);

                                 else if (target.startsWith("log"))
                                   common.plog("Cmd: stderr %s", line);

                                 else
                                   common.failure("Invalid target: " + target);
                               }

                               return true;
                             }
                           });

      ocmd.execute(true);

      if (abort && !ocmd.getRC())
        common.failure("Startcmd= or endcmd= call failed. Abort has been requested.");
    }

    return true;
  }

  public boolean masterOnly()
  {
    return master;
  }
}
