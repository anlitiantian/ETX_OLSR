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

import java.util.Iterator;
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;

public class LongestLinkMetrics {
	Scenario s;
	public LongestLinkMetrics(Scenario scenario) {
		s = scenario;
	}
	
	public double longestLink(final Vector<Double> times) {
		double longest = Double.MIN_VALUE;
		
		Iterator<Double> it = times.iterator();
		while(it.hasNext()) {
			final double next = it.next();
			final double nextLength = longest(next);
			if(nextLength > longest) {
				longest = nextLength;
			}
		}
		
		return longest;
	}
	
	public double longestLink(final double intervalLength) {
		if(s.getNode().length <= 1) {
	            System.err.println("Only " + s.getNode().length + " node in the scenario! No links available");
	            System.exit(1);
	    }
	
		double duration = s.getDuration();
		double time = 0;
	
		double longestLink = 0;
		
		while(time <= duration) {
			final double linkLen = longest(time);
			if(linkLen > longestLink) {
				longestLink = linkLen;
			}
						
			time += intervalLength;
		}
	
		return longestLink;
	}
		
	public double longest(final double time) {
		MobileNode[] node = s.getNode();
		double longestEdge = Double.MIN_VALUE;
		Vector<Position> inTheGraph = new Vector<Position>(node.length,1);
		Vector<Position> notInTheGraph = new Vector<Position>(node.length,1);
		
		inTheGraph.add(node[0].positionAt(time));
		
		for(int i=0;i<node.length;i++) {
			notInTheGraph.add(node[i].positionAt(time));
		}

		double edgeLength;
		Position next;
		double edge;	
		while(!notInTheGraph.isEmpty()) { //Add edges until graph is fully connected
			next = null;
			edgeLength = Double.MAX_VALUE;
			for(int i=0;i<inTheGraph.size();i++) {
				for(int j=0;j<notInTheGraph.size();j++) {
					edge = inTheGraph.elementAt(i).distance(notInTheGraph.elementAt(j));
					if(edge < edgeLength) {
						edgeLength = edge;
						next = notInTheGraph.elementAt(j);
					}
				}	
			}

			inTheGraph.add(next);
			notInTheGraph.remove(next);

			if(edgeLength > longestEdge) { longestEdge = edgeLength; }
		}
		
		return longestEdge;
	}
}
