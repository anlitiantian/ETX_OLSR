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
import java.util.Arrays;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.ScenarioLinkException;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

/**
 * Application to construct Steady State Random Waypoint mobility scenarios
 * 
 * Chris Walsh June 2009
 * 
 */

public class SteadyStateRandomWaypoint extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("SteadyStateRandomWaypoint");
        info.description = "Application to construct Steady State Random Waypoint mobility scenarios";
        
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

	protected double speedMean = 0.0;
	protected double speedDelta = 0.0;
	protected double pauseMean = 0.0;
	protected double pauseDelta = 0.0;

	public SteadyStateRandomWaypoint(int nodes, double x, double y, double duration, double ignore, long randomSeed, double speedMean,
			double speedDelta, double pauseMean, double pauseDelta) {
		super(nodes, x, y, duration, ignore, randomSeed);
		this.speedMean = speedMean;
		this.speedDelta = speedDelta;
		this.pauseMean = pauseMean;
		this.pauseDelta = pauseDelta;

		generate();
	}

	public SteadyStateRandomWaypoint(String[] args) {
		if (!Arrays.asList(args).contains("-i")) 		// if no ignore parameter is provided set it to zero
			parameterData.ignore = 0;
		
		go(args);
	}

	public void go(String[] args) {
		super.go(args);
		generate();
	}

	public SteadyStateRandomWaypoint(String args[], Scenario _pre,
			Integer _transitionMode) {
		// we've got a predecessor, so a transtion is needed
		predecessorScenario = _pre;
		transitionMode = _transitionMode.intValue();
		isTransition = true;
		go(args);
	}

	public void generate() {
		if (speedMean <= 0) {
		    System.err.println(getInfo().name + ".generate: Error: speed mean (-o) must be greater than 0");
		    System.exit(-1);
		}
		if ((speedDelta >= speedMean) || (speedDelta < 0)) {
		    System.err.println(getInfo().name + ".generate: Error: speed delta (-p) must be greater than or equal to 0 and less than speed mean");
	        System.exit(-1);
		}
		if (pauseMean < 0) {
		    System.err.println(getInfo().name + ".generate: Error: pause mean (-k) must be greater than or equal to 0");
	        System.exit(-1);
		}
		if ((pauseDelta > pauseMean) || (pauseDelta < 0)) {
		    System.err.println(getInfo().name + ".generate: Error: pause delta (-l) must be greater than or equal to 0 and less than or equal to pause mean");
	        System.exit(-1);
		}
		
		double speedMin = speedMean - speedDelta;
		double pauseMin = pauseMean - pauseDelta;
		double pauseMax = pauseMean + pauseDelta;
		double speedRange = 2 * speedDelta;
		double pauseRange = 2 * pauseDelta;
		double u1, u2, r, x1 = 0, x2 = 0, y1 = 0, y2 = 0;  	// steady-state initial position
		double u, v0, v1, speed; 							// steady-state initial velocity
		double expectedPauseTime, expectedTravelTime, probabilityPaused, a, b, pauseTime, pause;
		double log1, log2;
		
		preGeneration();
		
		// calculate the steady-state probability that a node is initially paused
		expectedPauseTime = pauseMean;
		a = parameterData.x;
		b = parameterData.y;
		v0 = speedMean - speedDelta;
		v1 = speedMean + speedDelta;
		log1 = b * b / a * Math.log(Math.sqrt((a * a) / (b * b) + 1) + a / b);
		log2 = a * a / b * Math.log(Math.sqrt((b * b) / (a * a) + 1) + b / a);
		expectedTravelTime = 1.0 / 6.0 * (log1 + log2);
		expectedTravelTime += 1.0 / 15.0
				* ((a * a * a) / (b * b) + (b * b * b) / (a * a)) - 1.0 / 15.0
				* Math.sqrt(a * a + b * b)
				* ((a * a) / (b * b) + (b * b) / (a * a) - 3);
		
		if (speedDelta == 0.0)
			expectedTravelTime /= speedMean;
		else
			expectedTravelTime *= Math.log(v1 / v0) / (v1 - v0);

		probabilityPaused = expectedPauseTime / (expectedPauseTime + expectedTravelTime);

		for (int i = 0; i < parameterData.nodes.length; i++) {
			parameterData.nodes[i] = new MobileNode();
			double t = 0.0;
			Position src = null;
			if (isTransition) {
				try {
					Waypoint lastW = transition(predecessorScenario, transitionMode, i);
					src = lastW.pos;
					t = lastW.time;
				} catch (ScenarioLinkException e) {
					e.printStackTrace();
				}
			} else {
				// steady-state initial positions
				r = 0;
				u1 = 1;
				while (u1 >= r) {
					x1 = randomNextDouble() * parameterData.x;
					x2 = randomNextDouble() * parameterData.x;
					y1 = randomNextDouble() * parameterData.y;
					y2 = randomNextDouble() * parameterData.y;
					u1 = randomNextDouble();
					// r is a ratio of the length of the randomly chosen path
					// over the length of a diagonal across the simulation area
					r = Math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) / (parameterData.x * parameterData.x + parameterData.y * parameterData.y));
					// u1 is a uniform random number between 0 and 1
				}
				// initially the node has traveled a proportion u2 of the path
				// from (x1,y1) to (x2,y2)
				u2 = randomNextDouble();
				src = new Position((u2 * x1 + (1 - u2) * x2), (u2 * y1 + (1 - u2) * y2));

				if (!parameterData.nodes[i].add(t, src))
					throw new RuntimeException(getInfo().name + ".generate: error while adding initial position waypoint (1)");
			}
			
			//steady-state initial speeds
			u = randomNextDouble();
			
			if(u < probabilityPaused) //node initially paused
			{
				//calculate initial node pause time
				u = randomNextDouble();
				
				if(pauseDelta != 0.0)
				{
					if(u < (2 * pauseMin / (pauseMin + pauseMax)) )
					{
						pauseTime = u * (pauseMin + pauseMax) / 2;
					}
					else
					{
						// there is an error in equation 20 in the Tech. Report MCS-03-04
						// this error is corrected in the TMC 2004 paper and below
						pauseTime = pauseMax - Math.sqrt((1 - u) * (pauseMax * pauseMax - pauseMin * pauseMin));
					}
				}
				else
					pauseTime = u * pauseMean;
				
				t = pauseTime;
			}
			else //node initially moving
			{
				//calculate initial node speed
				v0 = speedMin;
				v1 = speedMin + speedRange;
				u = randomNextDouble();
				speed = Math.pow(v1, u) / Math.pow(v0, u - 1);
				
				Position firstDst = new Position(x2, y2);
				t += src.distance(firstDst) / speed;

				if (!parameterData.nodes[i].add(t, firstDst))
					throw new RuntimeException(getInfo().name + ".generate: error while adding firstDst");
				
				//pause after reaching dest
				if (t < parameterData.duration) {
					pause = pauseRange * randomNextDouble() + pauseMin;
					t += pause;
				}
				src = firstDst;
			}
			
			while (t < parameterData.duration) {
				Position dst;
				if (!parameterData.nodes[i].add(t, src))
					throw new RuntimeException(getInfo().name + ".generate: error while adding waypoint (1)");
				dst = randomNextPosition();
				
				speed = speedRange * randomNextDouble() + speedMin;
				t += src.distance(dst) / speed;
				
				if (!parameterData.nodes[i].add(t, dst))
					throw new RuntimeException(getInfo().name + ".generate: error while adding waypoint (2)");

				//pause after reaching dest
				if (t < parameterData.duration) {
					pause = pauseRange * randomNextDouble() + pauseMin;
					t += pause;
				}
				src = dst;
			}
		}

		postGeneration();
	}

	protected boolean parseArg(String key, String value) {
		if (key.equals("speedMean")) {
			speedMean = Double.parseDouble(value);
			return true;
		}
		else if (key.equals("speedDelta")) {
			speedDelta = Double.parseDouble(value);
			return true;
		}
		else if (key.equals("pauseMean")) {
			pauseMean = Double.parseDouble(value);
			return true;
		}
		else if (key.equals("pauseDelta")) {
			pauseDelta = Double.parseDouble(value);
			return true;
		} else
			return super.parseArg(key, value);
	}

	public void write(String _name) throws FileNotFoundException, IOException {
		String[] p = new String[4];
		p[0] = "speedMean=" + speedMean;
		p[1] = "speedDelta=" + speedDelta;
		p[2] = "pauseMean=" + pauseMean;
		p[3] = "pauseDelta=" + pauseDelta;
		super.writeParametersAndMovement(_name, p);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
		case 'o': // speed mean
			speedMean = Double.parseDouble(val);
			return true;
		case 'p': // speed delta
			speedDelta = Double.parseDouble(val);
			return true;
		case 'k': // pause mean
			pauseMean = Double.parseDouble(val);
			return true;
		case 'l': // pause delta
			pauseDelta = Double.parseDouble(val);
			return true;
		default:
			return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		Scenario.printHelp();
		System.out.println(getInfo().name + ":");
		System.out.println("\t-o <speed mean>");
		System.out.println("\t-p <speed delta>");
		System.out.println("\t-k <pause mean>");
		System.out.println("\t-l <pause delta>");
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