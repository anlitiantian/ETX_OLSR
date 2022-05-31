/*******************************************************************************
 ** BonnMotion - a mobility scenario generation and analysis tool             **
 ** Copyright (C) 2002-2010 University of Bonn                                **
 ** Copyright (C) 2014      University of Osnabrueck                          **
 ** Code: Zia-Ul-Huda                                                         **
 **       Matthias Schwamborn                                                 **
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.MapScenario;
import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.models.slaw.*;
import edu.bonn.cs.iv.util.maps.*;
import edu.bonn.cs.iv.util.maps.RouteServiceInterface.RSIRequestFailedException;
import edu.bonn.cs.iv.util.maps.osm.OSMGraph;

/** Application to construct MSLAW mobility scenarios. */

public class MSLAW extends MapScenario {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("MSLAW");
        info.description = "Application to construct MSLAW mobility scenarios";
        
        info.major = 2;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 682 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Zia-Ul-Huda");
        info.authors.add("Matthias Schwamborn");
		info.references.add("Matthias Schwamborn, Nils Aschenbruck: \"Introducing Geographic Restrictions to the SLAW Human Mobility Model,\" in Proc. of the IEEE 21st Int. Symposium on Modeling, Analysis and Simulation of Computer and Telecommunication Systems (MASCOTS '13), San Francisco, CA, USA, 2013");
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
//    private String waypoints_filename = null;
    private PositionGeo[] waypoints;
    
    private Hashtable<String, Double> distanceTable = null;
    private MessageDigest sha1Digest = null;
    
    private static final double MIN_WINDOW_LENGTH = 100;

    private static final boolean DEBUG = true;

    /** speed parameters */
    private double minSpeed = -1;
    private double maxSpeed = -1;
    /** metric to use for distance computations */
    private String distanceMetric = "Route";

    public MSLAW(String[] args)
    {
        go(args);
    }

    public void go(String[] args)
    {
        super.go(args);
        generate();
    }

    public void generate()
    {
        preGeneration();

        if (isTransition) {
            System.out.println("Warning: Ignoring transition...");
        }

        if (this.waypoints == null) {
            System.out.println("Generating Waypoints.\n\n");
            
            this.waypoints = generate_waypoints();
        }

        System.out.println("Generating Clusters.\n\n");
        Cluster[] clusters = SLAWBase.generate_clusters(this.waypoints, this.cluster_range);
        System.out.println(clusters.length + " Clusters found.");

        // These variables have values same as in the matlab implementation of SLAW model by Seongik
        // Hong, NCSU, US (3/10/2009)
        int powerlaw_step = 1;
        int levy_scale_factor = 1;
        int powerlaw_mode = 1;


        System.out.println("Trace generation started.\n");
        
out:    for (int user = 0; user < parameterData.nodes.length; user++) {
            double t = 0.0;
            parameterData.nodes[user] = new MobileNode();
            if (wpGeo.size() == user) {
            	wpGeo.add(new Vector<WaypointGeo>());
            } else if (wpGeo.size() == user+1) {
            	wpGeo.get(user).clear();
            } else {
            	System.out.println("Error: Retrying movement generation for an earlier node?\n");
        		System.exit(-1);
            }

            // get random clusters and waypoints
            Cluster[] clts = SLAWBase.make_selection(clusters, null, false, cluster_ratio, waypoints.length, waypoint_ratio, this);
            // total list of waypoints assigned
            ClusterMember[] wlist = SLAWBase.get_waypoint_list(clts);
            // random source node
            int src = (int)Math.floor(randomNextDouble() * wlist.length);
            int dst = -1;
            int count = 0;

            while (t < parameterData.duration) {
                count = 0;
                
                assert(wlist[src].pos instanceof PositionGeo);
                PositionGeo source = (PositionGeo)wlist[src].pos;
                addWaypoint(user, t, source);

                wlist[src].is_visited = true;
                // get list of not visited locations
                for (int i = 0; i < wlist.length; i++) {
                    if (!wlist[i].is_visited) {
                        count++;
                    }
                }
                // if all waypoints are visited then change one of clusters randomly. Destructive
                // mode of original SLAW matlab implementation by Seongik Hong, NCSU, US (3/10/2009)
                while (count == 0) {
                    clts = SLAWBase.make_selection(clusters, clts, true, cluster_ratio, waypoints.length, waypoint_ratio, this);
                    wlist = SLAWBase.get_waypoint_list(clts);
                    for (int i = 0; i < wlist.length; i++) {
                        if (!wlist[i].is_visited) {
                            if (source.distance(wlist[i].pos) != 0.0) {
                                count++;
                            }
                            else {
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
                	assert(not_visited[i].pos instanceof PositionGeo);
                	PositionGeo tmp_dst = (PositionGeo)not_visited[i].pos;
                	
                	String key = new String(sha1Digest.digest((source.toString() + "," + tmp_dst.toString()).getBytes()));
                	
                    if (distanceMetric.equals("Route")) {
                        if (distanceTable.containsKey(key)) {
                            dist[i] = distanceTable.get(key);
                        } else {
                        	try {
                        		Route tmp = rsInstance.getOptimalRoute(source, tmp_dst);
                        		dist[i] = tmp.distanceGeodesic();
                        	} catch (RSIRequestFailedException e) {
                                // redo all for this node
                    			System.out.println("Error: Could not retrieve route distance (" + source.toString() + " to " + tmp_dst.toString() +", flight length " + source.distance(tmp_dst) + "m), retrying movement generation for node " + (user+1) + "...\n");
                                user--;
                                continue out;
                        	}
                        	
                        	if (dist[i] < 0) {
                        		System.out.println("Error: Negative route distance returned, exiting...\n");
                        		System.exit(-1);
                        	}

                            distanceTable.put(key, dist[i]);
                        }
                    } else {
                        dist[i] = source.distance(tmp_dst);
                    }
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
                
                if (index == weights.length) {
                    index--;
                }

                // select the next destination
                for (int i = 0; i < wlist.length; i++) {
                	assert(wlist[i].pos instanceof PositionGeo);
                	assert(not_visited[index].pos instanceof PositionGeo);
                    if (((PositionGeo)wlist[i].pos).x() == ((PositionGeo)not_visited[index].pos).x() &&
                    	((PositionGeo)wlist[i].pos).y() == ((PositionGeo)not_visited[index].pos).y()) {
                        dst = i;
                        break;
                    }
                }

                assert(wlist[dst].pos instanceof PositionGeo);
                PositionGeo destination = (PositionGeo)wlist[dst].pos;

                Route route = null;
                try {
                    route = rsInstance.getOptimalRoute(source, destination);
                } catch (RSIRequestFailedException e) {
                    // redo all for this node
        			System.out.println("Error: Could not retrieve route, retrying movement generation for node " + (user+1) + "...\n");
                    user--;
                    continue out;

//                    // if no route could be received discard destination
//                    wlist[dst].is_visited = true;
//                    continue;
                } catch (Exception e) {
                	e.printStackTrace();
                    System.err.println(e.toString());
                    System.exit(-1);
                }
                
                // add route to waypoint list (go to destination)
                route.setScenarioStartTime(t);
                t = addRoute(user, route, minSpeed, maxSpeed);

                // select pause time by power law formula
                if ((t < parameterData.duration) && (this.maxpause > 0.0)) {
                    t += SLAWBase.random_powerlaw(powerlaw_step, levy_scale_factor, powerlaw_mode, minpause, maxpause, beta, this)[0];
                }
                // change destination to next source
                src = dst;

            }
            System.out.println("Node " + (user + 1) + " of " + parameterData.nodes.length + " done.");
        }

        postGeneration();
    }
    
    private PositionGeo[] generate_waypoints()
    {
        // Number of levels as mentioned in original paper "SLAW: A Mobility Model for Human Walks"
        // in: Proc. of the IEEE Infocom (2009).
        int levels = 8;
        // convert hurst to alpha as done in matlab implementation by Seongik Hong, NCSU, US
        // (3/10/2009)
        double converted_hurst = 2 - 2 * hurst;
        // initial variance at first level as used in matlab implementation by Seongik Hong, NCSU,
        // US (3/10/2009)
        double initial_variance = 0.8;
        
        // adapt no. of levels to area size
        if (mapBBox.width() / Math.pow(2, levels) < MIN_WINDOW_LENGTH) {
        	levels = (int)Math.floor(Math.log(mapBBox.width()/MIN_WINDOW_LENGTH)/Math.log(2));
        }
        if (mapBBox.height() / Math.pow(2, levels) < MIN_WINDOW_LENGTH) {
        	levels = (int)Math.floor(Math.log(mapBBox.height()/MIN_WINDOW_LENGTH)/Math.log(2));
        }
        if (DEBUG) System.out.println("DEBUG: Smallest area size: " + mapBBox.width() / Math.pow(2, levels) + " x " + mapBBox.height() / Math.pow(2, levels));

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
            Xwind = mapBBox.widthDeg() / Math.pow(2, level);
            Ywind = mapBBox.heightDeg() / Math.pow(2, level);

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
        
//        if (DEBUG) System.out.println(wpoints.toString());

        // create waypoints
        Xwind = mapBBox.widthDeg() / Math.sqrt(Math.pow(4, levels));
        Ywind = mapBBox.heightDeg() / Math.sqrt(Math.pow(4, levels));

        int total_squares = (int)Math.pow(4, levels);
        Vector<PositionGeo> waypoints = new Vector<PositionGeo>();
        PositionGeo temp = null;
        int w, count = 0;
        int noRouteCount = 0;
        int wpInEmptySquareCount = 0;
        for (int i = 0; i < total_squares; i++) {
            // get waypoints of current square
            w = wpoints.get(levels + "," + i);
            if (w != 0) {
            	// create sub-graph for current grid segment
            	double xoff = Xoffset.get(levels + "," + i);
            	double yoff = Yoffset.get(levels + "," + i);
            	OSMGraph subGraph = null;
            	if (osmGraph != null) {
                	BoundingBox bb = new BoundingBox(mapBBox.ll().x() + xoff, mapBBox.ll().y() + yoff,
                									 mapBBox.ll().x() + xoff + Xwind, mapBBox.ll().y() + yoff + Ywind);
                	subGraph = this.osmGraph.createSubGraph(bb);
            	}

            	int j = 0;
                while (j < w) {
                	if (subGraph != null) {
                    	temp = subGraph.getRandomPosition(randomNextDouble());
                	} else {
                        temp = randomNextWaypoint(xoff, yoff, Xwind, Ywind);
                	}

                    if (temp != null) {
                    	boolean newWaypoint = true;
                    	for (PositionGeo p : waypoints) {
                    		if (temp.toString().equals(p.toString())) {
                    			newWaypoint = false;
                    			break;
                    		}
                    	}
                    	
                    	if (newWaypoint) { // store the new calculated waypoint
                    		waypoints.add(temp);
                    		j++;
                    		
                    		// test routes and store distances
                    		Route route = null;
                    		String key = null;
                    		for (PositionGeo p : waypoints) {
                    			if (p.equals(temp)) {
                    				continue;
                    			}
                    			
                                try {
                                    route = rsInstance.getOptimalRoute(p, temp);
                                    key = new String(sha1Digest.digest((p.toString() + "," + temp.toString()).getBytes()));
                                    assert(!distanceTable.containsKey(key));
                                    distanceTable.put(key, route.distanceGeodesic());
                                    
                                    route = rsInstance.getOptimalRoute(temp, p);
                                    key = new String(sha1Digest.digest((temp.toString() + "," + p.toString()).getBytes()));
                                    assert(!distanceTable.containsKey(key));
                                    distanceTable.put(key, route.distanceGeodesic());
                                } catch (RSIRequestFailedException e) {
                                	noRouteCount++;
                                	waypoints.remove(temp);
                                	j--;
                                	break;
                                } catch (Exception e) {
                                	e.printStackTrace();
                                    System.err.println(e.toString());
                                    System.exit(-1);
                                }
                    		}
                    	}
                    } else { // temp == null (empty sub-graph)
                    	System.out.println("Unable to generate " + w + " waypoints for square (" + levels + "," + i + ").");
                    	wpInEmptySquareCount += w;
                    	break;
                    }
                    count++;
                    
                    if (count % 100 == 0 || waypoints.size() == this.noOfWaypoints - wpInEmptySquareCount) {
                        System.out.println(count + " waypoints tested. " + waypoints.size() + " of " + (this.noOfWaypoints - wpInEmptySquareCount) + " generated.");
                    }
                }
            }
        }
        
        System.out.println(wpInEmptySquareCount + " waypoints not generated due to empty squares.");
        System.out.println(noRouteCount + " waypoints dropped due to routing problems.");

        return waypoints.toArray(new PositionGeo[0]);
    }

    protected boolean parseArg(String key, String value)
    {
        if (key.equals("noOfWaypoints")) {
            noOfWaypoints = Integer.parseInt(value);
            return true;
        } else if (key.equals("minpause")) {
            minpause = Double.parseDouble(value);
            return true;
        } else if (key.equals("maxpause")) {
            maxpause = Double.parseDouble(value);
            return true;
        } else if (key.equals("beta")) {
            beta = Double.parseDouble(value);
            return true;
        } else if (key.equals("hurst")) {
            hurst = Double.parseDouble(value);
            return true;
        } else if (key.equals("dist_weight")) {
            dist_weight = Double.parseDouble(value);
            return true;
        } else if (key.equals("cluster_range")) {
            cluster_range = Double.parseDouble(value);
            return true;
        } else if (key.equals("cluster_ratio")) {
            cluster_ratio = Integer.parseInt(value);
            return true;
        } else if (key.equals("waypoint_ratio")) {
            waypoint_ratio = Integer.parseInt(value);
            return true;
        } else if (key.equals("speed")) {
        	String[] v = value.split(" ");
            minSpeed = Double.parseDouble(v[0]);
            maxSpeed = Double.parseDouble(v[1]);
        	return true;
        } else if (key.equals("distance_metric")) {
            distanceMetric = value;
            return true;
        } else {
            return super.parseArg(key, value);
        }
    }

    protected boolean parseArg(char key, String val)
    {
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
//            case 'F': // provide waypoint csv
//                waypoints_filename = val;
//                return true;
            case 's': // "speed interval"
                String[] v = val.split(" ");
                minSpeed = Double.parseDouble(v[0]);
                maxSpeed = Double.parseDouble(v[1]);
                return true;
            case 'D': // "distance metric"
                distanceMetric = val;
                return true;
            default:
                return super.parseArg(key, val);
        }
    }

    public void write(String _name) throws FileNotFoundException, IOException
    {
        String[] p = new String[11];
        p[0] = "noOfWaypoints=" + this.noOfWaypoints;
        p[1] = "minpause=" + this.minpause;
        p[2] = "maxpause=" + this.maxpause;
        p[3] = "beta=" + this.beta;
        p[4] = "hurst=" + this.hurst;
        p[5] = "dist_weight=" + this.dist_weight;
        p[6] = "cluster_range=" + this.cluster_range;
        p[7] = "cluster_ratio=" + this.cluster_ratio;
        p[8] = "waypoint_ratio=" + this.waypoint_ratio;
        p[9] = "speed=" + minSpeed + " " + maxSpeed;
        p[10] = "distance_metric=" + distanceMetric;
        super.write(_name, p);

        try {
            PrintWriter csv = new PrintWriter(new FileOutputStream(_name + "_waypoints_geo.csv"));
            for (PositionGeo pos : this.waypoints) {
                csv.print(pos.toString());
            }
            csv.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void printHelp()
    {
        System.out.println(getInfo().toDetailString());
        MapScenario.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-w <Number of waypoints to generate>\n" +
                		   "\t-p <Minimum pause time>\n" +
                		   "\t-P <Maximum pause time>\n" +
                		   "\t-b <Levy exponent for pause time>\n" +
                		   "\t-h <Hurst parameter for self-similarity of waypoints>\n" +
                		   "\t-l <distance weight>\n" +
                		   "\t-r <clustering range (meter)>\n" +
                		   "\t-Q <Cluster ratio>\n" +
        				   "\t-W <waypoint ratio>\n" +
        				   "\t-s <minSpeed> <maxSpeed>\n" +
        				   "\t-D <distance metric (\"Flight\" or \"Route\")>");
    }

    protected void preGeneration()
    {
        super.preGeneration();

        if (minSpeed > maxSpeed || minSpeed <= 0 || maxSpeed <= 0) {
            System.out.println("Error: There is something wrong with the speed values!");
            System.exit(0);
        }
        
        try {
			this.sha1Digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
        
        this.distanceTable = new Hashtable<String, Double>();
        
//        if (this.waypoints_filename != null) {
//            System.out.println("Loading waypoints from file: " + this.waypoints_filename + "\n");
//            this.waypoints = SLAWBase.readWaypointsFromFile(this.waypoints_filename);
//            this.noOfWaypoints = this.waypoints.length;
//        }
    }

    protected void postGeneration()
    {
        super.postGeneration();
    }

    private PositionGeo randomNextWaypoint(double Xoffset, double Yoffset, double Xwind, double Ywind)
    {
    	BoundingBox bb = new BoundingBox(mapBBox.ll().x() + Xoffset,         mapBBox.ll().y() + Yoffset,
    									 mapBBox.ll().x() + Xoffset + Xwind, mapBBox.ll().y() + Yoffset + Ywind);
//    	if (DEBUG) System.out.println("(" + bb.ll().x() + ", " + bb.ll().y() + ", " + bb.ur().x() + ", " + bb.ur().y() + ")");
    	
    	PositionGeo result = null;
    	
    	try {
    		result = randomNextGeoPosition(bb);
    	} catch(RandomPositionFailedException e) {
    		result = null;
    	}
    	
        return result;
    }
}
