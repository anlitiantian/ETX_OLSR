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

import java.io.FileNotFoundException;
import java.io.IOException;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.RandomSpeedBase;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.ScenarioLinkException;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

/** Application to construct RandomDirection mobility scenarios. */
/** 
 *  Chris Walsh
 *  June 2009
 * 
 *  Nodes will select a random direction and speed. They travel
 *  until the reach the edge of the simulation. They then pause
 *  and pick a new direction and speed.
 *  
 *  
 */

public class RandomDirection extends RandomSpeedBase {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("RandomDirection");
        info.description = "Application to construct Random Direction mobility scenarios";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Chris Walsh");
        
        info.affiliation = ModuleInfo.TOILERS;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }

	protected double minpause = 0.0;

	public RandomDirection(int nodes, double x, double y, double duration, double ignore, long randomSeed, double minspeed, double maxspeed, double maxpause, double minpause) {
		super(nodes, x, y, duration, ignore, randomSeed, minspeed, maxspeed, maxpause);
		this.minpause = minpause;
		generate();
	}
	
	public RandomDirection(String[] args) {
		go(args);
	}

	public void go(String[] args) {
		super.go(args);
		generate();
	}

	public RandomDirection(String args[], Scenario _pre, Integer _transitionMode) {
		// we've got a predecessor, so a transition is needed
		predecessorScenario = _pre;
		transitionMode = _transitionMode.intValue();
		isTransition = true;
		go(args);
	}
	
	public void generate() {	
		double xTime, yTime, speed, newX, newY, angle;
		preGeneration();

		for (int i = 0; i < parameterData.nodes.length; i++) {
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
			
			angle = randomNextDouble() * 2 * Math.PI;
			
			while (t < parameterData.duration) {
				Position dst;
				
				if (!parameterData.nodes[i].add(t, src))
					throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (1)");
				
				speed = (maxspeed - minspeed) * randomNextDouble() + minspeed;
				
				if (angle >= 0 && angle < Math.PI/2)
				{
					xTime = (parameterData.x - src.x)/(speed*Math.cos(angle));
					yTime = (parameterData.y - src.y)/(speed*Math.sin(angle));
					
					if(xTime < yTime) // hit right wall first 
					{
						newX = parameterData.x;
						newY = (speed*xTime*Math.sin(angle)) + src.y;
						angle = (randomNextDouble() * Math.PI) + (Math.PI/2);
					}
					else if (yTime < xTime) // hit top wall first 
					{
						newX = (speed*yTime*Math.cos(angle)) + src.x;
						newY = parameterData.y;
						angle = (randomNextDouble() * Math.PI) + (Math.PI);
					}
					else // hit corner angle = Math.PI/2
					{
						newX = parameterData.x;
						newY = parameterData.y;
						angle = (randomNextDouble() * Math.PI/2) + (Math.PI);
					}					
				}
				else if (angle >= Math.PI/2 && angle < Math.PI)
				{
					xTime = (0 - src.x)/(speed*Math.cos(angle));
					yTime = (parameterData.y - src.y)/(speed*Math.sin(angle));
					
					if(xTime < yTime) // hit left wall first
					{
						newX = 0;
						newY = (speed*xTime*Math.sin(angle)) + src.y;
						angle = ((randomNextDouble() * Math.PI) + (Math.PI*3/2)) % (2*Math.PI);
					}
					else if (yTime < xTime) // hit top wall first
					{
						newX = (speed*yTime*Math.cos(angle)) + src.x;
						newY = parameterData.y;
						angle = (randomNextDouble() * Math.PI) + (Math.PI);
					}
					else // hit corner angle = Math.PI/2
					{
						newX = 0;
						newY = parameterData.y;
						angle = (randomNextDouble() * Math.PI/2) + (Math.PI*3/2);
					}	
				}
				else if (angle >= Math.PI && angle < Math.PI*3/2)
				{
					xTime = (0 - src.x)/(speed*Math.cos(angle));
					yTime = (0 - src.y)/(speed*Math.sin(angle));
					
					if(xTime < yTime) // hit left wall first
					{
						newX = 0;
						newY = (speed*xTime*Math.sin(angle)) + src.y;
						angle = ((randomNextDouble() * Math.PI) + (Math.PI*3/2)) % (2*Math.PI);
					}
					else if (yTime < xTime) // hit bottom wall first
					{
						newX = (speed*yTime*Math.cos(angle)) + src.x;
						newY = 0;
						angle = (randomNextDouble() * Math.PI);
					}
					else // hit corner angle = Math.PI/2
					{
						newX = 0;
						newY = 0;
						angle = (randomNextDouble() * Math.PI/2);
					}	
				}
				else if (angle >= Math.PI*3/2 && angle < Math.PI*2)
				{
					xTime = (parameterData.x - src.x)/(speed*Math.cos(angle));
					yTime = (0 - src.y)/(speed*Math.sin(angle));
					
					if(xTime < yTime) // hit right wall first
					{
						newX = parameterData.x;
						newY = (speed*xTime*Math.sin(angle)) + src.y;
						angle = (randomNextDouble() * Math.PI) + (Math.PI/2);
					}
					else if (yTime < xTime) // hit bottom wall first
					{
						newX = (speed*yTime*Math.cos(angle)) + src.x;
						newY = 0;
						angle = (randomNextDouble() * Math.PI);
					}
					else // hit corner angle = Math.PI/2
					{
						newX = parameterData.x;
						newY = 0;
						angle = (randomNextDouble() * Math.PI/2) + (Math.PI/2);
					}
				}
				else throw new RuntimeException(getInfo().name + ".go: error angle didn't fall into any of the four quadrants. (Something blew up?)");
				
				dst = new Position(newX, newY);
				t += src.distance(dst) / speed;
				
				if (!parameterData.nodes[i].add(t, dst))
					throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (2)");
				
				if ((t < parameterData.duration) && (maxpause > 0.0)) {
					double pause = (maxpause-minpause) * randomNextDouble() + minpause;
					t += pause;
				}
				src = dst;
			}
		}

		postGeneration();
	}

	protected boolean parseArg(String key, String value) {
		if (key.equals("minpause")) {
			minpause = Double.parseDouble(value);
			return true;
		} 
		else return super.parseArg(key, value);
	}

	public void write(String _name) throws FileNotFoundException, IOException {
		String[] p = new String[1];
		p[0] = "minpause=" + minpause;
		super.write(_name, p);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'O':
			case 'o': // "minimum pause time"
				minpause = Double.parseDouble(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}
	
	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		RandomSpeedBase.printHelp();
		System.out.println( getInfo().name + ":");
		System.out.println("\t-o <minimum pause time>");
	}

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