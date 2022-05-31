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

import java.util.List;

/**
 * This class is used to store the information from a dijkstra run
 */
public class TIMM_Dijkstra_Information {
    private final TIMM_Vertex vertex;
    private TIMM_StatusType status;
    private double distance;
    private TIMM_Vertex predecessor;

    public TIMM_Dijkstra_Information(TIMM_Vertex vertex) {
        this.vertex = vertex;
        this.status = TIMM_StatusType.NOT_ACTIVE;
        this.distance = Double.MAX_VALUE;
        this.predecessor = null;
    }
    
    public TIMM_Vertex getVertex() {
        return vertex;
    }

    public String getIdentification() {
        return vertex.getIdentification();
    }

    public TIMM_StatusType getStatus() {
        return status;
    }

    public Position getPosition() {
        return vertex.getPosition();
    }

    public List<TIMM_Vertex> getNeighbor() {
        return vertex.getNeighbors();
    }

    public double getDistance() {
        return distance;
    }

    public TIMM_Vertex getPredecessor() {
        return predecessor;
    }

    public void setStatus(TIMM_StatusType status) {
        this.status = status;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public void setPredecessor(TIMM_Vertex predecessor) {
        this.predecessor = predecessor;
    }
}
