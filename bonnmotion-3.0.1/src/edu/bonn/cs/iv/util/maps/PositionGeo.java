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

import java.awt.geom.Point2D;

import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.models.slaw.PositionInterface;

import net.sf.geographiclib.*;

/** Class for geographic positions (longitude, latitude)
 * 
 * @author schwamborn
 *
 */
public class PositionGeo implements PositionInterface
{
	private final double lon;
	private final double lat;
	
	public PositionGeo(double lon, double lat)
	{
		assert(lon >= -180.0 && lon <= 180.0);
		assert(lat >= -90.0 && lat <= 90.0);
		
		this.lon = lon;
		this.lat = lat;
	}
	
	public PositionGeo(PositionGeo other)
	{
		this.lon = other.lon();
		this.lat = other.lat();
	}
	
	public double lon()
	{
		return lon;
	}
	
	public double lat()
	{
		return lat;
	}
	
	public double x()
	{
		return lon;
	}
	
	public double y()
	{
		return lat;
	}
	
	/**
	 * geodesic longitudinal distance
	 */
	public double distanceX(double lon2)
	{
		PositionGeo p2 = new PositionGeo(lon2, this.lat);
		return distance(p2);
	}
	
	/**
	 * geodesic latitudinal distance
	 */
	public double distanceY(double lat2)
	{
		PositionGeo p2 = new PositionGeo(this.lon, lat2);
		return distance(p2);
	}
	
	/**
	 * geodesic (shortest path between two geographic points) distance
	 */
	public double distance(PositionGeo p)
	{
		return distance(this.lat, this.lon, p.lat(), p.lon());
	}
	
	/**
	 * geodesic (shortest path between two geographic points) distance
	 */
	public double distance(double lon2, double lat2)
	{
		return distance(this.lat, this.lon, lat2, lon2);
	}
	
	/**
	 * geodesic shift
	 */
	public PositionGeo shift(double angle, double dist) throws Exception
	{
		GeodesicData gd = Geodesic.WGS84.Direct(this.lat, this.lon, angle, dist, GeodesicMask.LATITUDE | GeodesicMask.LONGITUDE);
		return new PositionGeo(gd.lon2, gd.lat2);
	}
	
	public PositionGeo shiftX(double offset) throws Exception
	{
		return new PositionGeo(this.lon + offset, this.lat);
	}
	
	public PositionGeo shiftY(double offset) throws Exception
	{
		return new PositionGeo(this.lon, this.lat + offset);
	}
	
	public boolean equals(PositionGeo p)
	{
		return (p.x() == this.x() && p.y() == this.y());
	}
	
	@Override
	public String toString()
	{
//		return lon + " " + lat;
		return "[" + lon + "," + lat + "],";
	}
	
	public Point2D.Double transform(CoordinateTransformation t)
	{
		return t.transform(this.lon, this.lat);
	}
	
	public Position toPosition(BoundingBox bb)
	{
		return bb.lonLatToScenario(this);
	}
	
	/**
	 * geodesic (shortest path between two geographic points) distance
	 */
	public static double distance(double lat1, double lon1, double lat2, double lon2)
	{
		double result = -1.0;
		
		try {
			GeodesicData gd = Geodesic.WGS84.Inverse(lat1, lon1, lat2, lon2, GeodesicMask.DISTANCE);
			result = gd.s12;
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
		return result;
	}

	@Override
	public double distance(PositionInterface p)
	{
		assert(p instanceof PositionGeo);
		return distance((PositionGeo)p);
	}
	
	public PositionGeo getPhantomNode(PositionGeo p, double alpha)
	{
		assert(alpha >= 0 && alpha <= 1);
		PositionGeo result = null;
		
		try {
			GeodesicData gd = Geodesic.WGS84.Inverse(this.lat, this.lon, p.lat(), p.lon(), GeodesicMask.AZIMUTH ^ GeodesicMask.DISTANCE);
			gd = Geodesic.WGS84.Direct(this.lat, this.lon, gd.azi1, gd.s12 * alpha, GeodesicMask.LONGITUDE ^ GeodesicMask.LATITUDE);
			result = new PositionGeo(gd.lon2, gd.lat2);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		
		return result;
	}
}
