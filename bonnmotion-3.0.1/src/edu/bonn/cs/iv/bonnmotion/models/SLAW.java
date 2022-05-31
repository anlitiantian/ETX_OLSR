/*******************************************************************************
 ** BonnMotion - a mobility scenario generation and analysis tool             **
 ** Copyright (C) 2002-2010 University of Bonn                                **
 ** Code: Zia-Ul-Huda                                                         **
 **       Gufron Atokhojaev                                                   **       
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.Waypoint;
import edu.bonn.cs.iv.bonnmotion.models.slaw.Cluster;
import edu.bonn.cs.iv.bonnmotion.models.slaw.ClusterMember;
import edu.bonn.cs.iv.bonnmotion.models.slaw.SLAWBase;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;
import edu.bonn.cs.iv.bonnmotion.printer.Printer;
import edu.bonn.cs.iv.bonnmotion.printer.PrinterStyle;

/** Application to construct SLAW mobility scenarios. */

public class SLAW extends Scenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("SLAW");
        info.description = "Application to construct mobility scenarios according to the Self-similar Least Action Walk model";
        
        info.major = 1;
        info.minor = 1;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Zia-Ul-Huda");
        info.authors.add("Gufron Atokhojaev");
		info.authors.add("Florian Schmitt");
		info.authors.add("Raphael Ernst");
        info.references.add("http://research.csc.ncsu.edu/netsrv/?q=content/slaw-self-similar-least-action-walk");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
    /** SLAW parameters */
    private int noOfWaypoints = 1000;
    private int cluster_ratio = 5;
    private int waypoint_ratio = 5;
    private double minpause = 10;
    private double maxpause = 50;
    private double beta = 1;
    private double hurst = 0.75;
    private double dist_weight = 3;
    private double cluster_range = 50;
    private String waypoints_filename = null;
    private Position[] waypoints;

    public SLAW(int nodes, double x, double y, double duration, double ignore, long randomSeed, int waypoints, double minpause,
            double maxpause, double beta, double hurst, double dist_weight, double cluster_range, int cr, int wr) {
        super(nodes, x, y, duration, ignore, randomSeed);
        this.noOfWaypoints = waypoints;
        this.minpause = minpause;
        this.maxpause = maxpause;
        this.beta = beta;
        this.hurst = hurst;
        this.dist_weight = dist_weight;
        this.cluster_range = cluster_range;
        this.cluster_ratio = cr;
        this.waypoint_ratio = wr;
        generate();
    }

    public SLAW(String[] args) {
        go(args);
    }

    public void go(String[] args) {
        super.go(args);
        generate();
    }

    public SLAW(String args[], Scenario _pre, Integer _transitionMode) {
        // we've got a predecessor, so a transition is needed
        predecessorScenario = _pre;
        transitionMode = _transitionMode.intValue();
        isTransition = true;
        go(args);
    }

    public void generate() {
        preGeneration();

        if (isTransition) {
            System.err.println("Warning: Ignoring transition...");
        }

        if (this.waypoints == null) {
            System.out.println("Generating Waypoints.\n\n");
            
            this.waypoints = generate_waypoints();
            this.noOfWaypoints = waypoints.length;
        }
        
        System.out.println("Generating Clusters.\n\n");
        Cluster[] clusters = SLAWBase.generate_clusters(this.waypoints, this.cluster_range);
        System.out.println(clusters.length + " Clusters found.");

        // These variables have values same as in the matlab implementation of
        // SLAW model by Seongik Hong, NCSU, US (3/10/2009)
        final double speed = 1;
        final int powerlaw_step = 1;
        final int levy_scale_factor = 1;
        final int powerlaw_mode = 1;
        if (clusters.length > 1){
            System.out.println("Trace generation started.\n");

            for (int user = 0; user < parameterData.nodes.length; user++) {
            	parameterData.nodes[user] = new MobileNode();
                double t = 0.0;
                // get random clusters and waypoints
                Cluster[] clts = SLAWBase.make_selection(clusters, null, false, cluster_ratio, noOfWaypoints, waypoint_ratio, this);
                // total list of waypoints assigned
                ClusterMember[] wlist = SLAWBase.get_waypoint_list(clts);

                // random source node
                int src = (int)Math.floor(randomNextDouble() * wlist.length);
                int dst = -1;
                int count;
                
                while (t < parameterData.duration) {
                    count = 0;
                    
                    Position source = (Position)wlist[src].pos;
                    
                    if (!parameterData.nodes[user].add(t, source)) {
                        throw new RuntimeException(getInfo().name + ".generate: error while adding waypoint (1)");
                    }
                    wlist[src].is_visited = true;
                    
                    // get list of not visited locations
                    for (int i = 0; i < wlist.length; i++) {
                        if (!wlist[i].is_visited) {
                            count++;
                        }
                    }
                    
                    // if all waypoints are visited then select new clusters and
                    // waypoints. Destructive mode of original SLAW matlab
                    // implementation by Seongik Hong, NCSU, US (3/10/2009)
                    while (count == 0) {
                        clts = SLAWBase.make_selection(clusters, clts, true, cluster_ratio, noOfWaypoints, waypoint_ratio, this);
                        wlist = SLAWBase.get_waypoint_list(clts);
                        for (int i = 0; i < wlist.length; i++) {
                            if (!wlist[i].is_visited) {
                                if (source.distance(wlist[i].pos) != 0.0) {
                                    count++;
                                } else {
                                    wlist[i].is_visited = true;
                                }
                            }
                        }
                    }

                    ClusterMember[] not_visited = new ClusterMember[count];
                    count = 0;
                    for (int i = 0; i < wlist.length; i++) {
                        if (!wlist[i].is_visited) {
                            not_visited[count++] = wlist[i];
                        }
                    }
                    
                    // get distance from source to all remaining waypoints
                    double[] dist = new double[not_visited.length];
                    for (int i = 0; i < not_visited.length; i++) {
                        dist[i] = source.distance(not_visited[i].pos);
                    }

                    double[] weights = new double[not_visited.length];
                    // cumulative sum of distance weights
                    for (int i = 0; i < weights.length; i++) {
                        weights[i] = 0;
                        for (int j = 0; j <= i; j++) {
                            weights[i] += 1 / Math.pow(dist[j], this.dist_weight);
                        }
                    }

                    for (int i = 0; i < weights.length; i++) {
                        weights[i] /= weights[weights.length - 1];
                    }

                    double r = randomNextDouble();

                    int index;
                    for (index = 0; index < weights.length; index++) {
                        if (r < weights[index]) {
                            break;
                        }
                    }

                    // select the next destination
                    for (int i = 0; i < wlist.length; i++) {
                        if (wlist[i].pos.equals(not_visited[index].pos)) {
                            dst = i;
                            break;
                        }
                    }

                    double distance = source.distance(wlist[dst].pos);
                    t += distance / speed;

                    if (!parameterData.nodes[user].add(t, (Position)wlist[dst].pos)) {
                        throw new RuntimeException(getInfo().name + ".generate: error while adding waypoint (2)");
                    }
                    
                    // select pause time by power law formula
                    if ((t < parameterData.duration) && (this.maxpause > 0.0)) {
                        t += SLAWBase.random_powerlaw(powerlaw_step, levy_scale_factor, powerlaw_mode, minpause, maxpause, beta, this)[0];
                    }
                    // change destination to next source
                    src = dst;
                }
                System.out.println("Trace generation for node " + (user + 1) + " of " + parameterData.nodes.length + " done.");
            }
            System.out.println("\n");
            postGeneration();
            System.out.println("Trace generation done.\n");
        } else {
            System.out.println("Error: Too few Clusters to generate Trace!");
            System.exit(0);
        }
    }

    /**
     * generates waypoints for SLAW model
     * 
     * @return array of waypoint positions
     */
    private Position[] generate_waypoints() {
        // Number of levels as mentioned in original paper "SLAW: A Mobility Model for Human Walks" 
        // in: Proc. of the IEEE Infocom (2009).
        int levels = 8;
        // convert hurst to alpha as done in matlab implementation by Seongik
        // Hong, NCSU, US (3/10/2009)
        double converted_hurst = 2 - 2 * hurst;
        // initial variance at first level as used in matlab implementation by
        // Seongik Hong, NCSU, US (3/10/2009)
        double initial_variance = 0.8;

        // variances for all levels
        double[] xvalues = new double[levels];
        double[] level_variances = new double[levels];

        for (int i = 0; i < levels; i++) {
            xvalues[i] = Math.pow(4, i + 1);
            level_variances[i] = initial_variance * Math.pow(xvalues[i] / xvalues[0], converted_hurst);
        }

        Hashtable<String, Integer> wpoints = new Hashtable<String, Integer>();
        Hashtable<String, Double> Xoffset = new Hashtable<String, Double>();
        Hashtable<String, Double> Yoffset = new Hashtable<String, Double>();
        wpoints.put("0,0", this.noOfWaypoints);
        Xoffset.put("0,0", 0.0);
        Yoffset.put("0,0", 0.0);
        double Xwind, Ywind;

        for (int level = 0; level < levels; level++) {
            System.out.println("Level " + (level + 1) + " of " + levels + " started.");
            // Number of squares at current level
            double n_squares = Math.pow(4, level);
            Xwind = parameterData.x / Math.pow(2, level);
            Ywind = parameterData.y / Math.pow(2, level);

            for (int square = 0; square < n_squares; square++) {
                if (square % 2000 == 0 && square != 0) {
                    System.out.println(square + " of " + n_squares + " processed.");
                }
                // generate the offsets of x and y for children squares
                double val;
                double xval = Xoffset.get(level + "," + square);
                double yval = Yoffset.get(level + "," + square);

                for (int i = 0; i < 4; i++) {
                    val = xval;
                    // add window size to the Xoff set of second and third child square
                    if (i == 1 || i == 3) {
                        val += Xwind / 2;
                    }
                    Xoffset.put((level + 1) + "," + (4 * square + i), val);
                    
                    val = yval;
                    // add window size to the Yoff set of third and fourth child square
                    if (i == 2 || i == 3) {
                        val += Ywind / 2;
                    }
                    Yoffset.put((level + 1) + "," + (4 * square + i), val);
                }

                // get waypoints assigned to this node
                int wp = wpoints.get(level + "," + square);
                if (wp == 0) {
                    // assign 0 to all child nodes as waypoints
                    for (int i = 0; i < 4; i++) {
                        wpoints.put((level + 1) + "," + (4 * square + i), 0);
                    }
                } else if (level == 0) {
                    // first level
                    int[] num = SLAWBase.divide_waypoints(wp, level_variances[level], this);
                    for (int i = 0; i < 4; i++) {
                        wpoints.put((level + 1) + "," + (4 * square + i), num[i]);
                    }
                } else {
                    // inner levels
                    double[] cur_wp = new double[(int)Math.pow(4, level)];
                    for (int i = 0; i < cur_wp.length; i++) {
                        cur_wp[i] = wpoints.get(level + "," + i);
                    }

                    double avg = SLAWBase.calculate_average(cur_wp);

                    for (int i = 0; i < Math.pow(4, level); i++) {
                        cur_wp[i] /= avg;
                    }

                    double var = SLAWBase.calculate_var(cur_wp) + 1;
                    int[] num = SLAWBase.divide_waypoints(wp, ((level_variances[level] + 1) / var) - 1, this);
                    for (int i = 0; i < 4; i++) {
                        wpoints.put((level + 1) + "," + (4 * square + i), num[i]);
                    }
                }
            }// for squares
        }// for level

        // create waypoints
        Xwind = parameterData.x / Math.sqrt(Math.pow(4, levels));
        Ywind = parameterData.y / Math.sqrt(Math.pow(4, levels));
        int total_squares = (int)Math.pow(4, levels);

        double theta, xx, yy;
        Position[] waypoints = new Position[noOfWaypoints];
        int w, iterator = 0;
        for (int i = 0; i < total_squares; i++) {
            // get waypoints of current square
            w = wpoints.get(levels + "," + i);
            if (w != 0) {
                for (int j = 0; j < w; j++) {
                    theta = 2 * Math.PI * randomNextDouble();
                    xx = Xoffset.get(levels + "," + i) + Xwind / 2 + (randomNextDouble() * Xwind / 2) * Math.cos(theta);
                    yy = Yoffset.get(levels + "," + i) + Ywind / 2 + (randomNextDouble() * Ywind / 2) * Math.sin(theta);
                    waypoints[iterator++] = new Position(xx, yy);
                }
            }
        }

        return waypoints;
    }

    public void write(String _name) throws FileNotFoundException, IOException {
        String[] p = new String[9];
        p[0] = "noOfWaypoints=" + this.noOfWaypoints;
        p[1] = "minpause=" + this.minpause;
        p[2] = "maxpause=" + this.maxpause;
        p[3] = "beta=" + this.beta;
        p[4] = "hurst=" + this.hurst;
        p[5] = "dist_weight=" + this.dist_weight;
        p[6] = "cluster_range=" + this.cluster_range;
        p[7] = "cluster_ratio=" + this.cluster_ratio;
        p[8] = "waypoint_ratio=" + this.waypoint_ratio;
        super.writeParametersAndMovement(_name, p);

        try {
            PrintWriter csv = new PrintWriter(new FileOutputStream(_name + "_waypoints.csv"));
            Printer printer = new Printer(PrinterStyle.MovementString, Dimension.TWOD);
            for (Position pos : this.waypoints) {
                csv.println(printer.print(pos));
            }

            csv.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    protected boolean parseArg(String key, String value) {
        if (key.equals("noOfWaypoints")) {
            noOfWaypoints = Integer.parseInt(value);
            return true;
        }
        else if (key.equals("minpause")) {
            minpause = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("maxpause")) {
            maxpause = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("beta")) {
            beta = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("hurst")) {
            hurst = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("dist_weight")) {
            dist_weight = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("cluster_range")) {
            cluster_range = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("cluster_ratio")) {
            cluster_ratio = Integer.parseInt(value);
            return true;
        }
        else if (key.equals("waypoint_ratio")) {
            waypoint_ratio = Integer.parseInt(value);
            return true;
        }
        else
            return super.parseArg(key, value);
    }

    protected boolean parseArg(char key, String val) {
        switch (key) {
            case 'w': // "Number of waypoints"
                noOfWaypoints = Integer.parseInt(val);
                return true;
            case 'p': // "Minimum Pause time"
                minpause = Double.parseDouble(val);
                return true;
            case 'P': // "Maximum Pause time"
                maxpause = Double.parseDouble(val);
                return true;
            case 'b': // "Beta value"
                beta = Double.parseDouble(val);
                return true;
            case 'h': // "Hurst value"
                hurst = Double.parseDouble(val);
                return true;
            case 'l': // "Distance weight value"
                dist_weight = Double.parseDouble(val);
                return true;
            case 'r': // "Range for cluster"
                cluster_range = Double.parseDouble(val);
                return true;
            case 'Q': // "Number of clusters to be selected"
                cluster_ratio = Integer.parseInt(val);
                return true;
            case 'W': // "Number of waypoints to be selected"
                waypoint_ratio = Integer.parseInt(val);
                return true;            
            case 'F': // provide waypoint csv
                waypoints_filename = val;
                return true;
            default:
                return super.parseArg(key, val);
        }
    }

    public static void printHelp() {
        System.out.println(getInfo().toDetailString());
        Scenario.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-w <Number of waypoints to generate>\n" +
                		   "\t-p <Minimum pause time>\n" +
                		   "\t-P <Maximum pause time>\n" +
                		   "\t-b <Levy exponent for pause time>\n" +
                		   "\t-h <Hurst parameter for self-similarity of waypoints>\n" +
                		   "\t-l <distance weight>\n" +
                		   "\t-r <clustering range (meter)>\n" +
                		   "\t-Q <Cluster ratio>\n" +
        				   "\t-W <waypoint ratio>\n");
    }
    
    protected void preGeneration() {
        super.preGeneration();
        
        if (this.waypoints_filename != null) {
            System.out.println("Loading waypoints from file: " + this.waypoints_filename + "\n");
            this.waypoints = SLAWBase.readWaypointsFromFile(this.waypoints_filename);
            this.noOfWaypoints = this.waypoints.length;
        }
    }

    protected void postGeneration() {
        for (int i = 0; i < parameterData.nodes.length; i++) {
            Waypoint l = parameterData.nodes[i].getLastWaypoint();
            if (l.time > parameterData.duration) {
                Position p = parameterData.nodes[i].positionAt(parameterData.duration);
                parameterData.nodes[i].removeLastElement();
                parameterData.nodes[i].add(parameterData.duration, p);
            }
        }
        super.postGeneration();
    }
}