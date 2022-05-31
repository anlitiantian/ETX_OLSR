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

/** This class implements a node. */
public class Node implements Sortable {
	/** Node ID. */
	public final int id;
	
	/** List with outgoing edges. */
	protected EdgeList succList = new EdgeList(false);
	
	/** List with incoming edges. */
	protected EdgeList predList = new EdgeList(true);
	
	/** Graph to which this nodes belongs. */
	protected Graph graph;

	/** Used to generate node IDs. */
	protected static int idCount;
	
	/** Generate a hopefully unused node ID, must still be checked though. */
	public static int nextID() {
		return (0x00000100 | (idCount++));
	}

	/** Create a "stand-alone" node not belonging to any graph (yet). */
	public Node(int id) {
		this.id = id;
		graph = null;
	}

	/** Create a node that belongs to a graph. */
	public Node(Graph g) {
		int id;
		while (g.getNode(id = nextID()) != null);
		this.id = id;
		graph = g;
		g.addNode(this);
	}

	/** Create a node with a specified ID belonging to a graph. */
	public Node(int id, Graph g) {
		this.id = id;
		graph = g;
		g.addNode(this);
	}

	/** Add an outgoing edge.
 * 	@param succ Destination node.
 * 	@param weight Edge weight.
 * 	@return Edge added. */
	public Edge addSucc(Node succ, int weight) {
		Edge e = new Edge(this, succ, weight);
		if (succList.add(e) == -1)
			throw new RuntimeException("edge " + e.srcNode() + " -> " + e.dstNode() + " already exists!");
		succ.predList.add(e);
		return e;
	}

	/** Change edge weight.
 * 	@param succ Destination node.
 * 	@param delta Value to be added to edge weight. */
	public void adjustWeight(Node succ, int delta) {
		Edge e = succList.get(succ.getKey());
		if (e == null)
			addSucc(succ, delta);
		else {
    		e.weight += delta;
    		if (e.weight == 0)
	    		delSucc(succ.getKey());
		}
	}

	/** Retrieve an outgoing link.
 * 	@param succID ID of destination node.
 * 	@return Sought edge or null, if non-existent. */
	public Edge getSucc(int succID) {
		return succList.get(succID);
	}
	
	public Edge getPredec(int predID) {
		return predList.get(predID);
	}

	/** Get weight of an outgoing edge.
 * 	@param succID ID of destination node.
 * 	@return Edge's weight or 0, if non-existent. */
	public int getSuccWeight(int succID) {
		Edge e = succList.get(succID);
		if (e == null)
			return 0;
		else
			return e.weight;
	}

	/** Remove an outgoing edge.
 * 	@param succID ID of destination node.
 * 	@return Removed edge or null, if non-existent. */
	public Edge delSucc(int succID) {
		Edge e = succList.delete(succID);
		if (e != null)
			e.dstNode().predList.delete(id);
		return e;
	}

	/** Number of outgoing edges. */
	public int outDeg() {
		return succList.size();
	}

	/** Get outgoing edge at certain position in internal list.
 * 	@param pos Position in the internal list.
 * 	@return Sought edge. */
	public Edge succAt(int pos) {
		return succList.elementAt(pos);
	}

	/** Remove outgoing edge at certain position in internal list.
 * 	@param pos Position in the internal list.
 * 	@return Removed edge. */
	public Edge delSuccAt(int pos)  {
        Edge e = succList.deleteElementAt(pos);
        e.dstNode().predList.delete(id);
        return e;
	}

	/** Number of incoming edges. */
	public int inDeg() {
		return predList.size();
	}
	
	/** Get outgoing edge at certain position in internal list.
 * 	@param pos Position in the internal list.
 * 	@return Sought edge. */
	public Edge predAt(int pos) {
		return predList.elementAt(pos);
	}

	/** Remove outgoing edge at certain position in internal list.
 * 	@param pos Position in the internal list.
 * 	@return Removed edge. */
	public Edge delPredAt(int pos)  {
		Edge e = predList.deleteElementAt(pos);
        e.srcNode().succList.delete(id);
        return e;
	}

	/** Get this node's home graph. */
	public Graph homeGraph() {
		return graph;
	}
	
	/** Remove this node from its graph. Cooperates with Graph.delNode. */
	public void removeFromGraph() {
		if (graph != null) {
			int end;
			while ((end = outDeg()) > 0)
				delSuccAt(end - 1);
			while ((end = inDeg()) > 0)
				delPredAt(end - 1);
			if (graph.getNode(id) != null)
				graph.delNode(this);
			else
				graph = null;
		}
	}

	/** Add this node to a graph. Cooperates with Graph.addNode. */
	public boolean addToGraph(Graph g) {
		if (graph == null) {
			graph = g;
			if (g.getNode(id) == null)
				g.addNode(this);
			return true;
		}
		else
			return false;
	}

	/** Get the node ID. */
	public int getKey() {
		return id;
	}
	
	/** Get the node ID. */
	public String toString() {
		return String.valueOf(id);
	}

    /** is n in my successor list */
    public boolean has_succ(int n_id) {
	    for (int i = 0; i < outDeg(); i++) {
			if (succAt(i).dstNode().getKey() == n_id) 
			    return true;
	    }
	    return false;
    } 

    /** return number of bi-directional links */
    public int number_bi() {
	    int bis = 0;
	    for (int j = 0; j < outDeg(); j++) {
			if (succAt(j).dstNode().has_succ(id))
			    bis++;
	    }
	    return bis;
    }
}
