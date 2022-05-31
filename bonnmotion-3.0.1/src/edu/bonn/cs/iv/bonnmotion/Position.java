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

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import edu.bonn.cs.iv.bonnmotion.models.slaw.PositionInterface;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

public class Position implements PositionInterface{
	public double x;
	public double y;
	public double z;
	//borderentry -> "2";  (node OFF / leaves scenario)
	//borderexit  -> "1";  (node ON  / arrives in scenario)
	//not on border, not status change -> "0";
	public final double status;
	
	public Position() {
		this(0.0, 0.0, 0.0, 0.0);
	}
	
	public Position(Position p) {
		this(p.x,p.y,p.z);
	}
	
	public Position(double x, double y) {
		this(x ,y, 0.0, 0.0);
	}
	
	public Position(final double x, final double y, final double z) {
		this(x, y, z, 0.0);
	}
	
	public Position(final double x, final double y, final double z, final double status) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.status = status;
	}

	
	public double distance(Position _p) {
	    Position p = _p;
        double deltaX = p.x - x;
        double deltaY = p.y - y;
        double deltaZ = p.z - z;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
	}

	@Override
	public double distance(PositionInterface p) {
		assert(p instanceof Position);
		return distance((Position)p);
	}

	public Position rndprox(double maxdist, double _dist, double _dir, Dimension dim) {
		double dist = _dist * maxdist;
		double dir = _dir * 2 * Math.PI;
		final double newZ;
		if (dim == Dimension.THREED){
			newZ = z + (Math.cos(dir) + Math.sin(dir) - 1) * dist;
		}else{
			newZ = 0.0;
		}
		
		return new Position(x + Math.cos(dir) * dist, y + Math.sin(dir) * dist, newZ, status);
	}
	
	public double norm() {
		return Math.sqrt(x*x + y*y + z*z);
	}
	
	
	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
	
	public String toString(int precision) {
		int mult = 1;
		for (int i = 0; i < precision; i++)
			mult *= 10;
		return "(" + ((double) ((int) (this.x * mult + 0.5)) / mult) + ", " + 
		((double) ((int) (this.y * mult + 0.5)) / mult) + ", " +
		((double) ((int) (this.z * mult + 0.5)) / mult) + ")";
	}
	
	public boolean equals(Position _p) {        
        Position p = _p;
		return ((p.x == x) && (p.y == y) && (p.z == z));
	}
	
	
	/** Difference between q and p ("how to reach q from p"). */
	public static Position diff(Position p, Position q) {
		return new Position(q.x - p.x, q.y - p.y, q.z - p.z);
	}
	
	public static double scalarProduct(Position p, Position q) {
		return p.x * q.x + p.y * q.y + p.z * q.z;
	}

	public static double slope(Line2D.Double line){
		if((line.x2 - line.x1) == 0){
			return Double.MAX_VALUE;
		}
		double slope = (line.y2 - line.y1) / (line.x2 - line.x1);
		return slope;
	}
	
    
    public Position getWeightenedPosition(Position _w, double weight) {
        
        Position w = _w;
        return new Position(
                this.x * (1 - weight) + w.x * weight,
                this.y * (1 - weight) + w.y * weight,
                this.z * (1 - weight) + w.z * weight,
                this.status);
    }

    
    public Position newShiftedPosition(double _x, double _y, double _z) {
        return new Position(this.x + _x, this.y + _y, this.z + _z);
    }
    
    public Position newShiftedPosition(double _x, double _y) {
		return new Position(this.x + _x, this.y + _y);
	}
    
    
    
    public Position clone(double status) {
        return new Position(this.x, this.y, this.z, status);
    }
	
	/** Calculate angle between two vectors, their order being irrelevant.
	 * 	@return "Inner" angle between 0 and Pi. */
    public double angle(Position p) {
    	return angle(p,this);
    }
    
	public static double angle(Position p, Position q) {
		return Math.acos(scalarProduct(p, q) / (p.norm() * q.norm()));
	}
	
	/** Calculate angle, counter-clockwise from the first to the second vector.
	 * 	@return Angle between 0 and 2*Pi. */
	public static double angle2(Position _p, Position _q) {
        Position p = _p;
        Position q = _q;
		double a = angle(p, q);
		double o = angle(new Position(-p.y, p.x, p.z), q);
		if (o > Math.PI / 2)
			a = 2 * Math.PI - a;
		return a;
	}
	
	public Position add(Position p) {
		x += p.x;
		y += p.y;
		z += p.z;
		
		return this;
	}
	
	public static Position add(Position p, Position q) {
		return new Position(p).add(q);
	}
	
	public Position subtract(Position p) {
		x -= p.x;
		y -= p.y;
		z -= p.z;
		
		return this;
	}
	
	public static Position subtract(Position p, Position q) {
		return new Position(p).subtract(q);
	}
	
	public Position multiply(double m) {
		x *= m;
		y *= m;
		z *= m;
		
		return this;
	}
	
	public static Position multiply(Position p, double m) {
		return new Position(p).multiply(m);
	}
	
	
	/** Get a Point2D.Double representation of the current position. */
	public Point2D.Double getPoint2D() {
		return new Point2D.Double(this.x, this.y);
	}
}
