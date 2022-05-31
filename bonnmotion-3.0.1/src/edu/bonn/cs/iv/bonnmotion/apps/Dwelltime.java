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
import java.awt.geom.Rectangle2D;

import edu.bonn.cs.iv.bonnmotion.*;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

/** Application that creates statistics how long nodes stay in which area of the simulated region. */
public class Dwelltime extends App {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("Dwelltime");
        info.description = "Application that analyses scenarios according to Bettstetter";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Elmar Gerhards-Padilla");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	protected String name = null;
	Double measures = new Double(0.5);
	Double timestep = new Double(0.5);

	public Dwelltime(String[] args) {
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
		
        if (s.getScenarioParameters().calculationDim == Dimension.THREED){
            System.err.println("No 3D Version implemented yet");
            System.exit(-1);
        } else  {
    		MobileNode[] node = s.getNode();
    		
    		double row = s.getX() / measures.doubleValue();
    		int rowint = (int)Math.ceil(row);
    		
    		double column = s.getY() / measures.doubleValue();
    		int columnint = (int)Math.ceil(column);
    		
    		System.out.println("Rows/Columns : "+row+"/"+column+"\n");

//    		System.out.println("DEBUG: rowint * columnint : "+rowint * columnint+"\n");
    		if (rowint*columnint <= 0) {
    			System.err.println("Too many square cells (integer overflow), please specify a larger cell length!");
                System.exit(-1);
    		}
    		
    		double[] squares = new double[rowint * columnint];
    		Rectangle2D.Double[] rects = new Rectangle2D.Double[rowint * columnint];
    		int count = 0;
    		
    		for(int i = 0; i < rowint; i++){
    			for(int j = 0; j < columnint; j++){
    				++count;
    				Rectangle2D.Double rect = new Rectangle2D.Double(i*measures.doubleValue(), j*measures.doubleValue(), measures.doubleValue(), measures.doubleValue());
    				rects[i*columnint + j] = rect;
    			}
    		}
    		double[] einzeln = new double[node.length];
    		for(double i = 0 + timestep.doubleValue(); i <= s.getDuration(); i = i + timestep.doubleValue()) {
    			for(int p = 0; p < node.length; p++) {
    				double rectPositionX = node[p].positionAt(i).x;
    				double rectPositionY = node[p].positionAt(i).y;
    				for(int j = 0; j < rects.length; j++) {
    					if(rects[j].contains(rectPositionX, rectPositionY)) {
    						squares[j] += timestep.doubleValue();
    						einzeln[p] += timestep.doubleValue();
    					}
    				}
    			}
    		}
    		double gesamt = 0;
    		for(int i = 0; i < squares.length; i++){
    			gesamt += squares[i];
    		}
    		PrintWriter movements_ns = openPrintWriter(name + ".bettstetter_statistics");
    		PrintWriter help = openPrintWriter(name + ".bettstetter_statistics2");
    		for (int i = 0; i < rects.length; i++) {
    			double old = 0;
    			if(i != rects.length-1){
    				old = rects[i+1].x;	
    			}
    			String m = rects[i].x + " " + rects[i].y + " " + squares[i];
    			movements_ns.println(m);
    			if(squares[i] != 0){
    				help.println(m);
    			}
    			if(old != rects[i].x){
    				movements_ns.println();
    			}
    		}
    		movements_ns.close();
    		help.close();
    	}
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'f':
				name = val;
				return true;
			case 'm':
				measures = Double.valueOf(val);
				return true;
			case 't':
				timestep = Double.valueOf(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
        System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("Dwelltime:");
		System.out.println("\t-f <filename>");
		System.out.println("\t-m cell length (default: 0.5m)");
		System.out.println("\t-t discrete time step (default: 0.5s)");
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		new Dwelltime(args);
	}
}
