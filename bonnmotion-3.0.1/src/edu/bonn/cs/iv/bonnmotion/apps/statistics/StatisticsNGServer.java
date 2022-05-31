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

import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.apps.helper.TopologyChangeTimes;
import edu.bonn.cs.iv.bonnmotion.apps.helper.TopologyChangeTimesResult;

public class StatisticsNGServer {
	protected Scenario scenario = null;
	
	protected int nodeCount = -1;
	protected AverageSpeed averageSpeed = null;
	protected TotalDistance totalDistance = null;
	protected TopologyChangeTimesResult topologyChangeTimes = null;
	
	public StatisticsNGServer(Scenario s, double transmissionRange) {
		init(s);
		transmissionRangeMetrics(transmissionRange);
		independentMetrics();
	}
	
	public StatisticsNGServer(Scenario s) {
		init(s);
		independentMetrics();
	}
	
	protected void init(Scenario s) {
		scenario = s;
		nodeCount = scenario.nodeCount();
	}
	
	protected void transmissionRangeMetrics(final double transmissionRange) {
		TopologyChangeTimes tct = new TopologyChangeTimes(transmissionRange,scenario.getNode(),scenario.getDuration());
		topologyChangeTimes = tct.getTopologyChanges();
	}
	
	protected void independentMetrics() {
		averageSpeed = new AverageSpeed(this,scenario);
		totalDistance = new TotalDistance(this,scenario);
	}	
	
	public double getTotalDistance() {
		return totalDistance.getTotalDistance();
	}
	
	public double getTotalDistance(final int nodeNo) {
		return totalDistance.getTotalDistance(nodeNo);
	}
	
	public double getAverageSpeed() {
		return averageSpeed.getAverageSpeed();
	}
	
	public double getAverageSpeed(final int nodeNo) {
		return averageSpeed.getAverageSpeed(nodeNo);
	}
}
