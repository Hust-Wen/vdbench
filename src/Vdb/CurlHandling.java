package Vdb;

/*
 *
 * Copyright (c) 2000-2008 Sun Microsystems, Inc. All Rights Reserved.
 *
 */


import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import Utils.*;


/**
 * Cloud handling using curl.
 *
 */
public class CurlHandling implements Serializable
{
  private final static String c = "Copyright (c) 2000-2008 Sun Microsystems, Inc. " +
                                  "All Rights Reserved.";


  private String cloud_url  = null; //    = "http://bzs52-38m/object/auth/v1.0/export/zfs_objectstore";
  private String cloud_user = null; //       = "cloud";
  private String cloud_pwd  = null; //  = "fworks!";
  private String container  = null; //  "henk_container";

  private String stor_url     = null;
  private String auth_token   = null;

  public HashMap <String, String> file_map = new HashMap(1000);

  public CurlHandling(FsdEntry fsd)
  {
    cloud_url  = fsd.cloud_url;
    cloud_user = fsd.cloud_user;
    cloud_pwd  = fsd.cloud_pwd;
    container  = fsd.name;


  }

  public void getAuthorization()
  {
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -s -i %s/ -X LIST -H 'X-Auth-User: %s' -H 'X-Auth-Key: %s'",
             cloud_url, cloud_user, cloud_pwd);
    ocmd.execute();
    ocmd.printStderr();
    ocmd.printStdout();

    String rc = ocmd.getHttpCode();
    if (rc == null || !rc.equals("200 OK"))
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("failure getting authorization %s %s %s", cloud_url, cloud_user, "xxxpassword");
    }

    for (String line : ocmd.getStdout())
    {
      String[] split = line.split(" +");
      if (split[0].equalsIgnoreCase("X-Storage-Url:"))
        stor_url = split[1];
      else if (split[0].equalsIgnoreCase("X-Auth-Token:"))
        auth_token = "X-Auth-Token:" + split[1];

    }
    common.ptod("stor_url: " + stor_url);
    common.ptod("auth_token: " + auth_token);

  }

  public void listContainer()
  {
    common.ptod("listContainer: " + container);
    file_map = new HashMap(1000);
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -i -s -X GET -H %s %s%s", auth_token, stor_url, container);
    ocmd.execute();
    //ocmd.printStdout();

    String rc = ocmd.getHttpCode();
    //common.ptod("rc: " + rc);
    if (!rc.equals("200 OK") &&
        !rc.equals("204 No Content"))
    {
      common.ptod("There are %,d objects ", file_map.size());
      return;
    }

    /* These are the current objects in the container: */
    String[] lines = ocmd.getStdout();
    for (int i = 0; i < lines.length; i++)
    {
      String line = lines[i];
      if (line.length() != 0)
        continue;

      for (i++; i < lines.length; i++)
      {
        line = lines[i];
        file_map.put("/" + line, "/" + line);
      }
    }

    common.ptod("There are %,d objects ", file_map.size());
  }


  public void uploadFile(String fname)
  {
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -i -s  -X PUT -H Expect: -H %s %s%s%s -T %s",
             auth_token, stor_url, container, fname, fname);
    ocmd.execute(false);
    String rc = ocmd.getHttpCode();

    if (!rc.equals("201 Created"))
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("uploadFile failed for file %s", fname);
    }
  }



  public void downloadFile(String fname)
  {
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -D-  -s  -X GET -H %s %s%s%s -o %s",
             auth_token, stor_url, container, fname, fname);
    ocmd.execute(true);
    String rc = ocmd.getHttpCode();

    if (!rc.equals("200 OK"))
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("downloadFile failed for file %s", fname);
    }
  }

  private void deleteFile(String fname)
  {
    //common.ptod("deleteFile: " + fname);
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -s -X DELETE -H %s %s/%s/%s", auth_token, stor_url, container, fname);
    ocmd.execute(false);

    if (!ocmd.getRC())
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("failure deleting object %s/%s/%s", stor_url, container, fname);
    }
  }

  public void createContainer()
  {
    common.ptod("createContainer: " + container);
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -i -s -X PUT -H %s %s%s", auth_token, stor_url, container);
    ocmd.execute(true);
    //ocmd.printStdout();

    String rc = ocmd.getHttpCode();
    //common.ptod("rc: " + rc);
    if (rc.equals("201 Created"))
      return;

    if (!ocmd.getRC())
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("failure creating container %s/%s", stor_url, container);
    }
  }

  public void deleteContainer()
  {
    common.ptod("deleteContainer: " + container);
    OS_cmd ocmd = new OS_cmd();
    ocmd.add("curl -i -s -X DELETE -H %s %s%s", auth_token, stor_url, container);
    ocmd.execute(true);
    //ocmd.printStdout();

    String rc = ocmd.getHttpCode();
    //common.ptod("rc: " + rc);
    if (rc.equals("404 Not Found") ||
        rc.equals("204 No Content"))
      return;

    if (!ocmd.getRC())
    {
      ocmd.printStderr();
      ocmd.printStdout();
      common.failure("failure deleting container %s/%s", stor_url, container);
    }
  }
}

