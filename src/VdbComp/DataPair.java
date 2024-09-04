package VdbComp;

/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Author: Henk Vandenbergh.
 */

import java.util.ArrayList;
import java.util.StringTokenizer;

import Vdb.common;


public class DataPair implements Comparable
{
  private final static String c =
  "Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.";

  String rd_name;
  CompRunData old_rd;
  CompRunData new_rd;


  public int compareTo(Object obj)
  {
    DataPair rd1 = (DataPair) this;
    DataPair rd2 = (DataPair) obj;

    String name1 = expand(rd1.rd_name);
    String name2 = expand(rd2.rd_name);
    return name1.compareToIgnoreCase(name2);
  }

  /**
   * An attempt to properly sort ascii numeric values, e.g. 16k vs 128k etc, but
   * something doesn't look healthy.
   * Would a simpler solution be to always replace a numeric piece inside of an
   * rd_name to a longer zero-leading ascii value, e.g. rdwrt1m becomes
   * rdwrt1048576?, or xfersize=131072?
   *
   * TBD.
  */
  public int compareTo_failed(Object obj)
  {
    DataPair rd1 = (DataPair) this;
    DataPair rd2 = (DataPair) obj;

    String name1 = rd1.rd_name;
    String name2 = rd2.rd_name;

    /* Split the whole RD label into pieces, using '-', ',' and '=': */
    String[] tokens1 = mySplit(name1);
    String[] tokens2 = mySplit(name2);

    /* As long as we have equal amounts of tokens, compare them: */
    for (int i = 0; i < tokens1.length && i < tokens2.length; i++)
    {
      String sp1 = tokens1[i];
      String sp2 = tokens2[i];

      /* If the token ends with 'k' or 'm', remove it when the rest is numeric: */
      if ((sp1.endsWith("k") && sp2.endsWith("k")) ||
          (sp1.endsWith("m") && sp2.endsWith("m")))
      {
        if (common.isNumeric(sp1.substring(0, sp1.length() - 1)))
          sp1 = sp1.substring(0, sp1.length() - 1);
        if (common.isNumeric(sp2.substring(0, sp2.length() - 1)))
          sp2 = sp2.substring(0, sp2.length() - 1);
      }

      /* When the ascii values are identical, keep on comparing: */
      if (sp1.compareTo(sp2) == 0)
        continue;

      /* If both tokens are numeric, immediately bail: */
      /* (Aboce compare could have compared 0010 with 010 and be unequal!) */
      if (common.isNumeric(sp1) && common.isNumeric(sp2))
      {
        long tail1 = (int) Long.parseLong(sp1);
        long tail2 = (int) Long.parseLong(sp2);
        if (tail1 == tail2)
          continue;
        else
          return(int) (tail1 - tail2);
      }

      /* See if any token ends with a number, and if both, compare: */
      Long tail1 = tail(sp1);
      Long tail2 = tail(sp2);
      if (tail1 == null || tail2 == null)
      {
        if (sp1.compareToIgnoreCase(sp2) == 0)
          continue;
        else
          return sp1.compareToIgnoreCase(sp2);
      }

      if (tail1 != null && tail2 != null)
      {
        if (tail1.longValue() == tail2.longValue())
          continue;
        else
          return(int) (tail1.longValue() - tail2.longValue());
      }

      //if (sp1.compareToIgnoreCase(sp2) == 0)
      //  continue;
      //else
      //  return(int) (tail1.longValue() - tail2.longValue());
    }

    return rd1.rd_name.compareToIgnoreCase(rd2.rd_name);
  }


  private static String[] mySplit(String str)
  {
    StringTokenizer st = new StringTokenizer(str, "-,=");
    ArrayList <String> tokens = new ArrayList( st.countTokens());

    while (st.hasMoreTokens())
      tokens.add(st.nextToken());

    return tokens.toArray(new String[0]);
  }

  private static Long tail(String str)
  {
    long    number       = 0;
    boolean any_numerics = false;
    int     i;
    for (i = str.length() - 1; i > 0; i--)
    {
      String one = str.substring(i, i+1);
      //common.ptod("one: >>>%s<<<", one);
      if (!common.isNumeric("" + str.charAt(i)))
        break;
      {
        any_numerics = true;
        number *= 10;
        number += Long.parseLong("" + str.charAt(i));

        if (str.equals("128"))
          common.ptod("tail: %-12s %8d %s", str, number, one);
      }
    }

    if (!any_numerics)
      return null;
    String two = str.substring(i+1);
    if (str.equals("128"))
      common.ptod("two: %-12s %s %4d", str, two, Long.parseLong(two));
    return Long.parseLong(two);

  }

  public static void main(String[] args)
  {
    String result = expand(args[0]);
    common.ptod("result: " + result);
  }

  /**
   * Expand an RD name to facilitate a mixed numeric/alpha sorting by expanding
   * any numeric found in the String to an eight digit zero-leading value
   * E.g.: rd rndwrt16k becomes rndwrt00016384
   *
   * This does NOT have to be an RD name, any string will do.
   */
  public static String expand(String input)
  {
    boolean debug = false;
    String data    = "";
    String number  = "";
    String mask    = "%08d";

    if (debug) common.ptod("arg: " + input);

    for (int i = 0; i < input.length(); i++)
    {
      char ch = input.charAt(i);
      if (ch < '0' || ch > '9')
      {
        if (number.length() > 0)
        {
          data += String.format(mask, Long.parseLong(number));
          number = "";
          if (debug) common.ptod("data0: " + data);
        }
        //common.ptod("alp: " + ch);
        data += ch;
        if (debug) common.ptod("data1: " + data);
      }
      else
      {
        number += ch;
        if (debug) common.ptod("number: " + number);

        /* If the next character is 'k' or 'm', etc. end the number: */

        /* This is the end, just take the value: */
        if (i+1 == input.length())
        {
          data += String.format(mask, Long.parseLong(number));
          number = "";
          if (debug) common.ptod("data2: " + data);
          break;
        }

        char next = input.charAt(i+1);
        long mult = 1;
        if (next == 'k') mult = 1024;
        else if (next == 'm') mult = 1024*1024;
        else if (next == 'g') mult = 1024*1024*1024;

        // Removed '8th' does not mean 8tb....
        //else if (next == 't') mult = 1024*1024*1024*1024l;
        else
        {
          //data += String.format("%05d", Long.parseLong(number));
          //common.ptod("data3: " + data);
          //number = "";
          continue;
        }

        //run1th_rh_lun

        data += String.format(mask, Long.parseLong(number) * mult);
        if (debug) common.ptod("data4: " + data);
        number = "";
        i++;
        continue;
      }
    }

    /* If we have a pending number, add it: */
    if (number.length() > 0)
    {
      data += String.format(mask, Long.parseLong(number));
      if (debug) common.ptod("data5: " + data);
    }

     return data;

  }
}
