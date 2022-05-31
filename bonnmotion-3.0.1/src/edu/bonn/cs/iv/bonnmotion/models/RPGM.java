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
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.GroupNode;
import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.RandomSpeedBase;

/** Application to create movement scenarios according to the Reference Point Group Mobility model. */

public class RPGM extends RandomSpeedBase {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("RPGM");
        info.description = "Application to create movement scenarios according to the Reference Point Group Mobility model";
        
        info.major = 1;
        info.minor = 1;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
    /** Maximum deviation from group center [m]. */
    protected double maxdist = 2.5;
    /** Average nodes per group. */
    protected double avgMobileNodesPerGroup = 3.0;
    /** Standard deviation of nodes per group. */
    protected double groupSizeDeviation = 2.0;
    /** The probability for a node to change to a new group when moving into it's range. */
    protected double pGroupChange = 0.01;

    public RPGM(int nodes, double x, double y, double duration, double ignore, long randomSeed, double minspeed, double maxspeed,
            double maxpause, double maxdist, double avgMobileNodesPerGroup, double groupSizeDeviation, double pGroupChange) {
        super(nodes, x, y, duration, ignore, randomSeed, minspeed, maxspeed, maxpause);
        this.maxdist = maxdist;
        this.avgMobileNodesPerGroup = avgMobileNodesPerGroup;
        this.groupSizeDeviation = groupSizeDeviation;
        this.pGroupChange = pGroupChange;
        generate();
    }

    public RPGM(String[] args) {
        go(args);
    }

    public void go(String args[]) {
        super.go(args);
        generate();
    }

    public void generate() {
        preGeneration();

        final GroupNode[] node = new GroupNode[this.parameterData.nodes.length];
        final Vector<MobileNode> rpoints = new Vector<MobileNode>();

        // groups move in a random waypoint manner:
        int nodesRemaining = node.length;
        int offset = 0;

        while (nodesRemaining > 0) {
            MobileNode ref = new MobileNode();
            rpoints.addElement(ref);
            double t = 0.0;
            
            //pick position inside the interval [maxdist; x - maxdist], [maxdist; y - maxdist] 
            //(to ensure that the group area doesn't overflow the borders)
            Position src = new Position((parameterData.x - 2 * maxdist) * randomNextDouble() + maxdist, (parameterData.y - 2 * maxdist) * randomNextDouble() + maxdist);

            if (!ref.add(0.0, src)) {
                System.err.println(getInfo().name + ".generate: error while adding group movement (1)");
                System.exit(-1);
            }

            while (t < parameterData.duration) {
                Position dst = new Position((parameterData.x - 2 * maxdist) * randomNextDouble() + maxdist, (parameterData.y - 2 * maxdist) * randomNextDouble() + maxdist);

                double speed = (maxspeed - minspeed) * randomNextDouble() + minspeed;
                t += src.distance(dst) / speed;

                if (!ref.add(t, dst)) {
                    System.err.println(getInfo().name + ".generate: error while adding group movement (2)");
                    System.exit(-1);
                }

                if ((t < parameterData.duration) && (maxpause > 0.0)) {
                    double pause = maxpause * randomNextDouble();
                    if (pause > 0.0) {
                        t += pause;

                        if (!ref.add(t, dst)) {
                            System.err.println(getInfo().name + ".generate: error while adding group movement (3)");
                            System.exit(-1);
                        }
                    }
                }
                src = dst;
            }

            int size; // define group size
            while ((size = (int)Math.round(randomNextGaussian() * groupSizeDeviation + avgMobileNodesPerGroup)) < 1);

            if (size > nodesRemaining) {
                size = nodesRemaining;
            }

            nodesRemaining -= size;
            offset += size;

            for (int i = offset - size; i < offset; i++) {
                node[i] = new GroupNode(ref);
            }
        }

        // nodes follow their reference points:
        for (int i = 0; i < node.length; i++) {
            double t = 0.0;
            MobileNode group = node[i].group();
            Position src = group.positionAt(t).rndprox(maxdist, randomNextDouble(), randomNextDouble(), parameterData.calculationDim);

            if (!node[i].add(0.0, src)) {
                System.err.println(getInfo().name + ".generate: error while adding node movement (1)");
                System.exit(-1);
            }

            while (t < parameterData.duration) {
                Position dst;
                double speed;
                final double[] groupChangeTimes = group.changeTimes();
                int currentGroupChangeTimeIndex = 0;

                while ((currentGroupChangeTimeIndex < groupChangeTimes.length) && (groupChangeTimes[currentGroupChangeTimeIndex] <= t))
                    currentGroupChangeTimeIndex++;
                
                double next = (currentGroupChangeTimeIndex < groupChangeTimes.length) ? groupChangeTimes[currentGroupChangeTimeIndex] : parameterData.duration;
                boolean pause = (currentGroupChangeTimeIndex == 0);

                if (!pause) {
                    final Position pos1 = group.positionAt(groupChangeTimes[currentGroupChangeTimeIndex - 1]);
                    final Position pos2 = group.positionAt(groupChangeTimes[currentGroupChangeTimeIndex]);
                    pause = pos1.equals(pos2);
                }


                if (!pause) {
                    do {
                        dst = group.positionAt(next).rndprox(maxdist, randomNextDouble(), randomNextDouble(), parameterData.calculationDim);
                        speed = src.distance(dst) / (next - t);
                    }
                    while (speed > maxspeed || speed < minspeed);
                } else {
                    dst = src;
                }

                if (pGroupChange > 0.0) {
                    // create dummy with current src and dst for easier parameter passing
                    final MobileNode dummy = new MobileNode();

                    if (!dummy.add(t, src)) {
                        System.err.println(getInfo().name + ".generate: error while adding node movement (2)");
                        System.exit(-1);
                    }

                    if (!dummy.add(next, dst)) {
                        System.err.println(getInfo().name + ".generate: error while adding node movement (3)");
                        System.exit(-1);
                    }

                    // group to change to, null if group is not changed
                    MobileNode nRef = null;
                    // time when the link between ref and dummy gets up
                    double linkUp = parameterData.duration;
                    // time when the link between ref and dummy gets down
                    double linkDown = 0.0;
                    // time when the group is changed
                    double nNext = 0;

                    // check all reference points if currently a groupchange should happen
                    for (MobileNode ref : rpoints) {
                        if (ref != group) {
                            final double[] ct = MobileNode.pairStatistics(dummy, ref, t, next, maxdist, false, parameterData.calculationDim);
                            // check if the link comes up before any other link to a ref by now
                            if (ct.length > 6 && ct[6] < linkUp) {
                                if (randomNextDouble() < pGroupChange) {
                                    linkUp = ct[6];
                                    linkDown = (ct.length > 7) ? ct[7] : next;

                                    // change group at time tmpnext
                                    final double tmpnext = linkUp + randomNextDouble() * (linkDown - linkUp);

                                    // check if group change is possible at this time
                                    if (this.groupChangePossible(tmpnext, ref, dummy)) {
                                        nNext = tmpnext;
                                        nRef = ref;
                                    }
                                }
                            }
                        }
                    }

                    if (nRef != null) {
                        // change group to nRef at time nNext
                        group = nRef;
                        next = nNext;
                        dst = dummy.positionAt(next);
                        node[i].setgroup(nRef);
                    }
                }

                if (!node[i].add(next, dst)) {
                    System.err.println(getInfo().name + ".generate: error while adding node movement (4)");
                    System.exit(-1);
                }

                src = dst;
                t = next;
            }
        }

        // write the nodes into our base
        this.parameterData.nodes = node;

        postGeneration();
    }

    /**
     * Checks if the groupchange into the given group is currently possible for the given point
     * depending on the calculation of speed and next position of the group.
     * 
     * @param time
     *            current time
     * @param group
     *            group node
     * @param node
     *            the node that should change its group
     * @return true if groupchange is currently possible, false otherwise
     */
    private boolean groupChangePossible(final Double time, final MobileNode group, final MobileNode node) {
        /*
         * idea: build a line through the given points, walk maxdist - threshold in the other
         * direction and check if this position can be reached by maxspeed
         */
        final double threshold = 0.1;
        
        final Position refPos = group.positionAt(time);
        final Position nodePos = node.positionAt(time);
        final double scaledDistanceToWalk = (maxdist - threshold) / refPos.distance(nodePos);
        
        // get position of the point with max distance
        final Position src = new Position(refPos.x - scaledDistanceToWalk * nodePos.x, refPos.y - scaledDistanceToWalk * nodePos.y);

        // get time of next position of group
        final double[] groupChangeTimes = group.changeTimes();
        int currentGroupChangeTimeIndex = 0;

        while ((currentGroupChangeTimeIndex < groupChangeTimes.length) && (groupChangeTimes[currentGroupChangeTimeIndex] <= time)) {
            currentGroupChangeTimeIndex++;
        }

        if (currentGroupChangeTimeIndex >= groupChangeTimes.length) {
            return false;
        }

        // check for pause, there speed is calculated differently and may be > maxspeed
        boolean pause = (currentGroupChangeTimeIndex == 0);

        if (!pause) {
            Position pos1 = group.positionAt(groupChangeTimes[currentGroupChangeTimeIndex - 1]);
            Position pos2 = group.positionAt(groupChangeTimes[currentGroupChangeTimeIndex]);
            pause = pos1.equals(pos2);
        } else {
            return true;
        }

        final double next = (currentGroupChangeTimeIndex < groupChangeTimes.length) ? groupChangeTimes[currentGroupChangeTimeIndex] : parameterData.duration;
        final double speed = src.distance(nodePos) / (next - time);
        
        // check if the calculated needed speed is <= maxspeed
        return (speed <= maxspeed);
    }

    protected boolean parseArg(String key, String value) {
        if (key.equals("groupsize_E")) {
            avgMobileNodesPerGroup = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("groupsize_S")) {
            groupSizeDeviation = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("pGroupChange")) {
            pGroupChange = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("maxdist")) {
            maxdist = Double.parseDouble(value);
            return true;
        }
        else
            return super.parseArg(key, value);
    }

    public void write(String _name) throws FileNotFoundException, IOException {
        String[] p = new String[4];

        p[0] = "groupsize_E=" + avgMobileNodesPerGroup;
        p[1] = "groupsize_S=" + groupSizeDeviation;
        p[2] = "pGroupChange=" + pGroupChange;
        p[3] = "maxdist=" + maxdist;

        super.write(_name, p);
    }

    protected boolean parseArg(char key, String val) {
        switch (key) {
            case 'a': //
                avgMobileNodesPerGroup = Double.parseDouble(val);
                return true;
            case 'c':
                pGroupChange = Double.parseDouble(val);
                return true;
            case 'r':
                maxdist = Double.parseDouble(val);
                return true;
            case 's':
                groupSizeDeviation = Double.parseDouble(val);
                return true;
            default:
                return super.parseArg(key, val);
        }
    }

    public static void printHelp() {
        System.out.println(getInfo().toDetailString());
        RandomSpeedBase.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-a <average no. of nodes per group>");
        System.out.println("\t-c <group change probability>");
        System.out.println("\t-r <max. distance to group center>");
        System.out.println("\t-s <group size standard deviation>");
    }
}
