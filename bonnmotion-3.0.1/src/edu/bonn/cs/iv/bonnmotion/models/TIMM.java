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

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.models.timm.*;

import java.util.Arrays;
import java.util.Vector;
import java.io.IOException;

public class TIMM extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("TIMM");
        info.description = "Application to construct Tactical Indoor Mobility Model mobility scenarios";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Raphael Ernst");
		info.authors.add("Florian Schmitt");

		info.references.add("Nils Aschenbruck, Raphael Ernst, Peter Martini: \"Indoor Mobility Modeling\" in Proc. of the IEEE Global Communications Conference GLOBECOM 2010, Miami, Florida, USA, December 6-10, 2010");

		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
    public static boolean DEBUG = false;

    private TIMM_Group groups[];
    private TIMM_Settings settings = new TIMM_Settings();

    public TIMM(int nodes, double x, double y, double duration, double ignore, long randomSeed) {
        super(nodes, x, y, duration, ignore, randomSeed);
    }

    public TIMM(String[] args) {
        go(args);
    }

    public void go(String[] args) {
        if (!Arrays.asList(args).contains("-i")) { // if no ignore parameter is provided set it to zero
        	parameterData.ignore = 0;
        }

        super.go(args);
        generate();
    }

    public void generate() {
        preGeneration();

        groups = new TIMM_Group[settings.getGroupSizes().length];
        for (int i = 0; i < parameterData.nodes.length; i++) {
        	parameterData.nodes[i] = new MobileNode();
        }

        TIMM_Graph buildingGraph = new TIMM_Graph(settings, this);
        
        int cntr = 0;
        for (int i = 0; i < settings.getGroupSizes().length; i++) {
            MobileNode[] mn = new MobileNode[settings.getGroupSizes()[i]];
            for (int j = 0; j < settings.getGroupSizes()[i]; j++) {
                mn[j] = parameterData.nodes[cntr];
                mn[j].add(0, buildingGraph.getVertexByIdentification("StartVertex").getPosition());
                cntr++;
            }
            groups[i] = new TIMM_Group(mn, i, buildingGraph.getVertexByIdentification("StartVertex"), settings, this);
        }

        TIMM_EventManager em = new TIMM_EventManager();
        em.addEvent(0);
        for (int i = 0; i < settings.getStarttime().length; i++) {
            em.addEvent(settings.getStarttime()[i]);
        }

        while (!em.isFinished()) {
            double t = em.getNextEvent();
            for (int i = 0; i < settings.getGroupSizes().length; i++) {
                if (TIMM.DEBUG) {
                    System.out.println("\n");
                }
                Vector<Double> newTimes = groups[i].moveGroup(t, buildingGraph);
                em.addEvents(newTimes);
            }
        }

        if (TIMM.DEBUG) { 
            System.out.println(buildingGraph);
        }
        
        postGeneration();
    }

    public void write(String _name) throws FileNotFoundException, IOException {
        String[] p = new String[11];

        p[0] = "Building_graph=" + settings.getPathToGraph();
        p[1] = "Group_max_distance=" + arrayToString(settings.getMaxWalkingDistance());
        p[2] = "Group_endtime=" + arrayToString(settings.getEndtime());
        p[3] = "Fast_speed=" + settings.getFastSpeed() + "," + settings.getFastSpeedVariance();
        p[4] = "Group_size=" + arrayToString(settings.getGroupSizes());
        p[5] = "Graph_max_distance_vertices=" + settings.getMaxDistanceBetweenVerticesInTheBuildingGraph();
        p[6] = "Group_minimal_size=" + settings.getMinimalGroupSize();
        p[7] = "Door_wait_or_opening_time=" + settings.getDoorOpeningAndSecuring() + "," + settings.getDoorOpeningAndSecuringVariance();
        p[8] = "Slow_speed=" + settings.getSlowSpeed() + "," + settings.getSlowSpeedVariance();
        p[9] = "GroupOneRules=" + settings.isEnableGroupOneRules();
        p[10] = "Group_starttimes=" + arrayToString(settings.getStarttime());

        super.writeParametersAndMovement(_name, p);
    }
    
    public double randomNextDouble()
    {
        return super.randomNextDouble();
    }
    
    public int randomNextInt(int i)
    {
        return super.randomNextInt(i);
    }

    protected boolean parseArg(String key, String value) {
        boolean fail = false;
        
        try {
            if (key.equals("Building_graph")) {
                settings.setPathToGraph(value);
                return true;
            }
            else if (key.equals("Group_max_distance")) {
                settings.setMaxWalkingDistance(stringToDoubleArray(value));
                return true;
            }
            else if (key.equals("Group_endtime")) {
                settings.setEndtime(stringToDoubleArray(value));
                return true;
            }
            else if (key.equals("Fast_speed")) {
                double[] tmp = stringToDoubleArray(value);

                if (tmp.length != 2) {
                    return false;
                }
                else {
                    settings.setFastSpeed(tmp[0]);
                    settings.setFastSpeedVariance(tmp[1]);
                }
                return true;
            }
            else if (key.equals("Group_size")) {
                settings.setGroupSizes(stringToIntegerArray(value));
                return true;
            }
            else if (key.equals("Graph_max_distance_vertices")) {
                settings.setMaxDistanceBetweenVerticesInTheBuildingGraph(Double.parseDouble(value));
                return true;
            }
            else if (key.equals("Group_minimal_size")) {
                settings.setMinimalGroupSize(Integer.parseInt(value));
                return true;
            }
            else if (key.equals("Door_wait_or_opening_time")) {
                double[] tmp = stringToDoubleArray(value);

                if (tmp.length != 2) {
                    return false;
                }
                else {
                    settings.setDoorOpeningAndSecuring(tmp[0]);
                    settings.setDoorOpeningAndSecuringVariance(tmp[1]);
                }
                return true;
            }
            else if (key.equals("Slow_speed")) {
                double[] tmp = stringToDoubleArray(value);

                if (tmp.length != 2) {
                    return false;
                }
                else {
                    settings.setSlowSpeed(tmp[0]);
                    settings.setSlowSpeedVariance(tmp[1]);
                }
                return true;
            }
            else if (key.equals("GroupOneRules")) {
                settings.setEnableGroupOneRules(true);
                return true;
            }
            else if (key.equals("Group_starttimes")) {
                settings.setStarttime(stringToDoubleArray(value));
                return true;
            }
        }
        catch (SettingsException e) {
            System.err.println(e.getMessage());
            fail = true;
        }
        
        if (fail) {
            System.exit(-1);
        }
        
        return super.parseArg(key, value);
    }

    protected boolean parseArg(char key, String val) {
        double[] tmp;
        boolean fail = false;
        
        try {
            switch (key) {
                case 'b':
                    settings.setPathToGraph(val);
                    return true;
                case 'D':
                    settings.setMaxWalkingDistance(stringToDoubleArray(val));
                    return true;
                case 'E':
                    settings.setEndtime(stringToDoubleArray(val));
                    return true;
                case 'F':
                    tmp = stringToDoubleArray(val);

                    if (tmp.length != 2) {
                        return false;
                    }
                    else {
                        settings.setFastSpeed(tmp[0]);
                        settings.setFastSpeedVariance(tmp[1]);
                    }
                    return true;
                case 'g':
                    settings.setGroupSizes(stringToIntegerArray(val));
                    return true;
                case 'G':
                    settings.setEnableGroupOneRules(true);
                    return true;
                case 'm':
                    settings.setMaxDistanceBetweenVerticesInTheBuildingGraph(Double.parseDouble(val));
                    return true;
                case 'M':
                    settings.setMinimalGroupSize(Integer.parseInt(val));
                    return true;
                case 'o':
                    tmp = stringToDoubleArray(val);

                    if (tmp.length != 2) {
                        return false;
                    }
                    else {
                        settings.setDoorOpeningAndSecuring(tmp[0]);
                        settings.setDoorOpeningAndSecuringVariance(tmp[1]);
                    }
                    return true;
                case 's':
                    tmp = stringToDoubleArray(val);

                    if (tmp.length != 2) {
                        return false;
                    }
                    else {
                        settings.setSlowSpeed(tmp[0]);
                        settings.setSlowSpeedVariance(tmp[1]);
                    }
                    return true;
                case 'S':
                    settings.setStarttime(stringToDoubleArray(val));
                    return true;
            }
        }
        catch (SettingsException e) {
            System.err.println(e.getMessage());
            fail = true;
        }
        
        if (fail) {
            System.exit(-1);
        }

        return super.parseArg(key, val);
    }

    public static void printHelp() {
        System.out.println(getInfo().toDetailString());
        Scenario.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-b <path to graph file>");
        System.out.println("\t[-D <max Distance per group>\tSeperate values with ',', e.g. 10,20,30,40. Default is Double.MAX_VALUE for all groups.]");
        System.out.println("\t[-E <group endtime>\tSeperate values with ',', e.g. 10,20,30,40. Default is Double.MAX_VALUE for all groups.]");
        System.out.println("\t-F <fast speed>,<variance>");
        System.out.println("\t-g <group sizes>\tSeperate values with ',', e.g. 1,2,3,4.");
        System.out.println("\t[-G <enable special rule for group one>\t\tDefault: disabled]");
        System.out.println("\t[-m <max distance between vertices in the graph>\tDefault " + TIMM_Settings.DEFAULT_MAX_DISTANCE_VERTICES + ".]");
        System.out.println("\t[-M <minimal group size>\tDefault " + TIMM_Settings.DEFAULT_MIN_GROUP_SIZE + ".]");
        System.out.println("\t-o <door opening time>,<variance>");
        System.out.println("\t-s <slow speed>,<variance>");
        System.out.println("\t[-S <group starttime>\tSeperate values with ',', e.g. 10,20,30,40. Default is 0 for all groups.]");
    }

    protected void postGeneration() {
        super.postGeneration();
    }

    protected void preGeneration() {
        super.preGeneration();

        double[] tmp;
        boolean fail = false;
        
        // Check if everything is configured properly
        try {
            if (settings.getGroupSizes() == null) {
                throw new SettingsException("Error: No group sizes provided", true);
            }

            // Default values for group starttime, endtime, and maxdistance if necessary
            tmp = settings.getStarttime();
            if (tmp == null) {
                tmp = new double[settings.getGroupSizes().length];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = 0;
                }
                settings.setStarttime(tmp);
            }

            tmp = settings.getEndtime();
            if (tmp == null) {
                tmp = new double[settings.getGroupSizes().length];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = Double.MAX_VALUE;
                }
                settings.setEndtime(tmp);
            }

            tmp = settings.getMaxWalkingDistance();
            if (tmp == null) {
                tmp = new double[settings.getGroupSizes().length];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = Double.MAX_VALUE;
                }
                settings.setMaxWalkingDistance(tmp);
            }

            if (settings.getPathToGraph() == null) {
                throw new SettingsException("Error: Graph path is missing");
            }

            int requiredNodesAccordingToGroupSizes = 0;
    
            for (int i = 0; i < settings.getGroupSizes().length; i++) {
                requiredNodesAccordingToGroupSizes += settings.getGroupSizes()[i];
            }
            if (requiredNodesAccordingToGroupSizes != parameterData.nodes.length) {
                throw new SettingsException("Error: Group sizes does not fit to number of nodes");
            }
    
            if (settings.getSlowSpeed() == -1) {
                throw new SettingsException("Error: No slow speed (-s speed,variance) provided");
            }
    
            if (settings.getFastSpeed() == -1) {
                throw new SettingsException("Error: No fast speed (-F speed,variance) provided");
            }
    
            if (settings.getDoorOpeningAndSecuring() == -1) {
                throw new SettingsException("Error: No door opening time (-o time,variance) provided");
            }
    
            if (settings.getFastSpeed() < settings.getSlowSpeed()) {
                throw new SettingsException("Error: Fast speed (-F speed,variance) must be >= slow speed!");
            }
        
        }
        catch (SettingsException e) {
            System.err.println(e.getMessage());
            fail = true;
        }

        if (fail) {
            System.exit(-1);
        }
    }

    public String arrayToString(double[] data) {
        return arrayToString(data, ",");
    }

    public String arrayToString(double[] data, String separator) {
        StringBuilder sb = new StringBuilder();
        sb.append(Double.toString(data[0]));

        for (int i = 1; i < data.length; i++) {
            sb.append(separator);
            sb.append(Double.toString(data[i]));
        }

        return sb.toString();
    }

    public String arrayToString(int[] data) {
        return arrayToString(data, ",");
    }

    public String arrayToString(int[] data, String separator) {
        StringBuilder sb = new StringBuilder();
        sb.append(Integer.toString(data[0]));

        for (int i = 1; i < data.length; i++) {
            sb.append(separator);
            sb.append(Integer.toString(data[i]));
        }

        return sb.toString();
    }

    public int[] stringToIntegerArray(String data) {
        return stringToIntegerArray(data, ",");
    }

    public int[] stringToIntegerArray(String data, String separator) {
        String[] values = data.split(separator);
        int[] result = new int[values.length];

        try {
            for (int i = 0; i < values.length; i++) {
                result[i] = Integer.parseInt(values[i]);
            }
        }
        catch (NumberFormatException e) {
            System.err.println("Error while parsing string to integer array: \"" + data + "\"");
            System.err.println(e.toString());
            System.exit(-1);
        }
        return result;
    }

    public double[] stringToDoubleArray(String data) {
        return stringToDoubleArray(data, ",");
    }

    public double[] stringToDoubleArray(String data, String separator) {
        String[] values = data.split(separator);
        double[] result = new double[values.length];

        try {
            for (int i = 0; i < values.length; i++) {
                result[i] = Double.parseDouble(values[i]);
            }
        }
        catch (NumberFormatException e) {
            System.err.println("Error while parsing string to double array: \"" + data + "\"");
            System.err.println(e.toString());
            System.exit(-1);
        }

        return result;
    }
}