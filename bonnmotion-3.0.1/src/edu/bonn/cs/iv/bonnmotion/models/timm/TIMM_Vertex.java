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

public class TIMM_Vertex {
    private double firstVisit = Double.MAX_VALUE;
    private Position position;
    private final String identification;
    private List<TIMM_Vertex> neighbors;
    private TIMM_VertexType vType;
    private final TIMM_Settings settings;
    private final TIMM timm;

    public TIMM_Vertex(String _identification, TIMM_Settings _settings, TIMM _timm) {
        this.identification = _identification;   
        this.settings = _settings;
        this.timm = _timm;
    }
    
    public TIMM_Vertex(Position _vertexPosition, String _identification, List<TIMM_Vertex> neighbors, TIMM_VertexType type,
            TIMM_Settings _settings, TIMM _timm) {
        this.position = _vertexPosition;
        this.identification = _identification;
        this.neighbors = (neighbors == null) ? new ArrayList<TIMM_Vertex>() : neighbors;
        this.vType = type;
        if (vType == null) {
            throw new RuntimeException("Error: TIMM_Vertex. TIMM_VertexType cannot be null");
        }
        this.settings = _settings;
        this.timm = _timm;
    }

    public List<TIMM_Vertex> getNeighbors() {
        return neighbors;
    }

    public String getIdentification() {
        return identification;
    }

    public Position getPosition() {
        return position;
    }

    /**
     * Method returns if this vertex is already visited at a given time.
     * @param time
     * @return
     *          true if already visited
     */
    public boolean isVisited(double time) {
        return (time >= firstVisit) ? true : false;
    }

    public double getFirstVisit() {
        return firstVisit;
    }

    /**
     * This method gets the time a node can be at the vertex or in front of the door.
     * If there is time needed to open a door this time is added to the
     * arrival.
     * 
     * @param time
     *              time a node can be at the vertex or in front of the door.
     * @return
     *              the time the node will actually be at the vertex (if the vertex is
     *              a door it is open now)
     */
    public double setNodeReachsVertex(double time) {
        if (time < firstVisit) {
            firstVisit = time;
        }

        if (vType == TIMM_VertexType.DOOR) {
            final double opendoor = this.openingDoor();

            if (TIMM.DEBUG) {
                System.out.println(String.format("Node reaches door and waites until %f", firstVisit + opendoor));
            }
            return firstVisit + opendoor;
        }
        else {
            return firstVisit;
        }
    }

    public void setIsVisited(double time) {
        firstVisit = time;
    }

    private double openingDoor() {
        return (settings.getDoorOpeningAndSecuring() + Math.pow(-1, timm.randomNextInt(2)) * timm.randomNextDouble()
                * Math.sqrt(settings.getDoorOpeningAndSecuringVariance()));
    }
    
    public void overwriteFields(TIMM_Vertex v) {
        this.position = v.position;
        this.neighbors = v.neighbors;
        this.vType = v.vType;
    }
    
    public String toString() {
        return identification;
    }
}