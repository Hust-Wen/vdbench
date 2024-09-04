package Utils;

/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Vector;
import java.util.zip.*;

import Utils.Format;
import Vdb.common;

/**
 * This class reads data lines from an regular or GZIP file.
 */
public class Fget
{
  private final static String c =
  "Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.";

  private BufferedReader br          = null;
  private File           fptr        = null;
  private long           filesize    = 0;
  private long           bytesread   = 0;
  private long           early_eof   = Long.MAX_VALUE;
  private String         fname       = null;

  static String sep = System.getProperty("file.separator");


  /**
   * Open input file name
   */
  public Fget(String dir, String fname)
  {
    this(dir + sep + fname);
  }
  public Fget(String fname_in)
  {
    fname = fname_in;
    if (fname.endsWith("-"))
      br = new BufferedReader(new InputStreamReader(System.in));

    else
    {

      fptr = new File(fname);
      try
      {
        if (fptr.getName().toLowerCase().endsWith(".gz"))
          br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(fptr)))));
        else if (fptr.getName().toLowerCase().endsWith(".jz1"))
          br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(fptr)))));
        else
        {
          if (!fname.endsWith("stdin") && !fname.endsWith("_"))
          {
            br = new BufferedReader(new FileReader(fptr));
            filesize = fptr.length();
          }
          else
          {
            br = new BufferedReader(new InputStreamReader(new BufferedInputStream(System.in)));
          }
        }
      }


      catch (Exception e)
      {
        common.failure(e);
      }
    }
  }

  /**
   * Open input File pointer
   */
  public Fget(File fin)
  {

    try
    {
      fptr = fin;
      if (fptr.getName().toLowerCase().endsWith(".gz"))
        br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(fptr)))));
      else if (fptr.getName().toLowerCase().endsWith(".jz1"))
        br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(fptr)))));
      else
      {
        br = new BufferedReader(new FileReader(fptr));
        filesize = fptr.length();
      }
    }

    catch (Exception e)
    {
      common.failure(e);
    }
  }

  public void force_eof(long bytes)
  {
    early_eof = bytes;
  }


  public String getName()
  {
    return fname;
  }

  public static boolean fileRename(String parent, String oldname, String newname)
  {
    File oldf = new File(parent,oldname);
    File newf = new File(parent,newname);
    boolean ret = oldf.renameTo(newf);
    if (!ret)
      common.ptod("Fget.fileRename(): rename failed. " + oldf.getName() + " ===> " + newf.getName());
    return ret;
  }


  /**
   * Place a separator at the end of a directory name.
   */
  public static String separator(String dir)
  {
    /* Accept either an existing unix or windows separator: */
    if (!dir.endsWith("/") && !dir.endsWith("\\"))
      dir += sep;
    return dir;
  }

  /**
   * Return one line from input file
   */
  public String get()
  {
    /* Already EOF? */
    if (br == null)
      return null;

    try
    {
      String line = br.readLine();
      if (line == null)
      {
        close();
        return null;
      }

      bytesread += line.length();
      return line;
    }

    catch (Exception e)
    {
      e.printStackTrace();
      common.ptod("Exception with file name: " + getName());
      common.failure(e);
    }
    return null;
  }

  public String getNoEof()
  {
    /* Already EOF? */
    //if (br == null)
    //  return null;

    try
    {
      String line = br.readLine();
      if (line == null)
      {
        //close();
        return null;
      }

      bytesread += line.length();
      return line;
    }

    catch (Exception e)
    {
      e.printStackTrace();
      common.ptod("Exception with file name: " + getName());
      common.failure(e);
    }
    return null;
  }


  /**
   * Service routine to help read a (not too huge) flatfile into memory.
   */
  public static Vector read_file_to_vector(String parent, String filename)
  {
    return readTextFile(new File(parent, filename));
  }
  public static Vector read_file_to_vector(String filename)
  {
    return readTextFile(new File(filename));
  }
  public static String[] readFileToArray(String dir, String filename)
  {
    Vector <String> lines = readFile(dir, filename);
    if (lines == null)
      return null;
    return lines.toArray(new String[0]);
  }
  public static String[] readFileToArray(String filename)
  {
    Vector <String> lines = readFile(filename);
    if (lines == null)
      return null;
    return lines.toArray(new String[0]);
  }
  private static String[] obsolete_readFileToArray(File fptr)
  {
    Vector <String> lines = readTextFile(fptr);
    if (lines == null)
      return null;
    return lines.toArray(new String[0]);
  }


  private static Vector <String> readFile(String fname)
  {
    if (fname.startsWith("http://"))
      return readTextUrl(fname);
    else
      return readTextFile(new File(fname));
  }
  private static Vector <String> readFile(String dir, String fname)
  {
    if (dir.startsWith("http://"))
      return readTextUrl(dir, fname);
    else
      return readTextFile(new File(dir, fname));
  }

  private static Vector <String> readTextFile(File fptr)
  {
    Vector output = new Vector(64,0);
    if (!fptr.exists())
    {
      //common.plog("readFile(): file not found: " + fptr.getAbsolutePath());
      return null;
    }

    Fget fg = new Fget(fptr);

    /* Read all lines and store in vector: */
    while (true)
    {
      String line = fg.get();
      if (line == null)
        break;
      output.add(line);
    }
    fg.close();

    return output;
  }


  public static Vector <String> readTextUrl(String http, String file)
  {
    if (http.endsWith("/"))
      return readTextUrl(http + file);
    else
      return readTextUrl(http + "/" + file);
  }
  public static Vector <String> readTextUrl(String http)
  {
    //common.ptod("http: " + http);
    Vector <String> lines = new Vector(64);
    try
    {
      URL            url = new URL(http);
      BufferedReader in  = new BufferedReader(new InputStreamReader(url.openStream()));

      String inputLine;
      while ((inputLine = in.readLine()) != null)
        lines.add(inputLine);
      in.close();
    }

    catch (Exception e)
    {
      //common.ptod(e);
      return null;
    }

    return lines;
  }

  public String pct_read()
  {
    if (filesize == 0)
      return "n/a";
    return Format.f("%3d", bytesread * 100 / filesize);
  }

  public String get_parent()
  {
    return fptr.getAbsoluteFile().getParent();
  }


  public static String get_parent(String fname)
  {
    return new File(fname).getAbsoluteFile().getParent() + sep;
  }


  public void close()
  {
    //common.ptod("fget.close(): ");
    //Thread.currentThread().dumpStack();


    try
    {
      if (br != null)
      {
        br.close();
        br = null;
      }
    }
    catch (Exception e)
    {
      common.failure(e);
    }
  }

  /**
   * Check for existence of directory
   */
  public static boolean dir_exists(String dirname)
  {
    File dir = new File(dirname);
    if (!dir.exists())
      return false;
    return dir.isDirectory();
  }
  public static boolean dir_exists(String parent, String dirname)
  {
    File dir = new File(parent, dirname);
    if (!dir.exists())
      return false;
    return dir.isDirectory();
  }

  /**
   * Check to see if a file exists.
   * If the file exists, but it is a directory name, return false.
   */
  public static boolean file_exists(String parent, String fname)
  {
    //if (!parent.endsWith(sep))
    //  common.failure("Directory name not terminated by a separator: " + parent);

    boolean ret = new File(parent, fname).exists();
    if (ret)
      return (!dir_exists(parent, fname));
    return ret;
  }
  public static boolean file_exists(String fname)
  {
    boolean ret = new File(fname).exists();
    if (ret)
      return (!dir_exists(fname));
    return ret;
  }


  public static void file_delete(String fname)
  {
    new File(fname).delete();
  }
  public static void file_delete(String dir, String fname)
  {
    new File(dir, fname).delete();
  }




  public static void main(String[] args)
  {
    String http = "http://sbm-240a.us.oracle.com/shares/export/spp/87micro10/spp02/nfsv3_sbt-fill/output004/summary.html";
    String[] lines = readFileToArray(http);
    common.ptod("lines: " + lines.length);
  }

}




