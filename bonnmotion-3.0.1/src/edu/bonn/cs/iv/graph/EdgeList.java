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

import edu.bonn.cs.iv.util.*;

/** This class saves adjacency lists. */

public class EdgeList {
	class EdgeWrap implements Sortable {
		public final Edge e;
		public final int key;
		public EdgeWrap(Edge e, int key) {
			this.e = e;
			this.key = key;
		}
		public int getKey() {
			return key;
		}
	}
	
	protected SortedList list = new SortedList();
	
	protected boolean keyIsSrcNode;
    
	/**
 * 	@param keyIsSrcNode If this is true, the internal list is sorted by source nodes, else by destination nodes. Naturally, the prior is used for a list of incoming links and the latter for a list of outgoing links. */
	public EdgeList(boolean keyIsSrcNode) {
		this.keyIsSrcNode = keyIsSrcNode;
	}

	protected Edge unwrap(Sortable s) {
		if (s == null)
			return null;
		else {
			EdgeWrap w = (EdgeWrap)s;
			return w.e;
		}
	}

	/** See SortedList. */
    public int add(Edge e) {
		return (list.add(new EdgeWrap(e, keyIsSrcNode ? e.srcNode().getKey() : e.dstNode().getKey())));
    }

	/** See SortedList. */
    public Edge delete(int key) {
		return unwrap(list.delete(key));
    }
	
	/** See SortedList. */
	public Edge deleteElementAt(int p) {
		return unwrap(list.deleteElementAt(p));
	}

	/** See SortedList. */
	public Edge elementAt(int p) {
		return unwrap(list.elementAt(p));
	}

	/** See SortedList. */
	public Edge get(int key) {
		Sortable s = list.get(key);
		return unwrap(s);
	}

	/** See SortedList. */
	public int indexOf(int key) {
		return list.indexOf(key);
	}

	/** See SortedList. */
	public int size() {
		return list.size();
	}
}
