package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

/**
 * operation=get processing.
 *
 * The objective is to select a file and give it to 'curl' so that the file can
 * be put on the cloud. Somewhere.
 */
class OpGetCloud extends FwgThread
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";

  private CurlHandling curl;

  public OpGetCloud(Task_num tn, FwgEntry fwg)
  {
    super(tn, fwg);
    curl = fwg.anchor.curl;
  }

  protected boolean doOperation()
  {
    /* First get a file to fiddle with: */
    while (!SlaveJvm.isWorkloadDone())
    {
      FileEntry fe = findFileToRead();
      if (fe == null)
        return false;

      if (curl.file_map.get(fe.getFullName()) == null)
      {
        block(Blocked.FILE_MUST_EXIST);
        fe.setUnBusy();
        continue;
      }

      long tod = Native.get_simple_tod();
      curl.downloadFile(fe.getFullName());

      FwdStats.count(Operations.GET, tod);

      fe.setUnBusy();
      return true;
    }

    return true;
  }
}

