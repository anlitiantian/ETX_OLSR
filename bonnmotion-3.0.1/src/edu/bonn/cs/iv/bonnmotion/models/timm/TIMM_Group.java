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

package edu.bonn.cs.iv.bonnmotion.models.timm;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.models.TIMM;

import java.util.ArrayList;
import java.util.Vector;

public class TIMM_Group {
    private final MobileNode[] nodes;
    private double groupStarttime;
    private final double groupEndtime;
    private double distanceCounter;
    private final Vector<TIMM_Group> subgroups;
    private TIMM_Vertex currentPosition;
    private final String identification;
    private final int id;
    private final TIMM_Settings settings;
    private final TIMM timm;

    public TIMM_Group(MobileNode[] _nodes, final int _id, TIMM_Vertex vertex, TIMM_Settings _settings, TIMM _timm) {
        this.nodes = _nodes;
        this.identification = Integer.toString(_id);
        this.distanceCounter = 0;
        this.subgroups = new Vector<TIMM_Group>(0, 1);
        this.currentPosition = vertex;
        this.settings = _settings;
        this.id = _id;
        this.groupStarttime = settings.getStarttime()[this.id];
        this.groupEndtime = settings.getEndtime()[this.id];
        this.timm = _timm;

        if (settings.isEnableGroupOneRules() && this.id == 0) {
            for (int i = 0; i < this.nodes.length; i++) {
                MobileNode m[] = new MobileNode[1];
                m[0] = this.nodes[i];
                TIMM_Group s = new TIMM_Group(m, this.groupStarttime, this.groupEndtime, this.distanceCounter,
                        this.currentPosition, this.identification + "." + i, this.id, settings, this.timm);
                this.subgroups.add(s);
            }
        }
    }

    public TIMM_Group(MobileNode[] _nodes, final double _starttime, final double _endtime, final double _distanceCounter,
            TIMM_Vertex _currentPosition, final String _identification, final int _id, TIMM_Settings _settings, TIMM _timm) {
        this.nodes = _nodes;
        this.groupStarttime = _starttime;
        this.groupEndtime = _endtime;
        this.distanceCounter = _distanceCounter;
        this.subgroups = new Vector<TIMM_Group>(0, 1);
        this.currentPosition = _currentPosition;
        this.identification = _identification;
        this.settings = _settings;
        this.id = _id;
        this.timm = _timm;
    }

    private int getNodeCount() {
        return nodes.length;
    }

    private int getSubgroupCount() {
        return this.subgroups.size();
    }

    private boolean hasSubgroups() {
        return (this.getSubgroupCount() > 0);
    }

    /**
     * Moves group according to time, graph and maybe groups get split up. 
     * Recursive call by subgroups.
     * 
     * @param _time
     * @param _buildingGraph
     * @return
     *                          the times at which moves are happening.
     *                          Double.MAX_VALUE if move is over.
     */
    public Vector<Double> moveGroup(final double _time, final TIMM_Graph _buildingGraph) {
        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[Time %f]\t[Pos %s]\t[No movement until %s]", this.identification, _time,
                    this.currentPosition, this.getGroupStarttime()));
        }

        Vector<Double> result = new Vector<Double>(0, 1);

        if (settings.getMaxWalkingDistance()[id] < this.distanceCounter) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[Distance exceeded %f]", this.identification, this.distanceCounter));
            }
            
            result.add(Double.MAX_VALUE);
            return result;
        }

        if (this.groupEndtime <= _time) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[Time exceeded %f]", this.identification, this.groupEndtime));
            }
            
            result.add(Double.MAX_VALUE);
            return result;
        }

        if (this.getGroupStarttime() > _time) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[No movement]", this.identification));
            }
            
            result.add(Double.MAX_VALUE);
            return result;
        }

        if (this.hasSubgroups()) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[subgroups moving...]", this.identification));
            }
            
            for (TIMM_Group s : this.subgroups) {
                result.addAll(s.moveGroup(_time, _buildingGraph));
            }

            return result;
        }

        Vector<TIMM_Vertex> destinations = _buildingGraph.getNonvisitedNeighborVerticesOfVertex(this.currentPosition, _time);

        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[Time %f]\t[Pos %s]\t[%d neighbor unvisited]", this.identification, _time,
                    this.currentPosition, destinations.size()));
        }

        // one unvisited neighbor
        if (destinations.size() == 1) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[1 neighbor unvisited, Next: %s]", this.identification, destinations
                        .elementAt(0)));
            }

            result.add(moveGroup(destinations.elementAt(0), _time, _buildingGraph));
            return result;
        }

        // > 1 unvisited neighbors
        if (destinations.size() > 1) {
            int possibleNumberOfSubgroups = (int)Math.floor(this.getNodeCount() / (double)settings.getMinimalGroupSize());
            // special treatment for group no. 1
            if (this.nodes.length == 1) {
                possibleNumberOfSubgroups = 1;
            }

            int groups;
            if (destinations.size() > possibleNumberOfSubgroups) {
                groups = possibleNumberOfSubgroups;
            }
            else {
                groups = destinations.size();
            }

            if (TIMM.DEBUG) {
                System.out
                        .println(String.format("[Group %s]\t[multiple targets - splitting up in %d groups]", this.identification, groups));
            }

            @SuppressWarnings("unchecked")
            Vector<MobileNode>[] newGroups = new Vector[groups];

            String[] group = new String[groups];

            for (int i = 0; i < groups; i++) {
                newGroups[i] = new Vector<MobileNode>(0, 1);
                group[i] = "";
            }

            for (int i = 0; i < getNodeCount(); i++) {
                newGroups[i % groups].add(nodes[i]);
                group[i % groups] += Integer.toString(i) + ", ";
            }

            if (TIMM.DEBUG) {
                for (int i = 0; i < groups; i++) {
                    System.out.println(String.format("[Group %s]\t[Group %s.%d nodes: %s]", this.identification, this.identification, i,
                            group[i].substring(0, group[i].length() - 2)));
                }

                for (TIMM_Vertex s : destinations) {
                    System.out.println(String.format("[Group %s]\t[distance %s->%s = %f]", this.identification, this.currentPosition, s,
                            _buildingGraph.routeLength(this.currentPosition, s)));
                }
            }

            TIMM_Vertex destinationsSortedByDistance[] = new TIMM_Vertex[destinations.size()];
            Vector<TIMM_Vertex> sortMe = new Vector<TIMM_Vertex>(destinations.size(), 1);
            sortMe.addAll(destinations);
            int finished = 0;

            while (sortMe.size() > 0) {
                TIMM_Vertex z = null;
                double d = Double.MAX_VALUE;

                for (TIMM_Vertex s : sortMe) {
                    double tmp = _buildingGraph.routeLength(this.currentPosition, s);
                    if (tmp < d) {
                        d = tmp;
                        z = s;
                    }
                }

                destinationsSortedByDistance[finished++] = z;
                sortMe.remove(z);
            }

            if (TIMM.DEBUG) {
                StringBuilder sb = new StringBuilder(destinationsSortedByDistance[0].toString());

                for (int i = 1; i < destinationsSortedByDistance.length; i++) {
                    sb.append(", " + destinationsSortedByDistance[i]);
                }

                System.out.println(String.format("[Group %s]\t[nodes sorted by distance: %s]", this.identification, sb.toString()));
            }

            for (int i = 0; i < groups; i++) {
                MobileNode[] mn = new MobileNode[newGroups[i].size()];
                for (int j = 0; j < newGroups[i].size(); j++) {
                    mn[j] = newGroups[i].elementAt(j);
                }
                
                TIMM_Group s = new TIMM_Group(mn, _time, this.groupEndtime, this.distanceCounter, this.currentPosition,
                        this.identification + "." + i, this.id, settings, this.timm);
                this.subgroups.add(s);
                result.add(s.moveGroup(destinationsSortedByDistance[i], _time, _buildingGraph));
            }

            return result;
        }

        // no unvisited neighbors
        if (destinations.size() < 1) {
            TIMM_Vertex z = _buildingGraph.getNextNonvisitedNeighborOfVertex(this.currentPosition, _time);
            if (z != null) {
                if (TIMM.DEBUG) {
                    System.out.println(String.format("[Group %s]\t[0 neighbor unvisited, Next: %s]", this.identification, z));
                }
                
                result.add(moveGroup(z, _time, _buildingGraph));
                return result;
            }

        }

        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[all visited]", this.identification));
        }

        result.add(Double.MAX_VALUE);
        return result;
    }

    /**
     * Returns the lowest start time of this group and all subgroups.
     * 
     * @return
     *          start time of group/subgroups
     */
    private double getGroupStarttime() {
        if (!this.hasSubgroups()) {
            return this.groupStarttime;
        }
        else {
            double result = Double.MAX_VALUE;
            for (TIMM_Group g : this.subgroups) {
                double r = g.getGroupStarttime();
                if (r < result) {
                    result = r;
                }
            }

            return result;
        }
    }

    /**
     * In this method the actual move is happening and the waypoints are
     * saved into the field 'nodes'.
     * 
     * @param _destination
     * @param _time
     * @param _buildingGraph
     * @return
     *                          the time after the move.
     */
    private double moveGroup(TIMM_Vertex _destination, double _time, TIMM_Graph _buildingGraph) {
        ArrayList<TIMM_Vertex> route = _buildingGraph.calculateRoute(this.currentPosition, _destination);

        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[possible change of target: %s -> %s]", this.identification, _destination,
                    route.get(1)));
        }

        _destination = route.get(1);
        double distance = _buildingGraph.routeLength(this.currentPosition, _destination);
        this.distanceCounter += distance;

        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[move nodes from %s to %s]\t[Distance %f]", this.identification,
                    this.currentPosition, _destination, distance));
        }

        double spd;

        if (_destination.isVisited(_time)) {
            spd = fastMovement();

            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[fast speed: %f]", this.identification, spd));
            }
        }
        else {
            spd = slowMovement();

            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[slow speed: %f]", this.identification, spd));
            }
        }

        distance = distance / spd;

        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[%s visited: %b]", this.identification, _destination, _destination.isVisited(_time)));
        }

        distance += _time;

        if (TIMM.DEBUG) {
            System.out.println(String.format("[Group %s]\t[target reached at %f]", this.identification, distance));
        }

        for (int i = 0; i < nodes.length; i++) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[from %s to %s]\t[%f/%f]", this.identification, this.currentPosition,
                        _destination, _time, distance));
            }
            nodes[i].add(_time, this.currentPosition.getPosition());
            nodes[i].add(distance, _destination.getPosition());
        }

        final double wait = _destination.setNodeReachsVertex(distance);
        if (wait > distance) {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[waiting at door until: %f]", this.identification, wait));
            }
            distance = wait;
        }
        else {
            if (TIMM.DEBUG) {
                System.out.println(String.format("[Group %s]\t[no waiting]", this.identification));
            }
        }

        setCurrentPosition(_destination);
        setDonotMoveUntil(distance);

        return distance;
    }

    private double slowMovement() {
        return (settings.getSlowSpeed() + Math.pow(-1, timm.randomNextInt(2)) * timm.randomNextDouble()
                * Math.sqrt(settings.getSlowSpeedVariance()));
    }

    private double fastMovement() {
        return (settings.getFastSpeed() + Math.pow(-1, timm.randomNextInt(2)) * timm.randomNextDouble()
                * Math.sqrt(settings.getFastSpeedVariance()));
    }

    private void setDonotMoveUntil(double time) {
        this.groupStarttime = time;
    }

    private void setCurrentPosition(TIMM_Vertex _currentPosition) {
        this.currentPosition = _currentPosition;
    }
}
