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

package edu.bonn.cs.iv.bonnmotion.models;

import java.io.*;

import edu.bonn.cs.iv.bonnmotion.*;

/** Application to construct static scenarios. */

public class Static extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("Static");
        info.description = "Application to construct static scenarios with no movement at all";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("BonnMotion Team");
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }

	protected int densityLevels = 1;

	public Static(int nodes, double x, double y, double duration, double ignore, long randomSeed, int densityLevels, double[] aFieldParams) {
		super(nodes, x, y, duration, ignore, randomSeed);
		this.densityLevels = densityLevels;
		this.parameterData.aFieldParams = aFieldParams;
		generate();
	}

	public Static( String[] args ) {
		go(args);
	}
	
	public void go( String[] args ) {
		super.go(args);
		generate();
	}

	public void generate() {
		preGeneration();

		if (! isTransition) {

			double dx = parameterData.x / (double)densityLevels;
			double dn = (double)parameterData.nodes.length / (double)densityLevels;
			int n = 0;
			for (int l = 1; l <= densityLevels; l++) {
				double hx;
				int hn;
				if (l == densityLevels) {
					hx = parameterData.x;
					hn = parameterData.nodes.length;
				} else {
					hx = dx * (double)l;
					hn = (int)(dn * (double)l + 0.5);
				}
				double xSave = parameterData.x;
				parameterData.x = hx;
				for (int i = n; i < hn; i++) {
					Position pos;
					do {
						pos = randomNextPosition();
					} while (pos.x > parameterData.x); // this may happen because of the attractor field
					if (! (parameterData.nodes[i] = new MobileNode()).add(0.0, pos))
						throw new RuntimeException(getInfo().name + ".go: error while adding waypoint");
				}
				parameterData.x = xSave;
				n = hn;
			}
		
		}
		postGeneration();
	}

	protected boolean parseArg(String key, String val) {
		if (key.equals("densityLevels")) {
			densityLevels = Integer.parseInt(val);
		} else return super.parseArg(key, val);
		return true;
	}

	public void write( String _name ) throws FileNotFoundException, IOException {
		String[] p = new String[1];
		p[0] = "densityLevels=" + densityLevels;
		super.writeParametersAndMovement(_name, p);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'l':
				densityLevels = Integer.parseInt(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}
	
	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		Scenario.printHelp();
		System.out.println( getInfo().name + ":" );
		System.out.println("\t-l <no. density levels>");
	}
}
