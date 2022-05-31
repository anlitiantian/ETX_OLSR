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

package edu.bonn.cs.iv.util.maps.osm;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import edu.bonn.cs.iv.util.maps.BoundingBox;
import edu.bonn.cs.iv.util.maps.PositionGeo;

public class OSMGraph
{
	private HashMap<Long, OSMNode> osmNodes = null;
	private HashMap<Long, OSMWay> osmWays = null;
	private double roadNetworkLength = 0;
	private BoundingBox bbox = null;
	private int numEdges = 0;

	public OSMGraph()
	{
		osmNodes = new HashMap<Long, OSMNode>();
		osmWays = new HashMap<Long, OSMWay>();
	}
	
	public void putNode(long k, OSMNode v)
	{
		assert !osmNodes.containsKey(k) : "key: "+k;
		osmNodes.put(k, v);
	}
	
	public void putWay(long k, OSMWay v)
	{
		assert(!osmWays.containsKey(k));
		assert(v.nodeRefs().size() > 1);
		
		double len = calculateWayLength(v);
		v.setLength(len);
		roadNetworkLength += len;
		
		numEdges += v.nodeRefs().size() - 1;
		
		for (int i = 0; i < v.nodeRefs().size(); i++) {
			long nr = v.nodeRefs().get(i);
			OSMNode n = osmNodes.get(nr);
			n.addWay(k, (i == 0 || i == v.nodeRefs().size()-1));
		}
		
		osmWays.put(k, v);
	}
	
	public void removeIsolatedNodes()
	{
		ArrayList<Long> keys = new ArrayList<Long>(osmNodes.keySet());
		for (long nr : keys) {
			if (osmNodes.get(nr).ways().isEmpty()) {
				assert(osmNodes.get(nr).degree() == 0);
				osmNodes.remove(nr);
			}
		}
	}
	
	public void removeWay(long k)
	{
		assert(osmWays.containsKey(k));
		
		OSMWay w = osmWays.get(k);
		roadNetworkLength -= w.length();
		
		numEdges -= w.nodeRefs().size() - 1;
		
		for (int i = 0; i < w.nodeRefs().size(); i++) {
			long nr = w.nodeRefs().get(i);
			OSMNode n = osmNodes.get(nr);
			n.removeWay(k, (i == 0 || i == w.nodeRefs().size()-1));
		}
		
		osmWays.remove(k);
	}
	
	public boolean containsNode(long k)
	{
		return osmNodes.containsKey(k);
	}
	
	public boolean containsWay(long k)
	{
		return osmWays.containsKey(k);
	}
	
	public OSMNode getNode(long k)
	{
		return osmNodes.get(k);
	}
	
	public OSMWay getWay(long k)
	{
		return osmWays.get(k);
	}
	
	public double roadNetworkLength()
	{
		return this.roadNetworkLength;
	}
	
	public BoundingBox bbox()
	{
		return this.bbox;
	}
	
	public int numNodes()
	{
		return this.osmNodes.size();
	}
	
	public int numWays()
	{
		return this.osmWays.size();
	}
	
	public int numEdges()
	{
		return this.numEdges;
	}
	
	public double meanDegree()
	{
		double result = 0.0;
		
		for (long nr : osmNodes.keySet()) {
			assert(osmNodes.get(nr).degree() > 0);
			result += osmNodes.get(nr).degree();
		}
		result /= osmNodes.size();
		
		return result;
	}

	/**
	 * Calculate the compactness as described in Barthelemy: "Spatial Networks", Physics Reports, 2011
	 * @return
	 */
	public double compactness()
	{
		double A = bbox.height() * bbox.width();
		double l = roadNetworkLength;
		
		double compactness = 1 - (4*A / Math.pow(l - 2*Math.sqrt(A), 2));
		assert(compactness >= 0 && compactness <= 1);
		
		return compactness;
	}
	
	public HashMap<Integer, Integer> degreeDistribution()
	{
		HashMap<Integer, Integer> result = new HashMap<Integer, Integer>();
		
		for (long nr : osmNodes.keySet()) {			
			int d = osmNodes.get(nr).degree();
			assert(d > 0);
			
			if (result.containsKey(d)) {
				int count = result.get(d);
				result.put(d, ++count);
			} else {
				result.put(d, 1);
			}
		}
		
		return result;
	}
	
	public void setBoundingBox(BoundingBox bbox)
	{
		this.bbox = bbox;
	}
	
	/**
	 * Compute a random position by walking a random distance along the ways of the graph.
	 * The ways are traversed in the order they have been added to the graph.
	 * 
	 * @param rand random number in [0,1]
	 * @return The resulting random position
	 * @see computePhantomNode()
	 */
	public PositionGeo getRandomPosition(double rand)
	{
		PositionGeo result = null;
		
		double rlen = roadNetworkLength * rand;
		
		// TODO: implement this as binary search
		for (long k : osmWays.keySet()) {
			OSMWay w = osmWays.get(k);
			assert(w.length() > 0);
			if (rlen > w.length()) {
				rlen -= w.length();
			} else {
				result = computePhantomNode(w, rlen);
				break;
			}
		}
		
		return result;
	}
	
	private double calculateWayLength(OSMWay w)
	{
		double len = 0.0;
		
		ArrayList<Long> nr = w.nodeRefs();
		for (int i = 0; i < nr.size() - 1; i++) {
			OSMNode src = osmNodes.get(nr.get(i));
			OSMNode dst = osmNodes.get(nr.get(i+1));
			len += src.pos().distance(dst.pos());
		}
		
		return len;
	}
	
	/**
	 * Computes the phantom node resulting from walking a distance of l from the beginning of the way w.
	 * 
	 * @param w The way on which the phantom node is located
	 * @param l Distance to walk from the beginning of w until the phantom node
	 * @return The resulting phantom node
	 * @pre l <= w.length()
	 */
	private PositionGeo computePhantomNode(OSMWay w, double l)
	{
		assert(osmWays.containsValue(w));
		assert(l <= w.length());
		PositionGeo result = null;
		
		ArrayList<Long> nr = w.nodeRefs();
		for (int i = 0; i < nr.size() - 1; i++) {
			OSMNode src = osmNodes.get(nr.get(i));
			OSMNode dst = osmNodes.get(nr.get(i+1));
			double dist = src.pos().distance(dst.pos());
			
			if (l > dist) {
				l -= dist;
			} else {
				result = src.pos().getPhantomNode(dst.pos(), l/dist);
				break;
			}
		}
		
		assert(result != null);
		return result;
	}
	
	/**
	 * Creates a sub-graph from this graph for a given bounding box. Original nodes, ways, and their IDs are copied from this graph.
	 * Additional phantom (boundary) nodes with so far unused IDs as well as new (clipped) ways are introduced if necessary.
	 * 
	 * @param bb Spatial bounding box of the sub-graph
	 * @return Sub-graph with original and phantom (boundary) nodes, original (if any) and new (clipped) ways
	 * @see Route.clipRoute()
	 * @todo Might need optimization for large-sized graphs
	 * @note Currently creates a new ID even for unaltered ways (shouldn't be a problem though)
	 */
	public OSMGraph createSubGraph(BoundingBox bb)
	{
		OSMGraph sg = new OSMGraph();
		sg.setBoundingBox(bb);
		
		Set<Long> nodeKeys = osmNodes.keySet();
		ArrayList<Long> oldWayRefs = new ArrayList<Long>();
		for (Long k : nodeKeys) { // find and copy nodes within bounding box from this graph to sub-graph
			OSMNode oldNode = osmNodes.get(k);
			PositionGeo p = oldNode.pos();
			if (bb.contains(p)) {
				OSMNode newNode = new OSMNode(oldNode.id(), new PositionGeo(p));
				sg.putNode(k, newNode);
				for (Long w : oldNode.ways()) { // store IDs of ways that reference this node
					if (!oldWayRefs.contains(w)) {
						oldWayRefs.add(w);
					}
				}
			}
		}

		long phantomNodeId = 0;
		long newWayId = 0;
		for (Long oldWayId : oldWayRefs) { // now find out which way segments are within the bbox and create (boundary) phantom nodes if necessary
			OSMWay oldWay = osmWays.get(oldWayId);
			ArrayList<Long> oldNodeRefs = oldWay.nodeRefs();
			ArrayList<Long> newNodeRefs = null;
			boolean previousClipped = false;
			for (int i = 0; i < oldNodeRefs.size(); i++) {
				long currentNodeId = oldNodeRefs.get(i);
				if (sg.containsNode(currentNodeId)) { // within bounds
					if (previousClipped) {
						// start new way
						phantomNodeId = this.getLowestUnusedNodeIdForSubGraph(phantomNodeId);
						addPhantomNodeToSubGraph(sg, oldNodeRefs.get(i-1), currentNodeId, phantomNodeId);
						
						newWayId = this.getLowestUnusedWayIdForSubGraph(newWayId);
						newNodeRefs = new ArrayList<Long>();
						
						newNodeRefs.add(phantomNodeId);
					} else if (i == 0) {
						// start new way
						newWayId = this.getLowestUnusedWayIdForSubGraph(newWayId);
						newNodeRefs = new ArrayList<Long>();
					}
					
					newNodeRefs.add(currentNodeId);
					previousClipped = false;
				} else { // outside bounds
					if (!previousClipped) {
						if (i > 0) { // only if this is not the first node
							// end current way
							phantomNodeId = this.getLowestUnusedNodeIdForSubGraph(phantomNodeId);
							addPhantomNodeToSubGraph(sg, oldNodeRefs.get(i-1), currentNodeId, phantomNodeId);
							
							newNodeRefs.add(phantomNodeId);
							OSMWay newWay = new OSMWay(newWayId, newNodeRefs);
							sg.putWay(newWayId, newWay);
							newNodeRefs = null;
						}
					}
					
					previousClipped = true;
				}
			}
			
			if (newNodeRefs != null) { // end final way
				assert(newNodeRefs.size() > 1);
				OSMWay newWay = new OSMWay(newWayId, newNodeRefs);
				sg.putWay(newWayId, newWay);
			}
		}
		
		return sg;
	}
	
	/**
	 * Helper function for createSubGraph(). Adds a phantom node on the boundary between two original nodes,
	 * where one of the nodes lies outside the bounding box.
	 * 
	 * @param subGraph Sub-graph object to add phantom node to
	 * @param nodeId1 Original node 1
	 * @param nodeId2 Original node 2
	 * @param phantomNodeId Pre-determined ID for the new phantom node
	 * @see createSubGraph()
	 */
	private void addPhantomNodeToSubGraph(OSMGraph subGraph, long nodeId1, long nodeId2, long phantomNodeId)
	{
		PositionGeo p1 = osmNodes.get(nodeId1).pos();
		PositionGeo p2 = osmNodes.get(nodeId2).pos();
		PositionGeo phantomPos = subGraph.bbox().calcBoundsIntersection(p1, p2);
		
		OSMNode phantomNode = new OSMNode(phantomNodeId, phantomPos, true);
		subGraph.putNode(phantomNodeId, phantomNode);
	}
	
	/**
	 * Helper function for createSubGraph(). Finds the lowest node ID which is not in use by this graph or the sub-graph.
	 * 
	 * @param hint A hint to a previous newly created node ID in the sub-graph (lower bound)
	 * @return The lowest unused node ID
	 */
	private long getLowestUnusedNodeIdForSubGraph(long hint)
	{
		// hint is the lower bound
		long result = hint + 1;
		while (osmNodes.containsKey(result)) {
			result++;
		}
		return result;
	}
	
	/**
	 * Helper function for createSubGraph(). Finds the lowest way ID which is not in use by this graph or the sub-graph.
	 * 
	 * @param hint A hint to a previous newly created way ID in the sub-graph (lower bound)
	 * @return The lowest unused way ID
	 */
	private long getLowestUnusedWayIdForSubGraph(long hint)
	{
		// hint is the lower bound
		long result = hint + 1;
		while (osmWays.containsKey(result)) {
			result++;
		}
		return result;
	}
	
	/**
	 * Find connected components of this graph.
	 * 
	 * @return An array of components, where each component is an array of way IDs
	 */
	public ArrayList<ArrayList<Long>> findComponents()
	{
		ArrayList<ArrayList<Long>> result = new ArrayList<ArrayList<Long>>();
		
		for (long k : osmWays.keySet()) {
			OSMWay w = osmWays.get(k);
			
			if (w.componentId() >= 0) { // w has already been visited and assigned to a component
				continue;
			}
			
			// w has not been visited yet, therefore it must belong to a new component
			result.add(new ArrayList<Long>());
			int cId = result.size() - 1;
			w.setComponentId(cId);
			ArrayList<Long> component = result.get(cId);
			component.add(k);
			visitAdjacentWays(w, component, cId);
		}
		
		return result;
	}
	
	/**
	 * Recursive helper function for findComponents() to visit ways adjacent to src.
	 * 
	 * @param src Source way
	 * @param comp Component of way src
	 * @param id Component ID of comp
	 */
	private void visitAdjacentWays(OSMWay src, ArrayList<Long> comp, int id)
	{
		for (long nr : src.nodeRefs()) {
			OSMNode n = osmNodes.get(nr);
			for (long wr : n.ways()) {
				OSMWay w = osmWays.get(wr);
				
				if (w.componentId() < 0) { // this way has not been visited yet
					w.setComponentId(id);
					comp.add(wr);
					visitAdjacentWays(w, comp, id);
				} else { // we've already visited this way
					assert(w.componentId() == id);
				}
			}
		}
	}
	
	public void printToFile(String file)
	{
		PrintWriter writer;
		try {
			writer = new PrintWriter(file, "UTF-8");
			writer.println("way lon lat");
			for (long k : osmWays.keySet()) {
				OSMWay w = osmWays.get(k);
				for (long nr : w.nodeRefs()) {
					PositionGeo p = osmNodes.get(nr).pos();
					writer.printf("%d %.6f %.6f\n", k, p.lon(), p.lat());
				}
			}
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
