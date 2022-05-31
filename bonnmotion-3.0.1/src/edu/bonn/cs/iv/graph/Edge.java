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

package edu.bonn.cs.iv.graph;

/** This class represents an edge. */

import java.util.Hashtable;

public class Edge {
	protected Node source;
	protected Node dest;
	/** For practical reasons, this variable (saving the weight of the edge) is public. There is not much you can do wrong here, just watch out when using a weight of 0! */
	public int weight;
	
	protected Hashtable<Object,Object> label = null;
	
	public Edge(Node src, Node dst, int weight) {
		if (src.homeGraph() != dst.homeGraph())
			throw new RuntimeException("no intergraph edges!");
		if (src.homeGraph() == null)
			throw new RuntimeException("nodes must belong to a graph!");
		source = src;
		dest = dst;
		this.weight = weight;
	}
	
	public Node srcNode() {
		return source;
	}
	
	public Node dstNode() {
		return dest;
	}
	
	public Object getLabel(Object key) {
		if (label == null)
			return null;
		else
			return label.get(key);
	}
	
	public void setLabel(Object key, Object value) {
		if (label == null)
			label = new Hashtable<Object,Object>();
		label.put(key, value);
	}
	
	public Object removeLabel(Object key) {
		if (label == null)
			return null;
		else
			return label.remove(key);
	}

	public String toString() {
		return source.toString() + " " + dest.toString() + " " + weight;
	}
}
