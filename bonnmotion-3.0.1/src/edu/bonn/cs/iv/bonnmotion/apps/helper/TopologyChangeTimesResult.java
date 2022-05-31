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
import java.util.Vector;

public class TopologyChangeTimesResult {
	public class InRangeInfo {
		final public double time;
		final public int srcNode;
		final public int dstNode;
		final public boolean inRange;
		public InRangeInfo(final double time, final int srcNode, final int dstNode, boolean inRange) {
			this.time = time;
			this.srcNode = srcNode;
			this.dstNode = dstNode;
			this.inRange = inRange;
		}
	}
	
	public Vector<InRangeInfo> rangeInfo;
	protected HashSet<Double> topologyChangeTimes;	
	
	public TopologyChangeTimesResult() {
		rangeInfo = new Vector<InRangeInfo>(0,1);
		topologyChangeTimes = new HashSet<Double>();	
	}
	
	public void addChangeTime(final double time) {
		topologyChangeTimes.add(time);
	}
	
	public void addChangeTime(final double time, final int srcNode, final int dstNode, boolean inRange) {
		rangeInfo.add(new InRangeInfo(time,srcNode,dstNode,inRange));
		addChangeTime(time);
	}
	
	public ArrayList<Double> getChangeTimes() {
		ArrayList<Double> sortedChangeTimes = new ArrayList<Double>();
		
		sortedChangeTimes.addAll(topologyChangeTimes);
		Collections.sort(sortedChangeTimes);
		
		return sortedChangeTimes;
	}
}
