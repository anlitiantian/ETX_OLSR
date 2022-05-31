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

package edu.bonn.cs.iv.bonnmotion.apps.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.Position;


public class TopologyChangeTimes {
	protected final double range;
	protected MobileNode nodes[] = null;
	protected double duration;
	
	protected TopologyChangeTimesResult tctr;
	
	public TopologyChangeTimes(double transmissionRange, MobileNode scenarioNodes[], double scenarioDuration) {
		range = transmissionRange;
		nodes = scenarioNodes;
		duration = scenarioDuration;
		tctr = new TopologyChangeTimesResult();
		
		if(range <= 0) {
			throw new IllegalArgumentException("No interval given but range < 0. Cannot calculate topology change times.");
		}
		
		if(nodes == null) {
			throw new IllegalArgumentException("nodes == null");
		}
	}
	
	public TopologyChangeTimesResult getTopologyChanges() {
		ArrayList<Double> directionChangeTimes = new ArrayList<Double>();
		directionChangeTimes.addAll(getDirectionOrSpeedChangeTimes());
		Collections.sort(directionChangeTimes);
		
		tctr.addChangeTime(0.0);
		
		for(int i=0;i<directionChangeTimes.size()-1;i++) {
			final double windowStart = directionChangeTimes.get(i);
			final double windowEnd = directionChangeTimes.get(i+1);
			
			for(int src=0;src<nodes.length;src++) {
				for(int dst=src+1;dst<nodes.length;dst++) {
					final Position srcPositionStart = new Position(nodes[src].positionAt(windowStart));
					final Position srcPositionEnd = new Position(nodes[src].positionAt(windowEnd));
					final Position dstPositionStart = new Position(nodes[dst].positionAt(windowStart));
					final Position dstPositionEnd = new Position(nodes[dst].positionAt(windowEnd));
					
					final boolean inRangeAtStart = (srcPositionStart.distance(dstPositionStart) < range);
					final boolean inRangeAtEnd = (srcPositionEnd.distance(dstPositionEnd) < range);
					
					if(inRangeAtStart != inRangeAtEnd) {						
						final double duration = windowEnd - windowStart;
						
						Position srcDirectionVector = Position.subtract(srcPositionEnd, srcPositionStart).multiply(1.0/duration);
						Position dstDirectionVector = Position.subtract(dstPositionEnd, dstPositionStart).multiply(1.0/duration);
							
						dstDirectionVector.subtract(srcDirectionVector);
						
						final double relativeSpeed = dstDirectionVector.norm();
						
						dstDirectionVector.multiply(duration);
						
						final Position dstStartDisplacement = Position.subtract(dstPositionStart,srcPositionStart);
						final Position dstEndDisplacement = Position.add(dstStartDisplacement,dstDirectionVector);
						
						List<Position> intersections = intersect(dstStartDisplacement,dstEndDisplacement,new Position(),range);

						double t;
						if(intersections.size() == 1) {
							Position p = intersections.get(0);
							t = windowStart + p.norm()/relativeSpeed;
						} else if(intersections.size() == 2) {
							final Position p = intersections.get(0);
							final Position q = intersections.get(1);

							final double COMPARE_FOR_ZERO_WITH_RESPECT_TO_ROUNDING_ERROR = 1.0;
							boolean pCorrectDirection = dstDirectionVector.angle(p) < COMPARE_FOR_ZERO_WITH_RESPECT_TO_ROUNDING_ERROR;
							boolean qCorrectDirection = dstDirectionVector.angle(q) < COMPARE_FOR_ZERO_WITH_RESPECT_TO_ROUNDING_ERROR;
								
							final double pLen = p.norm();
							final double qLen = q.norm();
							
							if(pCorrectDirection && qCorrectDirection) {
								if(pLen < qLen) {
									qCorrectDirection = false;
								} else {
									pCorrectDirection = false;
								}
							}
							
							t = pCorrectDirection ? windowStart + pLen/relativeSpeed : windowStart + qLen/relativeSpeed;							
						} else {
							throw new IllegalArgumentException("No intersection found");
						}
						tctr.addChangeTime(t,src,dst,!inRangeAtStart);
					}
				}
			}
		}
		
		return tctr;
	}
	
	protected HashSet<Double> getDirectionOrSpeedChangeTimes() {
		HashSet<Double> tempChangeTimes = new HashSet<Double>();
		tempChangeTimes.add(0.0);
		tempChangeTimes.add(duration);
		
		for(int n=0;n<nodes.length;n++) {
			double nodeChangeTimes[] = nodes[n].changeTimes();
			for(int i=0;i<nodeChangeTimes.length;i++) {
				tempChangeTimes.add(nodeChangeTimes[i]);
			}
		}
		
		return tempChangeTimes;
	}
	
	public List<Position> intersect(Position x, Position y, Position m, double r) {
		ArrayList<Position> intersections;
		double a, b, c, d;

		if (x.equals(y)) {
			throw new IllegalArgumentException("x and y are equal");
		}

		intersections = new ArrayList<Position>();

		a = Math.pow(y.x - x.x, 2) + Math.pow(y.y - x.y, 2) + Math.pow(y.z - x.z, 2);
		b = 2 * ((y.x - x.x) * (x.x - m.x) + (y.y - x.y) * (x.y - m.y) + (y.z - x.z) * (x.z - m.z));
		c = Math.pow(m.x, 2) + Math.pow(m.y, 2) + Math.pow(m.z, 2) + Math.pow(x.x, 2) + Math.pow(x.y, 2) + Math.pow(x.z, 2) - 2* (m.x * x.x + m.y *x.y + m.z * x.z) - Math.pow(r, 2);

		d = b * b - 4 * a * c;
		if (d >= 0) {
			double u1;

			u1 = (-b + Math.sqrt(Math.pow(b, 2) - 4 * a * c)) / (2 * a);
			intersections.add(Position.add(x, Position.subtract(y, x).multiply(u1)));
			if (d > 0) {
				double u2;
				u2 = (-b - Math.sqrt(Math.pow(b, 2) - 4 * a * c)) / (2 * a);
				intersections.add(Position.add(x, Position.subtract(y, x).multiply(u2)));
			}
		}		

		return intersections;
	}
}
