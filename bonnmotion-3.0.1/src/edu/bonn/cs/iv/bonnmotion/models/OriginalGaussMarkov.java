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
public class OriginalGaussMarkov extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("OriginalGaussMarkov");
        info.description = "Application to construct mobility scenarios according to the original Gauss-Markov model";
        
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

	/** Update frequency [s]. */
	protected double updateFrequency = 2.5;

	protected double velocityStdDev = 0.1;

	protected double avgSpeed = 0.;
	protected double maxSpeed = Double.POSITIVE_INFINITY;

	protected double alpha = 1.0;
	protected double alpha2 = 0.;
	protected double alpha3 = 0.;
	
	protected boolean parseArg(String key, String value) {
		if (key.equals("updateFrequency")) {
			updateFrequency = Double.parseDouble(value);
			return true;
		} else if (key.equals("maxSpeed")) {
			maxSpeed = Double.parseDouble(value);
			return true;
		} else if (key.equals("avgSpeed")) {
			avgSpeed = Double.parseDouble(value);
			return true;
		} else if (key.equals("velocityStdDev")) {
			velocityStdDev = Double.parseDouble(value);
			return true;
		} else if (key.equals("alpha")) {
			setAlpha(Double.parseDouble(value));
			return true;
		} else
			return super.parseArg(key, value);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'a' :
				avgSpeed = Double.parseDouble(val);
				return true;
			case 'm' :
				maxSpeed = Double.parseDouble(val);
				return true;
			case 'q' :
				updateFrequency = Double.parseDouble(val);
				return true;
			case 's' :
				velocityStdDev = Double.parseDouble(val);
				return true;
			case 'w' :			
				setAlpha(Double.parseDouble(val));
				return true;
			default :
				return super.parseArg(key, val);
		}
	}

	public OriginalGaussMarkov(String[] args) {
		setAlpha(this.alpha);
		go(args);
	}

	public OriginalGaussMarkov(
		int nodes,
		double x,
		double y,
		double duration,
		double ignore,
		long randomSeed,
		double alpha,
		double updateFrequency,
		double velocityStdDev,
		double avgSpeed,
		double maxSpeed) {
		super(nodes, x, y, duration, ignore, randomSeed);
		setAlpha(alpha);
		this.updateFrequency = updateFrequency;
		this.velocityStdDev = velocityStdDev;
		this.avgSpeed = avgSpeed;
		this.maxSpeed = maxSpeed;
		preGeneration();
		generate();
		postGeneration();
	}

	public OriginalGaussMarkov(String args[], Scenario _pre, Integer _transitionMode) {
		// we've got a predecessor, so a transtion is needed
		super( args, _pre, _transitionMode  );
		go(args);
	}

	public void go(String[] args) {
		super.go(args);
		generate();
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
		alpha2 = 1. - alpha;
		alpha3 = Math.sqrt(1. - alpha * alpha);
		
	}

	public void generate() {
		preGeneration();

		alpha3 *= velocityStdDev;
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
				src = new Position(parameterData.x * randomNextDouble(), parameterData.y * randomNextDouble());
				if (!parameterData.nodes[i].add(0.0, src)) {
					System.out.println(getInfo().name + ".<init>: error while adding node movement (1)");
					System.exit(0);
				}
			}

			Position velocityMean = getMeanVelocity(randomNextDouble() * 2. * Math.PI);
			Position velocity = new Position(0., 0.);
			double remaining = updateFrequency;
			
			while (t < parameterData.duration) {
				double t1;
				Position dst = new Position(src.x + velocity.x * remaining, src.y + velocity.y * remaining);

				boolean oobBottom = (dst.y < 0.);
				boolean oobTop = (dst.y > parameterData.y);
				boolean oobLeft = (dst.x < 0.);
				boolean oobRight = (dst.x > parameterData.x);
				if (oobBottom || oobTop || oobLeft || oobRight) {
					if (oobTop && oobRight) {
						double r1 = (dst.y - src.y) / (dst.x - src.x);
						double r2 = (dst.y - parameterData.y) / (dst.x - parameterData.x);
						if (r1 < r2)
							oobRight = false;
						else
							oobTop = false;
					} else if (oobTop && oobLeft) {
						double r1 = (dst.y - src.y) / (dst.x - src.x);
						double r2 = (dst.y - parameterData.y) / dst.x;
						if (r1 < r2)
							oobTop = false;
						else
							oobLeft = false;
					} else if (oobBottom && oobLeft) {
						double r1 = (dst.y - src.y) / (dst.x - src.x);
						double r2 = dst.y / dst.x;
						if (r1 < r2)
							oobLeft = false;
						else
							oobBottom = false;
					} else if (oobBottom && oobRight) {
						double r1 = (dst.y - src.y) / (dst.x - src.x);
						double r2 = dst.y / (dst.x - parameterData.x);
						if (r1 < r2)
							oobBottom = false;
						else
							oobRight = false;
					}
					double wNew = -1.;
					double tbounce = Double.POSITIVE_INFINITY;
					if (oobTop) {
						tbounce = (wNew = (parameterData.y - src.y) / (dst.y - src.y)) * remaining;
						dst = new Position(src.x + tbounce * velocity.x, parameterData.y);
						velocity = new Position(velocity.x, -velocity.y);
						velocityMean = new Position(velocityMean.x, -velocityMean.y);
					} else if (oobBottom) {
						tbounce = (wNew = src.y / (src.y - dst.y)) * remaining;
						dst = new Position(src.x + tbounce * velocity.x, 0.);
						velocity = new Position(velocity.x, -velocity.y);
						velocityMean = new Position(velocityMean.x, -velocityMean.y);
					} else if (oobRight) {
						tbounce = (wNew = (parameterData.x - src.x) / (dst.x - src.x)) * remaining;
						dst = new Position(parameterData.x, src.y + tbounce * velocity.y);
						velocity = new Position(-velocity.x, velocity.y);
						velocityMean = new Position(-velocityMean.x, velocityMean.y);
					} else if (oobLeft) {
						tbounce = (wNew = src.x / (src.x - dst.x)) * remaining;
						dst = new Position(0., src.y + tbounce * velocity.y);
						velocity = new Position(-velocity.x, velocity.y);
						velocityMean = new Position(-velocityMean.x, velocityMean.y);
					}
					if ((wNew < 0.) || (tbounce > remaining)) {
						System.out.println(getInfo().name + ".generate: Obviously, something is going wrong here! wNew=" + wNew + " tbounce=" + tbounce + " remaining=" + remaining);
						System.exit(0);
					}
					t1 = t + tbounce;
					remaining -= tbounce;
				} else {
					t1 = t + remaining;
					remaining = updateFrequency;
					velocity = new Position(alpha * velocity.x + alpha2 * velocityMean.x + alpha3 * randomNextGaussian(), alpha * velocity.y + alpha2 * velocityMean.y + alpha3 * randomNextGaussian());
					double speed = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
					if (speed > maxSpeed) {
						double scale = maxSpeed / speed;
						velocity = new Position(scale * velocity.x, scale * velocity.y);
					}
				}
				if (!parameterData.nodes[i].add(t1, dst)) {
					System.out.println(getInfo().name + ".<init>: error while adding node movement (2)");
					System.exit(0);
				}
				src = dst;
				t = t1;
			}
		}

		postGeneration();
	}

	public Position getMeanVelocity(double dir) {
		return new Position(avgSpeed * Math.cos(dir), avgSpeed * Math.sin(dir));
	}

	public void write(String _name) throws FileNotFoundException, IOException {
		String[] p = new String[5];

		p[0] = "updateFrequency=" + updateFrequency;
		p[1] = "velocityStdDev=" + velocityStdDev;
		p[2] = "alpha=" + alpha;
		p[3] = "avgSpeed=" + avgSpeed;
		p[4] = "maxSpeed=" + maxSpeed;
		
		super.writeParametersAndMovement(_name, p);
	}

	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		Scenario.printHelp();
		System.out.println( getInfo().name + ":" );
		System.out.println("\t-a <avg speed (default=0)>");
		System.out.println("\t-m <max speed (default=infinite)>");
		System.out.println("\t-q <velocity update frequency>");
		System.out.println("\t-s <velocity standard deviation (for each dimension)>");
		System.out.println("\t-w <alpha>");
	}
	
	
}
