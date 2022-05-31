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

package edu.bonn.cs.iv.util;

import java.io.*;
import java.util.Vector;

public class GeometricMeanCalculator {
	public final double AGGR_THRESHOLD = 100.;
	public final boolean debug = false;
	
	protected static int next_iid = 0;
	protected int iid;

	protected Vector<Double> s = new Vector<Double>();
	
	protected int n = -1; // number of samples aggregated
	protected double currentProduct = 1.; // current product
	protected int numberOfSamplesInP = 0; // number of samples in p
	protected int totalSamples = 0;
	
	protected double x = 0.; // current exponent
	
	public GeometricMeanCalculator() {
		iid = next_iid++;
	}
	
	public void add(double v) {
		if (debug) System.err.println("GEODEBUG("+iid+") add "+v);
		if (v < 0) {
			System.err.println("GeometricMeanCalculator.add: negative v="+v);
			System.exit(0);
		}
		if (Double.isNaN(v)) {
			System.err.println("GeometricMeanCalculator.add: NaN (1)");
			System.exit(0);
		}
		currentProduct *= v;
		if (Double.isNaN(currentProduct)) {
			System.err.println("GeometricMeanCalculator.add: NaN (2)");
			System.exit(0);
		}
		numberOfSamplesInP++;
		totalSamples++;
		if ((n < 0) && (numberOfSamplesInP > 1) && (currentProduct > AGGR_THRESHOLD)) {
			if (debug) System.err.println("GEODEBUG("+iid+") aggregation size " + numberOfSamplesInP);
			n = numberOfSamplesInP;
			x = 1./(double)n;
		}
		if ((n > 0) && (numberOfSamplesInP == n)) {
			double pp = Math.pow(currentProduct, x);
			if (Double.isNaN(pp)) {
				System.err.println("GeometricMeanCalculator.add: NaN (3) p="+currentProduct+" x="+x);
				System.exit(0);
			}
			s.addElement(new Double(pp));
			numberOfSamplesInP = 0;
			currentProduct = 1.;
		}
	}

	public double result() {
		@SuppressWarnings("unchecked")
		Vector<Double> src = (Vector<Double>)s.clone();
		if (x > 0.)
			src.addElement(new Double(Math.pow(currentProduct, x)));
		else {
			if (src.size() > 0) {
				System.err.println("GeometricMeanCalculator.result: wrong vector size");
				System.exit(0);
			}
			src.addElement(new Double(Math.pow(currentProduct, 1./(double)n)));
		}
		Vector<Double> t = null;
		int exp1 = n;
		while (src.size() > 1) {
			if (debug) System.err.println("GEODEBUG("+iid+") loopstart src.size()="+src.size());
			t = new Vector<Double>();
			double p = 1.;
			int n = -1; // number of values we aggregate in this run
			int c = 0;  // number of values multiplied in p
			double x = 0.; // current exponent
			for (int i = 0; i < src.size(); i++) {
				double v = src.elementAt(i).doubleValue();
				p *= v;
				c++;
				if ((n < 0) && (c > 1) && ((p > AGGR_THRESHOLD) || (i == src.size() - 1))) {
					n = c;
					x = 1./(double)n;
					exp1 *= n;
				}
				if (((n > 0) && (c == n)) || (i == src.size() - 1)) {
					if (Double.isNaN(p)) {
						System.err.println("GeometricMeanCalculator.result: NaN (1)");
						System.exit(0);
					}
					p = Math.pow(p, x);
					if (Double.isNaN(p)) {
						System.err.println("GeometricMeanCalculator.result: NaN (2)");
						System.exit(0);
					}
					t.addElement(new Double(p));
					c = 0;
					p = 1.;
				}
			}
			if (src.size() == t.size()) {
				System.err.println("GeometricMeanCalculator.result: wrong vector sizes");
				System.exit(0);
			}
			src = t;
		}
		double r = src.elementAt(0).doubleValue();
		r = Math.pow(r, (double)exp1/(double)totalSamples);
		return r;
	}
	
	public int count() {
		return totalSamples;
	}
	
	public static void main(String[] args) throws java.io.IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String line;
		GeometricMeanCalculator inst = new GeometricMeanCalculator();
		while ((line = in.readLine()) != null) {
			double d = Double.parseDouble(line);
			if (d >= 0.)
				inst.add(d);
			else
				System.err.println("GeometricMeanCalculator.main: ignoring negative value "+d);
		}
		System.out.println(inst.result());
	}
}
