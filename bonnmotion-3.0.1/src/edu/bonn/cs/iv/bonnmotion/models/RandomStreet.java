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
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.MapScenario;
import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.util.maps.*;
import edu.bonn.cs.iv.util.maps.RouteServiceInterface.RSIRequestFailedException;

/** Application to construct RandomStreet mobility scenarios. */

public class RandomStreet extends MapScenario
{
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("RandomStreet");
        info.description = "Application to construct RandomStreet mobility scenarios";
        
        info.major = 2;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Matthias Schwamborn");
		info.affiliation = ModuleInfo.UOS_SYS;
		info.references.add("Nils Aschenbruck, Matthias Schwamborn: \"Synthetic Map-based Mobility Traces for the Performance Evaluation in Opportunistic Networks,\" in Proc. of the 2nd Int. Workshop on Mobile Opportunistic Networking (MobiOpp '10), Pisa, Italy, 2010");
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }

    private static final boolean DEBUG = true;
    
    /** speed parameters */
    private double minSpeed = -1;
    private double maxSpeed = -1;
    private double maxPause = -1;

    
    public RandomStreet(String[] args)
    {
        go(args);
    }

    public void go(String[] args)
    {
        super.go(args);
        generate();
    }

    public void generate()
    {
        preGeneration();

        if (isTransition) {
            System.out.println("Warning: Ignoring transition...");
        }

        for (int i = 0; i < parameterData.nodes.length; i++) {
            double t = 0.0;
            parameterData.nodes[i] = new MobileNode();
            wpGeo.add(new Vector<WaypointGeo>());

            // set initial position of mobile node
            PositionGeo src = randomNextGeoPosition();
            addWaypoint(i, t, src);

            while (t < parameterData.duration) {
                if (maxPause > 0.0) {
                    double pause = maxPause * randomNextDouble();
                    t += pause;
                }
                
                src = wpGeo.get(i).lastElement().pos;
                PositionGeo dst = randomNextGeoPosition();
                Route route = null;
                for (int r = 0; r <= RND_RETRY_COUNT; r++) {
                    try {
                    	route = rsInstance.getOptimalRoute(src, dst);
                    } catch (RSIRequestFailedException e) {
                    	if (r == RND_RETRY_COUNT) { // no valid route after RND_RETRY_COUNT retries
                    		System.out.println("Error: no valid route found after " + (RND_RETRY_COUNT+1) + " tries. Exiting...");
                    		System.exit(-1);
                    	}
                    	System.out.println("Warning: route from src to dst could not be computed (flight length "+ src.distance(dst) +"m). Retrying...");
                    	dst = randomNextGeoPosition();
                    	continue;
                    }
                    break;
                }
                
                route.setScenarioStartTime(t);

                // add route to waypoint list (drive to destination)
                t = addRoute(i, route, minSpeed, maxSpeed);
            }
            
            System.out.println("Node " + (i + 1) + " of " + parameterData.nodes.length + " done.");
        }

        postGeneration();
    }

    protected boolean parseArg(String key, String value)
    {
    	String[] v = null;
    	
        if (key.equals("maxPause")) {
        	maxPause = Double.parseDouble(value);
        	return true;
        } else if (key.equals("speed")) {
            v = value.split(" ");
            minSpeed = Double.parseDouble(v[0]);
            maxSpeed = Double.parseDouble(v[1]);
            
        	return true;
        } else {
            return super.parseArg(key, value);
        }
    }

    protected boolean parseArg(char key, String value)
    {
    	String[] v = null;
    	
        switch (key)
        {
            case 'p': // "max pause"
                maxPause = Double.parseDouble(value);

                if (DEBUG) System.out.println("DEBUG: maxPause = " + maxPause);
                return true;
            case 's': // "speed interval"
                v = value.split(" ");
                minSpeed = Double.parseDouble(v[0]);
                maxSpeed = Double.parseDouble(v[1]);

                if (DEBUG) System.out.println("DEBUG: minSpeed = " + minSpeed + ", maxSpeed = " + maxSpeed);
                return true;
//            case 'w': // write node movement to file
//                logFile = value;
//                return true;
            default:
                return super.parseArg(key, value);
        }
    }

    public void write(String _name) throws FileNotFoundException, IOException
    {
        String[] p = new String[2];
        p[0] = "maxPause=" + maxPause;
        p[1] = "speed=" + minSpeed + " " + maxSpeed;
        super.write(_name, p);
    }

    public static void printHelp()
    {
        System.out.println(getInfo().toDetailString());
        MapScenario.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-p <maxPause>");
        System.out.println("\t-s <minSpeed> <maxSpeed>");
    }

    protected void preGeneration()
    {
        super.preGeneration();

        if (minSpeed > maxSpeed || minSpeed <= 0 || maxSpeed <= 0)
        {
            System.out.println("Error: There is something wrong with the speed values!");
            System.exit(0);
        }
    }

    protected void postGeneration()
    {
//        mywrite();

        super.postGeneration();
    }

//    // write nodes' movement to file
//    private void mywrite()
//    {
//        try {
//    		if (logFile != null) {
//                FileWriter fileOut = new FileWriter(logFile);
//                for (int i = 0; i < node.length; i++) {
//                    fileOut.write("# node " + i + " movement\n");
//                    for (int j = 0; j < node[i].getNumWaypoints(); j++) {
//                        fileOut.write(node[i].getWaypoint(j).time + " " + node[i].getWaypoint(j).pos.x + " " + node[i].getWaypoint(j).pos.y + "\n");
//                    }
//                    fileOut.write("\n");
//                }
//                fileOut.close();
//    		}
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(0);
//        }
//    }
}
