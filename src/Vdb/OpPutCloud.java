package Vdb;

/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

/**
 * operation=put processing.
 *
 * The objective is to select a file and give it to 'curl' so that the file can
 * be put on the cloud. Somewhere.
 */
class OpPutCloud extends FwgThread
{
  private final static String c =
  "Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.";


  private CurlHandling curl;

  public OpPutCloud(Task_num tn, FwgEntry fwg)
  {
    super(tn, fwg);
    curl = fwg.anchor.curl;
  }

  protected boolean doOperation()
  {

    /* First get a file to fiddle with: */
    FileEntry fe = findFileToRead();
    if (fe == null)
    {
      common.where();
      return false;
    }


    long tod = Native.get_simple_tod();
    curl.uploadFile(fe.getFullName());

    curl.file_map.put(fe.getFullName(), fe.getFullName());

    FwdStats.count(Operations.PUT, tod);

    fe.setUnBusy();

    return true;
  }
}

