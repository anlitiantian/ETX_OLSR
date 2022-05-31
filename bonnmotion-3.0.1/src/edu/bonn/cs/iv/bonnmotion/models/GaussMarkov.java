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
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.ScenarioLinkException;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

/** Application to construct Gauss-Markov mobility scenarios. */

public class GaussMarkov extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("GaussMarkov");
        info.description = "Application to construct GaussMarkov mobility scenarios";
        
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
    
	private static final double twoPi = 2. * Math.PI;

	/** Update frequency [s]. */
	protected double updateFrequency = 2.5;
	/** Maximum speed [m/s]. */
	protected double maxspeed = 1.5;
	/** Border width [m]. */
	protected double angleStdDev = 0.125 * Math.PI;
	/** Speed standard deviation [m/s]. */
	protected double speedStdDev = 0.5;
	/** prevent nodes from running out of the simulation are? */
	protected boolean checkBounds = false;
	/** Initialize speed with gaussian distribution */
	protected boolean gaussSpeed = false;
	/** Force uniform speed distribution */
	protected boolean uniformSpeed = false;
	protected double minspeed = 0.0;

	protected double inputX = 0;
	protected double inputY = 0;

	protected boolean parseArg(String key, String value) {
		if (key.equals("updateFrequency")) {
			updateFrequency = Double.parseDouble(value);
			return true;
		} else if (key.equals("maxspeed")) {
			maxspeed = Double.parseDouble(value);
			return true;
		} else if (key.equals("angleStdDev")) {
			angleStdDev = Double.parseDouble(value);
			return true;
		} else if (key.equals("speedStdDev")) {
			speedStdDev = Double.parseDouble(value);
			return true;
		} else if (key.equals("randomSeed")) {
			parameterData.randomSeed = Long.parseLong(value);
			return true;
		} else if (key.equals("x")) {
			return true;
		} else if (key.equals("y")) {
			return true;
		} else if (key.equals("inputX")) {
			parameterData.x = Double.parseDouble(value);
			return true;
		} else if (key.equals("inputY")) {
			parameterData.y = Double.parseDouble(value);
			return true;
		} else if (key.equals("bounce")) {
			if (value.equals("true")) checkBounds = true;
			return true;
		} else if (key.equals("initGauss")) {
			if (value.equals("true")) gaussSpeed = true;
			return true;
		} else if (key.equals("uniformSpeed")) {
			if (value.equals("true")) uniformSpeed = true;
			return true;
		} else
			return super.parseArg(key, value);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'a':
				angleStdDev = Double.parseDouble(val);
				return true;
			case 'h':
				maxspeed = Double.parseDouble(val);
				return true;
			case 'q':
				updateFrequency = Double.parseDouble(val);
				return true;
			case 's':
				speedStdDev = Double.parseDouble(val);
				return true;
			case 'b':
				checkBounds = true;
				return true;
			case 'm':
				minspeed = Double.parseDouble(val);
				if (minspeed < 0.)
					minspeed = 0.;
				return true;
			case 'g':
				gaussSpeed = true;
				return true;
			case 'u':
				uniformSpeed = true;
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public GaussMarkov(String[] args) {
		go(args);
	}

	public GaussMarkov(int nodes, double x, double y, double duration, double ignore, long randomSeed,
			double updateFrequency, double maxspeed, double angleStdDev, double speedStdDev) {
		super(nodes, x, y, duration, ignore, randomSeed);
		this.updateFrequency = updateFrequency;
		this.maxspeed = maxspeed;
		this.angleStdDev = angleStdDev;
		this.speedStdDev = speedStdDev;
		preGeneration();
		generate();
		postGeneration();
	}

	public GaussMarkov(String args[], Scenario _pre, Integer _transitionMode) {
		// we've got a predecessor, so a transtion is needed
		super(args, _pre, _transitionMode);
		go(args);
	}

	public void go(String[] args) {
		super.go(args);
		generate();
	}

	public void generate() {
		preGeneration();

		double maxX = parameterData.x;
		double maxY = parameterData.y;
		double minX = 0;
		double minY = 0;

		if (maxspeed < minspeed) {
			double tempspeed = minspeed;
			minspeed = maxspeed;
			maxspeed = tempspeed;
		}

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
			else {
				src = new Position(parameterData.x * randomNextDouble(), parameterData.y * randomNextDouble());
				if (!parameterData.nodes[i].add(0.0, src)) {
					System.out.println(getInfo().name + ".<init>: error while adding node movement (1)");
					System.exit(0);
				}
			}

			double dir = randomNextDouble() * 2 * Math.PI;
			double speed = (randomNextDouble() * (maxspeed - minspeed)) + minspeed;
			if (gaussSpeed) {
				speed = getNewSpeed((maxspeed + minspeed) / 2.);
			}

			boolean intervalShortened = false;

			while (t < parameterData.duration) {
				double t1 = t + updateFrequency;

				if (!checkBounds) {
					dir = getNewDir(dir, src);
				}
				else {
					if (intervalShortened) {
						// reset status:
						intervalShortened = false;
					}
					else {
						dir = getNewDir(dir, src);
					}
				}

				speed = getNewSpeed(speed);
				if (speed > 0.0) {
					Position dst = new Position(src.x + Math.cos(dir) * updateFrequency * speed, src.y + Math.sin(dir) * updateFrequency * speed);

					if (checkBounds) {
						/* check if node will leave the simulation area */
						if ((dst.x < 0) || (dst.x > parameterData.x) || (dst.y < 0) || (dst.y > parameterData.y)) {
							/* calculate intersection with boundarys */
							double yR, yL, xU, xL, ratio;
							ratio = (dst.y - src.y) / (dst.x - src.x);

							/* right boundary */
							/*
							 * get y for dst on boundary ratio = (yR-src.y)/x-src.x <=> ratio *
							 * (x-src.x) + src.y = yr
							 */
							yR = ratio * (parameterData.x - src.x) + src.y;
							/* left boundary */
							/* get y for dst on boundary */
							yL = ratio * (-src.x) + src.y;
							/* upper boundary */
							/* get x for dst on boundary */
							/*
							 * ratio = (0-src.y)/(xU-src.x) <=> (-src.y)/ratio +src.x = xU
							 */
							xU = (-src.y) / ratio + src.x;
							/* lower boundary */
							/* get x for dst on boundary */
							xL = (parameterData.y - src.y) / ratio + src.x;

							double newX = 0.0, newY = 0.0;
							if ((yL >= 0) && (yL <= parameterData.y) && (dir > 0.5 * Math.PI) && (dir < 1.5 * Math.PI)) {
								newY = yL;
								newX = 0;
								dir = (twoPi + Math.PI - dir) % twoPi;
							}
							else if ((yR >= 0) && (yR <= parameterData.y) && ((dir > 1.5 * Math.PI) || (dir < 0.5 * Math.PI))) {
								newY = yR;
								newX = parameterData.x;
								dir = (twoPi + Math.PI - dir) % twoPi;
							}

							if ((xU >= 0) && (xU <= parameterData.x) && (dir > Math.PI) && (dir < 2.0 * Math.PI)) {
								newX = xU;
								newY = 0;
								dir = (twoPi + twoPi - dir) % twoPi;
							}
							else if ((xL >= 0) && (xL <= parameterData.x) && (dir > 0.0 * Math.PI) && (dir < Math.PI)) {
								newX = xL;
								newY = parameterData.y;
								dir = (twoPi + twoPi - dir) % twoPi;
							}

							Position newdst = new Position(newX, newY);

							// we have a new point => new interval for the given speed
							// length of the new vector / length of the old vector =
							// new interval / old interval
							// =>
							double vl1 = Math.sqrt((newdst.y - src.y) * (newdst.y - src.y) + (newdst.x - src.x) * (newdst.x - src.x));
							double vl2 = Math.sqrt((dst.y - src.y) * (dst.y - src.y) + (dst.x - src.x) * (dst.x - src.x));
							double newInterv = vl1 / vl2 * updateFrequency;
							t1 = t + newInterv;
							// memorize the shortening of the interval
							intervalShortened = true;
							dst = newdst;
						}
					}
					else {
						if (dst.x < minX)
							minX = dst.x;
						else if (dst.x > maxX)
							maxX = dst.x;
						if (dst.y < minY)
							minY = dst.y;
						else if (dst.y > maxY)
							maxY = dst.y;
					}

					if (!parameterData.nodes[i].add(t1, dst)) {
						System.out.println(getInfo().name + ".<init>: error while adding node movement (2)");
						System.exit(0);
					}
					src = dst;
				}
				t = t1;
			}
		}

		inputX = parameterData.x;
		inputY = parameterData.y;
		// setting new borders and shifting the waypoints ...
		double shiftX = Math.abs(minX);
		double shiftY = Math.abs(minY);
		parameterData.x = Math.ceil(maxX + shiftX);
		parameterData.y = Math.ceil(maxY + shiftY);
		for (int i = 0; i < parameterData.nodes.length; i++)
			parameterData.nodes[i].shiftPos(shiftX, shiftY);

		postGeneration();
	}

	public double getNewDir(double oldDir, Position pos) {
		if (checkBounds) {
			double newDir = (randomNextGaussian() * angleStdDev + oldDir) % twoPi;
			while (newDir < 0)
				newDir += twoPi;
			System.out.println("Dir " + newDir);
			return newDir;
		}
		else {
			// move away from the border in case we are getting too close
			if (pos.x < 0)
				if (pos.y < 0)
					oldDir = 0.25 * Math.PI;
				else if (pos.y > parameterData.y)
					oldDir = 1.75 * Math.PI;
				else
					oldDir = 0.0;
			else if (pos.x > parameterData.x)
				if (pos.y < 0)
					oldDir = 0.75 * Math.PI;
				else if (pos.y > parameterData.y)
					oldDir = 1.25 * Math.PI;
				else
					oldDir = Math.PI;
			else if (pos.y < 0)
				oldDir = 0.5 * Math.PI;
			else if (pos.y > parameterData.y)
				oldDir = 1.5 * Math.PI;
			return randomNextGaussian() * angleStdDev + oldDir;
		}
	}

	public double getNewSpeed(double oldSpeed) {
		double speed = oldSpeed + randomNextGaussian() * speedStdDev;
		if (uniformSpeed) {
			while ((speed < minspeed) || (speed > maxspeed)) {
				if (speed < minspeed)
					speed = minspeed + (minspeed - speed);
				else if (speed > maxspeed)
					speed = maxspeed - (speed - maxspeed);
			}
		}
		else {
			if (speed < minspeed)
				speed = minspeed;
			else if (speed > maxspeed)
				speed = maxspeed;
		}
		return speed;
	}

	public void write(String _name) throws FileNotFoundException, IOException {
		String[] p = new String[9];

		p[0] = "updateFrequency=" + updateFrequency;
		p[1] = "maxspeed=" + maxspeed;
		p[2] = "angleStdDev=" + angleStdDev;
		p[3] = "speedStdDev=" + speedStdDev;
		p[4] = "inputX=" + inputX;
		p[5] = "inputY=" + inputY;
		p[6] = "bounce=" + checkBounds;
		p[7] = "initGauss=" + gaussSpeed;
		p[8] = "uniformSpeed=" + uniformSpeed;

		super.writeParametersAndMovement(_name, p);
	}

	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		Scenario.printHelp();
		System.out.println(getInfo().name + ":");
		System.out.println("\t-a <angle standard deviation>");
		System.out.println("\t-h <max. speed>");
		System.out.println("\t-q <speed, angle update frequency>");
		System.out.println("\t-s <speed standard deviation>");
		System.out.println("\t-b bounce nodes at area boundaries");
		System.out.println("\t-m <min. speed (default = 0)>");
		System.out.println("\t-g gauss distribution of initial node speeds\n\t    centered around (minspeed + maxspeed)/2");
		System.out.println("\t-u force uniform speed distribution");
	}

}
