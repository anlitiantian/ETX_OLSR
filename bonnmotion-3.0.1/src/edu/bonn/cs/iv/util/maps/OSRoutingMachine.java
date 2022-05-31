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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.Vector;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;


/*
 * @see http://project-osrm.org/
 */
public class OSRoutingMachine implements RouteServiceInterface
{	
	private static final boolean DEBUG = true;
	
	/*
	 * @see https://github.com/DennisOSRM/Project-OSRM/wiki/Output-json
	 */
	public static final int STATUS_SUCCESSFUL = 0;
	public static final int STATUS_UNKNOWN_SERVER_ERROR = 1;
	public static final int STATUS_INVALID_PARAMETER = 2;
	public static final int STATUS_PARAMETER_OUT_OF_RANGE = 3;
	public static final int STATUS_REQUIRED_PARAMETER_MISSING = 4;
	public static final int STATUS_SERVICE_UNAVAILABLE = 5;
	public static final int STATUS_ROUTE_IS_BLOCKED = 202;
	public static final int STATUS_DB_CORRUPTED = 205;
	public static final int STATUS_DB_IS_NOT_OPEN = 206;
	public static final int STATUS_NO_ROUTE = 207;
	public static final int STATUS_INVALID_START_POINT = 208;
	public static final int STATUS_INVALID_END_POINT = 209;
	public static final int STATUS_START_AND_END_POINTS_ARE_EQUAL = 210;

	/** decimal digit precision for lat/lon values */
	public static final int LATLON_PRECISION = 6;
	
	/** threshold for accepting a NO_ROUTE response as a flight */
	public static final int ROUTE_AS_FLIGHT_THRESH = 0;
	
	private static final String OSRM_URL = "http://192.168.95.134:5000"; // shortest
	private String serviceUrl = null;
	public PrintWriter distWriter = null;
	
	public OSRoutingMachine(String serviceUrl,
							String distFile)
	{
		this.serviceUrl = serviceUrl;
		
		if (serviceUrl == null) {
			this.serviceUrl = OSRM_URL;
		}
		
		if (distFile != null) {
			try {
				this.distWriter = new PrintWriter(new FileOutputStream(distFile));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public Route getOptimalRoute(PositionGeo src, PositionGeo dst) throws RSIRequestFailedException
	{
		Route result = new Route(src, dst);
		
		if (almostEqual(src, dst)) { // src and dst are very close; might result in failure anyway
			result.setTurningPoint(new PositionGeo[0]);
			return result;
		}
		
        String request = serviceUrl; 
        request += "/viaroute?";
        request += "loc="+src.y()+","+src.x();
        request += "&";
        request += "loc="+dst.y()+","+dst.x();
        // zoom level [0...18]; editing might yield better routability
        request += "&z=18";
//        request += "&z=12"; // ignore unconnected components
        
        result.setRequest(request);
        
        try {
            // send route request
            URL url = new URL(request);
            URLConnection conn = url.openConnection();
            conn.setReadTimeout(30000);
            conn.setConnectTimeout(30000);

            // receive and parse JSON-formatted response
            JsonFactory jf = new JsonFactory();
            JsonParser jp = jf.createParser(conn.getInputStream());
            Vector<PositionGeo> tpLonLat = new Vector<PositionGeo>();

            JsonToken jt = jp.nextToken();
            assert(jt == JsonToken.START_OBJECT);
            while (jp.nextToken() != JsonToken.END_OBJECT) {
            	String fieldName = jp.getCurrentName();
            	jt = jp.nextToken();
            	
            	if (fieldName.equals("version")) {
            		jp.getDoubleValue();
            	} else if (fieldName.equals("status")) {
            		int status = jp.getIntValue();
            		try {
            			processResponseStatus(status);
            		} catch (RSIRequestFailedException e) {
            			System.err.println(request);
            			jp.nextToken();
            			jp.nextToken();
            			System.err.println("status_message: " + jp.getText());
            			
            			double dist = src.distance(dst);
            			if (status == STATUS_NO_ROUTE && almostEqual(getNearestPosition(src), getNearestPosition(dst))) { // OSRM maps src and dst to the same position
            				result.setTurningPoint(new PositionGeo[0]);
            				return result;
            			} else if (status == STATUS_NO_ROUTE && dist < ROUTE_AS_FLIGHT_THRESH) {
                			result.setTurningPoint(new PositionGeo[0]);
                			return result;
                		} else {
                			System.err.println(e.getMessage());
                			e.printStackTrace();
                			throw e;
            			}
            		}
            	} else if (fieldName.equals("status_message")) {
            		jp.getText();
            	} else if (fieldName.equals("route_geometry")) { // JSON array
//            		assert(jt == JsonToken.START_ARRAY);
            		
            		String polyline = jp.getText();
            		PositionGeo[] tmp = decodeEncodedPolyline(polyline);
            		
            		for (int i = 0; i < tmp.length; i++) {
            			tpLonLat.add(tmp[i]);
            		}
            		
//            		if (DEBUG) {
//            			System.out.println(request);
//            			System.out.println("Polyline encoded: \"" + polyline + "\"");
//            			System.out.println("Polyline decoded:");
//                		for (int i = 0; i < tmp.length; i++) {
//                			System.out.println(tmp[i].toString());
//                		}
//            		}
            	} else if (fieldName.equals("route_summary")) { // JSON object
            	    while (jp.nextToken() != JsonToken.END_OBJECT) {
            	    	fieldName = jp.getCurrentName();
            	    	jp.nextToken(); // move to value
              	      	if (fieldName.equals("total_distance")) { // total length in metres
              	      		result.setDistanceRS(jp.getIntValue());
              	      	} else if (fieldName.equals("total_time")) { // total trip time in seconds
              	      		result.setTripTime(jp.getIntValue());
              	      	} else if (fieldName.equals("start_point")) {
              	      		result.setStartPoint(jp.getText());
              	      	} else if (fieldName.equals("end_point")) {
              	      		result.setEndPoint(jp.getText());
              	      	} else {
              	      		throw new RSIRequestFailedException("Unrecognized field '" + fieldName + "'!");
              	      	}
            	    }
            	} else if (fieldName.equals("transactionId")) {
            		jp.getText();
            	} else {
            		throw new RSIRequestFailedException("Unrecognized field '" + fieldName + "'!");
            	}
            }

            jp.close();
            
    		// check whether tpLonLat contains src and dst
    		if (almostEqual(src, tpLonLat.firstElement())) {
    			tpLonLat.remove(0);
    		}
    		if (almostEqual(dst, tpLonLat.lastElement())) {
    			tpLonLat.remove(tpLonLat.size() - 1);
    		}
    		
            result.setTurningPoint(tpLonLat.toArray(new PositionGeo[tpLonLat.size()]));
        } catch (Exception e) {
        	System.err.println(request);
            if (e instanceof RSIRequestFailedException) {
            	throw (RSIRequestFailedException)e;
            } else if (e instanceof IOException) {
            	System.err.println(e.toString());
            	if (e.toString().matches("(.*)HTTP response code: 500(.*)")) {
            		throw new RSIRequestFailedException(e.toString());
            	} else {
                    e.printStackTrace();
                	System.exit(-1);
            	}
            } else {
                System.err.println(e.toString());
                e.printStackTrace();
            	System.exit(-1);
            }
        }

        if (distWriter != null) {
            distWriter.printf("%.6f %.6f %.6f %.6f %.2f %.2f %.4f\n", result.src().x(), result.src().y(), result.dst().x(), result.dst().y(), result.flightLength(), result.distanceGeodesic(), result.distanceGeodesic() / result.flightLength());
        }
		
		return result;
	}

	@Override
	public boolean isRoutable(PositionGeo p)
	{
		PositionGeo np = null;
		try {
			np = getNearestPosition(p);
		} catch (RSIRequestFailedException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
		}
		return np.equals(p);
	}

	@Override
	public PositionGeo getNearestPosition(PositionGeo p) throws RSIRequestFailedException
	{
		PositionGeo result = null;
		
        String request = serviceUrl;
        request += "/locate?";
        request += "loc="+p.y()+","+p.x();
                
//        if (DEBUG) System.out.println("OSRM Request: "+request);
        
        try {
            // send locate request
            URL url = new URL(request);
            URLConnection conn = url.openConnection();
            conn.setReadTimeout(30000);
            conn.setConnectTimeout(30000);

            // receive and parse JSON-formatted response
            JsonFactory jf = new JsonFactory();
            JsonParser jp = jf.createParser(conn.getInputStream());

            JsonToken jt = jp.nextToken();
            assert(jt == JsonToken.START_OBJECT);
            while (jp.nextToken() != JsonToken.END_OBJECT) {
            	String fieldName = jp.getCurrentName();
            	jt = jp.nextToken();
            	
            	if (fieldName.equals("version")) {
            		jp.getDoubleValue();
            	} else if (fieldName.equals("status")) {
            		try {
            			processResponseStatus(jp.getIntValue());
            		} catch (RSIRequestFailedException e) {
            			System.err.println(e.getMessage());
            			e.printStackTrace();
            			throw e;
            		}
            	} else if (fieldName.equals("mapped_coordinate")) { // JSON array
            		assert(jt == JsonToken.START_ARRAY);
            		
            		jt = jp.nextToken();
            		
        			double lat = jp.getDoubleValue();
        			jt = jp.nextToken();
        			assert(jt == JsonToken.VALUE_NUMBER_FLOAT);
        			double lon = jp.getDoubleValue();
        			result = new PositionGeo(lon, lat);
        			
        			jt = jp.nextToken();
        			assert(jt == JsonToken.END_ARRAY);
            	} else if (fieldName.equals("transactionId")) {
            		jp.getText();
            	} else {
            		throw new RSIRequestFailedException("Unrecognized field '" + fieldName + "'!");
            	}
            }

            jp.close();
            
//            if (DEBUG) System.out.println("NearestPosition():\nquery: "+this.toString(posLonLat)+"\nresponse: "+this.toString(resultLonLat));
            
//            if (almostEqual(p, result)) {
//            	result = p;
//            }
        } catch (Exception e) {
            if (e instanceof RSIRequestFailedException) {
            	throw (RSIRequestFailedException)e;
            } else {
                System.err.println(e.toString());
                e.printStackTrace();
            	System.exit(-1);
            }
        }
        
//        if (DEBUG) System.out.println("result = " + result.toString());
        
		return result;
	}

//	@Override
//	public String sendQuery(String query)
//	{
//		// TODO Auto-generated method stub
//		return null;
//	}
	
	/*
	 * @param p1 Lon/Lat position
	 * @param p2 Lon/Lat position
	 * @return true if and only if the first n decimal places of p1's and p2's lon and lat values are equal.
	 * 
	 */
	private boolean almostEqual(PositionGeo p1, PositionGeo p2)
	{
		// OSRM uses 6 decimal places
		DecimalFormat df = new DecimalFormat("###.######");
		
		String p1x = df.format(p1.x());
		String p1y = df.format(p1.y());
		String p2x = df.format(p2.x());
		String p2y = df.format(p2.y());
		
		boolean equalX = p1x.equals(p2x);
		boolean equalY = p1y.equals(p2y);
		boolean almostEqual = equalX && equalY;
		
//		if (DEBUG) System.out.println("almostEqual():\n" + p1.toString() + "\n" + p2.toString() + "\nresult: " + Boolean.toString(almostEqual));
		System.out.flush();
		
		return almostEqual;
	}
	
	private String toString(Point2D.Double p)
	{
		return p.x + " " + p.y;
	}
	
//	private String toString(Vector<Point2D.Double> tp)
//	{
//		String result = "";
//		
//		for (int i = 0; i < tp.size(); i++) {
//			result += this.toString(tp.get(i)) + "\n";
//		}
//		
//		return result;
//	}
	
	private String toString(Vector<PositionGeo> tp)
	{
		String result = "";
		
		for (int i = 0; i < tp.size(); i++) {
			result += tp.get(i).toString() + "\n";
		}
		
		return result;
	}
	
	private void processResponseStatus(int status) throws RSIRequestFailedException
	{
		switch(status) {
			case STATUS_SUCCESSFUL:
				return;
			case STATUS_UNKNOWN_SERVER_ERROR:
			case STATUS_INVALID_PARAMETER:
			case STATUS_PARAMETER_OUT_OF_RANGE:
			case STATUS_REQUIRED_PARAMETER_MISSING:
			case STATUS_SERVICE_UNAVAILABLE:
			case STATUS_ROUTE_IS_BLOCKED:
			case STATUS_DB_CORRUPTED:
			case STATUS_DB_IS_NOT_OPEN:
			case STATUS_NO_ROUTE:
			case STATUS_INVALID_START_POINT:
			case STATUS_INVALID_END_POINT:
			case STATUS_START_AND_END_POINTS_ARE_EQUAL:
			default:
				throw new RSIRequestFailedException("OSRM Request unsuccessful (status " + status + ")");
		}
	}

	/**
	 * Based on https://github.com/DennisSchiefer/Project-OSRM-Web/blob/develop/WebContent/routing/OSRM.RoutingGeometry.js
	 * @ref https://developers.google.com/maps/documentation/utilities/polylinealgorithm
	 */
	public static PositionGeo[] decodeEncodedPolyline(String line)
	{
		int index = 0;
		int lat = 0;
		int lng = 0;
		Vector<PositionGeo> tp = new Vector<PositionGeo>();
		
		while (index < line.length()) {
			byte b;
			
			int shift = 0;
			int result = 0;
			do {
				b = (byte) ((byte)line.charAt(index++) - 63);
				result |= (b & 0x1f) << shift;
				shift += 5;
			} while (b >= 0x20);
			int dlat = ((result & 1) == 1 ? ~(result >> 1) : (result >> 1));
			lat += dlat;
			
			shift = 0;
			result = 0;
			do {
				b = (byte) ((byte)line.charAt(index++) - 63);
				result |= (b & 0x1f) << shift;
				shift += 5;
			} while (b >= 0x20);
			int dlng = ((result & 1) == 1 ? ~(result >> 1) : (result >> 1));
			lng += dlng;
			
			try {
				tp.add(new PositionGeo(Double.parseDouble(integerLatLonToDecimalString(lng)), Double.parseDouble(integerLatLonToDecimalString(lat))));
			} catch (Exception e) {
				System.err.println(e.toString());
                e.printStackTrace();
            	System.exit(-1);
			}
		}
		
		return tp.toArray(new PositionGeo[tp.size()]);
	}
	
	/**
	 * Based on Project-OSRM-0.3.7/Util/StringUtil.h:printInt()
	 */
	public static String integerLatLonToDecimalString(int val)
	{
		int maxChars = LATLON_PRECISION + 5; // "[-]###.######"
		char[] result = new char[maxChars];
	    boolean minus = false;
	    if (val < 0) {
	    	minus = true;
	    	val = -val;
	    }
	    
	    int idx = result.length -1;

	    for (int i = 0; i < LATLON_PRECISION; i++) {
	        result[idx--] = String.valueOf(val % 10).charAt(0);
	        val /= 10;
	    }
	    
	    result[idx--] = '.';

	    for (int i = LATLON_PRECISION + 1; i < result.length; i++) {
	    	result[idx] = String.valueOf(val % 10).charAt(0);
	        val /= 10;
	        if (val == 0){
	        	break;
	        }
	        idx--;
	    }
	    
	    if (minus) {
	        result[--idx] = '-';
	    }
		
		return String.valueOf(result);
	}
}
