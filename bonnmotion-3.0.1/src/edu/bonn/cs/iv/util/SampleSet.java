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

import java.util.Vector;

public class SampleSet {
	protected Vector<Double> s = new Vector<Double>();
	protected double sum = 0.0;
	protected double min = Double.MAX_VALUE;
	protected double max = Double.MIN_VALUE;

	protected int getPos(double val) {
		int idxLo = 0, idxHi = s.size();
		while (idxLo < idxHi) {
			int idxTest = (idxLo + idxHi) / 2;
			double tst = s.elementAt(idxTest).doubleValue();
			if (val > tst)
				idxLo = idxTest + 1;
			else //if (val < tst)
				idxHi = idxTest - 1;
		}
		return idxLo;
	}

	public void add(double val) {
		s.insertElementAt(new Double(val), getPos(val));
		sum += val;
		if (val < min)
			min = val;
		if (val > max)
			max = val;
	}

	public int size() {
		return s.size();
	}

	public double avg() {
		double a = sum / (double)s.size();
		if (a < min)
			a = min;
		else if (a > max)
			a = max;
		return a;
	}

	public double min() {
		return min;
	}
	
	public double max() {
		return max;
	}

	public double conf95delta() {
		double avg = avg();
		double st = 0.0;
		for (int i = 0; i < s.size(); i++) {
			double tmp = avg - s.elementAt(i).doubleValue();
			st += tmp * tmp;
		}
		st = Math.sqrt(st / (double)(s.size() - 1));
		return 1.96 * st / Math.sqrt((double)s.size());
	}
	
	public double quantile(double q) {
		return ((Double)s.elementAt((int)(q * (double)(s.size() - 1)))).doubleValue();
	}
	
	public double fractionGreaterThan(double val) {
		return (double)(s.size() - getPos(val)) / (double)s.size();
	}
}
