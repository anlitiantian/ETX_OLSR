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

import java.util.Vector;

//import edu.bonn.cs.iv.bonnmotion.Position;

/** Class for a route of geographic coordinates
 * 
 * @author schwamborn
 *
 */
public class Route
{	
	protected final PositionGeo src;
	protected final PositionGeo dst;
	protected double scenarioStartTime = -1;
	protected String request = null;
	protected double distanceGeodesic = -1; // route length according to geodesic calculations
	protected double distanceRS = -1; // route length according to route service
	protected double flightLength = -1;
	protected double tripTime = -1;
	protected String startPoint = null;
	protected String endPoint = null;
	protected PositionGeo[] turningPoint = null;
	protected ClippingInfo ci = null;
	
	public class ClippingInfo
	{
		/** bounding box used for clipping */
		public BoundingBox clipBB = null;
		/** turning points after clipping (including introduced points) */
		public PositionGeo[] tpClip = null;
		/** information about clipped off route segments */
		public Vector<ClippedRouteInfo> clippedRoute = null;
	}
	
	public class ClippedRouteInfo
	{
		/** index of clipped points in original route (-> turningPoint) */
		public Vector<Integer> indexClipped = new Vector<Integer>();
		/** index of introduced (boundary) points (-> tpClip) */
		public Vector<Integer> indexNew = new Vector<Integer>();
		/** distance clipped off */
		public double clippedDistance = 0.0;
	}
	
	public Route(PositionGeo src, PositionGeo dst)
	{
		this.src = src;
		this.dst = dst;
		this.flightLength = src.distance(dst);
		this.distanceGeodesic = this.flightLength;
	}
	
	public void setScenarioStartTime (double t)
	{
		this.scenarioStartTime = t;
	}
	
	public void setRequest (String r)
	{
		this.request = r;
	}
	
	public void setDistanceRS (double d)
	{
		this.distanceRS = d;
	}
	
	public void setTripTime (double tt)
	{
		this.tripTime = tt;
	}
	
	public void setStartPoint (String sp)
	{
		this.startPoint = sp;
	}
	
	public void setEndPoint (String ep)
	{
		this.endPoint = ep;
	}
	
	public void setTurningPoint (PositionGeo[] tp)
	{
		this.turningPoint = tp;
		calculateGeodesicRouteDistance();
	}
	
	public PositionGeo src()
	{
		return this.src;
	}
	
	public PositionGeo dst()
	{
		return this.dst;
	}
	
	public double scenarioStartTime()
	{
		return this.scenarioStartTime;
	}
	
	public double distanceGeodesic()
	{
		return this.distanceGeodesic;
	}
	
	public double distanceRS()
	{
		return this.distanceRS;
	}
	
	public double flightLength()
	{
		return this.flightLength;
	}
	
	public PositionGeo[] turningPoint()
	{
		return this.turningPoint;
	}
	
	public ClippingInfo ci()
	{
		return this.ci;
	}
	
	public int numPoints()
	{
		if (turningPoint != null) {
			return turningPoint.length + 2;
		} else {
			return 2;
		}
	}
	
	public PositionGeo[] allPoints()
	{
		PositionGeo[] result = new PositionGeo[numPoints()];
		
		result[0] = src;
		result[result.length - 1] = dst;
		for (int i = 1; i < result.length - 1; i++) {
			result[i] = turningPoint[i-1];
		}
		
		return result;
	}
	
	public PositionGeo[] allClipPoints()
	{
		if (ci == null) {
			return null;
		}
		
		PositionGeo[] result = new PositionGeo[ci.tpClip.length+2];
		
		result[0] = src;
		result[result.length - 1] = dst;
		for (int i = 1; i < result.length - 1; i++) {
			result[i] = ci.tpClip[i-1];
		}
		
		return result;
	}
	
	/*
	 * clip route w.r.t. bounding box
	 */
	public ClippingInfo clipRoute(BoundingBox bb)
	{
		// TODO: account for clippings spanning over multiple bbox edges
		assert(bb != null);
		assert(turningPoint != null);
		
		if (bb.contains(this)) { // no clipping needed
			ci = null;
			return ci;
		}
		
		if (ci != null && ci.clipBB.equals(bb)) { // same bounding box as before
			return ci;
		}
		
		assert(bb.contains(src));
		assert(bb.contains(dst));
		
		if (ci == null) { // initialize
			ci = new ClippingInfo();
			ci.clipBB = bb;
			ci.clippedRoute = new Vector<ClippedRouteInfo>();
		}
		
		PositionGeo[] p = allPoints();
		Vector<PositionGeo> clip = new Vector<PositionGeo>();
		boolean previousClipped = false;
		for (int i = 0; i < p.length; i++) {
			if (ci.clipBB.contains(p[i])) { // within bounds
				if (previousClipped) { // insert intersection before p[i]
					clip.add(ci.clipBB.calcBoundsIntersection(p[i-1], p[i]));
					ci.clippedRoute.lastElement().indexNew.add(clip.size()-2); // 1st element will be removed later
					ci.clippedRoute.lastElement().clippedDistance += p[i-1].distance(clip.lastElement());
					assert(ci.clippedRoute.lastElement().indexNew.size() == 2);
				}
				
				clip.add(p[i]);
				previousClipped = false;
			} else { // outside bounds
				if (!previousClipped) { // insert intersection instead of p[i]
					clip.add(ci.clipBB.calcBoundsIntersection(p[i-1], p[i]));
					ci.clippedRoute.add(new ClippedRouteInfo());
					ci.clippedRoute.lastElement().indexNew.add(clip.size()-2); // 1st element will be removed later
					ci.clippedRoute.lastElement().clippedDistance += clip.lastElement().distance(p[i]);
				} else {
					ci.clippedRoute.lastElement().clippedDistance += p[i-1].distance(p[i]);
				}
				
				ci.clippedRoute.lastElement().indexClipped.add(i-1); // index of turningPoint
				previousClipped = true;
			}
		}
		
		// remove src and dst
		clip.removeElementAt(0);
		clip.removeElementAt(clip.size()-1);
		
		ci.tpClip = clip.toArray(new PositionGeo[clip.size()]);
		
		assert bb.containsClip(this) : this.toString();
//		if (DEBUG) System.out.println("Info: Route clipped:\n" + this.toString());
		
		return ci;
	}
	
	public String toString()
	{
		String result = "";
		
		result += "Route:\n";
		result += "src: " + src.x() + " " + src.y() + " (\"" + startPoint + "\")" + "\n";
		result += "dst: " + dst.x() + " " + dst.y() + " (\"" + endPoint + "\")" + "\n";
		result += "dist: " + distanceRS + " m\n";
		result += "trip time: " + tripTime + " s\n";
		result += "turning points (original):\n";
		for (int i = 0; i < turningPoint.length; i++) {
			result += turningPoint[i].toString() + "\n";
		}
		
		if (ci != null) {
			result += "\nturning points (clip):\n";
			for (int i = 0; i < ci.tpClip.length; i++) {
				result += ci.tpClip[i].toString() + "\n";
			}
		}
		
		return result;
	}
	
	private void calculateGeodesicRouteDistance()
	{
		PositionGeo[] rp = allPoints();
		this.distanceGeodesic = 0;
		for (int i = 0; i < rp.length-1; i++) {
			this.distanceGeodesic += rp[i].distance(rp[i+1]);
		}
	}
}
