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

/** Base class for those models that needs three parameters minimum speed, maximum speed maximum pause time. */

public abstract class RandomSpeedBase extends Scenario {
	protected double minspeed = 0.5;
	protected double maxspeed = 1.5;
	protected double maxpause = 60.0;

	public RandomSpeedBase(int nodes, double x, double y, double duration, double ignore, long randomSeed, double minspeed, double maxspeed, double maxpause) {
		this(nodes, x, y, 0.0, duration, ignore, randomSeed, minspeed, maxspeed, maxpause);
	}
	
	public RandomSpeedBase(int nodes, double x, double y, double z, double duration, double ignore, long randomSeed, double minspeed, double maxspeed, double maxpause) {
		super(nodes, x, y, z, duration, ignore, randomSeed);
		this.minspeed = minspeed;
		this.maxspeed = maxspeed;
		this.maxpause = maxpause;
	}
	
	public RandomSpeedBase() {}
	
	public void write(String basename, String[] params) throws FileNotFoundException, IOException {
		String[] p = new String[3];
		p[0] = "minspeed=" + minspeed;
		p[1] = "maxspeed=" + maxspeed;
		p[2] = "maxpause=" + maxpause;
		super.writeParametersAndMovement(basename, App.stringArrayConcat(params, p));
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'h': // "high"
				maxspeed = Double.parseDouble(val);
				return true;
			case 'l': // "low"
				minspeed = Double.parseDouble(val);
				return true;
			case 'p': // "pause"
				maxpause = Double.parseDouble(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}
	
	protected boolean parseArg(String key, String val) {
		if (key.equals("minspeed") ) {
			minspeed = Double.parseDouble(val);
			return true;
		} else if (	key.equals("maxspeed") ) {
			maxspeed = Double.parseDouble(val);
			return true;
		} else if (	key.equals("maxpause") ) {
			maxpause = Double.parseDouble(val);
			return true;
		} else return super.parseArg(key, val);
	}
	
	public static void printHelp() {
		Scenario.printHelp();
		System.out.println("RandomSpeedBase:");
		System.out.println("\t-h <max. speed>");
		System.out.println("\t-l <min. speed>");
		System.out.println("\t-p <max. pause time>");
	}
}
