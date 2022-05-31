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

package edu.bonn.cs.iv.bonnmotion;

import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

/** Mobile node. */

public class MobileNode {
	
	protected static final boolean printAngleStuff = false;

    /** Times when mobile changes speed or direction. */
    protected double[] changeSpeedOrDirectionTimes = null;

    protected Vector<Waypoint> waypoints = new Vector<Waypoint>();
	
    /** Move all waypoints by a certain offset. */
    public void shiftPos(double _x, double _y) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint oldWP = waypoints.get(i);
            Waypoint newWP = new Waypoint(oldWP.time, oldWP.pos.newShiftedPosition(_x, _y));
            waypoints.setElementAt(newWP, i);
        }
    }
	
    /** Move all waypoints by a certain offset. */
    public void shiftPos(double _x, double _y, double _z) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint oldWP = waypoints.get(i);
            Waypoint newWP = new Waypoint(oldWP.time, (oldWP.pos).newShiftedPosition(_x, _y, _z));
            waypoints.setElementAt(newWP, i);
        }
    }

    public void cut(double begin, double end) {
        if (waypoints.size() == 0) {
            return;
        }
        changeSpeedOrDirectionTimes = null;
        Vector<Waypoint> nwp = new Vector<Waypoint>();
        Waypoint w = null;
        double oldstatus = 0.0;
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w2 = waypoints.elementAt(i);
            if ((w2.time >= begin) && (w2.time <= end)) {
                w = w2;
                if (nwp.size() == 0) {
                    Position hpos = positionAt(begin);
                    Position bpos = hpos.clone(oldstatus);
                    nwp.addElement(new Waypoint(0.0, bpos));
                    if (w.time > begin) {
                        nwp.addElement(new Waypoint(w.time - begin, w.pos));
                    }
                }
                else
                    nwp.addElement(new Waypoint(w.time - begin, w.pos));
            }
            if (w2.pos.status == 2.0) {
                oldstatus = 2.0;
            }
            else if (w2.pos.status == 1.0) {
                oldstatus = 0.0;
            }
        }
        if (w == null) { // no waypoints with the given time span
            Waypoint start = new Waypoint(0.0, positionAt(begin));
            Waypoint stop = new Waypoint(end - begin, positionAt(end));
            nwp.addElement(start);
            if (!start.pos.equals(stop.pos))
                nwp.addElement(stop);
        }
        else if (w.time < end) {
            Position epos = positionAt(end);
            if (!epos.equals(w.pos))
                nwp.addElement(new Waypoint(end - begin, epos));
        }
        waypoints = nwp;
    }
    
    
    /**
     * Optimised for waypoints coming in with increasing time.
     * 
     * @return Success of insertion (will return false iff there is already another waypoint in the
     *         list with same time but different position).
     */
    public boolean add(double _time, Position _newPosition) {
        changeSpeedOrDirectionTimes = null;
        int waypointCount = waypoints.size() - 1;

        Waypoint newWaypoint = new Waypoint(_time, _newPosition);

        while (waypointCount >= 0) {
            Waypoint previousWaypoint = waypoints.elementAt(waypointCount);
            if (_time > previousWaypoint.time) {
                waypoints.insertElementAt(newWaypoint, waypointCount + 1);
                return true;
            }
            else if (_time == previousWaypoint.time) {
                return sameWaypointAndTime(previousWaypoint, newWaypoint);
            }
            else {
                waypointCount--;
                System.err.println("warning: MobileNode: trying to insert waypoint in the past <1>.");
                System.err.println("w.time: " + previousWaypoint.time + " time: " + _time);
            }
        }

        waypoints.insertElementAt(newWaypoint, 0);
        return true;
    }
    
    protected boolean sameWaypointAndTime(Waypoint _A, Waypoint _B) {
        if (_A.time != _B.time)
            return false;
        if (!_A.pos.equals(_B.pos))
            return false;
        return true;
    }
	
    public void addWaypointsOfOtherNode(MobileNode _node) {
        double timeOffset = 0;

        if (waypoints.size() > 0) {
            timeOffset = waypoints.lastElement().time + 0.0001; // Otherwise same
                                                                            // timestamp for 2
                                                                            // positions
        }

        for (int i = 0; i < _node.waypoints.size(); i++) {
            Waypoint next = _node.waypoints.get(i);
            waypoints.add(new Waypoint(next.time + timeOffset, next.pos));
        }
    }
    
    /** Remove the latest waypoint (last in the internal list). */
    public void removeLastElement() {
        waypoints.remove(waypoints.lastElement());
    }
    
    /** @return the latest waypoint (last in the internal list). */
    public Waypoint getLastWaypoint() {
        return waypoints.lastElement();
    }

    /** @return the number of waypoints */
    public int getNumWaypoints() {
        return waypoints.size();
    }
    
    public Vector<Waypoint> getWaypoints() {
    	return waypoints;
    }
    
    public Waypoint getWaypoint(int idx) {
        try {
            return waypoints.elementAt(idx);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Fatal error: Requested not existing waypoint: " + e.getLocalizedMessage());
            System.exit(-1);
            return null;
        }
    }
    
    /** @return Array with times when this mobile changes speed or direction. */
    public double[] changeTimes() {
        if (changeSpeedOrDirectionTimes == null) {
            changeSpeedOrDirectionTimes = new double[waypoints.size()];
            for (int i = 0; i < changeSpeedOrDirectionTimes.length; i++) {
                changeSpeedOrDirectionTimes[i] = waypoints.elementAt(i).time;
            }
        }
        return changeSpeedOrDirectionTimes;
    }
    
    
    public String movementString(Dimension dim) {
        StringBuffer sb = new StringBuffer(100 * waypoints.size());
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.elementAt(i);
            sb.append(" ");
            sb.append(w.getMovementStringPart(dim));
        }
        sb.deleteCharAt(0);
        return sb.toString();
    }

    public String placementStringGlomo(String id) {
        Waypoint w = waypoints.elementAt(0);
        Position p = w.pos;
        return id + " 0S (" + p.x + ", " + p.y + ", " + p.z + ")";
    }

    public String[] movementStringGlomo(String id) {
        String[] r = new String[waypoints.size() - 1];
        for (int i = 1; i < waypoints.size(); i++) {
            Waypoint w = waypoints.elementAt(i);
            Position p = w.pos;
            r[i - 1] = id + " " + w.time + "S (" + p.x + ", " + p.y + ", " + p.z + ")";
        }
        return r;
    }
    
    
    protected Position positionAt_old(double time) {
        Position p1 = null;
        double t1 = 0.0;
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.elementAt(i);
            if (w.time == time) {
                return w.pos;
            }
            else if (w.time > time) {
                if ((p1 == null) || p1.equals(w.pos))
                    return w.pos;
                else {
                    double weight = (time - t1) / (w.time - t1);
                    
                    return p1.getWeightenedPosition(w.pos, weight);
                }
            }
            p1 = w.pos;
            t1 = w.time;
        }
        return p1;
    }
    
    /** @return Position of this mobile at a given time. */
    public Position positionAt(double time) {
        int begin = 0;
        int end = waypoints.size() - 1;

        if (end < 2) { // there are up to two waypoints, then call the linear search (old
                       // positionAt() function)
            return positionAt_old(time);
        }

        // check initial conditions: out of range [begin,end]
        Waypoint firstWaypoint = waypoints.firstElement();
        Waypoint lastWaypoint = waypoints.lastElement();
        if (time < firstWaypoint.time)
            return firstWaypoint.pos;
        else if (time > lastWaypoint.time)
            return lastWaypoint.pos;
        else
            return binarySearch(begin, end, time);
    }

    protected Position binarySearch(int i, int j, double time) {
        int median = (i + j) / 2;
        Waypoint w = waypoints.elementAt(median);
        if (i + 1 == j) { // waypoint not found in waypoint list
            Waypoint w_i = waypoints.elementAt(i);
            Waypoint w_j = waypoints.elementAt(j);

            double weight = (time - w_i.time) / (w_j.time - w_i.time);
            
            // if positions of surrounding waypoints are equal => no movement at time
            // just return position
            if (w_i.pos.equals(w_j.pos)) {
                return new Position(w_i.pos.x, w_i.pos.y, w_i.pos.z, w_i.pos.status);
            }
            
            return w_i.pos.getWeightenedPosition(w_j.pos, weight);
        }
        else {
            if (time == w.time) { // waypoint found
                return w.pos;
            }
            else if (time < w.time) { // left recursion
                return binarySearch(i, median, time);
            }
            else { // right recursion
                return binarySearch(median, j, time);
            }
        }
    }

        
    @SuppressWarnings("unused")
    public static boolean sameBuilding(Building[] buildings, Position pos1, Position pos2, Dimension dim) {
        // takes care that two nodes do not communicate via opposite wall such that the following
        // will not be allowed:
        // __________
        // | |
        // x | x
        // |________|
        //
        // TODO: change loop, so that full array is checked
        // now: only i=0 is checked
        for (int i = 0; i < buildings.length; i++) {
            Building building = buildings[i];

            if (building.isInside(pos1, dim)) {
                // pos1 is in the building
                if (building.isInside(pos2, dim)) {
                    // pos2 is in the building, both are in the same building
                    return true;
                }

                // pos2 is not in the building
                // check if pos1 can communicate with pos2 through the door
                return building.canCommunicateThroughDoor(pos1, pos2, dim);
            }
            else {
                // pos1 is not in the building
                if (building.isInside(pos2, dim)) {
                    // pos2 is in the building
                    if (building.canCommunicateThroughDoor(pos2, pos1, dim)) {
                        return true;
                    }
                }

                return false;
            }
        }
        return true;
    }

    public static double[] pairStatistics(MobileNode node1, MobileNode node2, double start, double duration, double range,
            boolean calculateMobility, Dimension dim) {
        return pairStatistics(node1, node2, start, duration, range, calculateMobility, new Building[0], dim);
    }
    

    public static double[] pairStatistics(MobileNode _node1, MobileNode _node2, double start, double duration, double range,
            boolean calculateMobility, Building[] buildings, Dimension dim) {
        MobileNode node1 = _node1;
        MobileNode node2 = _node2;
        double[] ch1 = node1.changeTimes();
        double[] ch2 = node2.changeTimes();

        // ### wenn einer der beiden Knoten ausgeschaltet ist, ist der Link down

        Vector<Double> changes = new Vector<Double>();
        int i1 = 0;
        int i2 = 0;
        double t0 = start;
        Position o1 = node1.positionAt(start);
        Position o2 = node2.positionAt(start);

        double mobility = 0.0;
        double on_time = 0.0;
        double D_spatial = 0.0;
        int D_spatial_count = 0;
        boolean connected = false;
        boolean nodes_on_before = false;

        // For average relative speed metric
        double Relative_speed = 0.0;
        int Relative_speed_count = 0;

        while (t0 < duration) {
            double t1;
            if (i1 < ch1.length) {
                if (i2 < ch2.length) {
                    t1 = (ch1[i1] < ch2[i2]) ? ch1[i1++] : ch2[i2++];
                } else {
                    t1 = ch1[i1++];
                }
            } else if (i2 < ch2.length) {
                t1 = ch2[i2++];
            } else {
                t1 = duration;
            }
            
            if (t1 > duration) {
                t1 = duration;
            }
            
            if (t1 > t0) {
                Position n1 = node1.positionAt(t1);
                Position n2 = node2.positionAt(t1);
                boolean conn_t0 = ((o1.distance(o2) <= range) && (sameBuilding(buildings, o1, o2, dim)));
                boolean conn_t1 = ((n1.distance(n2) <= range) && (sameBuilding(buildings, n1, n2, dim)));
                boolean nodes_on = ((o1.status != 2) && (o2.status != 2));
                if ((!connected) && conn_t0 && nodes_on) {
                    // either we just started, or some floating point op went wrong in the last
                    // epoch.
                    changes.addElement(new Double(t0));
                    connected = true;
                    if ((t0 != start) && nodes_on_before)
                        System.out.println("MobileNode.pairStatistics: fp correction 1: connect at " + t0);
                }
                if (connected && conn_t0 && (!nodes_on)) {
                    changes.addElement(new Double(t0));
                    connected = false;
                }

                double dt = t1 - t0; // time
                double dxo = o1.x - o2.x; // distance x at t0
                double dxn = n1.x - n2.x; // distance x at t1
                double dyo = o1.y - o2.y; // distance y at t0
                double dyn = n1.y - n2.y; // distance y at t1
                double dzo = o1.z - o2.z; // distance z at t0
                double dzn = n1.z - n2.z; // distance z at t1
                double c1 = (dxn - dxo) / dt;
                double c0 = (dxo * t1 - dxn * t0) / dt;
                double d1 = (dyn - dyo) / dt;
                double d0 = (dyo * t1 - dyn * t0) / dt;
                double e1 = (dzn - dzo) / dt;
                double e0 = (dzo * t1 - dzn * t0) / dt;

                // calculate degree of spatial dependence
                if (o1.distance(o2) < 2 * range) {
                    Position v_i = Position.diff(o1, n1);
                    Position v_j = Position.diff(o2, n2);
                    double s_i = Math.sqrt(Position.scalarProduct(v_i, v_i));
                    double s_j = Math.sqrt(Position.scalarProduct(v_j, v_j));
                    if (s_i > 0.0 && s_j > 0.0) {
                        double RD = Position.scalarProduct(v_i, v_j) / (s_i * s_j);
                        double SR = s_i > s_j ? (s_j / s_i) : (s_i / s_j);
                        D_spatial += RD * SR;
                        if (RD * SR != 0.0) {
                            D_spatial_count++;
                        }
                    }

                    // calculate the relative speed
                    Position relative_vector = Position.diff(v_i, v_j);
                    double RS = relative_vector.norm();

                    if (Math.abs(RS) > 0.0000001) {
                        Relative_speed += RS;
                        Relative_speed_count++;
                    }

                }

                if (nodes_on) {
                    on_time = on_time + dt;
                }

                if ((c1 != 0.0) || (d1 != 0.0) || e1 != 0.0) { // we have relative movement
                    double m = -1.0 * (c0 * c1 + d0 * d1 + e0 * e1) / (c1 * c1 + d1 * d1 + e1 * e1);
                    // calculate relative mobility
                    double relmob = 0.0;
                    if ((calculateMobility || printAngleStuff) && nodes_on) {
                        double dOld = Math.sqrt(dxo * dxo + dyo * dyo + dzo * dzo);
                        double dNew = Math.sqrt(dxn * dxn + dyn * dyn + dzo * dzo);
                        if ((m > t0) && (m < t1)) {
                            // at t0, nodes were losing distance to each other, but at t1, they are
                            // gaining distance again
                            Position in1 = node1.positionAt(m);
                            Position in2 = node2.positionAt(m);
                            double dInt = in2.distance(in1);
                            relmob = (Math.abs(dInt - dOld) + Math.abs(dNew - dInt)) / dt;
                        }
                        else {
                            relmob = Math.abs(dNew - dOld) / dt;
                        }
                        mobility += relmob;
                    }

                    double m2 = m * m;
                    double q = (c0 * c0 + d0 * d0 + e0 * e0 - range * range) / (c1 * c1 + d1 * d1 + e1 * e1);
                    if (m2 - q > 0.0) {
                        double d = Math.sqrt(m2 - q);
                        double min = m - d;
                        double max = m + d;

                        if ((min >= t0) && (min <= t1) && sameBuilding(buildings, o1, o2, dim)) {
                            if (d < 0.01) {
                                System.out.println("---------------");
                                System.out.println("MobileNode.pairStatistics: The time span these 2 nodes are in range seems very");
                                System.out.println("  short. Might this be an error or a bad choice of parameters?");
                                System.out.println("o1=" + o1);
                                System.out.println("n1=" + n1);
                                System.out.println("o2=" + o2);
                                System.out.println("n2=" + n2);
                                System.out.println("[" + t0 + ";" + t1 + "]:[" + m + "-" + d + "=" + min + ";" + m + "+" + d + "=" + max
                                        + "]");
                                System.out.println("---------------");
                            }
                            if (nodes_on) {
                                if (!connected) {
                                    changes.addElement(new Double(min));
                                    connected = true;
                                }
                                else if (min - t0 > 0.001) {
                                    System.out.println("MobileNode.pairStatistics: sanity check failed (1)");
                                    System.exit(0);
                                }
                                else{
                                    System.out.println("MobileNode.pairStatistics: connect too late: t=" + min + " t0=" + t0);
                                }
                            }
                            if (printAngleStuff) {
                                Position meet1 = node1.positionAt(min);
                                Position meet2 = node2.positionAt(min);
                                Position axis = Position.diff(meet1, meet2);
                                Position mov1 = Position.diff(o1, n1);
                                Position mov2 = Position.diff(o2, n2);
                                Position movd = Position.diff(mov2, mov1);

                                double v_delta = movd.norm() / dt;
                                double phi_a = Position.angle2(axis, mov1);
                                double phi_b = Position.angle2(axis, mov2);
                                double phi_delta = Position.angle2(axis, movd);

                                System.out
                                        .println("phi_a=" + phi_a + " phi_b=" + phi_b + " phi_delta=" + phi_delta + " v_delta=" + v_delta);
                            }
                        }
                        if ((max >= t0) && (max <= t1) && (sameBuilding(buildings, o1, o2, dim))) {
                            if (nodes_on) // if not on, it was done before
                                if (connected) {
                                    changes.addElement(new Double(max));
                                    connected = false;
                                }
                                else if (t0 - min < 0.001 && max - t0 > 0.001) { // MS: contact starts just before t0 but boolean says unconnected -> fp error?!
//                                	if (!changes.isEmpty()){
//                                		assert(t0 - changes.lastElement() > 0.001);
//                                	}
                                	changes.addElement(new Double(t0));
                                	changes.addElement(new Double(max));
                                }
                                else if (max - t0 > 0.001) {
                                	System.out.println("MobileNode.pairStatistics: sanity check failed (2)");
                                    System.out.println("t0: " + t0 + ", t1: " + t1);
                                    System.out.println("min: " + min + ", d: " + d + ", max: " + max);
                                    System.out.println("last disconnect at " + changes.lastElement());
                                    System.out.println("n1 (t0): " + node1.positionAt(t0).toString() + ", n2 (t0): " + node2.positionAt(t0).toString());
                                    System.out.println("dist: " + node1.positionAt(t0).distance(node2.positionAt(t0)));
                                    System.out.println("n1 (t1): " + node1.positionAt(t1).toString() + ", n2 (t1): " + node2.positionAt(t1).toString());
                                    System.out.println("dist: " + node1.positionAt(t1).distance(node2.positionAt(t1)));
                                    System.exit(0);
                                }
                                else {
                                    System.out.println("MobileNode.pairStatistics: disconnect too late: t=" + max + " t0=" + t0);
                                }
                        }
                    }
                }
                t0 = t1;
                o1 = n1;
                o2 = n2;
                nodes_on_before = nodes_on;

                // floating point inaccuracy detection:

                if (connected) {
                    if (!conn_t1) {
                        changes.addElement(new Double(t1));
                        connected = false;
                        System.out.println("MobileNode.pairStatistics: fp correction 2: disconnect at " + t1);
                    }
                }
                else { // !connected
                    if (conn_t1 && nodes_on) {
                        changes.addElement(new Double(t1));
                        connected = true;
                        System.out.println("MobileNode.pairStatistics: fp correction 3: connect at " + t1);
                    }
                }
            }
        }
        /* add disconnect at the end of time - for correct stats link is counted at link-break */
        /*
         * NA: I do not know, why this wasn't needed before. However, due to our changes we seem to
         * need it and it shouldn't change anything
         */
        if (connected) {
            changes.addElement(new Double(duration));
        }

        double[] result = new double[changes.size() + 6];
        for (int i = 0; i < result.length; i++) {
            if (i == 0) {
                result[i] = mobility;
                result[i + 1] = on_time;
                result[i + 2] = D_spatial;
                result[i + 3] = D_spatial_count;
                result[i + 4] = Relative_speed;
                result[i + 5] = Relative_speed_count;
                i += 5;
            }
            else {
                result[i] = changes.elementAt(i - 6).doubleValue();
            }
        }

        return result;
    }
    
    
    /**
     * Finds the degree of temporal dependence for a node.
     * 
     * @param node
     *            Node being calculated
     * @param start
     *            Start time of the simulation
     * @param end
     *            End time of the simulation
     * @param c
     *            Value used to determine if times are too far apart to use
     * @return A double array containing the dependence and the number of calculations
     */
    public static double[] getDegreeOfTemporalDependence(MobileNode _node, double start, double end, double c) {
        MobileNode node = _node;
        double temporal_dependence[] = new double[2];
        double[] change_times = node.changeTimes();

        double temp_dependence = 0.0;
        int temporal_dependence_count = 0;

        for (int i = 0; i < change_times.length - 1; i++) {
            for (int j = i + 1; j < change_times.length - 1; j++) {
                if (Math.abs(change_times[i] - change_times[j]) > c) {
                    Position vector_t = Position.diff(node.positionAt(change_times[i]), 
                            node.positionAt(change_times[i + 1]));
                    Position vector_t_prime = Position.diff(node.positionAt(change_times[j]), 
                            node.positionAt(change_times[j + 1]));
                    double time_t = change_times[i + 1] - change_times[i];
                    double time_t_prime = change_times[j + 1] - change_times[j];

                    double dependence = getDegreeOfDependence(vector_t, vector_t_prime, time_t, time_t_prime);

                    if (Math.abs(dependence) > 0.0000001) {
                        temp_dependence += dependence;
                        temporal_dependence_count++;
                    }
                }
            }
        }

        temporal_dependence[0] = temp_dependence;
        temporal_dependence[1] = temporal_dependence_count;

        return temporal_dependence;
    }
    
    

    /**
     * Get the degree of dependence between two vectors
     * 
     * @param vector_i
     *            First vector
     * @param vector_j
     *            Second vector
     * @param time_i
     *            First time interval
     * @param time_j
     *            Second time interval
     * @return Double of the dependence
     */
    public static double getDegreeOfDependence(Position _vector_i, Position _vector_j, double time_i, double time_j) {
        Position vector_i = _vector_i;
        Position vector_j = _vector_j;
        double dependence = 0.0;
        double speed_i = vector_i.norm() / time_i;
        double speed_j = vector_j.norm() / time_j;

        if (speed_i > 0.0 && speed_j > 0.0) {
            double RD = Position.scalarProduct(vector_i, vector_j) / (vector_i.norm() * vector_j.norm());
            double SR = speed_i > speed_j ? (speed_j / speed_i) : (speed_i / speed_j);
            dependence = RD * SR;
        }
        return dependence;
    }
    
    
    public static double[] getConnectionTime(MobileNode node1, MobileNode node2, double start, double duration, double range, Dimension dim) {
        return getConnectionTime(node1, node2, start, duration, range, new Building[0], dim);
    }

    public static double[] getConnectionTime(MobileNode _node1, MobileNode _node2, double start, double duration, double range,
            Building[] buildings, Dimension dim) {

        MobileNode node1 = _node1;
        MobileNode node2 = _node2;
        double[] ch1 = node1.changeTimes();
        double[] ch2 = node2.changeTimes();

        double on_time = 0.0;
        double con_time = 0.0;
        double link_up_at = 0.0;

        int i1 = 0;
        int i2 = 0;
        double t0 = start;
        Position o1 = node1.positionAt(start);
        Position o2 = node2.positionAt(start);

        boolean connected = false;

        while (t0 < duration) {
            double t1;
            if (i1 < ch1.length) {
                if (i2 < ch2.length) {
                    t1 = (ch1[i1] < ch2[i2]) ? ch1[i1++] : ch2[i2++];
                } else {
                    t1 = ch1[i1++];
                }
            } else if (i2 < ch2.length) {
                t1 = ch2[i2++];
            } else {
                t1 = duration;
            }
            
            if (t1 > duration)
                t1 = duration;
            
            if (t1 > t0) {
                Position n1 = node1.positionAt(t1);
                Position n2 = node2.positionAt(t1);
                boolean conn_t0 = ((o1.distance(o2) <= range) && (sameBuilding(buildings, o1, o2, dim)));
                boolean conn_t1 = ((n1.distance(n2) <= range) && (sameBuilding(buildings, n1, n2, dim)));
                boolean nodes_on = ((o1.status != 2) && (o2.status != 2));
                if ((!connected) && conn_t0 && nodes_on) {
                    // either we just started, or some floating point op went wrong in the last
                    // epoch.
                    link_up_at = t0;
                    connected = true;
                }
                if (connected && conn_t0 && (!nodes_on)) {
                    con_time = con_time + (t0 - link_up_at);
                    connected = false;
                }

                double dt = t1 - t0; // time
                double dxo = o1.x - o2.x; // distance x at t0
                double dxn = n1.x - n2.x; // distance x at t1
                double dyo = o1.y - o2.y; // distance y at t0
                double dyn = n1.y - n2.y; // distance y at t1
                double dzo = o1.z - o2.z; // distance z at t0
                double dzn = n1.z - n2.z; // distance z at t1
                double c1 = (dxn - dxo) / dt;
                double c0 = (dxo * t1 - dxn * t0) / dt;
                double d1 = (dyn - dyo) / dt;
                double d0 = (dyo * t1 - dyn * t0) / dt;
                double e1 = (dzn - dzo) / dt;
                double e0 = (dzo * t1 - dzn * t0) / dt;

                if (nodes_on) {
                    on_time = on_time + dt;
                }

                if ((c1 != 0.0) || (d1 != 0.0) || (e1 != 0.0)) { // we have relative movement
                    // TODO: check if calculations are correct for 3D coordinates
                    double m = -1.0 * (c0 * c1 + d0 * d1 + e0 * e1) / (c1 * c1 + d1 * d1 + e1 * e1);
                    double m2 = m * m;
                    double q = (c0 * c0 + d0 * d0 + e0 * e0 - range * range) / (c1 * c1 + d1 * d1 + e1 * e1);
                    if (m2 - q > 0.0) {
                        double d = Math.sqrt(m2 - q);
                        double min = m - d;
                        double max = m + d;
                        if ((min >= t0) && (min <= t1) && sameBuilding(buildings, o1, o2, dim)) {
                            if (d < 0.01) {
                                System.out.println("---------------");
                                System.out.println("MobileNode.pairStatistics: The time span these 2 nodes are in range seems very");
                                System.out.println("  short. Might this be an error or a bad choice of parameters?");
                                System.out.println("o1=" + o1);
                                System.out.println("n1=" + n1);
                                System.out.println("o2=" + o2);
                                System.out.println("n2=" + n2);
                                System.out.println("[" + t0 + ";" + t1 + "]:[" + m + "-" + d + "=" + min + ";" + m + "+" + d + "=" + max
                                        + "]");
                                System.out.println("---------------");
                            }
                            if (nodes_on) {
                                if (!connected) {
                                    link_up_at = min;
                                    connected = true;
                                }
                                else if (min - t0 > 0.001) {
                                    System.out.println("MobileNode.pairStatistics: sanity check failed (1)");
                                    System.exit(0);
                                }
                                else
                                    System.out.println("MobileNode.pairStatistics: connect too late: t=" + min + " t0=" + t0);
                            }
                        }
                        if ((max >= t0) && (max <= t1) && (sameBuilding(buildings, o1, o2, dim))) {
                            if (nodes_on) // if not on, it was done before
                                if (connected) {
                                    con_time = con_time + (max - link_up_at);
                                    connected = false;
                                }
                                else if (max - t0 > 0.001) {
                                    System.out.println("MobileNode.pairStatistics: sanity check failed (2)");
                                    System.exit(0);
                                }
                                else
                                    System.out.println("MobileNode.pairStatistics: disconnect too late: t=" + max + " t0=" + t0);
                        }
                    }
                }
                t0 = t1;
                o1 = n1;
                o2 = n2;

                // floating point inaccuracy detection:

                if (connected) {
                    if (!conn_t1) {
                        con_time = con_time + (t1 - link_up_at);
                        connected = false;
                        System.out.println("MobileNode.pairStatistics: fp correction 2: disconnect at " + t1);
                    }
                }
                else { // !connected
                    if (conn_t1 && nodes_on) {
                        link_up_at = t1;
                        connected = true;
                        System.out.println("MobileNode.pairStatistics: fp correction 3: connect at " + t1);
                    }
                }
            }
        }
        /* add disconnect at the end of time - for correct stats link is counted at link-break */
        /*
         * NA: I do not know, why this wasn't needed before. However, due to our changes we seem to
         * need it and it shouldn't change anything
         */
        if (connected) {
            con_time = con_time + (duration - link_up_at);
        }

        double[] result = new double[2];
        result[0] = on_time;
        result[1] = con_time;
        return result;
    }
    
    
    
    

    public static double[] getSpeedoverTime(MobileNode _node, double start, double end, double interval) {
        MobileNode node = _node;
        int size = (int)(((end - start) / interval) + 1);
        double[] speed = new double[2 * size]; // it's not the speed - only distances and times

        double[] cht = node.changeTimes();

        double[] dist_cht = new double[cht.length];
        boolean[] on_cht = new boolean[cht.length];
        double[] time_cht = new double[cht.length];
        double[] time_start = new double[cht.length];

        double t0 = start;
        Position p0 = node.positionAt(start);

        for (int i = 0; i < cht.length; i++) {

            double t1 = cht[i];
            double dt = t1 - t0;

            Position p1 = node.positionAt(t1);

            double dp = p0.distance(p1);

            dist_cht[i] = dp;
            on_cht[i] = (p0.status != 2.0); // if status of t0 == 2.0 node is off/away
            time_cht[i] = dt;
            time_start[i] = t0;

            t0 = t1;
            p0 = p1;
        }

        int i = 0;
        for (int j = 0; j < size; j++) {
            int k = 0;
            while ((time_start.length > i + k) && (time_start[i + k] < (start + (j + 1) * interval))) {
                k++;
            }

            // calc on_time in interval and distance for interval
            double on_time = 0.0;
            double dist_on = 0.0;
            for (int i_run = i; i_run < i + k; i_run++) {
                if (on_cht[i_run]) {
                    double help = time_cht[i_run];
                    double help_dist = dist_cht[i_run];
                    if (time_start[i_run] < (start + j * interval)) {
                        help = help - (start + j * interval - time_start[i_run]);
                    }
                    if ((time_start[i_run] + time_cht[i_run]) > (start + (j + 1) * interval)) {
                        help = help - ((time_start[i_run] + time_cht[i_run]) - (start + (j + 1) * interval));
                    }
                    help_dist = help_dist - ((1 - help / time_cht[i_run]) * help_dist);

                    on_time = on_time + help;
                    dist_on = dist_on + help_dist;
                }
            }

            speed[2 * j] = dist_on; // speed
            speed[(2 * j) + 1] = on_time; // on_time

            i = i + k - 1;
        }

        return speed;
    }
    
    
    
    
    public static double getNodesOnTime(MobileNode node, double duration) {

        double[] cht = node.changeTimes();
        double on_time = 0.0;
        double t0 = 0.0;
        Position p0 = node.positionAt(t0);

        for (int i = 0; i < cht.length; i++) {

            double t1 = cht[i];
            double dt = t1 - t0;

            Position p1 = node.positionAt(t1);

            if (p0.status != 2.0) { // if status of t0 == 2.0 node is off/away
                on_time = on_time + dt;
            }

            t0 = t1;
            p0 = p1;
        }

        on_time = on_time + (duration - t0);

        return on_time;
    }

    public static boolean isNodeOffAtTime(MobileNode node, double time) {
        Position pos = node.positionAt(time);
        return (pos.status == 2);
    }

    public static double[] getOnOffChanges(MobileNode node) {
        double[] ch = node.changeTimes();
        boolean on = true;
        int events = 0;

        for (int t = 0; t < ch.length; t++) {
            if (node.positionAt(ch[t]).status > 0) {
                ++events;
            }
        }

        double[] result = new double[events];
        int j = 0;
        for (int t = 0; t < ch.length; t++) {
            if (node.positionAt(ch[t]).status == 2) {
                result[j] = ch[t];
                ++j;
                on = false;
            }
            if (node.positionAt(ch[t]).status == 1) {
                result[j] = ch[t];
                ++j;
                if (on) {
                    System.err.println("Error: Node switches on, but is already switched on! (t=" + ch[t] + ")");
                }
                on = true;
            }
        }
        return result;
    }
    
    
}