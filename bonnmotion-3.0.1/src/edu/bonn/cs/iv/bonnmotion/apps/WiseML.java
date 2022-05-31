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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;

import edu.bonn.cs.iv.bonnmotion.*;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

public class WiseML extends App {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("WiseML");
        info.description = "Application converts scenario files to WiseML format";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Matthias Schwamborn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
    static final int COMPRESSION_NONE = 0;
    static final int COMPRESSION_NOTABS = 1;
    static final int COMPRESSION_BEST = 2;

    static final int HEADER = 0;
    static final int NODE_LEVEL = HEADER+1;
    static final int DATA_LEVEL = NODE_LEVEL+1;
    static final int OTHER_LEVEL = DATA_LEVEL+1;

    protected static final String filesuffix = ".wml";

    protected String name = null;
    protected PrintWriter out = null;
    protected int compression = COMPRESSION_NONE;
    protected double intervalLength = -1.0;
    protected double defaultAltitude = 0;
    protected String path_header = null;
    protected String path_footer = null;
    protected String path_nodeId = null;
    protected boolean useIntegerTimes = false;
    protected boolean printOnlyChangingNodes = false;
	/* transmission range [m] */
	protected double transmissionRange = -1.0;
	protected boolean printlinkactions = false;
    
    public WiseML(String[] args) {
        go(args);
    }

    public void go(String[] args) {
    	
    	List<String> argsList = Arrays.asList(args);
    	if (!argsList.contains("-L") && !argsList.contains("-r")) {
    	    printHelp();
    		System.err.println("\nyou need to provide at least one of:\n\t'-L' for the interval length or\n\t'-r' for the transmission range");
    		System.exit(-1);
    	}
    	
        parse(args);

        Scenario s = null;
        if (name == null) {
            printHelp();
            System.exit(0);
        }

        try {
            s = Scenario.getScenario(name);
        } catch (Exception e) {
            App.exceptionHandler("Error reading file", e);
        }
        
        printWiseMLHead();
        printWiseMLNodeMovement(s);
        printWiseMLTail();

        closeWriter();
    }

    protected void printWiseMLHead() {
        catFile(path_header);
    }

    protected void printWiseMLTail() {
        catFile(path_footer);
    }

    protected void catFile(String _pathToFile) {
        if(_pathToFile != null) {
            BufferedReader input;
            try {
                input = new BufferedReader(new FileReader(_pathToFile));
                String line;
                while((line = input.readLine()) != null) {
                    print(line, HEADER);
                }
                input.close();
            }
            catch (IOException e) {
                System.err.println("IOError - Skipping this step. Errormessage: " + e.getLocalizedMessage());
            }
        }
    }
    
    protected List<Double> computeLinkChangeTimes(Scenario s) {
    	MobileNode[] _nodes = s.getNode();
    	final double _duration = Math.ceil(s.getDuration());
    	List<Double> linkChangeTimes = new ArrayList<Double>();
    	
		for (int j = 0; j < _nodes.length; j++) {
			for (int k = j+1; k < _nodes.length; k++) {
				double[] lsc = null;
				
			    lsc = MobileNode.pairStatistics(_nodes[j], _nodes[k], 0.0, _duration, transmissionRange, false, s.getBuilding(), s.getScenarioParameters().calculationDim);

				for (int l = 6; l < lsc.length; l += 2) {
					double linkUp = lsc[l];
					double linkDown = (l+1 < lsc.length) ? lsc[l+1] : Double.MAX_VALUE ;
					
					if (!linkChangeTimes.contains(linkUp))
						linkChangeTimes.add(linkUp);
					if (!linkChangeTimes.contains(linkDown))
						linkChangeTimes.add(linkDown);	
				}
			}
		}

		Collections.sort(linkChangeTimes);
    	
    	return linkChangeTimes;
    }
    
    protected List<Double> computeLinkChangeTimes(Scenario s, HashMap<Double, List<ActionItem>> actionsPerTime) {
    	MobileNode[] _nodes = s.getNode();
    	final double _duration = Math.ceil(s.getDuration());
    	List<Double> linkChangeTimes = new ArrayList<Double>();
    	
		for (int j = 0; j < _nodes.length; j++) {
			for (int k = 0; k < _nodes.length; k++) {
				if (j != k) {	// do not compare node to itself
	                double[] lsc = null;
	                
	                lsc = MobileNode.pairStatistics(_nodes[j], _nodes[k], 0.0, _duration, transmissionRange, false, s.getBuilding(), s.getScenarioParameters().calculationDim);
    				
	                for (int l = 6; l < lsc.length; l += 2) {
    					double linkUp = lsc[l];
    					double linkDown = (l+1 < lsc.length) ? lsc[l+1] : Double.MAX_VALUE ;
    					
    					if (!linkChangeTimes.contains(linkUp)) linkChangeTimes.add(linkUp);
    					if (!linkChangeTimes.contains(linkDown)) linkChangeTimes.add(linkDown);	
    					
    					List<ActionItem> tmp = actionsPerTime.get(linkUp);
    					if (tmp == null) {
    						tmp = new ArrayList<ActionItem>();
    					}
    					
    					tmp.add(new ActionItem(LinkAction.enableLink, j, k));
    					actionsPerTime.put(linkUp, tmp);
    					
    					tmp = actionsPerTime.get(linkDown);
    					if (tmp == null) {
    						tmp = new ArrayList<ActionItem>();	
    					}
    					
    					tmp.add(new ActionItem(LinkAction.disableLink, j, k));
    					actionsPerTime.put(linkDown, tmp);
    				}
				}
			}
		}
		
		Collections.sort(linkChangeTimes);
		
    	return linkChangeTimes;
    }

    protected void printWiseMLNodeMovement(Scenario s) {
    	MobileNode[] _nodes = s.getNode();
    	final double _duration = Math.ceil(s.getDuration());
    	
    	if (intervalLength > 0 && transmissionRange < 0)		/* interval-based */
    	{
    	    if (printOnlyChangingNodes) {
    	        System.err.println("Interval-based mode prints all nodes");
    	    }
    	    
	        double t = 0;
	
	        printWiseMLTimestamp(t);
	        
	        // Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
	        // If there are Problems with this behavior, its ok to change that according to the complains!
	        printWiseMLAllNodes(_nodes, t, s.getScenarioParameters().calculationDim);
	        
			t += intervalLength;
	
	        while(t < _duration + 1) {
			    printWiseMLTimestamp(t);
			    for(int currentNode = 0; currentNode < _nodes.length; currentNode++) {
			    	// Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
			        // If there are Problems with this behavior, its ok to change that according to the complains!
					printWiseMLOneNode(_nodes, t, currentNode, s.getScenarioParameters().calculationDim);
			    }
			    t += intervalLength;
	        }
    	}
    	else if (intervalLength < 0 && transmissionRange > 0)	/* contact-based */
    	{
            if (!printOnlyChangingNodes) { // print all nodes if a change happens
                List<Double> linkChangeTimes = null;
                HashMap<Double, List<ActionItem>> actionsPerTime = null;
                
                if (printlinkactions) {
                    actionsPerTime = new HashMap<Double, List<ActionItem>>();
                    linkChangeTimes = computeLinkChangeTimes(s, actionsPerTime);
                } else {
        			linkChangeTimes = computeLinkChangeTimes(s);
                }

                for (double timestamp : linkChangeTimes) {
                    printWiseMLTimestamp(timestamp);

                    if (printlinkactions) {
                        for (ActionItem item : actionsPerTime.get(timestamp)) {
                            printWiseMLLinkAction(item.action, getNodeId(item.source), getNodeId(item.target));
                        }
                    }
                    // Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
        	        // If there are Problems with this behavior, its ok to change that according to the complains!
                    printWiseMLAllNodes(_nodes, timestamp, s.getScenarioParameters().calculationDim);
                }
    		} else { // print only affected nodes
        		HashMap<Double, List<ActionItem>> actionsPerTime = new HashMap<Double, List<ActionItem>>();
        		List<Double> linkChangeTimes = computeLinkChangeTimes(s, actionsPerTime);
        		
        		for (double timestamp : linkChangeTimes) {
        			printWiseMLTimestamp(timestamp);

        			if (printlinkactions) {
            			for (ActionItem item : actionsPerTime.get(timestamp)) {
            				printWiseMLLinkAction(item.action, getNodeId(item.source), getNodeId(item.target));
            			}
        			}
        			
    			    ArrayList<Integer> changedNodes = new ArrayList<Integer>();
                    for (ActionItem item : actionsPerTime.get(timestamp)) {
                        if (!changedNodes.contains(item.source)) changedNodes.add(item.source);
                        if (!changedNodes.contains(item.target)) changedNodes.add(item.target);
                    }
                    
                    Collections.sort(changedNodes);
                    
                    for (int i : changedNodes) {
                        Position p = _nodes[i].positionAt(timestamp);
	                    // Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
	        	        // If there are Problems with this behavior, its ok to change that according to the complains!
	                    if (s.getScenarioParameters().calculationDim == Dimension.THREED) {
	                    	printWiseMLNodePosition(getNodeId(i), p.x, p.y, p.z);
	                    } else {
	                    	printWiseMLNodePosition(getNodeId(i), p.x, p.y, this.defaultAltitude);
	                    }
                    }
                }
            }
    	}
    	else if (intervalLength > 0 && transmissionRange > 0)	/* interval- & contact-based */
    	{
    		HashMap<Double, List<ActionItem>> actionsPerTime = null;
    		List<Double> linkChangeTimes = null;
    		
    		if (printlinkactions || printOnlyChangingNodes) {
                actionsPerTime = new HashMap<Double, List<ActionItem>>();
                linkChangeTimes = computeLinkChangeTimes(s, actionsPerTime);
    		} else {
                linkChangeTimes = computeLinkChangeTimes(s);
    		}
    		
	        double t = 0;
			t += intervalLength;
			int i = 0;
			double timestamp = linkChangeTimes.get(i);
	
	        while(t < _duration + 1) {
	        	while (timestamp < t) {
        			printWiseMLTimestamp(timestamp);

        			if (printlinkactions) {
            			for (ActionItem item : actionsPerTime.get(timestamp)) {
            				printWiseMLLinkAction(item.action, getNodeId(item.source), getNodeId(item.target));
            			}
        			}
        			
        			if (!printOnlyChangingNodes) {
        				// Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
	        	        // If there are Problems with this behavior, its ok to change that according to the complains!
        			    printWiseMLAllNodes(_nodes, timestamp, s.getScenarioParameters().calculationDim);
        			} else {
                        ArrayList<Integer> changedNodes = new ArrayList<Integer>();
                        for (ActionItem item : actionsPerTime.get(timestamp)) {
                            if (!changedNodes.contains(item.source)) changedNodes.add(item.source);
                            if (!changedNodes.contains(item.target)) changedNodes.add(item.target);
                        }
                        
                        Collections.sort(changedNodes);
                        
                        // Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
	        	        // If there are Problems with this behavior, its ok to change that according to the complains!
                        for (int index : changedNodes) {
                            Position p = _nodes[index].positionAt(timestamp);
                            if (s.getScenarioParameters().calculationDim == Dimension.THREED) {
                                printWiseMLNodePosition(getNodeId(index), p.x, p.y, p.z);
                        	} else {
                                printWiseMLNodePosition(getNodeId(index), p.x, p.y, this.defaultAltitude);
                            }
                        }
        			}
        			
        			i++;
        			timestamp = linkChangeTimes.get(i);
        		}
	        	
			    printWiseMLTimestamp(t);
			    for(int currentNode = 0; currentNode < _nodes.length; currentNode++) {
			    	// Dependent on calcDim because it uses the actual z-value if 3D=calcDim and a default value otherwise.
        	        // If there are Problems with this behavior, its ok to change that according to the complains!
					printWiseMLOneNode(_nodes, t, currentNode, s.getScenarioParameters().calculationDim);
			    }
			    t += intervalLength;
	        }
    	} else {
    		throw new RuntimeException("interval length or transmission range is = 0");
    	}
    }

    protected void printWiseMLOneNode(MobileNode[] _nodes, double t, int nodeIndex, Dimension dim) {
        Position p = _nodes[nodeIndex].positionAt(t);
        Position oldPosition = _nodes[nodeIndex].positionAt(t - intervalLength);
        if(oldPosition.equals(p)) {
            System.out.println("Omitting output of node " + getNodeId(nodeIndex) + ". It has not moved since last output at time " + (t-intervalLength) + ".");
        }
        else {
            if (dim == Dimension.THREED) {
                printWiseMLNodePosition(getNodeId(nodeIndex), p.x, p.y, p.z);
            } else {
                printWiseMLNodePosition(getNodeId(nodeIndex), p.x, p.y, this.defaultAltitude);
            }
        }
    }
    
	protected void printWiseMLLinkAction(LinkAction action, String source, String target)
	{
		print(String.format("<%s source=\"%s\" target=\"%s\" />", action, source, target), OTHER_LEVEL);
	}
    
	protected void printWiseMLAllNodes(MobileNode[] nodes, double time, Dimension dim)
	{
		for (int i = 0; i < nodes.length; i++)
		{
		    Position p = nodes[i].positionAt(time);
            if (dim == Dimension.THREED) {
                printWiseMLNodePosition(getNodeId(i), p.x, p.y, p.z);
            } else {
                printWiseMLNodePosition(getNodeId(i), p.x, p.y, this.defaultAltitude);
            }
		}
	}

    protected void printWiseMLTimestamp(final double _value) {
		String timeToPrint;
		if(this.useIntegerTimes) {
		    final int value = new Double(_value).intValue();
		    timeToPrint = Integer.toString(value);
		}
		else {
		    timeToPrint = Double.toString(_value);
		}
		print(String.format("<timestamp>%s</timestamp>", timeToPrint), OTHER_LEVEL);
    }

    protected void printWiseMLNodePosition(final String _nodeId, final double _posX, final double _posY, final double _posZ) {
		final String nodeId = "<node id=\"" + _nodeId + "\">";
		final String posX = "<x>" + _posX + "</x>";
		final String posY = "<y>" + _posY + "</y>";
		final String posZ = "<z>" + _posZ + "</z>";
	
		print(nodeId,OTHER_LEVEL);
		print("<position>",OTHER_LEVEL+1);
		print(posX,OTHER_LEVEL+2);
		print(posY,OTHER_LEVEL+2);
		print(posZ,OTHER_LEVEL+2);
		print("</position>",OTHER_LEVEL+1);
		print("</node>",OTHER_LEVEL);
    }

    protected String getNodeId(final int _nodeNumber) {
        String ret = null;

        if(path_nodeId != null) {
            BufferedReader nodeIds;
            try {
                nodeIds = new BufferedReader(new FileReader(path_nodeId));
                for(int i=0;i<=_nodeNumber;i++) {
                    ret = nodeIds.readLine();
                    if(ret == null) {
                        break;
                    }
                }
            }
            catch (IOException e) {
                System.err.println("Cannot assign node id using default value. Error message: " + e.getLocalizedMessage());
                ret = Integer.toString(_nodeNumber);
            }   
        }

        if(ret == null) {
            ret = Integer.toString(_nodeNumber);
        }

        return ret;
    }

    protected void print(String _writeOut) {
        print(_writeOut,0);
    }

    protected void print(String _writeOut, final int _level) {
        if(out == null) {
            out = openPrintWriter(name + filesuffix);
        }

        if(compression == COMPRESSION_NONE) {
            for(int level=0; level<_level; level++) {
                out.print("\t");
            }
        }

        out.print(_writeOut);

        if(compression != COMPRESSION_BEST) {
            out.println("");
        }
    }

    protected void closeWriter() {
		if (out != null) out.close();
		out = null;
    }
    
    protected boolean parseArg(char key, String val) {
        switch (key) {
        	case 'f':
                this.name = val;
                return true;
            case 'c':
                this.compression = Integer.parseInt(val);
                if(this.compression > COMPRESSION_BEST) {
                    System.err.println("Invalid compression defined! Using no compression.");
                    this.compression = COMPRESSION_NONE;
                }
                return true;
            case 'a':
                this.defaultAltitude = Double.parseDouble(val);
                return true;
        	case 'F':
                this.path_footer = val;
                return true;
        	case 'H':
                this.path_header = val;
                return true;
        	case 'I':
				this.useIntegerTimes = true;
				System.out.println("Warning: This (-I) will convert all printed times into integers!");
				return true;
        	case 'L':
				this.intervalLength = Double.parseDouble(val);
				return true;
            case 'N':
                this.path_nodeId = val;
                return true;
			case 'r':
				this.transmissionRange = Double.parseDouble(val);
				return true;
			case 'e':
				this.printlinkactions = true;
				return true;
			case 'o':
			    this.printOnlyChangingNodes = true;
			    return true;
            default:
                return super.parseArg(key, val);
        }
    }

    public static void printHelp() {
        System.out.println(getInfo().toDetailString());
        App.printHelp();
        System.out.println("WiseML:");
        System.out.println("\t[-a <altitude>]\t(default 0)");
        System.out.println("\t[-c <compressionlevel>]\t(default 0) 0 = NONE, 1 = No tabs, 2 = No tabs, no newlines");
        System.out.println("\t-f <filename>\tScenario");
        System.out.println("\t[-F <path to file>]\tWiseML footer");
        System.out.println("\t[-H <path to file>]\tWiseML header");
        System.out.println("\t[-I]\tConvert times to integer values");
        System.out.println("\t-L <double>\tTime between two outputs [s] (interval-based)");
        System.out.println("\t[-N <path to file>]\tWiseML node ids");
		System.out.println("\t-r <double>\ttransmission range [m] (contact-based)");
		System.out.println("\t[-e]\tPrint which links were enabled/disabled (contact-based)");		
		System.out.println("\t[-o]\tPrint only positions of nodes, which links are changing (contact-based)");
    }

    public static void main(String[] args) throws FileNotFoundException, IOException {
        new WiseML(args);
    }
    
    private enum LinkAction { enableLink, disableLink } 
    
    private final class ActionItem
    {
    	int source; 	// source node id
    	int target; 	// target node id
    	LinkAction action;
    	
    	ActionItem(LinkAction _action, int _source, int _target)
    	{
    		action = _action;
    		source = _source;
    		target = _target;
    	}
    }
}