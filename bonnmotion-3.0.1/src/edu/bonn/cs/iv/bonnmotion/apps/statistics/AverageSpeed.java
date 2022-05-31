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

public class AverageSpeed {
	protected Scenario scenario = null;
	protected StatisticsNGServer sngs = null;
	
	protected int nodeCount = -1;
	protected Double[] averageSpeed = null;
		
	public AverageSpeed(StatisticsNGServer statistics, Scenario s) {
		sngs = statistics;
		scenario = s;
		nodeCount = scenario.nodeCount();
		averageSpeed = new Double[nodeCount];
		for(int i=0;i<nodeCount;i++) {
			averageSpeed[i] = null;
		}
	}
		
	public double getAverageSpeed() {
		double averageSpeed = 0;
		for(int node=0;node<nodeCount;node++) {
			averageSpeed += getAverageSpeed(node);
		}
		return averageSpeed /= nodeCount;
	}
	
	public double getAverageSpeed(final int nodeNo) {
		if(nodeNo >= nodeCount) {
			throw new IndexOutOfBoundsException("Requested " + nodeNo + " but scenario has only " + nodeCount + " nodes");
		}
		
		if(averageSpeed[nodeNo] == null) {
			averageSpeed[nodeNo] = sngs.getTotalDistance(nodeNo) / scenario.getDuration();
		}
		
		return averageSpeed[nodeNo];
	}
}
