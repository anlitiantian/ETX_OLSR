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

package edu.bonn.cs.iv.bonnmotion.models;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.Serializable;
import java.util.Vector;
import java.util.LinkedList;
import java.util.Map;
import java.util.Iterator;
import java.awt.geom.Line2D;
import java.awt.Rectangle;

import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.RandomSpeedBase;
import edu.bonn.cs.iv.bonnmotion.models.da.CatastropheArea;
import edu.bonn.cs.iv.bonnmotion.models.da.CatastropheNode;
import edu.bonn.cs.iv.bonnmotion.models.da.Obstacle;
import edu.bonn.cs.iv.util.PositionHashMap;
import edu.bonn.cs.iv.util.IntegerHashMap;

/** Application to create movement scenarios according to the Disaster Area model. */

public class DisasterArea extends RandomSpeedBase {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("DisasterArea");
        info.description = "Application to create extended catastrophe scenarios according to the Disaster Area model";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	private static boolean debug = false;

	/** Maximum deviation from group center [m]. */
	protected double maxdist = 2.5;
	/** Minimum space needed by group. */
	protected double mindist = 2.5;
	/** Average nodes per group. */
	protected double avgMobileNodesPerGroup = 3.0;
	/** Standard deviation of nodes per group. */
	protected double groupSizeDeviation = 0;
	/** The probability for a node to change to a new group when moving into it's range. */
	protected double pGroupChange = 0;
	/** Number of groups (not an input parameter!). */
	protected int groups = 0;
	/** Size of largest group (not an input parameter!). */
	protected int maxGroupSize = 0;
	/** Count the number of CatastropheAreas . */
	protected int numCatastropheAreas = 0;
	/** Maximum number of CatastropheAreas . */
	protected int maxCatastropheAreas = 4;
	/** Only one Obstacle for all areas. */
	protected boolean onlyOneObstacle = false;
	/** Additional obstacle for type 4. */
	protected boolean addObsT4 = false;
	/** Manage the CatastropheAreas . */
	protected CatastropheArea[] catastropheAreas = null;
	/** temporary saves the arguments for the catastrophe areas */
	private LinkedList<String> catastropheAreaArgs = new LinkedList<String>();
	/** initialize dst . */
	Position dst = new Position(0,0);
	/** remember maxpause . */
	double oldmaxpause = 0;
	/** remember maxdist . */
	double oldmaxdist = 0;
	/** decide whether to write in file or not */
	boolean shallwrite = false;
	boolean write_moves = false;
	boolean write_vis = false;
	/** areas are also seen as obstacles for APP */
	boolean no_knock_over = false;
	/** number of vertices to approximate circle */
	int circlevertices = 4;
	/** remember nodes' status changes */
	/** not needed any more - Position-Class extended with status - ToDo delete */
	IntegerHashMap statuschanges = new IntegerHashMap();
	/** Factor to multiply pathlength for MinCObstacles with */
	double factor = 1.0;

	/*
		type == 0: "incident location" / Schadensstelle
		type == 1: "patients waiting for treatment area" / Verletztenablage
		type == 2: "casualties clearing station" / Behandlungsplatz
		type == 3: "technical operational command" / technische Einsatzleitung
		type == 4: "ambulance parking point" / Rettungsmittelhalteplatz
	 */
	/** Manage the Obstacles . */
	@SuppressWarnings("unchecked")
	protected LinkedList<Obstacle>[] obstacles = new LinkedList[5];
	/** Manage the C-Obstacles for maxdist. */
	@SuppressWarnings("unchecked")
	protected LinkedList<Obstacle>[] maxCObstacles = new LinkedList[5];
	/** Manage the C-Obstacles for mindist. */
	@SuppressWarnings("unchecked")
	protected LinkedList<Obstacle>[] minCObstacles = new LinkedList[5];
	/** remember Visibility Graph with MaxCObstacles*/
	@SuppressWarnings("unchecked")
	LinkedList<Serializable>[] Graph = new LinkedList[5];
	/** remember Visibility Graph with MinCObstacles*/
	@SuppressWarnings("unchecked")
	LinkedList<Serializable>[] MinGraph = new LinkedList[5];
	/** remember shortest paths with MaxCObstacles*/
	PositionHashMap[] shortestpaths = new PositionHashMap[5];
	/** remember shortest paths with MinCObstacles*/
	PositionHashMap[] Minshortestpaths = new PositionHashMap[5];

	public DisasterArea(int nodes, double x, double y, double duration, double ignore, long randomSeed, double minspeed, double maxspeed, double maxpause, double maxdist, double avgMobileNodesPerGroup, double groupSizeDeviation, double pGroupChange) {
		super(nodes, x, y, duration, ignore, randomSeed, minspeed, maxspeed, maxpause);
		this.maxdist = maxdist;
		this.avgMobileNodesPerGroup = avgMobileNodesPerGroup;
		this.groupSizeDeviation = groupSizeDeviation;
		this.pGroupChange = pGroupChange;
		for (int i = 0; i < 5; i++) { // for each type of area
			obstacles[i] = new LinkedList<Obstacle>();
		}
		generate();
	}

	public DisasterArea( String[] args ) {
		for (int i = 0; i < 5; i++) { // for each type of area
			obstacles[i] = new LinkedList<Obstacle>();
		}
		go( args );
	}

	public void go( String args[] ) {
		super.go(args);
		generate();
	}

	@SuppressWarnings("rawtypes")
	public void generate() {
		this.processArguments();
		preGeneration();
		if (numCatastropheAreas != maxCatastropheAreas){
			System.out.println("You wanted to specify " + maxCatastropheAreas + " catastrophe areas but specified only " + numCatastropheAreas + ". Please specify the correct number of areas!\nFor different number of areas see -e\naborting...");
			System.exit(0);
		}
		int toArea = 0;

		for (int t = 0; t < 5; t++) { // for each type of area
			maxCObstacles[t] = new LinkedList<Obstacle>();
			minCObstacles[t] = new LinkedList<Obstacle>();
			shortestpaths[t] = new PositionHashMap();
			Minshortestpaths[t] = new PositionHashMap();

			// add relevant areas to obstabcles ...
			for (int i = 0; i < catastropheAreas.length; i++){
				boolean add = false;
				/* sehe eigene Bereichsart nicht als Hindernis an */
				/*if (CatastropheAreas[i].type != t) {*/
				/* Nach Type of Area Bereiche hinzufgen
				   type == 0: "incident location" / Schadensstelle
				   type == 1: "patients waiting for treatment area" / Verletztenablage
				   type == 2: "casualties clearing station" / Behandlungsplatz
				   type == 3: "technical operational command" / technische Einsatzleitung
				   type == 4: "ambulance parking point" / Rettungsmittelhalteplatz
				 */
				switch (t) {
					case 4:
						if ((catastropheAreas[i].type != 4) && no_knock_over) {
							add = true;
						}
						break;
						/* case 0-3 event. later */
				}
				if (add) {
					double[] CA_Params = catastropheAreas[i].getPolygonParams();
					// System.out.println("Lnge: "+CA_Params.length+" \n");
					Obstacle newone = new Obstacle(CA_Params);
					obstacles[t].add(newone);
				}
			}
			System.out.println("#Obstacle[Type:"+t+"] = "+obstacles[t].size());

			//compute CObstacles for mindist and maxdist
			for(int i = 0; i < obstacles[t].size(); i++){
				maxCObstacles[t].add(obstacles[t].get(i).computeCObstacle(maxdist, circlevertices));
			}

			if(mindist != maxdist){
				for(int i = 0; i < obstacles[t].size(); i++){
					minCObstacles[t].add(obstacles[t].get(i).computeCObstacle(mindist, circlevertices));
				}
			}
			else{
				minCObstacles[t] = maxCObstacles[t];
			}

			//compute visibility graphs for minCObstacles and maxCObstacles
			Graph[t] = VisibilityGraph(maxCObstacles[t],t);
			MinGraph[t] = VisibilityGraph(minCObstacles[t],t);

			for(int i = 0; i < ((LinkedList)Graph[t].get(0)).size(); i++){
				shortestpaths[t].put(((Position)((LinkedList)Graph[t].get(0)).get(i)), Dijkstra(Graph[t], ((Position)((LinkedList)Graph[t].get(0)).get(i))));
			}

			for(int i = 0; i < ((LinkedList)MinGraph[t].get(0)).size(); i++){
				Minshortestpaths[t].put(((Position)((LinkedList)MinGraph[t].get(0)).get(i)), Dijkstra(MinGraph[t], ((Position)((LinkedList)MinGraph[t].get(0)).get(i))));
			}

			System.out.println("FINISHED: compute visibility graphs type: "+ t);
		}

		/** nodes needed by areas specified */
		int nodesneeded = 0;

		for (int i = 0; i < catastropheAreas.length; i++) {
			//for (int t = 0; t < 5; t++) { // for each type of area
			for(int j = 0; j < obstacles[catastropheAreas[i].type].size(); j++) {
				if(catastropheAreas[i].intersects(obstacles[catastropheAreas[i].type].get(j).getBounds())) {
					System.out.println("NOTE: in " + getInfo().name + ", one Obstacle's ("+j+") Bounds intersect a Catastrophe Area - type "+catastropheAreas[i].type+" \n");
					//System.exit(0);
				}
			}
			//}

			nodesneeded += (catastropheAreas[i].groupsize[0] * catastropheAreas[i].numtransportgroups) + (catastropheAreas[i].groupsize[1] * (catastropheAreas[i].wantedgroups - catastropheAreas[i].numtransportgroups));

			/*** NA_Debug */
			int help = (catastropheAreas[i].groupsize[0] * catastropheAreas[i].numtransportgroups) + (catastropheAreas[i].groupsize[1] * (catastropheAreas[i].wantedgroups - catastropheAreas[i].numtransportgroups));

			System.out.println("Area: "+ i + "(Type: "+ catastropheAreas[i].type +") -->" + catastropheAreas[i].groupsize[0] + " * " + catastropheAreas[i].numtransportgroups + " + " + catastropheAreas[i].groupsize[1] + " * ("+ catastropheAreas[i].wantedgroups + "-" +  catastropheAreas[i].numtransportgroups + ") = " + help );  
			/* NA_Debug **/

			/** calculate and remember way for node (saved in area.allways) */
			determineway(catastropheAreas[i]);
			// deprecated, just for historical reasons, replaced by allways
			/*catastropheAreas[i].waytoneighbor = determineway(catastropheAreas[i]);
			for (int j = 0; j < catastropheAreas[i].waytoneighbor.size(); j++){
				System.out.println("Weg des Knoten for "+ catastropheAreas[i].type + " --> " + 
						((Position)catastropheAreas[i].waytoneighbor.get(j)).x + " " + ((Position)catastropheAreas[i].waytoneighbor.get(j)).y);
			}*/
		}

		if (nodesneeded != parameterData.nodes.length) {
			System.out.println("You specified wrong number of nodes to fulfill the requirements arisen by your Area specifications, nodesneeded " + nodesneeded + " nodes " + parameterData.nodes.length);
			System.exit(0);
			//System.out.println("nodesneeded != nodes.length - I should stop here, but it's a bloody hack !! \n");
		}

		System.out.println("Start creating "+ this.parameterData.nodes.length +" nodes \n");
		CatastropheNode[] node = new CatastropheNode[this.parameterData.nodes.length];
		Vector<CatastropheNode> rpoints = new Vector<CatastropheNode>();
		oldmaxpause = maxpause;

		int nodesRemaining = node.length;
		int offset = 0;
		while (nodesRemaining > 0) {
			for (int i = 0; i < catastropheAreas.length; i++) {
				if(catastropheAreas[i].locatedgroups < catastropheAreas[i].wantedgroups){
					toArea = i;
					catastropheAreas[i].locatedgroups++;
					break;
				}
			}
			CatastropheNode ref = null;
			if(catastropheAreas[toArea].assignedtransportgroups < catastropheAreas[toArea].numtransportgroups){
				CatastropheNode ref1 = new CatastropheNode(toArea,0,catastropheAreas[toArea].entry, catastropheAreas[toArea].exit);
				ref = ref1;
				catastropheAreas[toArea].assignedtransportgroups++;
			}
			else{
				CatastropheNode ref1 = new CatastropheNode(toArea, 1, catastropheAreas[toArea].entry, catastropheAreas[toArea].exit);
				ref = ref1;
			}
			rpoints.addElement(ref);
			double t = 0.0;
			//System.out.println(" determine source for group depending on the area the group belongs to");
			// determine source for group depending on the area the group belongs to
			//			System.out.println("src=DetRandDst("+CatastropheAreas[((CatastropheNode)ref).belongsto].getBounds().x+","+CatastropheAreas[((CatastropheNode)ref).belongsto].getBounds().x+"+"+CatastropheAreas[((CatastropheNode)ref).belongsto].getBounds().width+","+CatastropheAreas[((CatastropheNode)ref).belongsto].getBounds().y+"+"+CatastropheAreas[((CatastropheNode)ref).belongsto].getBounds().height+","+CatastropheAreas[((CatastropheNode)ref).belongsto].getBounds().y+","+CatastropheAreas[((CatastropheNode)ref).belongsto].type+")");
			//CatastropheAreas[((CatastropheNode)ref).belongsto].print();
			Position src = DetRandDst(catastropheAreas[ref.belongsto].getBounds().x, catastropheAreas[ref.belongsto].getBounds().x + catastropheAreas[ref.belongsto].getBounds().width, catastropheAreas[ref.belongsto].getBounds().y + catastropheAreas[ref.belongsto].getBounds().height, catastropheAreas[ref.belongsto].getBounds().y, catastropheAreas[ref.belongsto]);

			if (! ref.add(0.0, src)) {
				System.out.println(getInfo().name + ".generate: error while adding group movement (1)");
				System.exit(0);
			}
			
			while (t < parameterData.duration) {
				//determine movementcycle for group
				LinkedList<Position> movementcycle = new LinkedList<Position>();
				movementcycle = determineMovementCycle(catastropheAreas[ref.belongsto], ref);
				//determine speed according to the area the group belongs to
				double speed = 0;
				maxspeed = catastropheAreas[ref.belongsto].maxspeed[ref.type];
				minspeed = catastropheAreas[ref.belongsto].minspeed[ref.type];
				speed = (maxspeed - minspeed) * randomNextDouble() + minspeed;

				/*** pause time for each group at the beginning 
				     to avoid sets of groups ***/
				if (t == 0.0) {
					double initial_maxpause = (2*parameterData.x + 2*parameterData.y) / minspeed;
					double pause = initial_maxpause * randomNextDouble();
					t += pause;
					if (! ref.add(t, src)) {
						System.out.println(getInfo().name + ".generate: error while adding group movement (2)");
						System.exit(0);
					}
				}
				/***************/

				for(int i = 0; i < movementcycle.size(); i++) {
					dst = movementcycle.get(i);
					t += src.distance(dst) / speed;
					if (! ref.add(t, dst)) {
						System.out.println(getInfo().name + ".generate: error while adding group movement (2)");
						System.exit(0);
					}
					if ((t < parameterData.duration) && (maxpause > 0.0)) {
						double pause = maxpause * randomNextDouble();
						if (pause > 0.0) {
							t += pause;
							if (! ref.add(t, dst)) {
								System.out.println(getInfo().name + ".generate: error while adding node movement (3)");
								System.exit(0);
							}
						}
					}
					src = dst;
				}
			}

			// define group size:
			int size = 0;
			if(ref.type == 0){
				size = catastropheAreas[ref.belongsto].groupsize[0];
			}
			else{
				size = catastropheAreas[ref.belongsto].groupsize[1];
			}
			if (size > nodesRemaining)
				size = nodesRemaining;
			if (size > maxGroupSize)
				maxGroupSize = size;
			nodesRemaining -= size;
			offset += size;
			for (int i = offset - size; i < offset; i++)
				node[i] = new CatastropheNode(ref);
			++groups;
		}
		// nodes follow their reference points:

		for (int i = 0; i < node.length; i++) {
			LinkedList<Double> statuschangetimes = new LinkedList<Double>();
			double t = 0.0;
			CatastropheNode group = node[i].group();
			maxspeed = catastropheAreas[group.belongsto].maxspeed[group.type];
			minspeed = catastropheAreas[group.belongsto].minspeed[group.type];
			Position src = null;
			src = group.positionAt(t).rndprox(maxdist, randomNextDouble(), randomNextDouble(), parameterData.calculationDim);

			if (! node[i].add(0.0, src)) {
				System.out.println(getInfo().name + ".generate: error while adding node movement (1)");
				System.exit(0);
			}
			while (t < parameterData.duration) {
				double[] gm = group.changeTimes();
				int gmi = 0;
				while ((gmi < gm.length) && (gm[gmi] <= t))
					++gmi;
				boolean pause = (gmi == 0);
				
				if (! pause) {
					Position pos1 = group.positionAt(gm[gmi-1]);
					Position pos2 = group.positionAt(gm[gmi]);
					pause = pos1.equals(pos2);
				}
				
				double next = (gmi < gm.length) ? gm[gmi] : parameterData.duration;
				Position dst; 
				double speed;
				
				do {
					dst = group.positionAt(next).rndprox(maxdist, randomNextDouble(), randomNextDouble(), parameterData.calculationDim);
					speed = src.distance(dst) / (next - t);
				} while ((! pause) && (speed > maxspeed));
				
				if (speed > maxspeed) {
					double c_dst = ((maxspeed - minspeed) * randomNextDouble() + minspeed) / speed;
					double c_src = 1 - c_dst;
					dst = new Position(c_src * src.x + c_dst * dst.x, c_src * src.y + c_dst * dst.y);
				}
				
				if (! node[i].add(next, dst)) {
					System.out.println(getInfo().name + ".generate: error while adding node movement (4)");
					System.exit(0);
				}
				
				if(catastropheAreas[node[i].belongsto].type == 4 && node[i].type == 0){
					if(group.positionAt(next).equals(catastropheAreas[node[i].belongsto].borderentry) || group.positionAt(next).equals(catastropheAreas[node[i].belongsto].borderexit)){
						Double value = new Double(next);
						statuschangetimes.add(value);
					}
				}
				src = dst;
				t = next;
			}
			if(catastropheAreas[node[i].belongsto].type == 4 && node[i].type == 0){
				Integer nodeaddress = new Integer(i);
				statuschanges.put(nodeaddress, statuschangetimes);
			}
		}
		// write the nodes into our base
		this.parameterData.nodes = node;
		if(shallwrite) mywrite();
		postGeneration();
	}

	protected boolean parseArg(String key, String value) {
		if ( key.equals("groupsize_E") ) {
			this.avgMobileNodesPerGroup = Double.parseDouble(value);
			System.out.println("avgMobileNodesPerGroup will not be considered in this model, because the group sizes depend on the areas");
			return true;
		} else if (	key.contains("catatastropheArea") ) {
			this.catastropheAreaArgs.add(value);
			return true;
		} else if (	key.equals("pGroupChange") ) {
			this.pGroupChange = Double.parseDouble(value);
			System.out.println("Group Change will not be considered in this model, because the groups belong to areas");
			return true;
		} else if (	key.equals("maxCataAreas") ) {
			this.maxCatastropheAreas = (int) Double.parseDouble(value);
			return true;
		} else if (	key.equals("circleVertices") ) {
			this.circlevertices = (int) Double.parseDouble(value);
			return true; // TODO: Fehlt bei parseArgs(-g) ?
		} else if (	key.equals("maxspeed") ) {
			System.out.println("In this model you can't specify maxspeed using area dependend speed");
			return true;
		} else if (	key.equals("factor") ) {
			this.factor = Double.parseDouble(value);
			return true;
		} else if (	key.equals("minspeed") ) {
			System.out.println("In this model you can't specify minspeed using area dependend speed");
			return true;
		} else if (	key.equals("obstacleForAllGrps") ) {
			double[] obstacleParams;
			obstacleParams = parseDoubleArray(value);
			for(int i = 0; i < obstacleParams.length; i = i+2){
				if(obstacleParams[i] > parameterData.x){
					System.out.println("Obstacles' x-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			for(int i = 1; i < obstacleParams.length; i = i+2){
				if(obstacleParams[i] > parameterData.y){
					System.out.println("Obstacles' y-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			Obstacle newone = new Obstacle(obstacleParams);
			for (int i = 0; i < 5; i++) { // for each type of area
				obstacles[i].add(newone);
			}
			onlyOneObstacle = true;
			return true;
		} else if (	key.contains("obstacleForOnlyOneGroup") ) {
			double[] help_param = parseDoubleArray(value);
			double[] obstacleParams_group = new double[help_param.length-1];
			// last parameter is the number of the group to add the obstacle
			System.arraycopy(help_param, 0, obstacleParams_group, 0, help_param.length - 1);
			for(int i = 0; i < obstacleParams_group.length; i = i+2) {
				if(obstacleParams_group[i] > parameterData.x){
					System.out.println("Obstacles' x-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			for(int i = 1; i < obstacleParams_group.length; i = i+2){
				if(obstacleParams_group[i] > parameterData.y){
					System.out.println("Obstacles' y-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			Obstacle newone_group = new Obstacle(obstacleParams_group);
			this.obstacles[(int)(help_param[help_param.length-1])].add(newone_group);
			onlyOneObstacle = false;
			if (help_param[help_param.length-1] == 4) {
				addObsT4 = true;
			}
			return true;
		} else if (	key.equals("mindist") ) {
			this.mindist = Double.parseDouble(value);
			return true;
		} else if (	key.equals("maxdist") ) {
			this.maxdist = Double.parseDouble(value);
			this.oldmaxdist = maxdist;
			return true;
		} else if (	key.equals("groupsize_S") ) {
			this.groupSizeDeviation = Double.parseDouble(value);
			System.out.println("Group Size Deviation will not be considered in this model, because the group sizes depend on the areas");
			return true;
		} else if (	key.equals("writeMoves") ) {
			boolean writeMoves = Boolean.parseBoolean(value);
			if (writeMoves) {
				this.shallwrite = true;
				this.write_moves = true;
			}
			return true;
		} else if (	key.equals("writeVisibilityGraph") ) {
			boolean writeVisibilityGraph = Boolean.parseBoolean(value);
			if (writeVisibilityGraph) {
				this.shallwrite = true;
				this.write_vis = true;
			}
			return true;
		} else if (	key.equals("noKnockOver") ) {
			boolean knock = Boolean.parseBoolean(value);
			if (knock) {
				this.no_knock_over = true;
			}
			return true;
		} else {
			return super.parseArg(key, value);
		}
	}

	public void write( String _name ) throws FileNotFoundException, IOException {
		
		int obsCount = 1;
		if (!onlyOneObstacle) {
			obsCount = 0;
			for (int i = 0; i < 5; i++) {
				if (obstacles[i].size() != 0) obsCount++;
			}
			if (!addObsT4) {
				obsCount--;
			}
		}
		
		String[] p = new String[11 + maxCatastropheAreas + obsCount];
		
		int idx = 0;
		
		p[idx++] = "groupsize_E=" + avgMobileNodesPerGroup;
		
		for (int i = 0; i < catastropheAreas.length; i++) {
			CatastropheArea area = catastropheAreas[i];
			double[] params = area.getPolygonParams();
			String paramsAsString = "";
			for (int j = 0; j < params.length; j++) {
				paramsAsString += (int)params[j] + ",";
			}
			String APPBorderEntryExit = "";
			if (area.type == 4) {
				APPBorderEntryExit = (int)area.borderentry.x + "," + (int)area.borderentry.y + "," + 
										(int)area.borderexit.x + "," + (int)area.borderexit.y + ",";
			}
			p[idx++] = "catatastropheArea" + i + "=" + paramsAsString + APPBorderEntryExit + 
						(int)area.entry.x + "," + (int)area.entry.y + "," + 
						(int)area.exit.x  + "," + (int)area.exit.y + "," + 
						area.type + "," + area.wantedgroups + "," + area.numtransportgroups;
		}

		p[idx++] = "pGroupChange=" + pGroupChange;
		p[idx++] = "maxCataAreas=" + maxCatastropheAreas;
		p[idx++] = "circleVertices=" + circlevertices;
		p[idx++] = "factor=" + factor;
		
		if (obstacles != null && obstacles.length > 0) {
			
			if (onlyOneObstacle) {
				
				double[] obsVertices = obstacles[0].getFirst().getVertices();
				String obsVerticesAsString = "";
				for (int i = 0; i < obsVertices.length; i++) {
					obsVerticesAsString += (int) obsVertices[i];
					if (i < obsVertices.length - 1) {
						obsVerticesAsString += ",";
					}
				}
				p[idx++] = "obstacleForAllGrps=" + obsVerticesAsString;
				
			} else {
				
				for (int i = 0; i < obstacles.length; i++) {
					
					if (obstacles[i].size() != 0) {
						
						if (i == 4 && !addObsT4) {
							continue;
						}
						
						double[] obsVertices = obstacles[i].getFirst().getVertices();
						String obsVerticesAsString = "";
						for (int j = 0; j < obsVertices.length; j++) {
							obsVerticesAsString += (int) obsVertices[j];
							if (j < obsVertices.length - 1) {
								obsVerticesAsString += ",";
							}
						}
						p[idx++] = "obstacleForOnlyOneGroup" + i + "=" + obsVerticesAsString + "," + i;
						
					} 
				}
				
			}
		}
		p[idx++] = "mindist=" + mindist;
		p[idx++] = "maxdist=" + maxdist;
		p[idx++] = "groupsize_S=" + groupSizeDeviation;		
		p[idx++] = "writeMoves=" + (shallwrite && write_moves);
		p[idx++] = "writeVisibilityGraph=" + (shallwrite && write_vis);
		p[idx] = "noKnockOver=" + no_knock_over;

		PrintWriter changewriter = new PrintWriter(new BufferedWriter(new FileWriter(_name + ".changes")));
		Iterator<Map.Entry<Integer, Object>> it = statuschanges.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<Integer, Object> entry = it.next();
			Integer key = (entry.getKey());
			changewriter.write(key.toString());
			changewriter.write(" ");
		}
		changewriter.close();

		super.write(_name, p);
		//super.write(_name, p, statuschanges);
	}

	/**
	 * Processes all arguments that depend on each other in some way
	 * that were skipped parseArg(char, String)
	 */
	private void processArguments() {
		// check if there are not too many catastrophe areas specified
		if(this.catastropheAreaArgs.size() > this.maxCatastropheAreas){
			System.out.println("Only " + maxCatastropheAreas + " CatastropheAreas permitted in this model");
			System.exit(0);
		}
		// reserve space for catastrophe areas
		this.catastropheAreas = new CatastropheArea[maxCatastropheAreas];
		// process catastrophe area arguments
		for (String arg : this.catastropheAreaArgs) {
                        ++this.numCatastropheAreas;
			/** fetch params for Area. */
			double[] areaParams = parseDoubleArray(arg);
			// last three params are no coordinates
			int check_till = areaParams.length - 3;
			if (areaParams[check_till] == 4) {
				// type is ambulance parking point, the last 12 parameters are no coordinates
				check_till = areaParams.length - 12;
			}
			// check if coordinates are valid for scenario
			for(int i = 0; i < check_till; i = i+2){
				if(areaParams[i] > parameterData.x - this.maxdist || areaParams[i] < 0 + this.maxdist){
					System.out.println("Areas' x-coordinates should be in scenario range and not too near to border");
					System.out.println("Area-Type: " + areaParams[areaParams.length - 3]+"maxdist " + maxdist + " Params " + areaParams[i]);
					System.exit(0);
				}
			}
			for(int i = 1; i < check_till; i = i+2){
				if(areaParams[i] > parameterData.y - this.maxdist || areaParams[i] < 0 + this.maxdist){
					System.out.println("Areas' y-coordinates should be in scenario range and not too near to border");
					System.out.println("Area-Type: " + areaParams[areaParams.length - 3] + " maxdist " + maxdist + " Params " + areaParams[i]);
					System.exit(0);
				}
			}
			// get the current catastrophe area instance
			catastropheAreas[numCatastropheAreas-1] = CatastropheArea.GetInstance(areaParams);
		}
		// arguments not needed anymore
		this.catastropheAreaArgs = null;
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
		case 'a': 
			// "avgMobileNodesPerGroup"
			this.avgMobileNodesPerGroup = Double.parseDouble(val);
			System.out.println("avgMobileNodesPerGroup will not be considered in this model, because the group sizes depend on the areas");
			return true;
		case 'b': // "specify catastrophe areas"
			this.catastropheAreaArgs.add(val);
			// further processing will be done later in processArguments
			return true;
		case 'c': // "change"
			this.pGroupChange = 0;
			System.out.println("Group Change will not be considered in this model, because the groups belong to areas");
			return true;
		case 'e': // "MaxCatastropheAreas"
			this.maxCatastropheAreas = (int)Double.parseDouble(val);
			// to avoid errors space for catastrophe areas will be reserved in processArguments
			return true;
		case 'g': // "Vertices to approximate circle"
			this.circlevertices = (int)Double.parseDouble(val);
		case 'h': //maxspeed
			System.out.println("In this model you can't specify maxspeed using area dependend speed");
			return true;
		case 'j': //factor to multiply MinCObstacle paths with
			this.factor = Double.parseDouble(val);
			return true;
		case 'l': //minspeed
			System.out.println("In this model you can't specify minspeed using area dependend speed");
			return true;
		case 'o': // obstacle for all groups
			/** fetch params for Obstacle. */
			double[] obstacleParams;
			obstacleParams = parseDoubleArray(val);
			for(int i = 0; i < obstacleParams.length; i = i+2){
				if(obstacleParams[i] > parameterData.x){
					System.out.println("Obstacles' x-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			for(int i = 1; i < obstacleParams.length; i = i+2){
				if(obstacleParams[i] > parameterData.y){
					System.out.println("Obstacles' y-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			Obstacle newone = new Obstacle(obstacleParams);
			for (int i = 0; i < 5; i++) { // for each type of area
				obstacles[i].add(newone);
			}
			onlyOneObstacle = true;
			return true;
		case 'O': //obstacle for only one group
			/** fetch params for Obstacle. */
			double[] help_param = parseDoubleArray(val);
			double[] obstacleParams_group = new double[help_param.length-1];
			// last parameter is the number of the group to add the obstacle
			System.arraycopy(help_param, 0, obstacleParams_group, 0, help_param.length - 1);
			for(int i = 0; i < obstacleParams_group.length; i = i+2) {
				if(obstacleParams_group[i] > parameterData.x){
					System.out.println("Obstacles' x-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			for(int i = 1; i < obstacleParams_group.length; i = i+2){
				if(obstacleParams_group[i] > parameterData.y){
					System.out.println("Obstacles' y-coordinates should be in scenario range");
					System.exit(0);
				}
			}
			Obstacle newone_group = new Obstacle(obstacleParams_group);
			this.obstacles[(int)(help_param[help_param.length-1])].add(newone_group);
			onlyOneObstacle = false;
			if (help_param[help_param.length-1] == 4) {
				addObsT4 = true;
			}
			return true;
		case 'q': // "min space for group to accept way as valid"
			this.mindist = Double.parseDouble(val);
			return true;
		case 'r': // "random vector max length"
			this.maxdist = Double.parseDouble(val);
			this.oldmaxdist = maxdist;
			return true;
		case 's': // "groupSizeDeviation"
			this.groupSizeDeviation = Double.parseDouble(val);
			System.out.println("Group Size Deviation will not be considered in this model, because the group sizes depend on the areas");
			return true;
		case 'w': //write all important aspects to file
			this.shallwrite = true;
			this.write_moves = true;
			return true;
		case 'v': //write all important aspects to file
			this.shallwrite = true;
			this.write_vis = true;
			return true;
		case 'K': // do not knock over pedestrians - no ambulances in areas
			this.no_knock_over = true;
			return true;
		default:
			return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		RandomSpeedBase.printHelp();
		System.out.println( getInfo().name + ":" );
		System.out.println("\t-a <average no. of nodes per group>");
		System.out.println("\t-b <catastrophe area (can be used multiple times for several catastrophe areas)>");
		System.out.println("\t-c <group change probability>");
		System.out.println("\t-e <max catastrophe areas>");
		System.out.println("\t-r <max. distance to group center>");
		System.out.println("\t-s <group size standard deviation>");
		System.out.println("\t-O <obstacle for only one group (specified in last param)>");
		System.out.println("\t-w <write vis. info to file & show movements>");
		System.out.println("\t-v <write vis. info to file & show vis.graph>");
		System.out.println("\t-K <do not knock over pedestrians - no ambulances in areas beside APP>");
	}

	//determins a valid random destination inside a specified area
	public Position DetRandDst(double xleft, double xright, double ylow, double yhigh, CatastropheArea area){
		int maximum = 0;
		boolean inobstacle = false;
		Position newdst = null;
		
		while(maximum < 10000){
			newdst = new Position(xleft + (randomNextDouble() * (xright - xleft)) , yhigh + (randomNextDouble() * (ylow - yhigh)));

			/*** Debug **/
			if (debug) {
				System.out.println("yhigh " + yhigh + " ylow " + ylow + " xright " + xright + " xleft " + xleft);
				System.out.println("dst " + newdst.x + " " + newdst.y);
				double test1 = newdst.x - maxdist;
				double test2 = newdst.y - maxdist;
				double test3 = 2*maxdist;
				double test4 = 2*maxdist;
				System.out.println("area contains " + area.contains(test1,test2,test3,test4));
				System.out.println("area contains point(x,y) " + area.contains(test1,test2));
				System.out.println("area contains point(x,y+height) " + area.contains(test1,test2+test3));
				System.out.println("area contains point(x+width,y) " + area.contains(test1+test3,test2));
				System.out.println("area contains point(x+width,y+height) " + area.contains(test1+test3,test2+test4));
				System.out.println("berechnete Werte " + test1 + " " + test2 + " " + test3 + " " + test4);
				Rectangle test = new Rectangle((int)newdst.x - (int)maxdist, (int)newdst.y - (int)maxdist, 2*(int)maxdist, 2*(int)maxdist);
				System.out.println("Rechteck x" + test.x + " y " + test.y + " width " + test.width + " height " + test.height);
			}
			/*** Debug **/

			if(area.contains(newdst.x - maxdist, newdst.y - maxdist, 2*maxdist, 2*maxdist)){
				for (int i = 0; i < obstacles[area.type].size(); i++){
					inobstacle = obstacles[area.type].get(i).contains(newdst.x, newdst.y);
					if (inobstacle){
						++maximum;
						break;
					} 
				}
				if (!inobstacle){
					if(OnObstacleBorder(newdst, area)){
						System.out.println("Fehler bei detranddst");
					}
					return newdst;
				}
			}
			else{
				++maximum;
			}
		}
		System.out.println("Please enlarge your area or specify other obstacles, System was not able to compute valid destination - DetRandDst("+area.type+")");
		System.out.println("exit(0) but HACKED");
		System.exit(0);
		return null;
	}

	public boolean OnObstacleBorder(Position dst, CatastropheArea area){
		for (int i = 0; i < obstacles[area.type].size(); i++){
			if(obstacles[area.type].get(i).intersectsLine(dst.x, dst.y, dst.x, dst.y)){
				obstacles[area.type].get(i).print();
				return true;
			}
		}
		return false;
	}

	//write coordinates of obstacles, cobstacles, areas and node movements to a file
	@SuppressWarnings("rawtypes")
	public void mywrite(){
		int t = 4;         // use obstacles from 3, cause TEL does not contain moving nodes
		try {
	        PrintWriter mywriter = new PrintWriter(new BufferedWriter(new FileWriter("Aspects.txt")));
			System.out.println("habe erstellt " + "Aspects.txt");
			for(int i=0; i < catastropheAreas.length;i++){
				mywriter.write("Area");
				mywriter.write(catastropheAreas[i].VerticesToString());
				mywriter.write("\n");
			}
			for(int i=0; i < obstacles[t].size(); i++){
				mywriter.write("Obstacle");
				mywriter.write(obstacles[t].get(i).VerticesToString());
				mywriter.write("\n");
			}
			for(int i=0; i < maxCObstacles[t].size(); i++){
				mywriter.write("maxCObstacle");
				mywriter.write(maxCObstacles[t].get(i).VerticesToString());
				mywriter.write("\n");
			}
			for(int i=0; i < minCObstacles[t].size(); i++){
				mywriter.write("minCObstacle");
				mywriter.write(minCObstacles[t].get(i).VerticesToString());
				mywriter.write("\n");
				}
			if (write_vis) { //write information for VisibilityGraph
				for(int i = 0; i < ((LinkedList)Graph[t].get(0)).size(); i++){
					//eine Position
					Position key = (Position)((LinkedList)Graph[t].get(0)).get(i);

					for(int j = 0; j < ((LinkedList)((PositionHashMap)Graph[t].get(1)).get(key)).size(); j++){
						mywriter.write("Visline ");
						mywriter.write(((Line2D.Double)((LinkedList)((PositionHashMap)Graph[t].get(1)).get(key)).get(j)).x1 + " " + ((Line2D.Double)((LinkedList)((PositionHashMap)Graph[t].get(1)).get(key)).get(j)).y1 + " " + ((Line2D.Double)((LinkedList)((PositionHashMap)Graph[t].get(1)).get(key)).get(j)).x2 + " " + ((Line2D.Double)((LinkedList)((PositionHashMap)Graph[t].get(1)).get(key)).get(j)).y2);
						mywriter.write("\n");
					}
				}
				System.out.println("wrote VisibilityGraph infos");
				//end VisibilityGraph
			}
			if (write_moves) {
				for(int i=0; i < parameterData.nodes.length; i++){
					String movement = parameterData.nodes[i].movementString(parameterData.outputDim);
					mywriter.write("Bewegung ");
					mywriter.write(movement);
					mywriter.write("\n");
				}
			}
			mywriter.close();
		} catch (Exception e) {
			System.out.println("Something is wrong with the File specified at Extended Catastrophe mywrite()");
			System.exit(0);
		}
	}

	// chooses the shortest path to next area (only areas that have to be visited for nodetype), even if its width is not maxdist but another has a width of maxdist
	@SuppressWarnings("unchecked")
	public LinkedList<Position> determineway(CatastropheArea area) {
		LinkedList<CatastropheArea> possibleAreas = determineAreastovisit(area);
		LinkedList<Position> completeway = null;
		LinkedList<Position> tempway = null;
		LinkedList<Position> tempway2 = null;
		Position src = null;
		Position toreach = null;
		double mindist2 = Double.MAX_VALUE;
		double tempdist = 0;
		for (int i = 0; i < possibleAreas.size(); i++) {
			switch(area.type){
			case 0:		
				Position end1 = new Position(possibleAreas.get(i).entry.x, possibleAreas.get(i).entry.y);
				toreach = end1;
				Position src1 = new Position(area.exit.x, area.exit.y);
				src = src1;
				break;
			case 1:		
				Position end2 = new Position(possibleAreas.get(i).entry.x, possibleAreas.get(i).entry.y);
				toreach = end2;
				Position src2 = new Position(area.exit.x, area.exit.y);
				src = src2;
				break;
			case 2:		
				break;
			case 3:		
				break;
			case 4:		
				Position end3 = new Position(possibleAreas.get(i).exit.x, possibleAreas.get(i).exit.y);
				toreach = end3;
				Position src3 = new Position(area.exit.x, area.exit.y);
				src = src3;
				break;
			default: 
				//should not be reached
				System.out.println("Error in " + getInfo().name + ", couldn't determine way");
			System.exit(0);
			}
			if(src != null && toreach != null){
				PositionHashMap waysForSrc = ((PositionHashMap)shortestpaths[area.type].get(src));
				PositionHashMap MinwaysForSrc = ((PositionHashMap)Minshortestpaths[area.type].get(src));
				tempway = (LinkedList<Position>)waysForSrc.get(toreach);
				tempway2 = (LinkedList<Position>)MinwaysForSrc.get(toreach);
				for(int j = 0; j < tempway.size()-1; j++){
					tempdist = tempdist + tempway.get(j).distance(tempway.get(j+1));
				}
				if(tempdist < mindist2){
					maxdist = oldmaxdist;
					mindist2 = tempdist;
					completeway = tempway;
				}
				tempdist = 0;
				for(int j = 0; j < tempway2.size()-1; j++){
					tempdist = tempdist + tempway2.get(j).distance(tempway2.get(j+1));
				}
				tempdist = tempdist * factor;
				if(tempdist < mindist2){
					maxdist = mindist;
					mindist2 = tempdist;
					completeway = tempway2;
				}
				tempdist = 0;
				mindist2 = Double.MAX_VALUE;
				area.allways.add(completeway);
			}
		}
		return completeway;
	}

	// determine which areas groups belonging to area may visit
	public LinkedList<CatastropheArea> determineAreastovisit(CatastropheArea area){
		LinkedList<CatastropheArea> Areastovisit = new LinkedList<CatastropheArea>();
		LinkedList<Integer> arraypos = new LinkedList<Integer>();
		switch(area.type){
			case 0:		
				for (int i = 0; i < catastropheAreas.length; i++){
					if(catastropheAreas[i].type == 1){
						Areastovisit.add(catastropheAreas[i]);
						Integer temp = new Integer(i);
						arraypos.add(temp);
					}
				}
				if (Areastovisit.size() == 0) {
					System.out.println("Please specify a patients waiting for treatment area for incident location! aborting...");
					System.exit(0);
				}
				break;
			case 1:		
				for (int i = 0; i < catastropheAreas.length; i++){
					if(catastropheAreas[i].type == 2){
						Areastovisit.add(catastropheAreas[i]);
						Integer temp = new Integer(i);
						arraypos.add(temp);
					}
				}
				if (Areastovisit.size() == 0) {
					System.out.println("Please specify a casualties clearing station for patients waiting for treatment area! aborting...");
					System.exit(0);
				}
				break;
			case 2:
				break;
			case 3:
				break;
			case 4:
				for (int i = 0; i < catastropheAreas.length; i++){
					if(catastropheAreas[i].type == 2){
						Areastovisit.add(catastropheAreas[i]);
						Integer temp = new Integer(i);
						arraypos.add(temp);
					}
				}
				if (Areastovisit.size() == 0) {
					System.out.println("Please specify a casualties clearing station for ambulance parking point! aborting...");
					System.exit(0);
				}
				break;
			default: //should not be reached
				System.out.println("Error in " + getInfo().name + ", couldn't determine Areas to visit");
			System.exit(0);
		}
		return Areastovisit;
	}

	// determine movement cycle depending on area the group belongs to and type of group
	public LinkedList<Position> determineMovementCycle(CatastropheArea area, CatastropheNode node){
		int movePhase = 0;
		LinkedList<Position> cycle = new LinkedList<Position>();
		double xleft = area.getBounds().x;
		double xright = area.getBounds().x + area.getBounds().width;
		double yhigh = area.getBounds().y;
		double ylow = area.getBounds().y + area.getBounds().height;
		double whichArea = randomNextDouble();
		double numAreas = area.allways.size();
		double step = 1 / numAreas;
		int PosInList = 0;
		for(int i = 1; i <= numAreas; i++) {
			if(whichArea <= i*step) {
				PosInList = i - 1;
				break;
			}
		}
		switch(area.type){
		case 0:		//incident location, therefore specific cycle
			do {
				if (movePhase == 0) {
					//random movement in incident site
					maxpause = oldmaxpause;
					dst = DetRandDst(xleft, xright, ylow, yhigh, area);
					cycle.add(dst);
					++movePhase;
				}
				else {
					if(movePhase == 1){
						//transport injured via incident location exit to patients waiting for treatment area entry
						maxpause = oldmaxpause;
						for(int i = 0; i < (area.allways.get(PosInList)).size(); i++){
							dst = ((area.allways.get(PosInList)).get(i));
							cycle.add(dst);
						}
						if(area.neighborAreaPos != null && area.exit.equals(catastropheAreas[area.neighborAreaPos.intValue()].entry)){
							movePhase = 0;
						}
						else {
							++movePhase;
						}
					}
					else {
						if(movePhase == 2){
							//return to incident location exit using the same way as before
							maxpause = 0.0;
							for(int i = (area.allways.get(PosInList)).size()-2; i >= 0; i--){
								//skip last entry, because you are already there
								dst = (area.allways.get(PosInList)).get(i);
								cycle.add(dst);
							}
							movePhase = 0;
						}

					}
				}
			}
			while(movePhase != 0);
			break;
		case 1:		 //patients waiting for treatment area, therefore specific cycle
			do {
				if (node.type == 0){ //transport node
					if (movePhase == 0) {
						//random movement to receive injured
						maxpause = oldmaxpause;
						dst = DetRandDst(xleft, xright, ylow, yhigh, area);
						cycle.add(dst);
						++movePhase;
					}
					else{
						if(movePhase == 1){
							//move via patients waiting for treatment area exit to casualties clearing station entry
							maxpause = oldmaxpause;
							for(int i = 0; i < (area.allways.get(PosInList)).size(); i++){
								dst = ((area.allways.get(PosInList)).get(i));
								cycle.add(dst);
							}
							++movePhase;
						}
						else {
							if (movePhase == 2){
								//return to patients waiting for treatment area exit
								maxpause = 0.0;
								for(int i = (area.allways.get(PosInList)).size()-2; i >= 0; i--){
									//skip last entry, because you are already there
									dst = ((area.allways.get(PosInList)).get(i));
									cycle.add(dst);
								}
								movePhase = 0;	
							}
						}
					}
				}
				else { //treatment node
					maxpause = oldmaxpause;
					dst = DetRandDst(xleft, xright, ylow, yhigh, area);
					cycle.add(dst);
				}
			}
			while(movePhase != 0);
			break;
		case 2:		//casualties clearing station, therefore specific cycle
			//random movement in casualties clearing station
			maxpause = oldmaxpause;
			dst = DetRandDst(xleft, xright, ylow, yhigh, area);
			cycle.add(dst);
			break;
		case 3:		//technical operational command, therefore speed specific cycle
			//random movement in technical operational command
			maxpause = oldmaxpause;
			dst = DetRandDst(xleft, xright, ylow, yhigh, area);
			cycle.add(dst);
			break;
		case 4:		//ambulance, therefore specific cycle
			do {
				if(node.type == 0){ //transport node
					if(movePhase == 0){
						//choose vehicle
						maxpause = oldmaxpause;
						//System.out.println("choose vehicle\n");
						dst = DetRandDst(xleft, xright, ylow, yhigh, area);
						cycle.add(dst);
						movePhase++;
					}
					else{
						if (movePhase == 1){
							//get to injured at casualties clearing station exit via ambulance parking point exit
							maxpause = oldmaxpause;
							for(int i = 0; i < (area.allways.get(PosInList)).size(); i++){
								dst = ((area.allways.get(PosInList)).get(i));
								cycle.add(dst);
							}
							++movePhase;
						}
						else {
							if (movePhase == 2){
								//drive street until new start
								maxpause = 0.0;
								LinkedList<Position> tempway = waysToBorder(((area.allways.get(PosInList)).get((area.allways.get(PosInList)).size()-1)), area);
								//way to borderentry
								for(int i = 1; i < tempway.size(); i++){
									//skip first entry, because you are already there
									dst = tempway.get(i);
									// end of this cycle ist the borderentry, thus switch off
									if (i == (tempway.size()-1)) {
										dst = new Position(dst.x,dst.y, 0.0, 2.0);
									}
									cycle.add(dst);
								}
								tempway = determineBorderWay(area.borderentry, area.borderexit);
								//move on border between borderenty and borderexit
								for(int i = 0; i < tempway.size(); i++){
									dst = tempway.get(i);
									cycle.add(dst);
								}
								tempway = waysFromBorder(((area.allways.get(PosInList)).get((area.allways.get(PosInList)).size()-1)), area);
								//move from borderexit to areaentry
								for(int i = 1; i < tempway.size(); i++){
									dst = tempway.get(i);
									// end of this cycle ist the area-entry, thus switch on
									if (i == (tempway.size()-1)) {
										dst = new Position(dst.x,dst.y, 0.0, 1.0);
									}
									cycle.add(dst);
								}
								movePhase = 0;
							}
						}
					}
				}
				else{ //treatment node
					maxpause = oldmaxpause;
					dst = DetRandDst(xleft, xright, ylow, yhigh, area);
					cycle.add(dst);
				}
			}
			while(movePhase != 0);
			break;
		default:	//should not be reached
			System.out.println("Error in " + getInfo().name + ", couldn't determine Movement Cycle");
			System.exit(0);
		}
		return cycle;
	}
	
	//initialize visibility graph
	public LinkedList<Serializable> VisibilityGraph(LinkedList<Obstacle> CObstacles, int type){
		LinkedList<Position> Vertices = new LinkedList<Position>();
		PositionHashMap Edges = new PositionHashMap();
		LinkedList<Serializable> VisGraph = new LinkedList<Serializable>();

		//add Corners of Obstacles (convex)
		for(int i = 0; i < CObstacles.size(); i++){
			Position[] temp = CObstacles.get(i).getPosVertices();
			for(int j = 0; j < temp.length; j++){
				Vertices.add(temp[j]);
			}
		}
		//add Entrys and Exits of DAs
		for(int i = 0; i < catastropheAreas.length; i++){
			//add relevant entrys/exits for the type
			// ToDo: specify for further types to optimize calculation
			switch (type) {
			case 4: //Ambulance Park. Point
				switch (catastropheAreas[i].type) {
				case 4: 
					//all entrys of my own area ...
					Vertices.add(catastropheAreas[i].entry);
					Vertices.add(catastropheAreas[i].exit);
					Vertices.add(catastropheAreas[i].borderentry);
					Vertices.add(catastropheAreas[i].borderexit);
					break;
				case 2: 
					//from clearing station the exits
					Vertices.add(catastropheAreas[i].exit);			    
				}  
				break;
			default: 			
				Vertices.add(catastropheAreas[i].entry);
				Vertices.add(catastropheAreas[i].exit);
				if(catastropheAreas[i].type == 4){
					Vertices.add(catastropheAreas[i].borderentry);
					Vertices.add(catastropheAreas[i].borderexit);
				}
				break;
			}
		}
		for(int i = 0; i < Vertices.size(); i++){
			LinkedList<Line2D.Double> VisEdges = new LinkedList<Line2D.Double>();
			LinkedList<Position> VisVert = new LinkedList<Position>();
			VisVert = VisibleVertices(Vertices.get(i), Vertices, CObstacles);
			for(int j = 0; j < VisVert.size(); j++){
				Line2D.Double line = new Line2D.Double(Vertices.get(i).x, Vertices.get(i).y, VisVert.get(j).x, VisVert.get(j).y);
				//to realize obstacles that reach to the boundary - vertices on the bound. are ignored
				if (!Vertice_on_Boundary( Vertices.get(i).x, Vertices.get(i).y, 
						VisVert.get(j).x, VisVert.get(j).y )) {
					VisEdges.add(line);
				}
			}
			Edges.put(Vertices.get(i), VisEdges);
		}
		VisGraph.add(Vertices);
		VisGraph.add(Edges);
		return VisGraph;
	}

	public boolean Vertice_on_Boundary (double x1, double y1, double x2, double y2) {
		if ((y1 - maxdist <= 0)&&(y2 - maxdist <= 0)) {
			return true;
		}
		if ((y1 + maxdist >= parameterData.y)&&(y2 + maxdist >= parameterData.y)) {
			return true;
		}
		if ((x1 - maxdist <= 0)&&(x2 - maxdist <= 0)) {
			return true;
		}
		if ((x1 + maxdist >= parameterData.x)&&(x2 + maxdist >= parameterData.x)) {
			return true;
		}
		return false;
	}


	//calculates visible vertices
	public LinkedList<Position> VisibleVertices(Position Vertex, LinkedList<Position> Vertices, LinkedList<Obstacle> CObstacles){
		boolean visible = false;
		boolean sameObstacle = false;
		boolean StartOnObstacle = false;
		boolean StopOnObstacle = false;
		Obstacle obstacle = null;
		int numintersections = 0;
		LinkedList<Position> VisVert = new LinkedList<Position>();
		for(int i = 0; i < Vertices.size(); i++){
			numintersections = 0;
			sameObstacle = false;
			StartOnObstacle = false;
			StopOnObstacle = false;
			for(int j = 0; j < CObstacles.size(); j++){
				if(CObstacles.get(j).contains(Vertex.x, Vertex.y)){
					//StartOnObstacle = true;
				}
				if(CObstacles.get(j).contains(Vertices.get(i).x, Vertices.get(i).y)){
					StopOnObstacle = true;
				}
				if(CObstacles.get(j).isVertice(Vertex)){
					StartOnObstacle = true;
				}
				if(CObstacles.get(j).isVertice((Vertices.get(i)))){
					StopOnObstacle = true;
				}
				if(CObstacles.get(j).sameObstacle(Vertex, Vertices.get(i))) {
					sameObstacle = true;
					obstacle = CObstacles.get(j);
				}
				if(CObstacles.get(j).intersectsLine(Vertex.x, Vertex.y, Vertices.get(i).x, Vertices.get(i).y)) {
					numintersections = numintersections + CObstacles.get(j).intersectsObstacle(Vertex.x, Vertex.y, Vertices.get(i).x, Vertices.get(i).y);
				}
			}
			if((numintersections == 2) && (StartOnObstacle || StopOnObstacle) && !Vertex.equals(Vertices.get(i))){
				//es handelt sich bei Vertex oder Vertices.get(i) um einen Eingang oder Ausgang eines Bereichs; 2 Schnitte heit Endpunkt ist ein Eckpunkt eines Hindernisses
				VisVert.add(Vertices.get(i));
			}
			if((numintersections == 0) && !StartOnObstacle && !StopOnObstacle && !Vertex.equals(Vertices.get(i))){
				//es handelt sich sowohl bei Vertex als auch bei Vertices.get(i) um einen Eingang oder Ausgang eines Bereichs; 0 Schnitte heit es existiert freier Weg
				VisVert.add(Vertices.get(i));
			}
			if(sameObstacle){
				if(numintersections == 3){
					//bei 3 Schnitten mit Kanten handelt es sich bei dem betrachteten Segment um eine Kante. Fr Start- und Endpunkt werden die Schnitte berechnet, 1 Schnittpunkt durch Kante zwischen Start- und Endpunkt + 2 Schnittpunkte durch Kanten an denen nur einer der Punkte beteiligt ist. Da jeder Eckpunkt an genau 2 Kanten beteiligt ist geht das.
					VisVert.add(Vertices.get(i));
				}
				else{
					if(numintersections == 4) {
						//bei 4 Schnittpunkten auf einem Hinderniss, verluft die Kante entweder komplett durch das Polygon oder auerhalb. Deshalb wird ein Punkt auf der Geraden gewhlt und getestet ob dieser im Polygon liegt.
						if(!obstacle.throughObstacle(Vertex.x, Vertex.y, Vertices.get(i).x, Vertices.get(i).y)){
							VisVert.add(Vertices.get(i));
						}
					}
				}
			}
			//zulssige Wege zwischen Polygonen schneiden genau 4 Kanten von Polygonen, nmlich alle Polygonkanten, die Start- bzw. Endpunkt des Weges (dies sind bislang Eckpunkte von Polygonen) als Start- bzw. Eckpunkt besitzen
			if(numintersections == 4 && !sameObstacle && StartOnObstacle && StopOnObstacle){
				visible = true;
			}
			else{
				visible = false;
			}
			if(visible){
				VisVert.add(Vertices.get(i));
			}
		}
		return VisVert;
	}

	//calculates the Algorithm of Dijkstra for given Graph and starting point
	@SuppressWarnings("rawtypes")
	public PositionHashMap Dijkstra(LinkedList Graph, Position start){
		PositionHashMap weights = new PositionHashMap();
		Double min = new Double(Double.MAX_VALUE);
		Position minpos = null;
		PositionHashMap ways = new PositionHashMap();
		for(int i = 0; i < ((LinkedList)Graph.get(0)).size(); i++){
			if(((Position)((LinkedList)Graph.get(0)).get(i)).equals(start)){
				Double value = new Double(0.0);
				weights.put(start, value);
				LinkedList<Position> computeway = new LinkedList<Position>();
				computeway.add(start);
				ways.put(start, computeway);
			}
			else{
				Double value = new Double(Double.MAX_VALUE);
				weights.put(((Position)((LinkedList)Graph.get(0)).get(i)), value);
			}
		}
		PositionHashMap vertices = new PositionHashMap();
		vertices = ((PositionHashMap)weights.clone());
		while(vertices.size() != 0){
			minpos = null;
			min = new Double(Double.MAX_VALUE);
			Iterator it = vertices.entrySet().iterator();
			while(it.hasNext()){
				Map.Entry entry = (Map.Entry)it.next();
				if(((Double)entry.getValue()).doubleValue() < min.doubleValue()){
					min = ((Double)entry.getValue());
					minpos = ((Position)entry.getKey());
				}
			}
			if(minpos == null){
				return ways;
			}
			vertices.remove(minpos);
			for(int i = 0; i < ((LinkedList)((PositionHashMap)Graph.get(1)).get(minpos)).size(); i++){
				Position endpoint1 = new Position(((Line2D.Double)((LinkedList)((PositionHashMap)Graph.get(1)).get(minpos)).get(i)).x1, ((Line2D.Double)((LinkedList)((PositionHashMap)Graph.get(1)).get(minpos)).get(i)).y1);
				Position endpoint2 = new Position(((Line2D.Double)((LinkedList)((PositionHashMap)Graph.get(1)).get(minpos)).get(i)).x2, ((Line2D.Double)((LinkedList)((PositionHashMap)Graph.get(1)).get(minpos)).get(i)).y2);
				if(!(endpoint1.equals(minpos))){
					double help1 = ((Double)weights.get(endpoint1)).doubleValue();
					double help2 = ((Double)weights.get(minpos)).doubleValue();
					if(help1 > help2 + minpos.distance(endpoint1)){
						Double value = new Double(help2 + minpos.distance(endpoint1));
						weights.changeto(endpoint1, value);
						vertices.changeto(endpoint1, value);
						LinkedList<Position> computeway = new LinkedList<Position>();
						for(int j = 0; j < ((LinkedList)ways.get(minpos)).size(); j++){
							computeway.add(((Position)((LinkedList)ways.get(minpos)).get(j)));
						}
						computeway.add(endpoint1);
						ways.changeto(endpoint1, computeway);
					} 
				}
				if(!(endpoint2.equals(minpos))){
					double help1 = ((Double)weights.get(endpoint2)).doubleValue();
					double help2 = ((Double)weights.get(minpos)).doubleValue();
					if(help1 > help2 + minpos.distance(endpoint2)){
						Double value = new Double(help2 + minpos.distance(endpoint2));
						weights.changeto(endpoint2, value);
						vertices.changeto(endpoint2, value);
						LinkedList<Position> computeway = new LinkedList<Position>();
						for(int j = 0; j < ((LinkedList)ways.get(minpos)).size(); j++){
							computeway.add(((Position)((LinkedList)ways.get(minpos)).get(j)));
						}
						computeway.add(endpoint2);
						ways.changeto(endpoint2, computeway);
					} 
				}
			}
		}

		return ways;
	}

	//determine way from borderentry to borderexit on border
	public LinkedList<Position> determineBorderWay(Position borderentry, Position borderexit){
		LinkedList<Position> way = new LinkedList<Position>();
		if(borderentry.equals(borderexit)){
			return null;
		}	
		if(borderentry.x == borderexit.x){
			way.add(borderexit);
			return way;
		}
		if(borderentry.y == borderexit.y){
			way.add(borderexit);
			return way;
		}
		else{
			Position temp = new Position(borderentry.x, borderexit.y);
			way.add(temp);
			way.add(borderexit);
		}
		return way;
	}

	//find ways to border
	@SuppressWarnings("unchecked")
	public LinkedList<Position> waysToBorder(Position src, CatastropheArea area) {
		double MinTempdist = 0.0;
		double MaxTempdist = 0.0;
		PositionHashMap waysForMinCObstacles = ((PositionHashMap)Minshortestpaths[area.type].get(src));
		PositionHashMap waysForMaxCObstacles = ((PositionHashMap)shortestpaths[area.type].get(src));
		LinkedList<Position> MinTempway = ((LinkedList<Position>)waysForMinCObstacles.get(area.borderentry));
		LinkedList<Position> MaxTempway = ((LinkedList<Position>)waysForMaxCObstacles.get(area.borderentry));
		for(int i = 0; i < MinTempway.size()-1; i++) {
			MinTempdist = MinTempdist + MinTempway.get(i).distance(MinTempway.get(i+1));
		}
		for(int i = 0; i < MaxTempway.size()-1; i++) {
			MaxTempdist = MaxTempdist + MaxTempway.get(i).distance(MaxTempway.get(i+1));
		}
		if(MinTempdist * factor < MaxTempdist) {
			maxdist = mindist;
			return MinTempway;
		}
		else {
			maxdist = oldmaxdist;
			return MaxTempway;
		}
	}

	//find ways from border
	@SuppressWarnings("unchecked")
	public LinkedList<Position> waysFromBorder(Position src, CatastropheArea area){
		double MinTempdist = 0.0;
		double MaxTempdist = 0.0;
		PositionHashMap waysForMinCObstacles = ((PositionHashMap)Minshortestpaths[area.type].get(src));
		PositionHashMap waysForMaxCObstacles = ((PositionHashMap)shortestpaths[area.type].get(src));
		LinkedList<Position> MinTempway = ((LinkedList<Position>)waysForMinCObstacles.get(area.entry));
		LinkedList<Position> MaxTempway = ((LinkedList<Position>)waysForMaxCObstacles.get(area.entry));
		for(int i = 0; i < MinTempway.size()-1; i++){
			MinTempdist = MinTempdist + MinTempway.get(i).distance(MinTempway.get(i+1));
		}
		for(int i = 0; i < MaxTempway.size()-1; i++){
			MaxTempdist = MaxTempdist + MaxTempway.get(i).distance(MaxTempway.get(i+1));
		}
		if(MinTempdist * factor < MaxTempdist){
			maxdist = mindist;
			return MinTempway;
		}
		else{
			maxdist = oldmaxdist;
			return MaxTempway;
		}
	}
}
