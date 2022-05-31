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

package edu.bonn.cs.iv.bonnmotion.apps.statistics;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

public class TotalDistance {
	protected Scenario scenario = null;
	protected StatisticsNGServer sngs = null;
	
	protected int nodeCount = -1;
	protected Double[] totalDistance = null;
		
	public TotalDistance(StatisticsNGServer statistics, Scenario s) {
		sngs = statistics;
		scenario = s;
		nodeCount = scenario.nodeCount();
		totalDistance = new Double[nodeCount];
		for(int i=0;i<nodeCount;i++) {
			totalDistance[i] = null;
		}
	}
		
	public double getTotalDistance() {
		double totalDistance = 0;
		for(int node=0;node<nodeCount;node++) {
			totalDistance += getTotalDistance(node);
		}
		return totalDistance;
	}
	
	public double getTotalDistance(final int nodeNo) {
		if(nodeNo >= nodeCount) {
			throw new IndexOutOfBoundsException("Requested " + nodeNo + " but scenario has only " + nodeCount + " nodes");
		}
		
		if(totalDistance[nodeNo] == null) {
			MobileNode node = scenario.getNode(nodeNo);
			final int waypoints = node.getNumWaypoints();
			double distance = 0;
			Waypoint lastWaypoint = node.getWaypoint(0);
			for(int i=1;i<waypoints;i++) {
				Waypoint currentWaypoint = node.getWaypoint(i);
				distance += lastWaypoint.pos.distance(currentWaypoint.pos);
			}
			
			totalDistance[nodeNo] = distance;
		}
		
		return totalDistance[nodeNo];
	}
}
