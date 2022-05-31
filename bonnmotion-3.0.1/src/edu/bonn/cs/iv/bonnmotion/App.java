/*******************************************************************************
 ** BonnMotion - a mobility scenario generation and analysis tool             **
 ** Copyright (C) 2002-2012 University of Bonn                                **
 ** Copyright (C) 2012-2016 University of Osnabrueck                          **
 **                                                                           **
 ** This program is free software; you can redistribute it and/or modify      **
 ** it under the terms of the GNU General Public License as published by      **
 ** the Free Software Foundation; either version 2 of the License, or         **
 ** (at your option) any later version.                                       **
 **                                                                           **
 ** This program is distributed in the hope that it will be useful,           **
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of            **
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             **
 ** GNU General Public License for more details.                              **
 **                                                                           **
 ** You should have received a copy of the GNU General Public License         **
 ** along with this program; if not, write to the Free Software               **
 ** Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA **
 *******************************************************************************/

package edu.bonn.cs.iv.bonnmotion;

import java.io.*;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Base class for all applications and all scenario generators.
 */
public abstract class App {

	private static boolean printStrace = false;


	protected void parse(String[] args) {
		String a = null;
		for (int i = 0; i < args.length; i++) {
			if ((args[i].charAt(0) == '-') && Character.isLetter(args[i].charAt(1)) && (a != null)) {
				parseArg(a);
				a = null;
			}
			if ((a == null) && (args[i].charAt(0) != '-')) {
				System.out.println("warning: ignoring argument \"" + args[i] + "\"");
			} else {
				if (a == null)
					a = args[i];
				else
					a = a + " " + args[i];
			}
		}
		if (a != null)
			parseArg(a);
	}

	protected boolean parseArg(String a) {
		char key = a.charAt(1);
		String val = a.substring(2);
		while ((val.length() > 0) && (val.charAt(0) == ' '))
			val = val.substring(1);
		return parseArg(key, val);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'D' :
				printStrace = true;
				return true;
			default:
				return false;
		}
	}

	/**
	 * Tries to catch all exceptions and to display them in a user friendly manner.
	 * @param _msg a user defined msg (e.a. the place where the exceptions occurs)
	 * @param _e the Exception
	 */
	public static void exceptionHandler( String _msg, Exception _e ) {
		System.out.println();
		System.out.println( "Error:" );
		System.out.println( "\t"+_msg );
		
		if ( _e.getClass() == FileNotFoundException.class )
			System.out.println( "\t"+((FileNotFoundException)_e).getMessage() );

		if ( _e.getClass() == NumberFormatException.class )
			System.out.println( "\tArgument "+_e.getMessage() + " is not a number." );

		if ( _e.getClass() == RuntimeException.class )
			System.out.println( "\t"+_e.getMessage() );
						
		if ( printStrace ) {
			System.err.println();
			_e.printStackTrace();
		}
			
		System.exit(0);
	}

	/**
	 * Main method from where all the magic starts ...
	 */
	public abstract void go(String[] args) throws FileNotFoundException, IOException;


	/**
	 * Writes the scenario data to a file.
	 * @param filename Filename
	 */
	public static PrintWriter openPrintWriter(String filename) {
		PrintWriter f = null;
		try {
			f = new PrintWriter(new FileOutputStream(filename));
		} catch (Exception e) {
			exceptionHandler( "Error opening " + filename, e);
		}
		return f;
	}
	
	/**
	 * Converts a String to an int array.
	 * @return new int array
	 */
	public static int[] parseIntArray(String arg) {
		StringTokenizer st = new StringTokenizer(arg, ",: ");
		Vector<Integer> rs = new Vector<Integer>();
		while (st.hasMoreTokens()) {
			rs.addElement(new Integer(Integer.parseInt(st.nextToken())));
		}
		int[] result = new int[rs.size()];
		for (int j = 0; j < result.length; j++)
			result[j] = rs.elementAt(j).intValue();
		return result;
	}

	/**
	 * Converts a String to a double array.
	 * @return new double array
	 */
	public static double[] parseDoubleArray(String arg) {
		StringTokenizer st = new StringTokenizer(arg, ",: ");
		Vector<Double> rs = new Vector<Double>();
		while (st.hasMoreTokens()) {
			rs.addElement(new Double(Double.parseDouble(st.nextToken())));
		}
		double[] result = new double[rs.size()];
		for (int j = 0; j < result.length; j++)
			result[j] = rs.elementAt(j).doubleValue();
		return result;
	}

	/**
	 * Converts a String to a String array.
	 * @return new double array
	 */
	public static String[] parseStringArray(String arg) {
		StringTokenizer st = new StringTokenizer(arg, ",: ");
		Vector<String> rs = new Vector<String>();
		while (st.hasMoreTokens()) {
			rs.addElement(st.nextToken());
		}
		return rs.toArray(new String[0]);
	}

	public static void printHelp() {
		System.out.println( "App:" );
		System.out.println( "\t-D print stack trace" );
	}

	/**
	 * Concatenates two string arrays
	 * @param a first array
	 * @param b first array
	 * @return new and concated string array
	 */
	public static String[] stringArrayConcat(String[] a, String[] b) {
		String[] c = new String[((a != null) ? a.length : 0) + ((b != null) ? b.length : 0)];
		if (a != null)
			System.arraycopy(a, 0, c, 0, a.length);
		if (b != null)
			System.arraycopy(b, 0, c, (a != null) ? a.length : 0, b.length);
		return c;
	}

}
