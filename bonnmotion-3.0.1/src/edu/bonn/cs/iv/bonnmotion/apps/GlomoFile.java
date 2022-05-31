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

package edu.bonn.cs.iv.bonnmotion.apps;

import java.io.*;

import edu.bonn.cs.iv.bonnmotion.*;

/** Application that creates a movement file for Glomosim (2.0.3) and Qualnet (3.5.1). */
public class GlomoFile extends App {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("GlomoFile");
        info.description = "Application that creates a movement file for Glomosim (2.0.3) and Qualnet (3.5.1)";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	protected String name = null;
	protected boolean qualnet = false;

	public GlomoFile(String[] args) {
		go( args );
	}

	public void go( String[] args ) {
		parse(args);

		Scenario s = null;
		if ( name == null ) {
			printHelp();
			System.exit(0);
		}
		
		try {
			s = Scenario.getScenario(name);
		} catch (Exception e) {
			App.exceptionHandler( "Error reading file", e);
		}

		MobileNode[] node = s.getNode();
		
		PrintWriter placements = openPrintWriter(name + ".glomo_nodes");
		PrintWriter movements = openPrintWriter(name + ".glomo_mobility");

		for (int i = 0; i < node.length; i++) {
			int idx = qualnet ? i + 1 : i;
			String[] m = node[i].movementStringGlomo("" + idx);
			for (int j = 0; j < m.length; j++)
				movements.println(m[j]);

			String m2 = node[i].placementStringGlomo("" + idx);
			placements.println(m2);
		}
		movements.close();
		placements.close();
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'f':
				name = val;
				return true;
			case 'q':
				qualnet = true;
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
        System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("GlomoFile:");
		System.out.println("\t-f <filename>");
		System.out.println("\t-q [ QualNet mode: node IDs start at index 1 ]\n");
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		new GlomoFile(args);
	}
}
