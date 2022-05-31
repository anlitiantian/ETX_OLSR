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

package edu.bonn.cs.iv.bonnmotion.apps;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import edu.bonn.cs.iv.bonnmotion.App;
import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.apps.helper.TopologyChangeTimes;
import edu.bonn.cs.iv.bonnmotion.apps.helper.TopologyChangeTimesResult;
import edu.bonn.cs.iv.bonnmotion.apps.helper.TopologyChangeTimesResult.InRangeInfo;

public class InRangePrinter extends App {
	private static ModuleInfo info;
	private static Scenario s = null;
	
	protected static final String filesuffix = ".irp";
	
	protected double intervalLength = -1.0;
	protected TopologyChangeTimesResult tctr = null;
	protected String name = null;
	protected double range = -1;
	protected int nodeCount = -1;
	protected MobileNode nodes[] = null;
	
	static {
		info = new ModuleInfo("InRangePrinter");
		info.description = "Prints information about nodes in range";

		info.major = 0;
		info.minor = 2;
		info.revision = ModuleInfo.getSVNRevisionStringValue("$rev$");

		info.contacts.add(ModuleInfo.BM_MAILINGLIST);
		info.authors.add("Raphael Ernst");
		info.authors.add("Sascha Jopen");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
	}

	public static ModuleInfo getInfo() {
		return info;
	}
	
	public InRangePrinter(String[] args) {
		go(args);
	}
	
	protected void initializeVariables() {
		if (name == null) {
			printHelp();
			System.exit(0);
		}
		
		if(range <= 0 && intervalLength <= 0) {
			System.out.println("You must set intervalLength or range.");
			System.exit(1);
		}

		try {
			s = Scenario.getScenario(name);
		} catch (Exception e) {
			App.exceptionHandler("Error reading file", e);
		}
		
		nodes = s.getNode();
		nodeCount = s.nodeCount();
		
	}
	
	public void go(String[] args) {
		parse(args);

		initializeVariables();
		
		ArrayList<Double> sortedChangeTimes = null;
		if(intervalLength > 0.0) {
			sortedChangeTimes = new ArrayList<Double>();
			double actualTime = intervalLength;
			while(actualTime < s.getDuration()) {
				sortedChangeTimes.add(actualTime);
				actualTime += intervalLength;
			}
		} else {
			TopologyChangeTimes tct = new TopologyChangeTimes(range,s.getNode(),s.getDuration());
			tctr = tct.getTopologyChanges();
			sortedChangeTimes = tctr.getChangeTimes();
		}
		
		prepareAndPrintResults(sortedChangeTimes);
	}
		
	protected void prepareAndPrintResults(ArrayList<Double> timestamps) {
		double inRange[][] = new double[nodeCount][nodeCount];
		
		PrintWriter out = openPrintWriter(name + filesuffix);
		
		for(int i=0;i<timestamps.size();i++) {
			final double actualTime = timestamps.get(i);
			for(int linkStartNode=0;linkStartNode<nodeCount;linkStartNode++) {
				Position posStartNode = nodes[linkStartNode].positionAt(actualTime);
				for(int linkEndNode=linkStartNode;linkEndNode<nodeCount;linkEndNode++) {
					if(linkEndNode == linkStartNode) {
						inRange[linkStartNode][linkStartNode] = 0.0;
						continue;
					}
					Position posEndNode = nodes[linkEndNode].positionAt(actualTime);
					
					final double distance = posStartNode.distance(posEndNode);
					inRange[linkStartNode][linkEndNode] = distance;
					inRange[linkEndNode][linkStartNode] = distance;
				}
			}
			
			printResults(out,actualTime, inRange,null);
		}
		
		out.close();
	}
	
	protected void printResults(PrintWriter out, final double actualTime, double inRange[][], double previousInRange[][]) {
		out.print(actualTime + " ");
		for(int linkStartNode=0;linkStartNode<nodeCount;linkStartNode++) {
			out.print("[ ");
			for(int linkEndNode=0;linkEndNode<nodeCount;linkEndNode++) {
				if(linkEndNode == linkStartNode) {
					continue;
				}
				
				if(range > 0) {
					final int src = linkStartNode < linkEndNode ? linkStartNode : linkEndNode;
					final int dst = linkStartNode < linkEndNode ? linkEndNode : linkStartNode;
					
					int inRangeIndicator = -1;
				
					if(tctr != null) {
						for(int i=0;i<tctr.rangeInfo.size();i++) {
							InRangeInfo ri = tctr.rangeInfo.elementAt(i); 
							if(ri.time == actualTime && ri.srcNode == src && ri.dstNode == dst) {
								inRangeIndicator = ri.inRange ? 1 : 0;
							}
						}
					}
					
					if(inRangeIndicator == -1) {
						inRangeIndicator = inRange[linkStartNode][linkEndNode] < range ? 1 : 0;
					}
					out.print(inRangeIndicator + " ");
				} else {
					out.print(inRange[linkStartNode][linkEndNode] + " ");
				}
			}
			out.print("]");
		}
		out.println();
	}
		
	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'f':
				this.name = val;
				return true;
			case 'l':
				intervalLength = Double.parseDouble(val);
				return true;
			case 'r':
				this.range = Double.parseDouble(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
		System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("InRangePrinter:");
		System.out.println("\t-f <filename> \tScenario");
		System.out.println("\t[-l <double>]\tInterval length. Set this if intervals should be printed instead of change topology changes only");
		System.out.println("\t[-r <double>]\tTransmission range. Required for non-interval mode");
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		new IntervalFormat(args);
	}
}
