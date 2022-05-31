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

import java.io.*;
import java.util.*;

/** This class implements a directed graph. However, many methods operate und undirected graphs, and expected that the edges are symmetric; if they are not, their output is undefined. */

public class Graph {
	/** Save the nodes of type Node. */
	protected SortedList nodeList = new SortedList();

	/** Create empty graph. */
	public Graph() {}

	/** Reads a graph object from a BufferedReader. */
	public Graph(BufferedReader in) throws IOException {
		String r = readFromFile(in);
		if (r != null)
			throw new IOException("Graph.<init>: cannot load graph: " + r);
	}

	/** Retrieve node with a certain ID. If it does not exist, it will be added to the graph.
 * 	@param id Node ID.
 * 	@return Sought or newly created node. */
	public Node checkNode(int id) {
		Node n = (Node)nodeList.get(id);
		if (n != null)
			return n;
		else {
			n = new Node(id, this);
			nodeList.add(n);
			return n;
		}
	}

	/** Added a node to the graph. Cooperates with Node.addToGraph. */
	public void addNode(Node n) {
		if (((n.homeGraph() != null) && (n.homeGraph() != this)) || (nodeList.add(n) == -1)) {
			System.out.println("Graph.addNode: error adding node");
			System.exit(0);
		}
		if (n.homeGraph() == null)
			n.addToGraph(this);
	}

	/** Retrieve node with a certain ID.
 * 	@param id Node ID.
 * 	@return Sought node or null, if non-existent. */
	public Node getNode(int id) {
		return (Node)nodeList.get(id);
	}

	/** Retrieve node from a certain position of the internal node list.
 * 	@param pos Position of the node in the internal list.
 * 	@return Sought node. */
	public Node nodeAt(int pos) {
		return (Node)nodeList.elementAt(pos);
	}

	/** Get position of a node in the internal list. */
	public int indexOf(int id) {
		return nodeList.indexOf(id);
	}

	/** Remove a node from the graph. Cooperates with Node.removeFromGraph.
 * 	@param v Node to be deleted.
 * 	@return true, if the node was in this graph, else false. */
	public boolean delNode(Node v) {
		if (v.homeGraph() == this) {
			boolean r = nodeList.delete(v.getKey()) != null;
			v.removeFromGraph();
			return r;
		}
		else {
			if (v.homeGraph() == null)
				System.out.println("DEBUG: v.homeGraph() == null");
			else
				System.out.println("DEBUG: v.homeGraph() != null");
			(new RuntimeException("Graph.delNode: wrong graph")).printStackTrace();
			return false;
		}
	}

	/** Get number of nodes in this graph.
 * 	@return Node count. */
	public int nodeCount() {
		return nodeList.size();
	}

	/** Change the weight of an edge. If this edge does not yet exist, its weight is assumed to be 0.
 * 	@param src ID of the source node.
 * 	@param dst ID of the destination ndoe.
 * 	@param delta Value to be added to the weight. */
	public void adjustWeight(int src, int dst, int delta) {
		Node s = checkNode(src);
		Node d = checkNode(dst);
		s.adjustWeight(d, delta);
	}


	/** Retrieve the distances (in edges, regardless of their weight!) of all nodes to a certain node.
 * 	@param start Node, to which the distances shall be calculated.
 *  @return Array with the distances. An array position corresponds to the position of the node in the internal node list. The node at a certain position can be retrieved with nodeAt. The distance -1 means that the node is node reachable. */
	public int[] labelDist(int startID) {
		int level = 0;
		int dist[] = new int[nodeList.size()];
		for (int i = 0; i < dist.length; i++)
			dist[i] = -1;
		dist[nodeList.indexOf(startID)] = 0;
		boolean n = true;
		while (n) {
			n = false;
			for (int i = 0; i < dist.length; i++)
				if (dist[i] == level) {
					Node v = nodeAt(i);
					for (int j = 0; j < v.outDeg(); j++) {
						Node w = v.succAt(j).dstNode();
						int pos = nodeList.indexOf(w.getKey());
						if (dist[pos] == -1) {
							dist[pos] = level + 1;
							n = true;
						}
					}
				}
			level++;
		}
		return dist;
	}

	public double[] unidirRemove(int[] result) {
		if (result != null) {
			result[0] = 0; // removed edges (weight)
			result[1] = 0; // # source nodes whose edges are removed
			result[2] = 0; // # dest nodes whose edges are removed
			result[3] = 0; // bidirectional edges (weight)
		}
		int[] uSrc = new int[nodeList.size()];
		int[] uDst = new int[nodeList.size()];
		for (int i = 0; i < nodeList.size(); i++) {
			uSrc[i] = 0;
			uDst[i] = 0;
		}
		for (int i = 0; i < nodeList.size(); i++) {
			Node v = (Node)nodeList.elementAt(i);
			int vID = v.getKey();
			for (int j = 0; j < v.outDeg(); j++) {
				Edge e = v.succAt(j);
				Node w = e.dest;
				if (w.getSucc(vID) == null) {
					uSrc[vID]++;
					uDst[w.getKey()]++;
					if (result != null)
						result[0] += e.weight;
					v.delSuccAt(j--);
				} else {
					if (result != null)
						result[3] += e.weight;
				}
			}
		}
		int asrc = 0, adst = 0;
		for (int i = 0; i < nodeList.size(); i++) {
			if (uSrc[i] > 0) {
				if (result != null)
					result[1]++;
				asrc += uSrc[i];
			}
			if (uDst[i] > 0) {
				if (result != null)
					result[2]++;
				adst += uDst[i];
			}
		}
		double[] r = null;
		if (result != null) {
			r = new double[2];
			r[0] = (double)asrc / (double)result[1];
			r[1] = (double)adst / (double)result[2];
		}
		return r;
	}

	public int[] biconnectivity(int s) {
		String u = "Graph.biconnectivity():visited";
	
		int nodes = nodeList.size();
		Node ns = nodeAt(s);
	
		boolean[] aPoint = new boolean[nodes];
		for (int i = 0; i < nodes; i++)
			aPoint[i] = false;
		int biconnectivity = 0;
	
		Vector<Integer> bcc = new Vector<Integer>();
	
		int[] num = new int[nodes];
		for (int i = 0; i < nodes; i++)
			num[i] = -1;
		
		int[] pred = new int[nodes];
		for (int i = 0; i < nodes; i++)
			pred[i] = -1;
		
		int[] lo = new int[nodes];
		
		bcc.addElement(new Integer(s));
		int i = 0;
		int v = s;
		Node nv = ns;
		num[v] = i;
		lo[v] = i;
		
		boolean cont = true;

		while (cont) {
			int w;
			do {
				for (w = 0; (w < nv.outDeg()) && (nv.succAt(w).getLabel(u) != null); w++);
				if (w < nv.outDeg()) {
					Node nw = nv.succAt(w).dstNode();
					nv.succAt(w).setLabel(u, "");
					nw.getSucc(nv.getKey()).setLabel(u, "");
					w = indexOf(nw.getKey());
					if (num[w] < 0) {
						pred[w] = v;
						lo[w] = num[w] = ++i;
						bcc.addElement(new Integer(w));
						v = w;
						nv = nw;
					} else
						if (lo[v] > num[w])
							lo[v] = num[w];
				} else
					w = nodes;
			} while (w < nodes);
			if (pred[v] != s) {
				if (lo[v] < num[pred[v]]) {
					if (lo[pred[v]] > lo[v])
						lo[pred[v]] = lo[v];
				} else {
					int c = 1;
					boolean found = false;
					while (! found) {
						int x = bcc.elementAt(bcc.size() - 1).intValue();
						c++;
						found = (x == v);
						bcc.removeElementAt(bcc.size() - 1);
					}
					aPoint[pred[v]] = true;
					if (c > 2) {
						biconnectivity += c * (c - 1);
					}
				}
			} else {
				for (cont = false, w = 0; (! cont) && (w < ns.outDeg()); w++)
					cont = (ns.succAt(w).getLabel(u) == null);
				if (cont) {
					int c = 1;
					boolean found = false;
					while (! found) {
						int x = bcc.elementAt(bcc.size() - 1).intValue();
						c++;
						found = (x == v);
						bcc.removeElementAt(bcc.size() - 1);
					}
					aPoint[s] = true;
					if (c > 2) {
						biconnectivity += c * (c - 1);
					}
				}
			}
			v = pred[v];
			nv = nodeAt(v);
			for (cont = (pred[v] >= 0), w = 0; (! cont) && (w < nv.outDeg()); w++)
				cont = (nv.succAt(w).getLabel(u) == null);
		}
		
		biconnectivity += bcc.size() * (bcc.size() - 1);
		int[] rVal = new int[2];
		rVal[0] = 0;
		for (i = 0; i < nodes; i++)
			if (aPoint[i])
				rVal[0]++;
		rVal[1] = biconnectivity / 2;
		
		for (i = 0; i < nodes; i++) {
			nv = nodeAt(i);
			for (int j = 0; j < nv.outDeg(); j++)
				nv.succAt(j).removeLabel(u);
		}
		
		return rVal;
	}

	/** Copy this graph with all its nodes and edges.
 * 	@return Copy of graph. */
	public Object clone() {
		Graph g = new Graph();
		for (int i = 0; i < nodeList.size(); i++) {
			Node v = (Node)nodeList.elementAt(i);
			int vID = v.getKey();
			g.checkNode(vID);
			for (int j = 0; j < v.outDeg(); j++) {
				Edge e = v.succAt(j);
				g.adjustWeight(vID, e.dstNode().getKey(), e.weight);
			}
		}
		return g;
	}

	/** Check for cycles.
 * 	@return true, if this graph is free of cycles, else false. */
	public boolean isTree() {
		int n = nodeCount();
		byte[] marker = new byte[n];
		int m1 = 0, m2 = 0;
		for (int i = 0; i < marker.length; i++)
			marker[i] = 0;
		while (m2 < n) {
			byte s = (m1 > 0) ? (byte)1: (byte)0;
			int i = 0;
			while (marker[i++] != s);
			Node v = nodeAt(--i);
			marker[i] = 2;
			if (m1 > 0)
				m1--;
			m2++;
			for (int j = 0; j < v.outDeg(); j++) {
				int k = indexOf(v.succAt(j).dstNode().getKey());
				if (marker[k] == 1)
					return false;
				else
					if (marker[k] == 0) {
						marker[k] = 1;
						m1++;
					}
			}
		}
		return true;
	}

    /** Delete nodes that have whether incoming nor outgoing edges.
 * 	@return Number of nodes removed. */
    public int deleteSingletons() {
		int singletons = 0;
        for (int i = 0; i < nodeCount(); i++) {
            Node n = nodeAt(i);
            if ((n.inDeg() == 0) && (n.outDeg() == 0)) {
				singletons++;
                delNode(n);
                i--;
            }
        }
		return singletons;
    }

	/** Find edge with maximum weight. */
	public Edge findMaxEdge() {
		Edge r = null;
		for (int i = 0; i < nodeList.size(); i++) {
			Node n = (Node)nodeList.elementAt(i);
			for (int j = 0; j < n.outDeg(); j++) {
				Edge e = n.succAt(j);
				if (r == null)
					r = e;
				else
					if (r.weight < e.weight)
						r = e;
			}
		}
		return r;
	}

	/** Find edge with minimum weight. */
	public Edge findMinEdge() {
		Edge r = null;
		for (int i = 0; i < nodeList.size(); i++) {
			Node n = (Node)nodeList.elementAt(i);
			for (int j = 0; j < n.outDeg(); j++) {
				Edge e = n.succAt(j);
				if (r == null)
					r = e;
				else
					if (r.weight > e.weight)
						r = e;
			}
		}
		return r;
	}

	/** Create a maximum spanning tree of this graph (expects the graph to be undirected). */
	public Graph maximumSpanningTree() {
		Heap h = new Heap(false);
		Graph tree = new Graph();
		int[] part = new int[nodeList.size()];
		for (int i = 0; i < nodeList.size(); i++) {
			Node v = (Node)nodeList.elementAt(i);
			int vID = v.getKey();
			tree.checkNode(vID);
			part[i] = i;
			for (int j = 0; j < v.outDeg(); j++) {
				Edge e = v.succAt(j);
				if (vID < e.dstNode().getKey()) // damit wir die Kanten nicht in beiden Richtungen betrachten
					h.add(e, (double)e.weight);
			}
		}
		int np = part.length; // number of partitions
		while ((h.size() > 0) && (np > 1)) {
			Edge e = (Edge)h.deleteMin();
			int vID = e.srcNode().getKey();
			int wID = e.dstNode().getKey();
			int vPos = nodeList.indexOf(vID);
			int wPos = nodeList.indexOf(wID);
			Node tv = tree.checkNode(vID);
			Node tw = tree.checkNode(wID);
			if (part[vPos] != part[wPos]) {
				tv.addSucc(tw, e.weight);
				tw.addSucc(tv, e.weight);
				int o = part[wPos];
				for (int i = 0; i < part.length; i++)
					if (part[i] == o)
						part[i] = part[vPos];
				np--;
			}
		}
		return tree;
	}

	/** Helperfunction: Replace two array elements */
	/** Number of connected components (expects undirected graph).
	@param threshold Only account for edges with a weight of at least this value. */
	public int partitions(int threshold) {
		int components = 0;
		SortedList[] s = new SortedList[2];
		s[0] = new SortedList();
		s[1] = (SortedList)nodeList.clone();
        while (s[1].size() > 0) {
    		Vector<Node> toDo = new Vector<Node>();
            Node src = (Node)s[1].deleteElementAt(0);
    		s[0].add(src);
    		toDo.addElement(src);
    		while (toDo.size() > 0) {
    			Node v = toDo.firstElement();
    			toDo.removeElementAt(0);
    			for (int i = 0; i < v.outDeg(); i++) {
    				Edge e = v.succAt(i);
                    if (e.weight >= threshold) {
     				Node w = e.dstNode();
    	    			int wID = w.getKey();
    		    		if (s[0].indexOf(wID) == -1) {
    			    		s[0].add(w);
    				    	s[1].delete(wID);
    					    toDo.addElement(w);
        				}
                    }
    			}
            }
			components++;
            s[0] = new SortedList();
		}
		return components;
	}

	/** "Degree of separation" within this graph (expects the graph do be undirected).
	@param threshold Only account for edges with a weight of at least this value.
	@return "Degree of separation": How likely is that two randomly chosen nodes lie within the same connected component? */
	public double partdeg(int threshold) {
		double rVal = 0.0;
		SortedList[] s = new SortedList[2];
		s[0] = new SortedList();
		s[1] = (SortedList)nodeList.clone();
        while (s[1].size() > 0) {
    		Vector<Node> toDo = new Vector<Node>();
            Node src = (Node)s[1].deleteElementAt(0);
    		s[0].add(src);
    		toDo.addElement(src);
    		while (toDo.size() > 0) {
    			Node v = toDo.firstElement();
    			toDo.removeElementAt(0);
    			//int vID = v.getKey();
    			for (int i = 0; i < v.outDeg(); i++) {
    				Edge e = v.succAt(i);
                    if (e.weight >= threshold) {
     				Node w = e.dstNode();
    	    			int wID = w.getKey();
    		    		if (s[0].indexOf(wID) == -1) {
    			    		s[0].add(w);
    				    	s[1].delete(wID);
    					    toDo.addElement(w);
        				}
                    }
    			}
            }
			rVal += (double)(s[0].size() * (nodeList.size() - s[0].size()));
            s[0] = new SortedList();
		}
		return rVal / (double)(nodeList.size() * (nodeList.size() - 1));
	}

	/** Retrieve connected components (expects undirected graph).
	@param threshold Only account for edges with a weight of at least this value. */
	public SortedList[] getCCs(int threshold) {
        Vector<SortedList> components = new Vector<SortedList>();
		SortedList[] s = new SortedList[2];
		s[0] = new SortedList();
		s[1] = (SortedList)nodeList.clone();
        while (s[1].size() > 0) {
    		Vector<Node> toDo = new Vector<Node>();
            Node src = (Node)s[1].deleteElementAt(0);
    		s[0].add(src);
    		toDo.addElement(src);
    		while (toDo.size() > 0) {
    			Node v = toDo.firstElement();
    			toDo.removeElementAt(0);
    			for (int i = 0; i < v.outDeg(); i++) {
    				Edge e = v.succAt(i);
                    if (e.weight >= threshold) {
     				Node w = e.dstNode();
    	    			int wID = w.getKey();
    		    		if (s[0].indexOf(wID) == -1) {
    			    		s[0].add(w);
    				    	s[1].delete(wID);
    					    toDo.addElement(w);
        				}
                    }
    			}
            }
			components.addElement(s[0]);
            s[0] = new SortedList();
		}
		SortedList[] r = new SortedList[components.size()];
		for (int i = 0; i < r.length; i++)
			r[i] = components.elementAt(i);
		return r;
	}

	/** Calculate maximum flow between two nodes with the highest-label preflow-push algorithm.
 * 	@return Residual graph. */
	public static Graph maxFlow(Graph g, Node src, Node dst) {
		int srcID = src.getKey();
		int dstID = dst.getKey();
		int[] dist = g.labelDist(dst.getKey());
		int[] excess = new int[dist.length];
		Graph flowGraph = new Graph();
		for (int i = 0; i < g.nodeCount(); i++)
			flowGraph.checkNode(g.nodeAt(i).getKey());
		Graph resGraph = (Graph)g.clone();
		Heap active = new Heap(false);
		for (int i = 0; i < src.outDeg(); i++) {
			Edge e = src.succAt(i);
			int wID = e.dstNode().getKey();
			int wPos = g.indexOf(wID);
			flowGraph.adjustWeight(srcID, wID, e.weight);
			resGraph.adjustWeight(srcID, wID, -e.weight);
			resGraph.adjustWeight(wID, srcID, e.weight);
			excess[wPos] = e.weight;
			active.add(resGraph.checkNode(wID), (double)dist[wPos]);
		}
		dist[g.indexOf(srcID)] = g.nodeCount();
		while (active.size() > 0) {
			Node rv = (Node)active.deleteMin();
			int vID = rv.getKey();
			if ((vID != srcID) && (vID != dstID)) {
				int vPos = g.indexOf(vID);
				Node fv = flowGraph.checkNode(vID);
				int minDist = Integer.MAX_VALUE;
				int minDistPos = -1;
				boolean done = false;
				for (int i = 0; i < rv.outDeg(); i++) {
					Edge e = rv.succAt(i);
					Node rw = e.dstNode();
					int wID = rw.getKey();
					int wPos = g.indexOf(wID);
					int min = (e.weight < excess[vPos]) ? e.weight : excess[vPos];
					if (min > 0) {
						if (dist[vPos] == dist[wPos] + 1) {
							Node fw = flowGraph.checkNode(wID);
							int prevBackFlow = fw.getSuccWeight(vID);
							if (prevBackFlow == 0)
								fv.adjustWeight(fw, min);
							else
								if (min <= prevBackFlow)
									fw.adjustWeight(fv, -min);
								else {
									fw.adjustWeight(fv, -prevBackFlow);
									fv.adjustWeight(fw, min - prevBackFlow);
								}
							rv.adjustWeight(rw, -min);
							rw.adjustWeight(rv, min);
							excess[vPos] -= min;
							excess[wPos] += min;
							if (excess[wPos] == min)
								active.add(rw, (double)dist[wPos]);
							if (excess[vPos] > 0)
								active.add(rv, (double)dist[vPos]);
							done = true;
							break;
						}
						else
							if (dist[wPos] < minDist) {
								minDistPos = wPos;
								minDist = dist[wPos];
							}
					}
				}
				if (! done) {
					if (minDistPos >= 0) {
						dist[vPos] = dist[minDistPos] + 1;
						active.add(rv, (double)dist[vPos]);
					}
					else {
						System.out.println("Graph.maxFlow: this is impossible: nowhere to put the flow!!");
						System.out.println("Node: " + vID);
						System.out.println("Edges in original graph: ");
						Node v = g.getNode(vID);
						for (int i = 0; i < v.inDeg(); i++)
							System.out.println(v.predAt(i));
						for (int i = 0; i < v.outDeg(); i++)
							System.out.println(v.succAt(i));
						System.out.println("Edges in flow graph: ");
						for (int i = 0; i < fv.inDeg(); i++)
							System.out.println(fv.predAt(i));
						for (int i = 0; i < fv.outDeg(); i++)
							System.out.println(fv.succAt(i));
						System.out.println("Edges in residual graph: ");
						for (int i = 0; i < rv.inDeg(); i++)
							System.out.println(rv.predAt(i));
						for (int i = 0; i < rv.outDeg(); i++)
							System.out.println(rv.succAt(i));
						System.exit(0);
					}
				}
			}
		}
		return resGraph;
	}

	/** Create the "seperator tree" of a graph (expects undirected graph). */
	public static Graph buildSeperatorTree(Graph g) {
		int nodeCount = g.nodeCount();
		// In folgendem Array wird zu jedem Knoten derjenige Knoten gespeichert, an den er im Seperator Tree angeknpft werden mu. Genauer: Am Anfang mu jeder Knoten natrlich an den einzigen, bereits im Baum vorhandenen Knoten angefgt werden. Dann bekommt jeder zum Baum hinzugefgte Knoten diejenigen Knoten vom alten "berwiesen", die auf seiner Seite des minimalen Schnittes liegen.
		Node[] part = new Node[nodeCount];
		for (int i = 0; i < nodeCount; i++)
			part[i] = g.nodeAt(0); // Der erste Knoten, an den wird des Rest des Baums drangebaut

		Graph tree = new Graph();

		for (int i = 1; i < nodeCount; i++) {
			// Der Knoten n ist noch nicht im Baum enthalten, wird im folgenden hinzugefgt, und zwar mu er an m angefgt werden:
			
			Node n = g.nodeAt(i);
			int nID = n.getKey();
			Node m = part[i];
			int mID = m.getKey();

			// Der Residualgraph zum maximalen Flugraphen reicht, um den minimalen Schnitt zu bekommen; Knoten auf m's Seite des Schnitts sind im Residualgraph nicht von n aus erreichbar, haben in diesem Array also Distanz -1:

			Graph resGraph = maxFlow(g, n, m);
			int[] rDist = resGraph.labelDist(nID);

			// Alle Knoten, die vorher zu m gehrten, aber auf n's Seite des Schnittes liegen, gehren jetzt zu n:

			for (int j = 0; j < part.length; j++)
				if ((part[j] == m) && (rDist[j] >= 0))
					part[j] = n;

			// Der Wert des minimalen Schnitts mu berechnet werden:

			int cutVal = 0;
			for (int j = 0; j < part.length; j++)
				if (rDist[j] >= 0) {
					Node v = g.nodeAt(j);
					for (int k = 0; k < v.outDeg(); k++) {
						Edge e = v.succAt(k);
						if (rDist[g.indexOf(e.dstNode().getKey())] < 0)
							cutVal += e.weight;
					}
				}

			// ...und das Ganze im Baum verewigen:

			if (cutVal > 0) {
				Node tn = tree.checkNode(nID);
				Node tm = tree.checkNode(mID);
				tn.addSucc(tm, cutVal);
				tm.addSucc(tn, cutVal);
			}
		}
		
		return tree;
	}

	/** Mincut algorithm, specifically for edge weights exclusively in {0,1}.*/
	protected int mincut01(int sID, int tID) {
		int cut = 0, tIdx = indexOf(tID);
		Node source = getNode(sID), t = getNode(tID);
		while (true) {
			int [] dist = labelDist(sID);
			int d = dist[tIdx];
			if (d == -1)
				break;
			Node target = t;
			while (source != target) {
				boolean ok = false;
				for (int i = 0; (i < target.inDeg()) && (! ok); i++) {
					Node n = target.predAt(i).srcNode();
					if (dist[indexOf(n.getKey())] == d - 1) {
						n.delSucc(target.getKey());
						target = n;
						ok = true;
					}
				}
				if (! ok) {
					System.out.println("[rlps]");
					System.exit(0);
				}
				d--;
			}
			cut++;
		}
		return cut;
	}

	protected int pathnr(Node s, Node t) {
		Node[][] sn = new Node[nodeList.size()][2];
		Graph sg = new Graph();
		int sID = s.getKey();
		int tID = t.getKey();
		sg.checkNode(sID);
		sg.checkNode(tID);
		for (int i = 0; i < nodeList.size(); i++) {
			Node n = nodeAt(i);
			int nID = n.getKey();
			if ((nID != sID) && (nID != tID)) {
				sn[i][0] = new Node(sg);
				sn[i][1] = new Node(sg);
				sn[i][0].addSucc(sn[i][1], 1);
			}
		}
		for (int i = 0; i < nodeList.size(); i++) {
			Node n = nodeAt(i);
			int nID = n.getKey();
			if (nID != tID)
				for (int j = 0; j < n.outDeg(); j++) {
					Edge e = n.succAt(j);
					Node m = e.dstNode();
					int mID = m.getKey();
					if ((mID != sID) && ((mID != tID) || (nID != sID))) {
						if (nID == sID)
							sg.getNode(nID).addSucc(sn[indexOf(mID)][0], 1);
						else if (mID == tID)
							sn[indexOf(nID)][1].addSucc(sg.getNode(mID), 1);
						else {
							Node[] v = sn[indexOf(nID)], w = sn[indexOf(mID)];
							if (w[1].getSucc(v[0].getKey()) == null)
								w[1].addSucc(v[0], 1);
							if (v[1].getSucc(w[0].getKey()) == null)
								v[1].addSucc(w[0], 1);
						}
					}
				}
		}
		int nc = sg.mincut01(sID, tID);
		if (s.getSucc(tID) == null)
			return nc;
		else
			return nc + 1;
	}
	
	/** Calculate minimum number of node disjoint paths between all node pairs. */
	public int stability() {
		int k = 0, y = nodeList.size() - 1;
		while (k <= y) {
			for (int i = k + 1; i < nodeCount(); i++)
				if (nodeAt(k).getSucc(nodeAt(i).getKey()) == null) {
					int x = pathnr(nodeAt(k), nodeAt(i));
					if (x < y)
						y = x;
				}
			k++;
		}
		return y;
	}

	public String toString() {
		String s = "";
		for (int i = 0; i < nodeList.size(); i++) {
			Node n = (Node)nodeList.elementAt(i);
			s += Integer.toString(n.getKey()) + ": ";
			for (int j = 0; j < n.outDeg(); j++)
				s += "[" + n.succAt(j) + "]";
			s += "\n";
		}
		return s;
	}

	public String readFromFile(BufferedReader in) throws IOException {
		String line = in.readLine();
		if (line.startsWith("V2")) {
			line = in.readLine();
			String loc = " in line \"" + line + "\"";
			try {
				int count = Integer.parseInt(line);
				for (int i = 0; i < count; i++) {
					int a = Integer.parseInt(in.readLine());
					checkNode(a);
				}
				line = in.readLine();
				loc = " in line \"" + line + "\"";
				count = Integer.parseInt(line);
				for (int i = 0; i < count; i++) {
					line = in.readLine();
					loc = " in line \"" + line + "\"";
					line = line.trim();
					StringTokenizer st = new StringTokenizer(line);
					if (st.countTokens() != 3)
						return "wrong column count" + loc;
					try {
						int a1 = Integer.parseInt(st.nextToken());
						int a2 = Integer.parseInt(st.nextToken());
						int weight = Integer.parseInt(st.nextToken());
						adjustWeight(a1, a2, weight);
					} catch (java.util.NoSuchElementException e1) {
						return "NoSuchElementException: " + e1 + loc;
					} catch (NumberFormatException e2) {
						return "weight is no int" + loc;
					}
				}
			} catch (NumberFormatException e) {
				return "no int" + loc;
			}
			return null;
		}
		else
			return "wrong fileformat [" + line + "]";
	}

	public String readFile(String fileName) {
		try {
			BufferedReader in = new BufferedReader(new FileReader(fileName));
			String t = readFromFile(in);
			try {
				in.close();
			} catch (Exception e) {}
			return t;
		} catch (FileNotFoundException e3) {
			return e3.toString();
		} catch (IOException e4) {
			return e4.toString();
		}
	}

	public void writeToFile(PrintStream out) {
		out.println("V2");
		out.println("" + nodeList.size());
		int edgeCount = 0;
		for (int i = 0; i < nodeList.size(); i++) {
			Node v = (Node)nodeList.elementAt(i);
			out.println(v.getKey());
			edgeCount += v.outDeg();
		}
		out.println("" + edgeCount);
		for (int i = 0; i < nodeList.size(); i++) {
			Node v = (Node)nodeList.elementAt(i);
			for (int j = 0; j < v.outDeg(); j++) {
				Edge e = v.succAt(j);
				out.println(v.getKey() + " " + e.dstNode().getKey() + " " + e.weight);
			}
		}
	}

	public String writeFile(String filename) {
		try {
			PrintStream out = new PrintStream(new FileOutputStream(filename));
			writeToFile(out);
			out.close();
		} catch (Exception e) {
			return e.toString();
		}
		return null;
	}
}
