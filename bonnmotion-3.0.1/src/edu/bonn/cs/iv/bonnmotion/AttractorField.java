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

import java.util.Vector;

public class AttractorField {
	class Attractor {
		public final Position pos;
		public final double level;
		public final double stdDev;
		public final double stdDev2;

		public Attractor(Position pos, double level, double stdDev) {
			this.pos = pos;
			this.level = level;
			this.stdDev = stdDev;
			this.stdDev2 = stdDev;
		}

		public Attractor(Position pos, double level, double stdDev, double stdDev2) {
			this.pos = pos;
			this.level = level;
			this.stdDev = stdDev;
			this.stdDev2 = stdDev2;
		}	        
	}

	protected Vector<Attractor> attractors = new Vector<Attractor>();
	/** Sum over all level-values. */
	protected double lTotal = 0.0;

	protected final double x;
	protected final double y;

	public AttractorField(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public void add(Position attractor, double level, double stdDev) {
		attractors.addElement(new Attractor(attractor, level, stdDev));
		lTotal += level;
	}

	public void add(Position attractor, double level, double stdDev, double stdDev2) {
		attractors.addElement(new Attractor(attractor, level, stdDev, stdDev2));
		lTotal += level;
	}

	public void add(double[] param) {
		for (int p = 0; p < param.length; p += 5)
		{
			if (param.length - p >= 5)
				add(new Position(param[p], param[p+1]), param[p+2], param[p+3], param[p+4]);
			else
				System.out.println("warning: attraction field argument list has wrong number of elements!");
		}
	}

	public Position getPosJunk(double rndUniform1, double rndUniform2, double rndGaussian) {
		double r = rndUniform1 * lTotal;
		double s = 0.0;
		Attractor a = null;
		int i = 0;
		while ((i < attractors.size()) && (r >= s)) {
			a = attractors.elementAt(i++);
			s += a.level;
		}
		if ((r >= s) || (a == null)) {
			System.out.println("AttractorField.getPos: Somethings going wrong here");
			System.exit(0);
		}
		double dir = Math.PI * rndUniform2; // only half circle cause we can have negative distance as well
		double dist = rndGaussian * a.stdDev;
		Position rVal = new Position(a.pos.x + Math.cos(dir) * dist, a.pos.y + Math.sin(dir) * dist);
		if ((rVal.x >= 0.0) && (rVal.y >= 0.0) && (rVal.x <= x) && (rVal.y <= y))
			return rVal;
		else
			return null;
	}

	public Position getPos(double rndUniform, double rndGaussian1, double rndGaussian2) {
		double r = rndUniform * lTotal;
		double s = 0.0;
		Attractor a = null;
		int i = 0;
		while ((i < attractors.size()) && (r >= s)) {
			a = attractors.elementAt(i++);
			s += a.level;
		}
		if ((r >= s) || (a == null)) {
			System.out.println("AttractorField.getPos: Somethings going wrong here");
			System.exit(0);
		}
		Position rVal = new Position(a.pos.x + rndGaussian1 * a.stdDev, a.pos.y + rndGaussian2 * a.stdDev2);
		if ((rVal.x >= 0.0) && (rVal.y >= 0.0) && (rVal.x <= x) && (rVal.y <= y))
			return rVal;
		else
			return null;
	}
}
