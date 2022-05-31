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

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.RandomSpeedBase;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.ScenarioLinkException;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

public class RandomWaypoint extends RandomSpeedBase {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("RandomWaypoint");
        info.description = "Application to construct RandomWaypoint (2D/3D) mobility scenarios";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Florian Schmitt");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }

	protected int dim = 4;
	
	private double meshNodeDistance = -1.;
	
	public RandomWaypoint(int nodes, double x, double y, double z, double duration, double ignore, long randomSeed, double minspeed, double maxspeed, double maxpause, int dim) {
		super(nodes, x, y, z, duration, ignore, randomSeed, minspeed, maxspeed, maxpause);
		this.dim = dim;
		generate();
	}

	public RandomWaypoint(String[] args) {
		go(args);
	}
	
	public void go(String[] args) {
		super.go(args);
		generate();
	}
	
	public RandomWaypoint(String args[], Scenario _pre, Integer _transitionMode){
		predecessorScenario = _pre;
		transitionMode = _transitionMode.intValue();
		isTransition = true;
		go(args);
	}

	public void generate(){
		preGeneration();	// Sets Random Seed & Duration += ignore-Time & Attractor Field
		
		for(int i = 0; i < parameterData.nodes.length; i++){
			parameterData.nodes[i] = new MobileNode();
			double t = 0.;
			Position src = null;
			if(isTransition){
				try{
					Waypoint lastW = transition(predecessorScenario, transitionMode, i);
					src = lastW.pos;
					t = lastW.time;
				} catch(ScenarioLinkException e){
					e.printStackTrace();
				}
			} 
			else{
				src = randomNextPosition();
			}
			while(t < parameterData.duration){
				Position dst;
				if(!parameterData.nodes[i].add(t, src))
					throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (1)");
			
				switch(dim){
					case 1 :		// movement only on x-axis
						dst = randomNextPosition(-1., src.y, -1.);
						break;
					case 2 : 		// movement on x or y-axis
						switch((int)(randomNextDouble() * 2.0)){
							case 0 :
								dst = randomNextPosition(-1., src.y, src.z);
								break;
							case 1 :
								dst = randomNextPosition(src.x, -1., src.z);
								break;
							default :
								throw new RuntimeException(getInfo().name + ".go: This is impossible - how can (int)(randomNextDouble() * 2.0) be something other than 0 or 1?!");
						}
						break;
					case 3 :
						dst = randomNextPosition();
						break;
					case 4 : 		// movement on x, y or z-axis
						switch((int)(randomNextDouble() * 3.0)){
							case 0 :
								dst = randomNextPosition(-1., src.y, src.z);
								break;
							case 1 :
								dst = randomNextPosition(src.x, -1., src.z);
								break;
							case 2 :
								dst = randomNextPosition(src.x, src.y, -1.);
								break;
							default :
								throw new RuntimeException(getInfo().name + ".go: This is impossible - how can (int)(randomNextDouble() * 3.0) be something other than 0, 1 or 2?!");
						}
						break;
					case 5 : 		// classical Random Waypoint
						dst = randomNextPosition();
						break;
					default :
						throw new RuntimeException(getInfo().name + ".go: dimension may only be of value 1, 2, 3, 4 or 5.");
				}
				double speed = (maxspeed - minspeed) * randomNextDouble() + minspeed;
				double dist = src.distance(dst);
				double time = dist / speed;
				t += time;
				if(!parameterData.nodes[i].add(t, dst))
					throw new RuntimeException(getInfo().name + ".go: error while adding waypoint (2)");
				if((t < parameterData.duration) && (maxpause > 0.0)){
					double pause = maxpause * randomNextDouble();
					t += pause;
				}
				src = dst;
			}
		}
		postGeneration();
	}

    protected boolean parseArg(String key, String value) {
        if (key.equals("dim")) {
            dim = Integer.parseInt(value);
            return true;
        } else return super.parseArg(key, value);
    }
	
	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'o': // "dimensiOn"
				dim = Integer.parseInt(val);
				if ((dim < 1) || (dim > 5)) {
					System.out.println("dimension must be between 1 and 5");
					System.exit(0);
				}
				if ((parameterData.aFieldParams != null) && (dim != 5))
					System.out.println("warning: attractor field not used if dim != 3 (2D) OR dim != 5 (3D");
				return true;
			case 'm': // set mesh node distance
			    meshNodeDistance = Double.parseDouble(val);
			    return true;
			default:
				return super.parseArg(key, val);
		}
	}
	
	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		RandomSpeedBase.printHelp();
		System.out.println( getInfo().name + ":");
		System.out.println("\t-o <dimension: 1: x only, 2: x or y, 3: x and y, 4: x or y or z, 5: x and y and z>");
	}
	
	protected void postGeneration() {
		for ( int i = 0; i < parameterData.nodes.length; i++ ) {

			Waypoint l = parameterData.nodes[i].getLastWaypoint();
			if (l.time > parameterData.duration) {
				Position p = parameterData.nodes[i].positionAt(parameterData.duration);
				parameterData.nodes[i].removeLastElement();
				parameterData.nodes[i].add(parameterData.duration, p);
			}
		}
		
		if (meshNodeDistance > 0)
		    addMeshNodes();
    
		super.postGeneration();
	}
	
	private void addMeshNodes()
    {
        int numMeshX = (int)Math.floor(parameterData.x / meshNodeDistance);
        int numMeshY = (int)Math.floor(parameterData.y / meshNodeDistance);
        int numMeshZ = (int)Math.floor(parameterData.z / meshNodeDistance);
        int numMeshNodes = numMeshX * numMeshY * numMeshZ;

        System.out.println("Adding a grid of " + numMeshNodes + " static mesh nodes...");

        MobileNode[] nodeNew = new MobileNode[parameterData.nodes.length + numMeshNodes];
        for (int i = 0; i < parameterData.nodes.length; i++)
            nodeNew[i] = parameterData.nodes[i];
        for (int i = 0; i < numMeshNodes; i++)
            nodeNew[parameterData.nodes.length + i] = new MobileNode();

        for (int j = 0; j < numMeshY; j++)
        {
            for (int i = 0; i < numMeshX; i++)
                nodeNew[parameterData.nodes.length + j*numMeshX + i].add(0.0, new Position((i+1) * meshNodeDistance, (j+1) * meshNodeDistance, (i+1)*(j+1)*meshNodeDistance));
        }

        parameterData.nodes = nodeNew;
    }
	
	public void write( String _name ) throws FileNotFoundException, IOException {
		String[] p = new String[1];
		p[0] = "dim=" + dim;
		super.write(_name, p);
	}
}
