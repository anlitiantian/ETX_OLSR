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

import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.models.TIMM;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TIMM_Graph {
    private final Vector<TIMM_Vertex> vertices;
    private int additionalVertices = 0;
    private final TIMM_Settings settings;
    private final TIMM timm;

    public TIMM_Graph(TIMM_Settings _settings, TIMM _timm) {
        this.vertices = new Vector<TIMM_Vertex>(0, 1);
        this.settings = _settings;
        this.timm = _timm;
        readGraph();
    }

    /**
     * Read the graph representing the scenario
     * 
     */
    private void readGraph() {
        boolean startvertexprovided = false;
        try {
            BufferedReader in = new BufferedReader(new FileReader(settings.getPathToGraph()));
            String line = null;

            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue; /* Ignore comments */
                }

                if (line.startsWith("Pos=")) { /* Position */
                    startvertexprovided = parseGraphline(startvertexprovided, line);
                }
            }
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }

        if (!startvertexprovided)
            throw new RuntimeException("Error in buildinggraph. StartVertex not defined");

        while (addAdditionalVertices());
    }

    /**
     * Parse one line of a building graph file and add vertices. Format:
     * Pos=NodeID,x,y,Neighbor1;Neighbor2;...;NeighborN,VertexType
     * 
     * @param startvertexprovided
     *            is there already a StartVertex provided?
     * @param line
     *            the line from the building graph file
     * @return true if there is already a StartVertex provided
     */
    private boolean parseGraphline(boolean startvertexprovided, String line) {
        String[] p = line.split("=");
        p = p[1].split(",");

        if (p.length != 5)
            throw new RuntimeException("Error in buildinggraph. Line: " + line);

        String id = p[0];
        final double x = Double.parseDouble(p[1]);
        final double y = Double.parseDouble(p[2]);
        String neighbors = p[3];
        TIMM_VertexType vType = null;

        p[4] = p[4].toUpperCase();
        
        if (p[4].equals("DOOR")) {
            vType = TIMM_VertexType.DOOR;
        }
        else if (p[4].equals("ROOM")) {
            vType = TIMM_VertexType.ROOM;
        }
        else if (p[4].equals("STRUCT")) {
            vType = TIMM_VertexType.STRUCT;
        }

        p = neighbors.split(";");

        List<TIMM_Vertex> neighbor_list = new ArrayList<TIMM_Vertex>();
        TIMM_Vertex v;
        
        for (int i = 0; i < p.length; i++) {
            v = this.tryGetVertexByIdentification(p[i]);
            
            if (v == null) {
                v = new TIMM_Vertex(p[i], settings, timm);
                addVertex(v);
            }
            
            neighbor_list.add(v);
        }

        if (id.equals("StartVertex")) {
            if (startvertexprovided) {
                throw new RuntimeException("Error in buildinggraph. There can only be one StartVertex.");
            }
            else {
                startvertexprovided = true;
            }
            
            addVertex(new TIMM_Vertex(new Position(x, y), id, neighbor_list, vType, settings, timm), true);
        }
        else {
            addVertex(new TIMM_Vertex(new Position(x, y), id, neighbor_list, vType, settings, timm));
        }
        
        return startvertexprovided;
    }

    /**
     * This method returns all non visited neighbor vertices of one vertex.
     * 
     * @param _vertex
     * @param _time
     * @return
     *                  all non visited neighbor vertices
     */
    public Vector<TIMM_Vertex> getNonvisitedNeighborVerticesOfVertex(final TIMM_Vertex _vertex, final double _time) {
        Vector<TIMM_Vertex> unvisitedNeighbors = new Vector<TIMM_Vertex>(0, 1);

        for (TIMM_Vertex neighbor : _vertex.getNeighbors()) {
            if (!neighbor.isVisited(_time)) {
                unvisitedNeighbors.add(neighbor);
            }
        }

        return unvisitedNeighbors;
    }

    /**
     * Calculates the nearest next unvisited vertex from a given start vertex.
     * 
     * @param _vertex
     *                  start vertex
     * @param _time
     * @return
     *                  the nearest next unvisited vertex
     */
    public TIMM_Vertex getNextNonvisitedNeighborOfVertex(final TIMM_Vertex _vertex, double _time) {
        double distance = Double.MAX_VALUE;
        TIMM_Vertex result = null;
        for (TIMM_Vertex e : vertices) {
            if (!e.isVisited(_time)) {
                final double d = this.routeLength(_vertex, e);
                if (d < distance) {
                    distance = d;
                    result = e;
                }
            }
        }

        return result;
    }

    /**
     * Algorithm to find a route from _src to _dst.
     * 
     * @param _src
     *            Route starting point
     * @param _dst
     *            Route destination
     * @return route information
     * 
     * TODO: why is _src not used?
     */
    private Vector<TIMM_Dijkstra_Information> findRoute(final TIMM_Vertex _src, final TIMM_Vertex _dst) {
        Vector<TIMM_Dijkstra_Information> dv = new Vector<TIMM_Dijkstra_Information>(0, 1);
        for (TIMM_Vertex e : vertices) {
            dv.add(new TIMM_Dijkstra_Information(e));
        }

        for (TIMM_Dijkstra_Information v : dv) {
            if (v.getVertex().equals(_dst)) {
                v.setStatus(TIMM_StatusType.ACTIVE);
                v.setDistance(0);
                break;
            }
        }

        boolean notReady = true;
        while (notReady) {
            for (TIMM_Dijkstra_Information v : dv) {
                if (v.getStatus() != TIMM_StatusType.ACTIVE) {
                    continue;
                }

                v.setStatus(TIMM_StatusType.FINISHED);
                Position p = v.getPosition();
                for (TIMM_Vertex neighbor : v.getNeighbor()) {
                    for (TIMM_Dijkstra_Information cmp : dv) {
                        if (!cmp.getVertex().equals(neighbor)) {
                            continue;
                        }

                        double distance = p.distance(cmp.getPosition()) + v.getDistance();
                        if (cmp.getDistance() > distance) {
                            cmp.setDistance(distance);
                            cmp.setStatus(TIMM_StatusType.TEMP);
                            cmp.setPredecessor(v.getVertex());
                        }
                        break;
                    }
                }
            }

            TIMM_Dijkstra_Information t = null;
            double distance = Double.MAX_VALUE;
            for (TIMM_Dijkstra_Information v : dv) {
                if (v.getStatus() == TIMM_StatusType.TEMP && v.getDistance() < distance) {
                    distance = v.getDistance();
                    if (t != null) {
                        t.setStatus(TIMM_StatusType.TEMP);
                    }
                    v.setStatus(TIMM_StatusType.ACTIVE);
                    t = v;
                }
            }

            // Check if algorithm finished
            notReady = false;
            for (TIMM_Dijkstra_Information v : dv) {
                if (v.getStatus() == TIMM_StatusType.ACTIVE) {
                    notReady = true;
                    break;
                }
            }
        }

        return dv;
    }

    /**
     * This method calculates the route between two vertices.
     * 
     * @param src
     *              source vertex
     * @param dst
     *              destination vertex
     * @return
     *              an ArrayList with the routes vertices in it.
     */
    public ArrayList<TIMM_Vertex> calculateRoute(final TIMM_Vertex src, final TIMM_Vertex dst) {
        ArrayList<TIMM_Vertex> result = new ArrayList<TIMM_Vertex>();
        final Vector<TIMM_Dijkstra_Information> dv = findRoute(src, dst);
        TIMM_Vertex vertex = src;
        
        while (!vertex.equals(dst)) {
            result.add(vertex);
            for (TIMM_Dijkstra_Information v : dv) {
                if (v.getVertex().equals(vertex)) {
                    vertex = v.getPredecessor();
                    break;
                }
            }
        }
        result.add(dst);

        return result;
    }

    /**
     * This method calculates the distance between two vertices.
     * 
     * @param src
     *              source vertex
     * @param dst
     *              destination vertex
     * @return
     *              the distance (or -1 if no route exists)
     */
    public double routeLength(final TIMM_Vertex src, final TIMM_Vertex dst) {
        Vector<TIMM_Dijkstra_Information> dv = findRoute(src, dst);

        for (TIMM_Dijkstra_Information e : dv) {
            if (e.getVertex().equals(src)) {
                return e.getDistance();
            }
        }
        return -1;
    }
    
    private void addVertex(TIMM_Vertex e) {
        addVertex(e, false);
    }

    /**
     * This method tries to add a vertex to the graph.
     * First it looks up if there is already a vertex by the given name.
     * If thats true then the existing one is used but the fields 
     * are overwritten with v1's values.
     * 
     * @param vertex1 
     *              the vertex you want to add
     * @param _isVisited
     *              is the vertex already visited (StartVertex)
     */
    private void addVertex(TIMM_Vertex vertex1, boolean _isVisited) {
        TIMM_Vertex vertex2 = tryGetVertexByIdentification(vertex1.getIdentification());
        
        if (vertex2 == null) {
            if (_isVisited) {
                vertex1.setIsVisited(-1);
            }
            vertices.add(vertex1);
        } else {
            vertex2.overwriteFields(vertex1);
            if (_isVisited) {
                vertex2.setIsVisited(-1);
            }
        }
    }

    /**
     * This function adds an additional vertex to the graph if the distance between two vertices is
     * bigger than _maxVertexdistance. As long as true is returned, it may be needed to recall this
     * function to shorten all distances according to the limit.
     * 
     * @return true if another vertex was added (maybe further calls of this function required),
     *         false if all distances are short enough
     */
    private boolean addAdditionalVertices() {
        for (TIMM_Vertex e : vertices) {
            for (TIMM_Vertex f : e.getNeighbors()) {
                if (e.getPosition().distance(f.getPosition()) > settings.getMaxDistanceBetweenVerticesInTheBuildingGraph()) {
                    double x = (e.getPosition().x + f.getPosition().x) / 2;
                    double y = (e.getPosition().y + f.getPosition().y) / 2;
                    
                    List<TIMM_Vertex> neighbors = new ArrayList<TIMM_Vertex>();
                    neighbors.add(e);
                    neighbors.add(f);
                    
                    TIMM_Vertex v_new = new TIMM_Vertex(new Position(x, y), "additionalVertex" + this.additionalVertices, neighbors, TIMM_VertexType.DISTANCE, settings, timm);
                    addVertex(v_new);

                    neighbors = e.getNeighbors();
                    neighbors.remove(f);
                    neighbors.add(v_new);

                    neighbors = f.getNeighbors();
                    neighbors.remove(e);
                    neighbors.add(v_new);

                    this.additionalVertices++;
                    return true;
                }
            }
        }

        return false;
    }
    
    /**
     * This method tries to find a vertex by its name. 
     * Throws an exception if it is not found.
     * 
     * @param _identification
     * @return the found vertex
     */
    public TIMM_Vertex getVertexByIdentification(final String _identification) {
        for (TIMM_Vertex e : vertices) {
            if (e.getIdentification().equals(_identification)) {
                return e;
            }
        }

        throw new RuntimeException("Error. Didn't found vertex by identification.");
    }
    
    /**
     * This method tries to find a vertex by its name. 
     * Returns null if it is not found.
     * 
     * @param _identification
     * @return the found vertex or null
     */
    private TIMM_Vertex tryGetVertexByIdentification(final String _identification) {
        for (TIMM_Vertex e : vertices) {
            if (e.getIdentification().equals(_identification)) {
                return e;
            }
        }

        return null;
    }

    /**
     * Nice representation of the graph as string
     * 
     * @return Graph as multiline string
     */
    public String toString() {
        StringBuilder sb = new StringBuilder("#Graph\n");

        double _lastVertexVisitedAt = Double.MIN_VALUE;
        for (TIMM_Vertex e : vertices) {
            if (_lastVertexVisitedAt < e.getFirstVisit()) {
                _lastVertexVisitedAt = e.getFirstVisit();
            }
            sb.append("#Graph " + e.getIdentification() + " First Visit: " + e.getFirstVisit() + " Neighbors: ");

            for (TIMM_Vertex s : e.getNeighbors()) {
                sb.append(s.getIdentification() + " ");
            }
            sb.append("\n");
        }
        sb.append("#Done: " + _lastVertexVisitedAt + "\n");

        return sb.toString();
    }
}
