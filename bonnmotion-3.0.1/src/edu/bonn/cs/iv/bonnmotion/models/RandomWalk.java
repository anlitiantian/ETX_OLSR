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
import java.util.SortedMap;
import java.util.TreeMap;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.RandomSpeedBase;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.ScenarioLinkException;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

/** Application to construct RandomWalk mobility scenarios. */
/** 
 *  Chris Walsh
 *  June 2009
 * 
 *  Nodes will select a random direction and speed. They travel
 *  until either: 
 *  1) a set distance is traveled
 *    OR
 *  2) a fixed time has passed
 *  
 *  For example, the user will specify either "-t 60" for a time 
 *  interval of 60 seconds or "-d 45" for a distance of 45 units.
 *  
 *  Nodes will reflect/bounce off walls and continue traveling 
 *  should they come into contact.
 *  
 */

public class RandomWalk extends RandomSpeedBase {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("RandomWalk");
        info.description = "Application to construct RandomWalk mobility scenarios";
        
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
	
	protected char mode = 'x';
	protected double modeDelta = 60;

	public RandomWalk(int nodes, double x, double y, double duration, double ignore, long randomSeed, double minspeed, double maxspeed, char mode, double modeDelta) {
		super(nodes, x, y, duration, ignore, randomSeed, minspeed, maxspeed, 0);
		this.mode = mode;
		this.modeDelta = modeDelta;
		generate();
	}
	
	public RandomWalk(String[] args) {
		go(args);
	}

	public void go(String[] args) {
		super.go(args);
		generate();
	}

	public RandomWalk(String args[], Scenario _pre, Integer _transitionMode) {
		// we've got a predecessor, so a transition is needed
		predecessorScenario = _pre;
		transitionMode = _transitionMode.intValue();
		isTransition = true;
		go(args);
	}
	
	public void generate() {
		SortedMap<Double, Position> dstList = new TreeMap<Double, Position>();
		double maxDist;
		
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
			
			if (!parameterData.nodes[i].add(t, src))   		// add source Waypoint
				throw new RuntimeException(getInfo().name + ".go: error while adding waypoint");
			
			while (t < parameterData.duration) {
				Position dst = null;
				double angle, dX, dY;

				angle = randomNextDouble() * 2 * Math.PI;
				
				double speed = (maxspeed - minspeed) * randomNextDouble() + minspeed;
				
				switch (mode)
				{
					case 't': //Time is the limiter
						// Compute how far we are allowed to travel in time T, where T = modeDelta. Then add the necessary destinations.
						maxDist = speed * modeDelta;
						if((parameterData.duration - t) < (maxDist / speed))
						{
							maxDist = speed * (parameterData.duration - t);
							dX = (parameterData.duration - t) * speed * Math.cos(angle);
							dY = (parameterData.duration - t) * speed * Math.sin(angle);
						}
						else
						{
							dX = modeDelta * speed * Math.cos(angle);
							dY = modeDelta * speed * Math.sin(angle);
						}
						break;
				
					case 's': //diStance is the limiter
						// We know how far we're allowed to travel (modeDelta), now compute the necessary destinations
						maxDist = modeDelta;
						if((parameterData.duration - t) < (maxDist / speed))
						{
							maxDist = speed * (parameterData.duration - t);
							dX = (parameterData.duration - t) * speed * Math.cos(angle);
							dY = (parameterData.duration - t) * speed * Math.sin(angle);
						}
						else
						{
							dX = modeDelta * Math.cos(angle);
							dY = modeDelta * Math.sin(angle);
						}
						break;
					default: 
						throw new RuntimeException(getInfo().name + ".go: error calculating next destination - mode is not 't' or 's'. Please supply -t or -s flag");
				}
				
				dstList = checkReflection(dstList, src, dX, dY, angle, t, parameterData.duration, 0, maxDist, speed);
				
				for (double key : dstList.keySet())
				{
			    	t = key;
			    	dst = dstList.get(key);
			    	if (!parameterData.nodes[i].add(t, dst)) throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (2t)");
				}
				
				dstList.clear();
				src = dst;
			}
		}

		postGeneration();
	}

	protected boolean parseArg(String key, String value) {
		if (key.equals("mode"))
		{
			mode = value.toCharArray()[0];
			return true;
		}
		else if (key.equals("modeDelta"))
		{
			modeDelta = Double.parseDouble(value);
			return true;
		} else return super.parseArg(key, value);
	}

	public void write( String _name ) throws FileNotFoundException, IOException {
		String[] p = new String[2];
		p[0] = "mode=" + mode;
		p[1] = "modeDelta=" + modeDelta;
		super.write(_name, p);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'T':
			case 't': // "limit by: [T]ime"
				mode = 't';
				modeDelta = Double.parseDouble(val);
				return true;
			case 'S':
			case 's': // "limit by: Di[s]tance"
				mode = 's';
				modeDelta = Double.parseDouble(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}
	
	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		RandomSpeedBase.printHelp();
		System.out.println( getInfo().name + ":");
		System.out.println("\t-t x (mode: [T]ime. Nodes will walk until time interval x is up)");
		System.out.println("\tOR (do not use both -t and -s)");
		System.out.println("\t-s x (mode: Di[s]tance. Nodes will walk until distance x has been covered)");
	}
	
	/**
	 * 
	 * @param list the sortedmap of all the bounces 
	 * @param src source destination
	 * @param dX potential movement in X
	 * @param dY potential movement in Y
	 * @param dir node movement direction
	 * @param time current time
	 * @param dur simulation duration
	 * @param distTraveled distance traveled - pass 0 to begin this function
	 * @param maxDist how far the node is allowed to travel
	 * @param speed node speed
	 * @returnThis function will check if the node is attempting to move outside of the simulation space. 
	 * It will return a SortedMap containing a list of the positions and their respective times.
	 * For example if a node hit the left wall once, this function would return a map with two elements, one of the 
	 * collision with the wall itself and then another of the resting point of the node after the reflection. 
	 */
	protected SortedMap<Double, Position> checkReflection(SortedMap<Double, Position> list, Position src, 
			double dX, double dY, double dir, double time, double dur, double distTraveled, double maxDist, double speed)
	{
		// there are 8 cases to check
		/*************************************/
		/*                                   */
		/*           |           |           */
		/*     3     |     2     |     1     */
		/*           |           |           */
		/* --------------------------------- */
		/*           |           |           */
		/*           |           |           */
		/*     4     |  default  |     0     */
		/*           |           |           */
		/*           |           |           */
		/* --------------------------------- */
		/*           |           |           */
		/*     5     |     6     |     7     */
		/*           |           |           */
		/*                                   */
		/*************************************/

		double newX, newY, surfaceAngle, newDir, xTime, yTime;
		Position potentialDst = new Position(src.x + dX, src.y + dY);
		Position newSrc;
		
		if(potentialDst.x > parameterData.x && potentialDst.y < parameterData.y && potentialDst.y > 0) // case 0
		{
			//calculate destination (the wall collision)
			xTime = (parameterData.x - src.x)/(speed*Math.cos(dir));
			newX = parameterData.x;
			newY = (speed*xTime*Math.sin(dir)) + src.y;
			distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
			//put the collision with the wall into the list
			newSrc = new Position(newX, newY);
			list.put((distTraveled/speed)+time, newSrc);
			//calculate reflection
			surfaceAngle = Math.PI/2;
			newDir = 2*surfaceAngle - dir;
			
			//the new dX and dY for the reflected movement
			newX = (maxDist - distTraveled)*Math.cos(newDir);
			newY = (maxDist - distTraveled)*Math.sin(newDir);
			
			//call recursively to check if the reflection dst will hit a wall
			return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
		}
		else if(potentialDst.x > parameterData.x && potentialDst.y > parameterData.y) // case 1
		{
			xTime = (parameterData.x - src.x)/(speed*Math.cos(dir));
			yTime = (parameterData.y - src.y)/(speed*Math.sin(dir));
			
			if(xTime < yTime) //hit the side first
			{
				//calculate destination (the wall collision)
				newX = parameterData.x;
				newY = (speed*xTime*Math.sin(dir)) + src.y;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI/2;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else if(xTime > yTime) // hit the top first
			{
				//calculate destination (the wall collision)
				newY = parameterData.y;
				newX = (speed*yTime*Math.cos(dir)) + src.x;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else // hit corner
			{
				//calculate destination (the wall collision)
				newY = parameterData.y;
				newX = parameterData.x;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				newDir = Math.PI + dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
		}
		else if(potentialDst.y > parameterData.y && potentialDst.x < parameterData.x && potentialDst.x > 0) // case 2
		{
			//calculate destination (the wall collision)
			yTime = (parameterData.y - src.y)/(speed*Math.sin(dir));
			newY = parameterData.y;
			newX = (speed*yTime*Math.cos(dir)) + src.x;
			distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
			//put the collision with the wall into the list
			newSrc = new Position(newX, newY);
			list.put((distTraveled/speed)+time, newSrc);
			//calculate reflection
			surfaceAngle = Math.PI;
			newDir = 2*surfaceAngle - dir;
			
			//the new dX and dY for the reflection
			newX = (maxDist - distTraveled)*Math.cos(newDir);
			newY = (maxDist - distTraveled)*Math.sin(newDir);
			
			//call recursively to check if the reflection dst will hit a wall
			return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
		}
		else if(potentialDst.x < 0 && potentialDst.y > parameterData.y) // case 3
		{
			xTime = (0 - src.x)/(speed*Math.cos(dir));
			yTime = (parameterData.y - src.y)/(speed*Math.sin(dir));
			
			if(xTime < yTime) //hit the side first
			{
				//calculate destination (the wall collision)
				newX = 0;
				newY = (speed*xTime*Math.sin(dir)) + src.y;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI/2;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else if(xTime > yTime) // hit the top first
			{
				//calculate destination (the wall collision)
				newY = parameterData.y;
				newX = (speed*yTime*Math.cos(dir)) + src.x;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else // hit corner
			{
				//calculate destination (the wall collision)
				newY = parameterData.y;
				newX = 0;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				newDir = Math.PI + dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
		}
		else if(potentialDst.x < 0 && potentialDst.y < parameterData.y && potentialDst.y > 0) // case 4
		{
			//calculate destination (the wall collision)
			xTime = (0 - src.x)/(speed*Math.cos(dir));
			newX = 0;
			newY = (speed*xTime*Math.sin(dir)) + src.y;
			distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
			//put the collision with the wall into the list
			newSrc = new Position(newX, newY);
			list.put((distTraveled/speed)+time, newSrc);
			//calculate reflection
			surfaceAngle = Math.PI/2;
			newDir = 2*surfaceAngle - dir;
			
			//the new dX and dY for the reflection
			newX = (maxDist - distTraveled)*Math.cos(newDir);
			newY = (maxDist - distTraveled)*Math.sin(newDir);
			
			//call recursively to check if the reflection dst will hit a wall
			return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
		}
		else if(potentialDst.x < 0 && potentialDst.y < 0) // case 5
		{
			xTime = (0 - src.x)/(speed*Math.cos(dir));
			yTime = (0 - src.y)/(speed*Math.sin(dir));
			
			if(xTime < yTime) //hit the side first
			{
				//calculate destination (the wall collision)
				newX = 0;
				newY = (speed*xTime*Math.sin(dir)) + src.y;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI/2;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else if(xTime > yTime) // hit the bottom first
			{
				//calculate destination (the wall collision)
				newY = 0;
				newX = (speed*yTime*Math.cos(dir)) + src.x;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else // hit corner
			{
				//calculate destination (the wall collision)
				newY = 0;
				newX = 0;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				newDir = Math.PI + dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
		}
		else if(potentialDst.y < 0 && potentialDst.x < parameterData.x && potentialDst.x > 0) // case 6
		{
			//calculate destination (the wall collision)
			yTime = (0 - src.y)/(speed*Math.sin(dir));
			newY = 0;
			newX = (speed*yTime*Math.cos(dir)) + src.x;
			distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
			//put the collision with the wall into the list
			newSrc = new Position(newX, newY);
			list.put((distTraveled/speed)+time, newSrc);
			//calculate reflection
			surfaceAngle = Math.PI;
			newDir = 2*surfaceAngle - dir;
			
			//the new dX and dY for the reflection
			newX = (maxDist - distTraveled)*Math.cos(newDir);
			newY = (maxDist - distTraveled)*Math.sin(newDir);
			
			//call recursively to check if the reflection dst will hit a wall
			return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
		}
		else if(potentialDst.x > parameterData.x && potentialDst.y < 0) // case 7
		{
			xTime = (parameterData.x - src.x)/(speed*Math.cos(dir));
			yTime = (0 - src.y)/(speed*Math.sin(dir));
			
			if(xTime < yTime) //hit the side first
			{
				//calculate destination (the wall collision)
				newX = parameterData.x;
				newY = (speed*xTime*Math.sin(dir)) + src.y;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI/2;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else if(xTime > yTime) // hit the bottom first
			{
				//calculate destination (the wall collision)
				newY = 0;
				newX = (speed*yTime*Math.cos(dir)) + src.x;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				surfaceAngle = Math.PI;
				newDir = 2*surfaceAngle - dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
			else // hit corner
			{
				//calculate destination (the wall collision)
				newY = 0;
				newX = parameterData.x;
				distTraveled += Math.sqrt((newX-src.x)*(newX-src.x) + (newY-src.y)*(newY-src.y));
				//put the collision with the wall into the list
				newSrc = new Position(newX, newY);
				list.put((distTraveled/speed)+time, newSrc);
				//calculate reflection
				newDir = Math.PI + dir;
				
				//the new dX and dY for the reflection
				newX = (maxDist - distTraveled)*Math.cos(newDir);
				newY = (maxDist - distTraveled)*Math.sin(newDir);
				
				//call recursively to check if the reflection dst will hit a wall
				return checkReflection(list, newSrc, newX, newY, newDir, time, dur, distTraveled, maxDist, speed);
			}
		}
		else // default case  - no walls hit, thus no reflection needed
		{
			distTraveled += Math.sqrt((potentialDst.x-src.x)*(potentialDst.x-src.x) + (potentialDst.y-src.y)*(potentialDst.y-src.y));
			list.put((distTraveled/speed)+time, potentialDst);
			return list;
		}
	}

	protected void postGeneration() {
		for ( int i = 0; i < parameterData.nodes.length; i++ ) 
		{
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