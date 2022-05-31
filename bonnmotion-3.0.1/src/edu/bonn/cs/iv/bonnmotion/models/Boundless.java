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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.RandomSpeedBase;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.ScenarioLinkException;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

/** Application to construct boundless mobility scenarios. */

public class Boundless extends RandomSpeedBase {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("Boundless");
        info.description = "Application to construct Boundless mobility scenarios";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 723 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Chris Walsh");

		info.affiliation = ModuleInfo.TOILERS;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }

	protected double deltaT = 0.1;
	protected double accelMax = 1.5;
	protected double alpha = Math.PI/2;
	HashMap<Integer, LinkedList<Double>> statuschanges = new HashMap<Integer, LinkedList<Double>>();

	public Boundless(int nodes, double x, double y, double duration, double ignore, long randomSeed, 
			double minspeed, double maxspeed, double maxpause, double deltaT, double accelMax, double alpha) {
		super(nodes, x, y, 0, duration, ignore, randomSeed, minspeed, maxspeed, maxpause);
		this.deltaT = deltaT;
		this.accelMax = accelMax;
		this.alpha = alpha;
		generate();
	}
	
	public Boundless(String[] args) {
		go(args);
	}

	public void go(String[] args) {
		super.go(args);
		generate();
	}

	public Boundless(String args[], Scenario _pre, Integer _transitionMode) {
		// we've got a predecessor, so a transition is needed
		predecessorScenario = _pre;
		transitionMode = _transitionMode.intValue();
		isTransition = true;
		go(args);
	}
	
	public void generate() {
		preGeneration();

		for (int i = 0; i < parameterData.nodes.length; i++) {
			LinkedList<Double> statuschangetimes = new LinkedList<Double>();
			parameterData.nodes[i] = new MobileNode();
			double t = 0.0;
			Position src = null;
			
			if (isTransition) {
				try {
					Waypoint lastW = transition(predecessorScenario, transitionMode, i);
					src = lastW.pos;
					t = lastW.time;
				} 
				catch (ScenarioLinkException e) {
					e.printStackTrace();
				}
			} 
			else src = randomNextPosition();
			
			//give our nodes a random starting direction and speed
			double theta = randomNextDouble() * 2 * Math.PI;
			double speed = (maxspeed - minspeed) * randomNextDouble() + minspeed;
			int status = 0;
		
			while (t < parameterData.duration) {
				Position dst;
				
				if (!parameterData.nodes[i].add(t, src))
					throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (1)");
				
				//set our node status back to 'no status change'
				status = 0;
				double deltaSpeed = (randomNextDouble()*2*(accelMax*deltaT)) - (accelMax*deltaT); 
				double deltaTheta = (randomNextDouble()*2*(alpha*deltaT)) - (alpha*deltaT);	
				
				theta = theta + deltaTheta;
				speed = Math.min(Math.max(speed + deltaSpeed, 0), maxspeed);
				
				double newX = src.x + speed*Math.cos(theta);
				double newY = src.y + speed*Math.sin(theta);
				
				//Check to see if the node hits the boarders at all
				// if so set status to 'OFF / leaves scenario'
				if (newX > parameterData.x) {
					newX = 0;
					status = 2;
					statuschangetimes.add(t);
				} else if (newX < 0) {
					newX = parameterData.x;
					status = 2;
					statuschangetimes.add(t);
				}
				
				if (newY > parameterData.y) {
					newY = 0;
					status = 2;
					statuschangetimes.add(t);
				} else if (newY < 0) {
					newY = parameterData.y;
					status = 2;
					statuschangetimes.add(t);
				}
				
				dst = new Position(newX, newY, 0.0, status);
				
				t += deltaT;
				if (!parameterData.nodes[i].add(t, dst))
					throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (2)");
				
				//Check to see if the node's status is 2
				// if so set status to 'ON / arrives in scenario'
				if(status == 2){
					statuschanges.put(i, statuschangetimes);
					status = 1;
				}
				
				src = new Position(dst.x, dst.y, 0.0, status);
			}
		}

		postGeneration();
	}

	protected boolean parseArg(String key, String value) {
		if (key.equals("deltaT")) {
			deltaT = Double.parseDouble(value);
			return true;
		}
		else if (key.equals("accelMax")) {
			accelMax = Double.parseDouble(value);
			return true;
		}
		else if (key.equals("alpha")) {
			alpha = Double.parseDouble(value);
			return true;
		} 
		else return super.parseArg(key, value);
	}

	public void write(String _name) throws FileNotFoundException, IOException {
		String[] p = new String[3];
		p[0] = "deltaT=" + deltaT;
		p[1] = "accelMax=" + accelMax;
		p[2] = "alpha=" + alpha;
		
		// not sure this is working according to the specifications of the .changes file
		// this is directly copy pasted from DistasterArea.java
		// TODO: check this code such that it conforms to the .changes file specifications

		PrintWriter changewriter = new PrintWriter(new BufferedWriter(new FileWriter(_name + ".changes")));
		for (Integer i : statuschanges.keySet())
		{
			changewriter.write(i.toString());
			changewriter.write(" ");
			LinkedList<Double> list = statuschanges.get(i);
			changewriter.write(list.toString());
		}
		changewriter.close();
		
		super.write(_name, p);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 't': // "delta time"
				deltaT = Double.parseDouble(val);
				return true;
			case 'm': // "max acceleration"
				accelMax = Double.parseDouble(val);
				return true;
			case 's': // "max angular change"
				alpha = Double.parseDouble(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}
	
	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		RandomSpeedBase.printHelp();
		System.out.println( getInfo().name + ":");
		System.out.println("\t-t <time step>");
		System.out.println("\t-m <max acceleration change>");
		System.out.println("\t-s <alpha: max angular change>");
	}
	
	/* (non-Javadoc)
	 * @see edu.bonn.cs.iv.bonnmotion.Scenario#postGeneration()
	 */
	protected void postGeneration() {
		for (int i = 0; i < parameterData.nodes.length; i++) {
			Waypoint l = parameterData.nodes[i].getLastWaypoint();
			if (l.time > parameterData.duration) {
				Position p = parameterData.nodes[i].positionAt(parameterData.duration);
				parameterData.nodes[i].removeLastElement();
				parameterData.nodes[i].add(parameterData.duration, p);
			}
		}
		super.postGeneration();
	}
}