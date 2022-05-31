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
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

public class IntervalFormat extends App {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("IntervalFormat");
        info.description = "Application that converts scenario file into interval format";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Raphael Ernst");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	protected static final String filesuffix = ".if";
        
	protected String name = null;
	protected double intervalLength = 1.0;
	protected boolean skipHead = false;

	public IntervalFormat(String[] args) {
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
		
		PrintWriter out = openPrintWriter(name + filesuffix);
		Dimension outputDim = s.getScenarioParameters().outputDim;
		
		if(!skipHead) {
            out.println("#X " + s.getX());
            out.println("#Y " + s.getY());
            if (outputDim == Dimension.THREED){
            	out.println("#Z " + s.getZ());
            }
            out.println("#Nodes " + s.nodeCount());
            out.println("#Duration " + s.getDuration());
            
            for(int i = 0; i < node.length; i++) {
                String nodeMovementString = node[i].movementString(outputDim);
                out.println("#Waypoints node " + i + ": " + nodeMovementString);
            }
            out.println("#Node Time X Y" + (outputDim == Dimension.THREED ? " Z" : ""));
        }
	    
		double duration = Math.ceil(s.getDuration());
	    
        for(int i = 0; i < node.length; i++){
            double t = 0.0;
            while(t < duration + 1.0){
                Position p = node[i].positionAt(t);
                out.println(i + " " + t + " " + p.x + " " + p.y + (outputDim == Dimension.THREED ? " "+p.z : ""));
                t += intervalLength;
            }
        }
        
		out.close();
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
		    case 'f':
		    	this.name = val;
		    	return true;
		    case 's':
		    	this.skipHead = true;
		    	return true;
		    case 'l':
		    	this.intervalLength = Double.parseDouble(val);
		    	return true;
		    default:
		    	return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
        System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("IntervalFormat:");
		System.out.println("\t-f <filename> (Scenario)");
		System.out.println("\t-l <double> (Intervallength, Optional)");
		System.out.println("\t-s (Skip head of outputfile, Optional)");
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		new IntervalFormat(args);
	}
}
