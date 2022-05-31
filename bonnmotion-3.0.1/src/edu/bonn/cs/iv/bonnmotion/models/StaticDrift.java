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

import java.io.*;
import java.util.ArrayList;

import edu.bonn.cs.iv.bonnmotion.*;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

/** Application to construct static scenarios with a drift. */

public class StaticDrift extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("StaticDrift");
        info.description = "Application to construct static scenarios with a drift";
        
        info.major = 1;
        info.minor = 2;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Raphael Ernst");
        info.authors.add("Sascha Jopen");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }

    private static final double DEFAULT_X = 0;
    private static final double DEFAULT_Y = DEFAULT_X;
    private static final double DEFAULT_Z = DEFAULT_X;
	private static final double DEFAULT_INTERVAL_LEN = 0;

	protected double deltaX = DEFAULT_X;
	protected double deltaY = DEFAULT_Y;
	protected double deltaZ = DEFAULT_Z;
	protected String input_filename = "";
	protected double interval_len = DEFAULT_INTERVAL_LEN;

	public StaticDrift(int nodes, double x, double y, double z, double duration, double ignore, long randomSeed, double deltaX,
			double deltaY, double deltaZ) {
		super(nodes, x, y, z, duration, ignore, randomSeed);
		this.deltaX = deltaX;
		this.deltaY = deltaY;
		this.deltaZ = deltaZ;
		generate();
	}

	public StaticDrift(String[] args) {
		go(args);
	}

	public void go(String[] args) {
		parameterData.nodes = new MobileNode[0]; // to hide warning that number of nodes should be defined
		super.go(args);
		generate();
	}

	public void generate() {
		if (input_filename.isEmpty()) {
			throw new RuntimeException("you have to define a filename (-f)");
		}

		preGeneration();

		ArrayList<Position> inputPositions = getPositionsFromFile(input_filename);
		parameterData.nodes = new MobileNode[inputPositions.size()];
		for (int i = 0; i < parameterData.nodes.length; i++) {
			parameterData.nodes[i] = new MobileNode();
		}
		
		for (int i = 0; i < parameterData.nodes.length; i++) {
			Position tmp = inputPositions.get(i);
			
			double time = 0;
			while (time < parameterData.duration) {
				double newX = (tmp.x - deltaX) + randomNextDouble() * 2 * deltaX;
				double newY = (tmp.y - deltaY) + randomNextDouble() * 2 * deltaY;
				double newZ = 0.0;
				if (this.parameterData.calculationDim == Dimension.THREED){
					newZ = (tmp.z - deltaZ) + randomNextDouble() * 2 * deltaZ;
				}
				
				
				if (!(parameterData.nodes[i].add(time, new Position(newX, newY, newZ)))) {
					throw new RuntimeException(getInfo().name + ".generate: error while adding waypoint");
				}
				
				if(interval_len > 0) {
					time += interval_len;
				}
				else {
					time = parameterData.duration;
				}
			}
		}
			
		postGeneration();
	}

	/**
	 * reads positions from a file. 
	 * @param filename 
	 * @return list of the read positions
	 */
	protected static ArrayList<Position> getPositionsFromFile(String filename) {
		BufferedReader reader = null;
		ArrayList<Position> result = new ArrayList<Position>();
		int line = 1;
		
		try {
			reader = new BufferedReader(new FileReader(filename));

			for (String c; (c = reader.readLine()) != null;) {
				c = c.trim();
				if (c.length() > 0 && !c.startsWith("#")) {
					String[] tmp = c.split("\\s+");
					double x = Double.parseDouble(tmp[0]);
					double y = Double.parseDouble(tmp[1]);
					double z = 0.0;

					if (tmp.length > 2 && !tmp[2].startsWith("#")) {
						z = Double.parseDouble(tmp[2]);
					}
					result.add(new Position(x, y, z));
				}
				line++;
			}
		}
		catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException(getInfo().name + ".getPositionsFromFile: error while reading position from file in line " + line);
		}
		catch (FileNotFoundException e) {
			throw new RuntimeException(getInfo().name + ".getPositionsFromFile: error while reading file, file not found");
		}
		catch (IOException e) {
			throw new RuntimeException(getInfo().name + ".getPositionsFromFile: error while reading file");
		}
		finally {
			try {
				reader.close();
			}
			catch (Exception e) {
			}
		}
		return result;
	}

	protected boolean parseArg(String key, String val) {
		if (key.equals("deltaX")) {
			deltaX = Double.parseDouble(val);
		}
		else if (key.equals("deltaY")) {
			deltaY = Double.parseDouble(val);
		}
		else if (key.equals("deltaZ")) {
			deltaZ = Double.parseDouble(val);
		}
		else if (key.equals("input_filename")) {
			input_filename = val;
		}
		else
			return super.parseArg(key, val);
		return true;
	}

	public void write(String _name) throws FileNotFoundException, IOException {
		String[] p = new String[4];
		p[0] = "deltaX=" + deltaX;
		p[1] = "deltaY=" + deltaY;
		p[2] = "deltaZ=" + deltaZ;
		p[3] = "input_filename=" + input_filename;
		super.writeParametersAndMovement(_name, p);
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'X':
				deltaX = Double.parseDouble(val);
				return true;
			case 'Y':
				deltaY = Double.parseDouble(val);
				return true;
			case 'Z':
				deltaZ = Double.parseDouble(val);
				return true;
			case 'B':
				deltaX = Double.parseDouble(val);
				deltaY = Double.parseDouble(val);
				deltaZ = Double.parseDouble(val);
				return true;
			case 'T':
				interval_len = Double.parseDouble(val);
				return true;
			case 'f':
				input_filename = val;
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
	    System.out.println(getInfo().toDetailString());
		Scenario.printHelp();
		System.out.println(getInfo().name + ":");
		System.out.println("\t-X <delta X>\t(Default: " + DEFAULT_X + ")");
		System.out.println("\t-Y <delta Y>\t(Default: " + DEFAULT_Y + ")");
		System.out.println("\t-Z <delta Z>\t(Default: " + DEFAULT_Z + ")");
		System.out.println("\t-B <set delta X, delta Y, and delta Z to the same value>");
		System.out.println("\t-T <N>\tRecalculate the position each N seconds. Set to <= 0 to disable. (Default: " + DEFAULT_INTERVAL_LEN + ")");
		System.out.println("\t-f filename");
		System.out.println();
		System.out.println("Warning: Random behaviour if -X, -Y, or -Z are combined with -B.");
	}
}
