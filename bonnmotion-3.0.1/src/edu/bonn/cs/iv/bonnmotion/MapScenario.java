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

import java.awt.geom.Point2D;
import java.io.*;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import crosby.binary.file.BlockInputStream;
import crosby.binary.file.BlockReaderAdapter;

import edu.bonn.cs.iv.util.maps.*;
import edu.bonn.cs.iv.util.maps.CoordinateTransformation.proj4lib;
import edu.bonn.cs.iv.util.maps.Route.ClippedRouteInfo;
import edu.bonn.cs.iv.util.maps.Route.ClippingInfo;
import edu.bonn.cs.iv.util.maps.osm.OSMGraph;
import edu.bonn.cs.iv.util.maps.osm.PBFParser;

/** Base class for map-based models. */

public class MapScenario extends Scenario {
    @SuppressWarnings("serial")
	public static class RandomPositionFailedException extends Exception
    {
        public RandomPositionFailedException(String string)
        {
            super(string);
        }
    }
	
    private static final boolean DEBUG = true;

    protected static final int RND_RETRY_COUNT = 3;
    
    // map clipping methods
    protected static final int CLIP_RESIZE_BB = 0; // do not clip, resize bbox/area instead
    protected static final int CLIP_USE_NEW_LENGTH = 1; // clip and use length of new (boundary) segments
    protected static final int CLIP_USE_OLD_LENGTH = 2; // clip and use length of old (clipped) segments
    protected static final int CLIP_MAX = 3;

    // needed for CLIP_RESIZE_BB
    protected Point2D.Double positionMin = null;
    protected Point2D.Double positionMax = null;

    /** bounding box for the underlying map */
    protected BoundingBox mapBBox = null;
    /** clipping method */
    protected int clippingMethod = CLIP_RESIZE_BB;
    /** route service URL */
    protected String rsURL = null;
    /** route service metric ("Car" or "Pedestrian") */
    protected String rsMetric = "Pedestrian";
    /** route service instance */
    protected RouteServiceInterface rsInstance = null;
    /** projection of the scenario area positions */
    protected CoordinateTransformation transformation = new CoordinateTransformation(null, proj4lib.PROJ4J);
    /** OSM input file */
    protected String osmFile = null;
    /** OSM graph */
    protected OSMGraph osmGraph = null;
    /** geometric waypoint list */
    protected Vector<Vector<WaypointGeo>> wpGeo = null;

    protected String distFile = null;

	public MapScenario() {}
	
	protected MapScenario(String basename) throws FileNotFoundException, IOException
	{
		read(basename);
	}
	
    public static MapScenario getScenario(String basename) throws FileNotFoundException, IOException
    {
    	return new MapScenario(basename);
    }
	
    protected double addRoute(int nodeIndex, Route route, double minSpeed, double maxSpeed)
    {
    	// the route might be (partially) outside the bounding box -> clip it
    	PositionGeo[] p = null;
    	assert(route.ci() == null);
    	ClippingInfo ci = null;
    	if (clippingMethod != CLIP_RESIZE_BB) {
        	ci = route.clipRoute(mapBBox);
    	}
    	
    	if (ci == null) { // CLIP_RESIZE_BB or no clipping was needed
    		p = route.allPoints();
    	} else { // route was clipped
    		p = route.allClipPoints();
    	}

        PositionGeo src = p[0];
        PositionGeo dst = null;
        double t = route.scenarioStartTime();
        
        int j = 0;
        for (int i = 0; i < p.length; i++) {
            dst = p[i];
            double speed = (maxSpeed - minSpeed) * randomNextDouble() + minSpeed;
            
            // choose distance according to clipping method
            double dist = 0.0;
            if (ci == null || clippingMethod != CLIP_USE_OLD_LENGTH || j >= ci.clippedRoute.size()) {
            	dist = src.distance(dst);
            } else {
            	ClippedRouteInfo cri = ci.clippedRoute.get(j);
            	
            	if (cri.indexNew.firstElement() != i-1) {
            		dist = src.distance(dst);
            	} else {
            		dist = ci.clippedRoute.get(j).clippedDistance;
            		j++;
            	}
            }
            
            double time = dist / speed;
            t += time;
            addWaypoint(nodeIndex, t, dst);
            if (t >= parameterData.duration) {
                return t;
            }
            
            src = dst;
        }

        return t;
    }
	
    protected void addWaypoint(int nodeIndex,
            				   double t,
            				   PositionGeo p)
    {
    	if (clippingMethod != CLIP_RESIZE_BB) {
    		assert mapBBox.contains(p) : p.toString();
    	}

//    	Position old = null;
//    	double dist = -1.0;
//    	if (node[nodeIndex].getNumWaypoints() > 0) {
//    		old = node[nodeIndex].getLastWaypoint().pos;
//    		dist = p.distance(old);
//    		// dirty hack: even if the map positions are equal, the corresponding scenario positions might differ
//    		if (node[nodeIndex].getLastWaypoint().time == t && (dist == 0.0 || old.toString().equals(p.toString())))
//    			return;
//    	}

    	Position posScenario = mapBBox.lonLatToScenario(p);
    	if (clippingMethod != CLIP_RESIZE_BB) {
    		assert(mapBBox.containsScenarioPos(posScenario));
    	}

    	if (!parameterData.nodes[nodeIndex].add(t, posScenario)) {
    		Position old = parameterData.nodes[nodeIndex].getLastWaypoint().pos;
    		System.out.println("Error: Adding waypoint "+ posScenario.toString() + " failed (there was already a waypoint " + old.toString() + " at time " + t +"; dist = " + posScenario.distance(old) + ")!");
    		System.exit(0);
    	} else {
    		if (wpGeo.get(nodeIndex).isEmpty() || wpGeo.get(nodeIndex).lastElement().time < t) {
        		// also add lon/lat position
        		wpGeo.get(nodeIndex).add(new WaypointGeo(t, p));

        		// keep track for simulation area resize
        		if (clippingMethod == CLIP_RESIZE_BB && t <= parameterData.duration) {
        			updateMinMaxPos(posScenario);
        		}
    		} else if (wpGeo.get(nodeIndex).lastElement().time == t) {
    			assert(wpGeo.get(nodeIndex).lastElement().pos.equals(p));
    		} else {
    			System.out.println("Error: Adding waypoint "+ p.toString() + " in the past!");
        		System.exit(0);
    		}
    	}
    }
    
    protected PositionGeo randomNextGeoPosition()
    {
    	PositionGeo result = null;
    	
    	if (osmGraph != null) {
    		result = osmGraph.getRandomPosition(this.randomNextDouble());
//    		System.out.println(result.toString());
    	} else {
        	try {
        		result = randomNextGeoPosition(this.mapBBox);
        	} catch(RandomPositionFailedException e) {
                System.err.println(e.toString());
                e.printStackTrace();
                System.out.println("Error while generating a valid random position within the bounding box! Does it contain any road network?");
            	System.exit(-1);
        	}
    	}
    	
    	assert mapBBox.contains(result) : result.toString();
    	assert(result != null);
    	return result;
    }
	
    protected PositionGeo randomNextGeoPosition(BoundingBox bb) throws RandomPositionFailedException
    {
    	PositionGeo result = null;
    	
    	PositionGeo ll = bb.ll();
    	PositionGeo ur = bb.ur();
    	
    	try {
            PositionGeo p = new PositionGeo(ll.x() + randomNextDouble() * (ur.x() - ll.x()), ll.y() + randomNextDouble() * (ur.y() - ll.y()));
            
            // getNearestPosition() might return a position which is out of bounds
            for (int i = 1; i <= RND_RETRY_COUNT; i++) {
            	result = rsInstance.getNearestPosition(p);
//            	if (DEBUG) System.out.println(p.toString() + " -> " + result.toString());
            	
            	if (bb.contains(result)) { // position OK
            		break;
            	} else if (i < RND_RETRY_COUNT) { // retry
            		p = new PositionGeo(ll.x() + randomNextDouble() * (ur.x() - ll.x()), ll.y() + randomNextDouble() * (ur.y() - ll.y()));
            	} else { // no valid position after RND_RETRY_COUNT retries
            		throw new RandomPositionFailedException("No valid random position in bounding box found after " + (RND_RETRY_COUNT+1) + " tries!");
            	}
            }
    	} catch(Exception e) {
            if (e instanceof RandomPositionFailedException) {
            	throw (RandomPositionFailedException)e;
            } else {
                System.err.println(e.toString());
                e.printStackTrace();
            	System.exit(-1);
            }
    	}

        return result;
    }
    
    protected void updateMinMaxPos(Position posScenario)
    {
		if (posScenario.x > positionMax.x) {
			positionMax.x = posScenario.x;
		}
		if (posScenario.y > positionMax.y) {
			positionMax.y = posScenario.y;
		}
		if (posScenario.x < positionMin.x) {
			positionMin.x = posScenario.x;
		}
		if (posScenario.y < positionMin.y) {
			positionMin.y = posScenario.y;
		}
    }
    
    private void processPBFFile()
    {
    	System.out.println("Parsing PBF file \"" + osmFile + "\"...");
    	
        InputStream input = null;
        BlockInputStream bis = null;
        OSMGraph completeGraph = new OSMGraph();
		try {
			input = new FileInputStream(osmFile);
	        BlockReaderAdapter brad = new PBFParser(completeGraph, null);
	        bis = new BlockInputStream(input, brad);
	        bis.process();
	        bis.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		osmGraph = completeGraph.createSubGraph(mapBBox);
        System.out.println("Total road network length: " + osmGraph.roadNetworkLength());
    	System.out.println("done");
    }
    
    /**
     * 
     * @return
     * @pre wg1 and wg2 are subsequent waypoints for the same node
     * @pre wg1.time <= time <= wg2.time
     */
    protected WaypointGeo wpGeoAt(WaypointGeo wg1, WaypointGeo wg2, double time)
    {
    	WaypointGeo result = null;
    	
    	if (wg1.time <= time && time <= wg2.time) {
    		double alpha = (time - wg1.time) / (wg2.time - wg1.time);
    		result = new WaypointGeo(time, wg1.pos.getPhantomNode(wg2.pos, alpha));
    	}
    	
    	return result;
    }
    
    /**
     * @return PositionGeo of node idx at time time.
     */
    public PositionGeo posGeoAt_old(int idx, double time)
    {
    	PositionGeo p1 = null;
    	double t1 = 0.0;
    	for (int i = 0; i < wpGeo.get(idx).size(); i++) {
    		WaypointGeo w = wpGeo.get(idx).get(i);
    		
            if (w.time == time) {
                return w.pos;
            } else if (w.time > time) {
                if ((p1 == null) || p1.equals(w.pos)) {
                	return w.pos;
                } else {
                    double alpha = (time - t1) / (w.time - t1);
                    return p1.getPhantomNode(w.pos, alpha);
                }
            }
    		
    		p1 = w.pos;
    		t1 = w.time;
    	}
    	
    	return p1;
    }
    
    /**
     * @return PositionGeo of node idx at time time.
     */
    public PositionGeo posGeoAt(int idx, double time)
    {
        int begin = 0;
        int end = wpGeo.get(idx).size() - 1;

        if (end < 2) { // there are up to two waypoints, then call the linear search
            return posGeoAt_old(idx, time);
        }

        // check initial conditions: out of range [begin,end]
        WaypointGeo firstWaypoint = wpGeo.get(idx).firstElement();
        WaypointGeo lastWaypoint = wpGeo.get(idx).lastElement();
        
        if (time < firstWaypoint.time) {
            return firstWaypoint.pos;
        } else if (time > lastWaypoint.time) {
            return lastWaypoint.pos;
        } else {
            return binarySearch(idx, begin, end, time);
        }
    }

    private PositionGeo binarySearch(int idx, int i, int j, double time)
    {
        int median = (i + j) / 2;
        WaypointGeo w = wpGeo.get(idx).elementAt(median);
        if (i + 1 == j) { // waypoint not found in waypoint list
            WaypointGeo w_i = wpGeo.get(idx).elementAt(i);
            WaypointGeo w_j = wpGeo.get(idx).elementAt(j);

            double weight = (time - w_i.time) / (w_j.time - w_i.time);
            
            // if positions of surrounding waypoints are equal => no movement at time
            // just return position
            if (w_i.pos.equals(w_j.pos)) {
                return new PositionGeo(w_i.pos.x(), w_i.pos.y());
            }
            
            return w_i.pos.getPhantomNode(w_j.pos, weight);
        } else {
            if (time == w.time) { // waypoint found
                return w.pos;
            } else if (time < w.time) { // left recursion
                return binarySearch(idx, i, median, time);
            } else { // right recursion
                return binarySearch(idx, median, j, time);
            }
        }
    }
    
    /** @return Array with times when node idx changes speed or direction. */
    public double[] changeTimes(int idx)
    {
        double[] result = new double[wpGeo.get(idx).size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = wpGeo.get(idx).elementAt(i).time;
        }
        
        return result;
    }
	
    protected void preGeneration()
    {
        super.preGeneration();
        
        if (clippingMethod >= CLIP_MAX || clippingMethod < 0)
        {
            System.out.println("Error: Invalid clipping method!");
            System.exit(0);
        }
        
        if (transformation == null) {
        	transformation = new CoordinateTransformation(null, proj4lib.PROJ4J);
        }
        
        if (DEBUG) System.out.println("DEBUG: transformation (proj4): " + transformation.getProj4Description());
        
        mapBBox.setTransformation(transformation);
        Point2D.Double areaSize = mapBBox.getProjectedSize();
        parameterData.x = areaSize.x;
        parameterData.y = areaSize.y;
        if (clippingMethod == CLIP_RESIZE_BB) {
        	positionMin = new Point2D.Double(0.0, 0.0);
        	positionMax = new Point2D.Double(parameterData.x, parameterData.y);
        }
        
        if (DEBUG) System.out.println("DEBUG: scenario area: " + parameterData.x + ", " + parameterData.y);
        
        wpGeo = new Vector<Vector<WaypointGeo>>(parameterData.nodes.length);
        
        rsInstance = new OSRoutingMachine(rsURL, distFile);
        
        if (osmFile != null) {
        	processPBFFile();
        }
    }
    
    protected void postGeneration()
    {
    	// calculate position shift if necessary
    	double shiftX = (clippingMethod == CLIP_RESIZE_BB && positionMin.x < 0) ? -positionMin.x : 0;
    	double shiftY = (clippingMethod == CLIP_RESIZE_BB && positionMin.y < 0) ? -positionMin.y : 0;
    	boolean shiftNecessary = shiftX > 0 || shiftY > 0;
    	
        for (int i = 0; i < parameterData.nodes.length; i++) {
            // remove waypoints exceeding duration
            Waypoint l = parameterData.nodes[i].getLastWaypoint();
            Position borderPos = null;
            WaypointGeo borderWPG = null;
            while (l.time > parameterData.duration) {
            	if (parameterData.nodes[i].getWaypoint(parameterData.nodes[i].getNumWaypoints()-2).time <= parameterData.duration) {
            		borderPos = parameterData.nodes[i].positionAt(parameterData.duration);
            		borderWPG = wpGeoAt(wpGeo.get(i).get(wpGeo.get(i).size()-2), wpGeo.get(i).lastElement(), parameterData.duration);
            	}
            	parameterData.nodes[i].removeLastElement();
                wpGeo.get(i).remove(wpGeo.get(i).lastElement());
                l = parameterData.nodes[i].getLastWaypoint();
            }
            
            if (l.time < parameterData.duration) { // add last waypoint
            	if (borderPos != null) {
            		parameterData.nodes[i].add(parameterData.duration, borderPos);
            	}
            	if (borderWPG != null) {
            		wpGeo.get(i).add(borderWPG);
            	}
            }
            
            // remove geo waypoints in ignore phase
            if (parameterData.ignore > 0.0 && parameterData.ignore <= parameterData.duration) {
            	WaypointGeo f = wpGeo.get(i).firstElement();
            	borderWPG = null;
                while (f.time < parameterData.ignore) {
                	if (wpGeo.get(i).get(1).time >= parameterData.ignore) {
                		borderWPG = wpGeoAt(f, wpGeo.get(i).get(1), parameterData.ignore);
                	}
                    wpGeo.get(i).remove(f);
                    f = wpGeo.get(i).firstElement();
                }
                
                if (f.time > parameterData.ignore) { // add first waypoint
                	if (borderWPG != null) {
                		wpGeo.get(i).add(0, borderWPG);
                	}
                }
                
                // subtract offset from timestamps
                Vector<WaypointGeo> wpgn = new Vector<WaypointGeo>();
                for (WaypointGeo wp : wpGeo.get(i)) {
                	wpgn.add(new WaypointGeo(wp.time - parameterData.ignore, wp.pos));
                }
                wpGeo.get(i).clear();
                wpGeo.get(i).addAll(wpgn);
            }
            
            if (clippingMethod == CLIP_RESIZE_BB && shiftNecessary) { // shift all waypoint positions
            	parameterData.nodes[i].shiftPos(shiftX, shiftY);
            }
        }
        
        if (clippingMethod == CLIP_RESIZE_BB && shiftNecessary) { // resize simulation area
        	parameterData.x = positionMax.x - positionMin.x;
        	parameterData.y = positionMax.y - positionMin.y;
        	
        	if (DEBUG) System.out.println("DEBUG: Resizing simulation area...\n\tpositionMin: " + positionMin.toString() + "\n\tpositionMax: " + positionMax.toString() + "\n\tnew size: "+ parameterData.x + "x" + parameterData.y);
        	if (DEBUG) System.out.println("DEBUG: Positions shifted by (" + shiftX + ", " + shiftY + ")");
        }
        
        // close writer for evaluation
        if (rsInstance instanceof OSRoutingMachine && ((OSRoutingMachine) rsInstance).distWriter != null) {
        	((OSRoutingMachine) rsInstance).distWriter.close();
        }

        super.postGeneration();
    }
    
	protected String read(String basename) throws FileNotFoundException, IOException
	{
		paramFromFile(basename+".params");
		
		wpGeo = new Vector<Vector<WaypointGeo>>(parameterData.nodes.length);

		// read movements
		// 0.0 [139.68717088631996,35.68943748150533], 4027.553786216944 [139.68717088631996,35.68943748150533],
		int i = 0;
		String line;
		BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(basename + ".movements.geo.gz"))));
		while ((line = in.readLine()) != null) {
		    //comment prefix
            if (line.startsWith("#")) {
                continue;
            }
            
            wpGeo.add(new Vector<WaypointGeo>());

			StringTokenizer st = new StringTokenizer(line, " [,]");
			while (st.hasMoreTokens()) {
				double time = Double.parseDouble(st.nextToken());
				PositionGeo pos = new PositionGeo(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()));
				WaypointGeo wpg = new WaypointGeo(time, pos);
				wpGeo.get(i).add(wpg);
			}
			i++;
		}
		
		in.close();

		return null;
	}
    
	public void write(String basename, String[] params) throws FileNotFoundException, IOException
	{
		String[] p = new String[6];
        p[0] = "boundingBox=" + mapBBox.ll().x() + " " + mapBBox.ll().y() + " " + mapBBox.ur().x() + " " + mapBBox.ur().y();
        p[1] = "clippingMethod=" + clippingMethod;
        p[2] = "rsMetric=" + rsMetric;
        p[3] = "osmFile=" + osmFile;
        p[4] = "rsURL=" + rsURL;
        p[5] = "distFile=" + distFile;
		super.writeParametersAndMovement(basename, App.stringArrayConcat(p, params));
		
        // write to movements.geo file
    	PrintWriter movementsGeo = new PrintWriter(new GZIPOutputStream(new FileOutputStream(basename + ".movements.geo.gz")));
    	
    	for (int i = 0; i < parameterData.nodes.length; i++) {
    		for (int j = 0; j < wpGeo.get(i).size(); j++) {
    			movementsGeo.print(wpGeo.get(i).get(j).getMovementStringPart());
    			movementsGeo.print(j < wpGeo.get(i).size()-1 ? " " : "\n");
    		}
    	}
    	
    	osmGraph.printToFile(basename+".road_network.dat");
    	
		movementsGeo.close();
	}
	
	public void paramFromFile(String _fn) throws FileNotFoundException, IOException
	{
		String line;
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(_fn)));
		while ((line = in.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line, "=");
			String key = st.nextToken();
			String value = st.nextToken();
			
			parseArg(key, value);
		}
		in.close();
	}
	
    protected boolean parseArg(String key, String value)
    {
    	String[] v = null;
    	
        if (key.equals("boundingBox"))
        {
        	v = value.split(" ");
            double left = Double.parseDouble(v[0]);
            double bottom = Double.parseDouble(v[1]);
            double right = Double.parseDouble(v[2]);
            double top = Double.parseDouble(v[3]);
            
            mapBBox = new BoundingBox(left, bottom, right, top);
            return true;
        } else if (key.equals("clippingMethod")) {
        	clippingMethod = Integer.parseInt(value);
        	return true;
        } else if (key.equals("rsMetric")) {
        	if (!value.equals("Car") && !value.equals("Pedestrian")) {
                System.out.println("Warning: rsMetric must be either \"Car\" or \"Pedestrian\"... using default: \""+rsMetric+"\"");
            } else {
            	rsMetric = value;
            }
        	return true;
        } else if (key.equals("osmFile")) {
            osmFile = value;
        	return true;
        } else if (key.equals("rsURL")) {
            rsURL = value;
        	return true;
        } else if (key.equals("distFile")) {
            distFile = value;
        	return true;
        } else {
            return super.parseArg(key, value);
        }
    }

    protected boolean parseArg(char key, String value)
    {
    	String[] v = null;
    	
        switch (key)
        {
        	case 'B': // "map bounding box"
                v = value.split(" ");
                double left = Double.parseDouble(v[0]);
                double bottom = Double.parseDouble(v[1]);
                double right = Double.parseDouble(v[2]);
                double top = Double.parseDouble(v[3]);
                
                mapBBox = new BoundingBox(left, bottom, right, top);

                if (DEBUG) System.out.println("DEBUG: bbox = (" + left + ", " + bottom + ", " + right + ", " + top + ")");
        		return true;
        	case 'C': // "clipping method"
        		clippingMethod = Integer.parseInt(value);
        		if (DEBUG) System.out.println("DEBUG: clippingMethod = " + clippingMethod);
        		return true;
            case 'm': // "route service metric"
                if (!value.equals("Car") && !value.equals("Pedestrian")) {
                    System.out.println("Warning: rsMetric must be either \"Car\" or \"Pedestrian\"... using default: \""+rsMetric+"\"");
                } else {
                	rsMetric = value;
                }
                return true;
            case 'o': // "OSM input file"
                osmFile = value;
                return true;
            case 'u': // "route service URL"
                rsURL = value;
                return true;
            case 'X': // "distance eval filename"
                distFile = value;
                return true;
            default:
                return super.parseArg(key, value);
        }
    }
	
	public static void printHelp()
	{
		Scenario.printHelp();
		System.out.println("MapScenario:");
        System.out.println("\t-B <left> <bottom> <right> <top>");
        System.out.println("\t-C <clipping method>");
        System.out.println("\t-m <route service metric string>");
        System.out.println("\t-o <OSM (.pbf) input file>");
        System.out.println("\t-u <route service URL>");
        System.out.println("\t-X <distance eval filename>");
	}
	
	public void printMemUsage()
	{
		int mb = 1024*1024;
        
        Runtime runtime = Runtime.getRuntime();
        System.out.println("##### Heap utilization statistics [MB] #####");
        System.out.println("Used Memory:\t" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
        System.out.println("Free Memory:\t" + runtime.freeMemory() / mb);
        System.out.println("Total Memory:\t" + runtime.totalMemory() / mb);
        System.out.println("Max Memory:\t" + runtime.maxMemory() / mb);
	}
}
