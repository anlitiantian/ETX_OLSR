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

package edu.bonn.cs.iv.util.maps;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import edu.bonn.cs.iv.bonnmotion.Position;

/** Bounding Box for geographic coordinates
 *
 * @author schwamborn
 *
 */

public class BoundingBox
{
    protected PositionGeo ll = null;
    protected PositionGeo ur = null;
    protected Point2D.Double projectedOrigin = null;
    protected double width = 0; // in meters
    protected double height = 0; // in meters
    protected CoordinateTransformation transformation = null;

    public BoundingBox(double left, double bottom, double right, double top)
    {
        this.ll = new PositionGeo(left, bottom);
        this.ur = new PositionGeo(right, top);
    	
    	// assumption: ll.distance(right, bottom) = ur.distance(left, top)
    	//             ll.distance(left, top) = ur.distance(right, bottom)
    	
//        System.out.println("DEBUG: lower left position: " + ll.toString());
//        System.out.println("DEBUG: upper right position: = " + ur.toString());
//        System.out.println("DEBUG: ll.distance(right, bottom) = " + ll.distance(right, bottom));
//        System.out.println("DEBUG: ur.distance(left, top) = " + ur.distance(left, top));
//        System.out.println("DEBUG: ll.distance(left, top) = " + ll.distance(left, top));
//        System.out.println("DEBUG: ur.distance(right, bottom) = " + ur.distance(right, bottom));
    	
        this.width = ll.distance(right, bottom);
        this.height = ll.distance(left, top);
    }
    
    public PositionGeo ll()
    {
        return this.ll;
    }
    
    public PositionGeo ur()
    {
        return this.ur;
    }
    
    public Point2D.Double projectedOrigin()
    {
        return this.projectedOrigin;
    }

    public double width()
    {
        return this.width;
    }

    public double height()
    {
        return this.height;
    }
    
    public double widthDeg()
    {
        return this.ur.x() - this.ll.x();
    }

    public double heightDeg()
    {
    	return this.ur.y() - this.ll.y();
    }

    public CoordinateTransformation transformation()
    {
        return this.transformation;
    }
    
    public Point2D.Double getProjectedSize()
    {
    	assert(transformation != null);
    	
    	Point2D.Double result = new Point2D.Double();
    	Point2D.Double projectedUR = transformation.transform(ur.x(), ur.y());
    	result.x = projectedUR.x - projectedOrigin.x;
    	result.y = projectedUR.y - projectedOrigin.y;
    	
    	return result;
    }

    public void setTransformation(CoordinateTransformation transformation)
    {
        this.transformation = transformation;
        projectedOrigin = transformation.transform(ll.x(), ll.y());
    }

    public boolean contains(PositionGeo p)
    {
    	
        return p.x() >= ll.x() && p.x() <= ur.x() && p.y() >= ll.y() && p.y() <= ur.y();
    }
    
    public boolean contains(Route r)
    {
    	PositionGeo[] p = r.allPoints();
    	
    	for (int i = 0; i < p.length; i++) {
    		if (!this.contains(p[i])) {
    			return false;
    		}
    	}
    	
        return true;
    }
    
    public boolean containsClip(Route r)
    {
    	PositionGeo[] p = r.allClipPoints();
    	
    	for (int i = 0; i < p.length; i++) {
    		if (!this.contains(p[i])) {
    			return false;
    		}
    	}
    	
        return true;
    }
    
    public boolean containsScenarioPos(Position sp)
    {
    	Point2D.Double scenarioArea = getProjectedSize();
        return sp.x >= 0.0 && sp.x <= scenarioArea.x && sp.y >= 0.0 && sp.y <= scenarioArea.y;
    }
    
    // TODO: intersection w.r.t. _geodesic_ lines
	public PositionGeo calcBoundsIntersection(PositionGeo p1, PositionGeo p2)
	{
		assert(!contains(p1) || !contains(p2));
		
		Line2D.Double l = new Line2D.Double(p1.x(), p1.y(), p2.x(), p2.y());
		Line2D.Double lleft = new Line2D.Double(ll.x(), ll.y(), ll.x(), ur.y());
		Line2D.Double ltop = new Line2D.Double(ll.x(), ur.y(), ur.x(), ur.y());
		Line2D.Double lright = new Line2D.Double(ur.x(), ll.y(), ur.x(), ur.y());
		Line2D.Double lbottom = new Line2D.Double(ll.x(), ll.y(), ur.x(), ll.y());
		
		PositionGeo result = null;
		if ((result = getLineLineIntersection(l, lleft)) != null) {
			if (result.x() < ll.x()) { // account for rounding error
				try {
					result = result.shiftX(ll.x() - result.x());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return result;
		}
		if ((result = getLineLineIntersection(l, ltop)) != null) {
			if (result.y() > ur.y()) { // account for rounding error
				try {
					result = result.shiftY(ur.y() - result.y());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return result;
		}
		if ((result = getLineLineIntersection(l, lright)) != null) {
			if (result.x() > ur.x()) { // account for rounding error
				try {
					result = result.shiftX(ur.x() - result.x());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return result;
		}
		if ((result = getLineLineIntersection(l, lbottom)) != null) {
			if (result.y() < ll.y()) { // account for rounding error
				try {
					result = result.shiftY(ll.y() - result.y());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return result;
		}
		
		return result;
	}
	
	/*
	 * @see http://mathworld.wolfram.com/Line-LineIntersection.html
	 */
	static PositionGeo getLineLineIntersection(Line2D.Double l1, Line2D.Double l2)
	{
		if (!l1.intersectsLine(l2)) {
			return null;
		}
		
		PositionGeo result = null;
		
		double x1 = l1.getX1(), y1 = l1.getY1(),
			   x2 = l1.getX2(), y2 = l1.getY2(),
			   x3 = l2.getX1(), y3 = l2.getY1(),
			   x4 = l2.getX2(), y4 = l2.getY2();

		double x = det(det(x1, y1, x2, y2), x1 - x2,
					   det(x3, y3, x4, y4), x3 - x4) /
				   det(x1 - x2, y1 - y2, x3 - x4, y3 - y4);
		double y = det(det(x1, y1, x2, y2), y1 - y2,
					   det(x3, y3, x4, y4), y3 - y4) /
				   det(x1 - x2, y1 - y2, x3 - x4, y3 - y4);

		try{
			result = new PositionGeo(x, y);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
		
		return result;
	}
	
	static double det(double a, double b,
					  double c, double d)
	{
	    return a * d - b * c;
	}
    
    /** Get a Point2D.Double representation of the current bounding box. */
    public Point2D.Double[] getPoint2D()
    {
    	Point2D.Double[] bbp2d = new Point2D.Double[4];
    	bbp2d[0] = new Point2D.Double(ll.x(), ll.y());
    	bbp2d[1] = new Point2D.Double(ll.x(), ur.y());
    	bbp2d[2] = new Point2D.Double(ur.x(), ur.y());
    	bbp2d[3] = new Point2D.Double(ur.x(), ll.y());
    	return bbp2d;
    }
    
    /** Get a Line2D.Double representation of the current bounding box. */
    public Line2D.Double[] getLine2D()
    {
    	Point2D.Double[] p2d = this.getPoint2D();
    	Line2D.Double[] bbl2d = new Line2D.Double[4];
    	bbl2d[0] = new Line2D.Double(p2d[0], p2d[1]);
    	bbl2d[1] = new Line2D.Double(p2d[1], p2d[2]);
    	bbl2d[2] = new Line2D.Double(p2d[2], p2d[3]);
    	bbl2d[3] = new Line2D.Double(p2d[3], p2d[0]);
    	return bbl2d;
    }
    
    public Position mapToScenarioPosition(Position mapPosition)
    {
        return new Position(mapPosition.x - projectedOrigin.x, mapPosition.y - projectedOrigin.y);
    }

    public Position scenarioToMapPosition(Position scenarioPosition)
    {
        return new Position(projectedOrigin.x + scenarioPosition.x, projectedOrigin.y + scenarioPosition.y);
    }
    
    public PositionGeo scenarioToLonLat(Position scenarioPosition)
    {
    	PositionGeo result = null;

        if (transformation != null) { // transform coordinates to lon/lat (WGS84)
        	Position p = scenarioToMapPosition(scenarioPosition);
            Point2D.Double tmp = transformation.transform_inverse(p.x, p.y);
            try {
				result = new PositionGeo(tmp.x, tmp.y);
			} catch (Exception e) {
				System.err.println(e.getMessage());
				e.printStackTrace();
			}
        }

        return result;
    }
    
    public Position lonLatToScenario(PositionGeo lonLatPosition)
    {
    	Position result = null;

        if (transformation != null) { // transform coordinates from lon/lat (WGS84)
        	Point2D.Double p = transformation.transform(lonLatPosition.x(), lonLatPosition.y());
            result = mapToScenarioPosition(new Position(p.x, p.y));
        }
        
        return result;
    }
    
	@Override
	public String toString()
	{
		return "[" + ll.lon() + "," + ll.lat() + ", " + ur.lon() + "," + ur.lat() + "],";
	}
}
