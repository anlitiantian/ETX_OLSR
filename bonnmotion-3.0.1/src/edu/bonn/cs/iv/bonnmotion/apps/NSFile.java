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
import edu.bonn.cs.iv.bonnmotion.models.DisasterArea;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

/** Application that creates a movement file for ns-2. */
public class NSFile extends App {
	
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("NSFile");
        info.description = "Application that creates movement files for ns-2";
        
        info.major = 1;
        info.minor = 1;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	/** Add border around the scenario to prevent ns-2 from crashing. */
	public static double border = 10.0;
	private static boolean useDefaultNSConverter = false;

	//String routingprotocol = new String(); //TODO: Can we remove this?
	//int[] mcastgroups = null; //TODO: Can we remove this?

	protected String name = null;

	public NSFile(String[] args) {
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

		PrintWriter settings = openPrintWriter(name + ".ns_params");
		settings.println("set val(x) " + (s.getX() + 2 * border));
		settings.println("set val(y) " + (s.getY() + 2 * border));
        if (s.getScenarioParameters().outputDim == Dimension.THREED) {
            settings.println("set val(z) " + (s.getZ()==0.0 ? "0.0" : (s.getZ() + 2 * border)));
        }
		settings.println("set val(nn) " + node.length);
		settings.println("set val(duration) " + s.getDuration());
		settings.close();
				
		try {
			
			PrintWriter movements_ns = openPrintWriter(name + ".ns_movements");
			
			// If required: Use DisasterArea model.
			if (s.getModelName().equals(DisasterArea.getInfo().name) && !useDefaultNSConverter) {
				
				String allmovements = s.movements;
				String[] m = allmovements.split("\n");
				for (int i = 0; i < m.length; i++) {
					
					String[] oneWaypoint = m[i].split(" ");
	
					StringBuilder towriteBuilder = new StringBuilder();
					towriteBuilder.append("$node_(");
					towriteBuilder.append(i);
					towriteBuilder.append(")");
					towriteBuilder.append(" set X_ ");
					towriteBuilder.append(oneWaypoint[2]);
					towriteBuilder.append("\n");
					towriteBuilder.append("$node_(");
					towriteBuilder.append(i);
					towriteBuilder.append(")");
					towriteBuilder.append(" set Y_ ");
					towriteBuilder.append(oneWaypoint[3]);
					movements_ns.println(towriteBuilder.toString());
	
					for (int j = 4; j < oneWaypoint.length - 1; j = j + 4) {
						Double time =    new Double(oneWaypoint[j + 1]);
						Double newx =    new Double(oneWaypoint[j + 2]);
						Double newy =    new Double(oneWaypoint[j + 3]);
						Double status =  new Double(oneWaypoint[j + 4]);
						Double oldtime = new Double(oneWaypoint[j - 3]);
						Double oldx =    new Double(oneWaypoint[j - 2]);
						Double oldy =    new Double(oneWaypoint[j - 1]);
						Position newWaypoint = new Position(newx.doubleValue(), newy.doubleValue());
						Position oldWaypoint = new Position(oldx.doubleValue(), oldy.doubleValue());
						double dist = newWaypoint.distance(oldWaypoint);
	
						towriteBuilder = new StringBuilder();
						towriteBuilder.append("$ns_ at ");
						towriteBuilder.append(time.doubleValue());
						towriteBuilder.append(" \"");
						towriteBuilder.append("$node_(" + i + ")");
						towriteBuilder.append(" setdest ");
						towriteBuilder.append(newx.doubleValue() + border);
						towriteBuilder.append(" ");
						towriteBuilder.append(newy.doubleValue() + border);
						towriteBuilder.append(" ");
						towriteBuilder.append((dist / (time.doubleValue() - oldtime.doubleValue())));
						towriteBuilder.append(" ");
						towriteBuilder.append(status.doubleValue());
						towriteBuilder.append("\"");
						movements_ns.println(towriteBuilder.toString());
						if (status.doubleValue() == 2.0) {
							String towrite;
							towrite = "set RoutingAgent [$node_(" + i + ") agent 255]";
							movements_ns.println(towrite);
							towrite = "$ns_ at " + time.doubleValue() + " \"$RoutingAgent deactivate\"" ;
							movements_ns.println(towrite);
						}
						if (status.doubleValue() == 1.0) {
							String towrite;
							towrite = "set RoutingAgent [$node_(" + i + ") agent 255]";
							movements_ns.println(towrite);
							towrite = "$ns_ at " + time.doubleValue() + " \"$RoutingAgent activate\"" ;
							movements_ns.println(towrite);
						}
					}
				}
				
			} 
			// Else: Use standard model.
			else {
				
				System.out.println("movement string " + node[0].movementString(s.getScenarioParameters().outputDim));
				
				for (int i = 0; i < node.length; i++) {
					//String[] m = node[i].movementStringNS("$node_(" + i + ")", border);
					String[] m = movementStringNS(node[i],"$node_(" + i + ")", border, s.getScenarioParameters().outputDim);
					for (int j = 0; j < m.length; j++){
						movements_ns.println(m[j]);
						System.out.println(m[j]);
					}
				}
				
			}
			
			movements_ns.close();
			
		} catch(Exception e){
			System.out.println("Error in NSFile, while reading node movements");
			System.exit(0);
		}
		
	}
	
	protected String[] movementStringNS(MobileNode mn, String id, double border, Dimension dim) {
		if (dim == Dimension.TWOD){
			final int numWaypoints = mn.getNumWaypoints();
			
	        System.out.println("waypoints " + numWaypoints);
	        String[] r = new String[numWaypoints + 1];
	        Waypoint w = mn.getWaypoint(0); //waypoints.elementAt(0);
	        r[0] = id + " set X_ " + (w.pos.x + border);
	        r[1] = id + " set Y_ " + (w.pos.y + border);
	        //for (int i = 1; i < waypoints.size(); i++) {
	        for (int i = 1; i < numWaypoints; i++) {
	            //Waypoint w2 = waypoints.elementAt(i);
	        	Waypoint w2 = mn.getWaypoint(i);
	            double dist = w.pos.distance(w2.pos);
	            r[i + 1] = "$ns_ at " + w.time + " \"" + id + " setdest " + (w2.pos.x + border) + " " + (w2.pos.y + border) + " "
	                    + (dist / (w2.time - w.time)) + "\"";
	            if (dist == 0.0) {
	                r[i + 1] = "# " + r[i + 1];
	            }
	            // hack alert... but why should we schedule these in ns-2?
	            w = w2;
	        }
	        return r;
		}else{
			final int numWaypoints = mn.getNumWaypoints();
			//System.out.println("waypoints " + waypoints.size());
			System.out.println("waypoints " + numWaypoints);
			//String[] r = new String[waypoints.size() + 2];
			String[] r = new String[numWaypoints + 2];
			//Waypoint w = waypoints.elementAt(0);
			Waypoint w = mn.getWaypoint(0);
			Position p = w.pos;
			r[0] = id + " set X_ " + (p.x + border);
			r[1] = id + " set Y_ " + (p.y + border);
			r[2] = id + " set Z_ " + (p.z + border);
			//for (int i = 1; i < waypoints.size(); i++) {
			for (int i = 1; i < numWaypoints; i++) {
				//Waypoint w2 = waypoints.elementAt(i);
				Waypoint w2 = mn.getWaypoint(i);
				Position p2 = w2.pos;
				double dist = p.distance(w2.pos);
				r[i + 2] = "$ns_ at " + w.time + " \"" + id + " setdest " + (p2.x + border) + " " + (p2.y + border) + " " + (p2.z + border)
						+ " " + (dist / (w2.time - w.time)) + "\"";
				if (dist == 0.0) {
					r[i + 2] = "# " + r[i + 1];
				}
				// hack alert... but why should we schedule these in ns-2?
				w = w2;
			}
			return r;
			
		}
	}
	

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'd':
				useDefaultNSConverter = true;
				return true;
			case 'f':
				name = val;
				return true;
			case 'b':
				border = 0;
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
        System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("NSFile:");
		System.out.println("\t[-d]\tOverride module specific convertes and use the standard converter");
		System.out.println("\t[-b]\tDisable additional margin");
		System.out.println("\t-f <filename>");
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		new NSFile(args);
	}
}
