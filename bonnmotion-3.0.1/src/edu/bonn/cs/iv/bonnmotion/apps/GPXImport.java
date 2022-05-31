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

package edu.bonn.cs.iv.bonnmotion.apps;

import java.awt.geom.Point2D;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Set;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;
import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.bonn.cs.iv.util.maps.*;
import edu.bonn.cs.iv.util.maps.CoordinateTransformation.proj4lib;

import edu.bonn.cs.iv.bonnmotion.App;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;

/**
 * Application that converts GPX files to Bonnmotion output.
 *
 */
public class GPXImport extends App {

    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("GPXImport");
        info.description = "Application that converts GPX files to Bonnmotion format";
        
        info.major = 2;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
        info.authors.add("Karina Meyer <karimeye@uos.de>");
        info.authors.add("Matthias Schwamborn");
		info.affiliation = ModuleInfo.UOS_SYS;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
	
	private class GpxPoint {
		public final double x;
		public final double y;
		public double z;
		public Date time;
		public double convertedTime;
		
		public GpxPoint(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}
	
	private class TimePoint {
		public final String FileName;
		public final String RteTrkWp;
		public final int numberOf;
		public int numberOfSegment;
		public int numberOfPoint;
		
		public TimePoint (String Filename, String RteTrkWp, int numberOf, int numberOfSegment, int numberOfPoint){
			this.FileName = Filename;
			this.RteTrkWp = RteTrkWp;
			this.numberOf = numberOf;
			this.numberOfSegment = numberOfSegment;
			this.numberOfPoint = numberOfPoint;
		}
		public TimePoint (String Filename, String RteTrkWp, int numberOf){
			this.FileName = Filename;
			this.RteTrkWp = RteTrkWp;
			this.numberOf = numberOf;
		}
	}
	
	private String fileName = null;
	private String projCRSName = null;

	private CoordinateTransformation transformation = null;
	private ArrayList <String> filenames = new ArrayList <String>();
	private HashMap<String, ArrayList<Double>> bounds = new HashMap<String, ArrayList<Double>>();
	private HashMap<String, ArrayList<GpxPoint>> trks = new HashMap<String, ArrayList<GpxPoint>>();
	private HashMap<String, ArrayList<GpxPoint>> wps = new HashMap<String, ArrayList<GpxPoint>>();
	private HashMap<String, ArrayList<GpxPoint>> rtes = new HashMap<String, ArrayList<GpxPoint>>();
	
	private boolean waypoint = false;
	private boolean route = false;
	private String timestr = "";
	private boolean compress = false;
	private boolean importHeight = false;
	private double defaultHeight = 0;
	private String invalidHeight = "-99999.000000";
	private int lostTrkPointCounter = 0;
	private int lostWPointCounter = 0;
	private int lostRtePointCounter = 0;
	private int TrkNameCounter = 0; 
	private int WPNameCounter = 0; 
	private int RteNameCounter = 0; 
	private Date starttime = null;
	private Date endtime = null;
	
	// Not Used?
//	private GpxPoint currentWPoint = null;
//	private GpxPoint currentTrkPoint = null;
//	private GpxPoint currentRtePoint = null;
	private GpxPoint currentPoint = null;
	private TimePoint currentStart = null;
	private TimePoint currentEnd = null;
	//private ArrayList<TrkPoint> coordlist = new ArrayList<TrkPoint>();

	public GPXImport(String[] args) {
		this.bounds.put("min_x", new ArrayList<Double>());
		this.bounds.put("min_y", new ArrayList<Double>());
		this.bounds.put("max_x", new ArrayList<Double>());
		this.bounds.put("max_y", new ArrayList<Double>());
	    this.bounds.put("max_z", new ArrayList<Double>());
	    bounds.get("max_z").add(.0);
	    
		this.go(args);
	}
	
	@Override
	public void go(String[] args) {
		parse(args);

		if (this.filenames == null) {
			GPXImport.printHelp();
			System.exit(0);
		}
		if (projCRSName != null && ((filenames.size() > 1 && (fileName!=null && !fileName.equals(""))) || filenames.size() == 1)){
			transformation = new CoordinateTransformation(projCRSName, proj4lib.PROJ4J);
			
			if (filenames.size() == 1 && (fileName==null || fileName.equals(""))) {
				fileName = filenames.get(0);
			}
			for (int i=0; i<filenames.size(); i++){
				this.ParseDoc(filenames.get(i));
			}
			this.createParams();
			this.createMovements();
		} else {
			if (projCRSName == null) {
				System.out.println("Error: projected CRS name required (see \"-p\"!");
			}
			if ((filenames.size() > 1 && fileName==null)) {
				System.out.println("Error: No Filename found for result data file!");
			}
			
			this.printHelp();
		}
	} //ende go
		
		
	/**
	* parse the input Files
	*/
	private void ParseDoc (String FileName) {

		 try {
			File file = new File(FileName);
			System.out.println(" ");
			System.out.println("Parsing: [" + FileName + "]");
			RteNameCounter = 0;
			TrkNameCounter = 0;
			WPNameCounter = 0;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();
			NodeList nodeLst = null;
			Element rootEl = doc.getDocumentElement();
			//************ parsing bounds ***************************
			nodeLst = doc.getElementsByTagName("metadata");
			if (nodeLst != null && nodeLst.getLength() > 0){
				for (int s = 0; s < nodeLst.getLength(); s++) {

					Node fstNode = nodeLst.item(s); 
    
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) { 
						Element mdata = (Element)fstNode; 
						NodeList boundList = mdata.getElementsByTagName("bounds");
						this.parseBound(boundList);
					} 
				} 
			} else {
				NodeList boundList2 = rootEl.getElementsByTagName("bounds");
				parseBound(boundList2);
			}
		//******************* Parsing Track ***************************
			nodeLst = doc.getElementsByTagName("trk");
			if (nodeLst != null){
				for (int s = 0; s < nodeLst.getLength(); s++) {
					ArrayList<GpxPoint> coordlist = new ArrayList<GpxPoint>();
					Node fstNode = nodeLst.item(s); 
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element srcTag = (Element)fstNode;
						Element trkseg = (Element)fstNode;
						NodeList trksegList = trkseg.getElementsByTagName("trkseg");
						for (int i =0; i<trksegList.getLength(); i++){
							Node trksegNode = nodeLst.item(i);
							lostTrkPointCounter=0;
							if (trksegNode.getNodeType() == Node.ELEMENT_NODE) { 
								Element trkpt = (Element)fstNode;
								NodeList trkptList = trkpt.getElementsByTagName("trkpt");
								for (int j=0; j<trkptList.getLength(); j++) {
									Element trkpoint = (Element) trkptList.item(j);
									TimePoint trkTimePoint = new TimePoint(FileName, "trk", s, i, j);
									parsePoints (trkpoint, coordlist, 1, trkTimePoint);
								}
							}
						}
						
						String Name = "";
						if (coordlist!=null && coordlist.size() > 0){
							NodeList srcList = srcTag.getElementsByTagName("src");
							
							if (srcList != null && srcList.getLength() > 0){ //anfang if 1
								for (int a = 0; a < srcList.getLength(); a++) {
//									Element srcElement = (Element)srcList.item(0);
//									NodeList srcList2 = srcElement.getChildNodes();
//									String name = srcList2.item(0).getNodeValue().trim();
									boolean twice = false;
									Set<String> trkkeys = this.trks.keySet();
									for (String str : trkkeys){
										if (Name.equals(str)){
											ArrayList<GpxPoint> pointlist = trks.get(str);
											for (int i = 0; i<coordlist.size(); i++){
												pointlist.add(coordlist.get(i));
											}
											trks.remove(str);
											trks.put(Name, pointlist);
											twice = true;
											break;
										}
				
									}
									if (twice == false){
										trks.put(Name, coordlist);
									}
								}
							} else {
								Name = String.valueOf(TrkNameCounter);
								trks.put(Name,coordlist);
								System.out.println("Warning: No <src> Tag found for Track " + s);
								TrkNameCounter++;
							}
						}
						if (lostTrkPointCounter>0){
							System.out.println("Warning: In Track " + s + " " + lostTrkPointCounter + " TrkPoints ignored (no time found)");
						}
					}
				}
			} else {
				System.out.println("Waring: No <trk> Element found");
			}
		//****************** Parsing Waypoints ***************************
			if (waypoint == true){
				nodeLst = doc.getElementsByTagName("wpt"); 
				if (nodeLst != null){
					ArrayList<GpxPoint> wcoordlist = new ArrayList<GpxPoint>();
				
					for (int s = 0; s < nodeLst.getLength(); s++) {
						Node fstNode = nodeLst.item(s); 
//						Element srcTag = (Element)fstNode;
						if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
							Element wpoint = (Element) nodeLst.item(s);
							TimePoint wptTimePoint = new TimePoint(FileName, "wpt", s);
							parsePoints (wpoint, wcoordlist, 2, wptTimePoint);
						}	

					}
					String Name = "";
					Name = String.valueOf(WPNameCounter);
					WPNameCounter++;
					if (wcoordlist.size()>0){
						wps.put(Name,wcoordlist);
					}					
					if (lostWPointCounter>0){
						System.out.println("Warning: " + lostWPointCounter + " WPoint(s) ignored (no time found)");
					}
					
			}
			
		}
		//************************* Parsing Routes *************************
		if (route == true) {
			nodeLst = doc.getElementsByTagName("rte");
			if (nodeLst != null){
				for (int s = 0; s < nodeLst.getLength(); s++) {
					ArrayList<GpxPoint> coordlist = new ArrayList<GpxPoint>();
					Node fstNode = nodeLst.item(s); 
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element srcTag1 = (Element)fstNode;
						Element rtepoint1 = (Element)fstNode;
						NodeList rtePointList = rtepoint1.getElementsByTagName("rtept");
						for (int t = 0; t < rtePointList.getLength(); t++) {
							Element rtepoint = (Element) rtePointList.item(t);
							TimePoint rteTimePoint = new TimePoint(FileName, "rte", s, 0, t);
							parsePoints (rtepoint, coordlist, 1, rteTimePoint);		
							
						} //end for t
						if (lostRtePointCounter>0){
							System.out.println("Warning: in Rte " + s + " " + lostWPointCounter + " RtePoint(s) ignored (no time found)");
						}
						if (coordlist!=null && coordlist.size() > 0) {
							NodeList srcList = srcTag1.getElementsByTagName("src");
							String Name = "";	
							if (srcList != null && srcList.getLength() > 0){ 
								for (int a = 0; a < srcList.getLength(); a++) {
//									Element srcElement = (Element)srcList.item(0);
//									NodeList srcList2 = srcElement.getChildNodes();
//									String name = srcList2.item(0).getNodeValue().trim();
									boolean twice = false;
									Set<String> trkkeys = this.trks.keySet();
									for (String str : trkkeys){
										if (Name.equals(str)){
											ArrayList<GpxPoint> pointlist = rtes.get(str);
											
											for (int i = 0; i<coordlist.size(); i++){
												pointlist.add(coordlist.get(i));
											}
											rtes.remove(str);
											rtes.put(Name, pointlist);
											twice = true;
											break;
										}
				
									}
									if (twice == false){
										rtes.put(Name, coordlist);
									}
								}
							} else {
								Name = String.valueOf(RteNameCounter);
								rtes.put(Name,coordlist);
								RteNameCounter++;
								System.out.println("Warning: No <src> Tag found for Route " + s);
							}
						
						}
					}
				}	
			}
		}
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	} //end ParseDoc
	
	/**
	* Parse Gpx points from Files
	*/
	private void parsePoints (Element point, ArrayList<GpxPoint> coordlist, int counter, TimePoint p) {
		double lat = Double.parseDouble(point.getAttribute("lat")); //parses lat and lon from Point
		double lon = Double.parseDouble(point.getAttribute("lon"));

		Point2D.Double dst = transformation.transform(lon, lat);
		
		this.currentPoint = new GpxPoint(dst.x, dst.y);
								
		NodeList hightList = point.getElementsByTagName("ele");
		String ele = "-99999.000000";
		if (hightList!= null && hightList.getLength() >0 ){  //parse the ele tack if exists otherwise it 
			Element eleElement = (Element)hightList.item(0); // will be replaced by default height
			NodeList eleList = eleElement.getChildNodes();
			ele = eleList.item(0).getNodeValue().trim();
		} else {
			ele = "-99999.000000";
		}
									
		double z;
		if (ele.equals(invalidHeight)) {
			z = this.defaultHeight;
		} else {
			try {
				z = Double.parseDouble(ele);
			} catch (NumberFormatException e) {
				z = this.defaultHeight;
			}
		}
            
		double maxz = bounds.get("max_z").get(0);
		if (z > maxz) bounds.get("max_z").set(0, z);
            
		this.currentPoint.z = z;
	
		// parse time from every point if exists, otherwise the Point will be ignored
		NodeList timeList = point.getElementsByTagName("time");
		if (timeList.getLength()!=0){
			Element timeElement = (Element)timeList.item(0);
			NodeList timeList2 = timeElement.getChildNodes();
			this.timestr = "";
			SimpleDateFormat f = new SimpleDateFormat("y-M-d'T'H:m:s'Z'");
			timestr = timeList2.item(0).getNodeValue().trim();
									
			try {
				this.currentPoint.time = f.parse(this.timestr);
			} catch (ParseException e) {
				App.exceptionHandler("Error parsing Date ", e);
			}
			coordlist.add(this.currentPoint);
			seek0Time(currentPoint, p);
		} else {
			if (counter == 1){
				lostTrkPointCounter++;
			}
			if (counter == 2){
				lostWPointCounter++;
			}
			if (counter == 3){
				lostRtePointCounter++;
			}
		}
	} //end parsePoints
	
	
	/**
	* Sorts the input ArrayList with bubblesort
	* sort List beginns with lowest time
	*/
	private void bubbleSort(ArrayList<GpxPoint> wcoordlist){
		boolean bubble;
		do {
			bubble = false;
			for (int i=0; i< wcoordlist.size()-1; i++) {
				GpxPoint p = wcoordlist.get(i);
				GpxPoint x = wcoordlist.get(i+1);
				if ((p.convertedTime) > (x.convertedTime)) {
					wcoordlist.set(i, x);
					wcoordlist.set((i+1), p);
					bubble = true;
				}
			}
		} while (bubble);
	}
	
	/**
	* seek zero time of all input
	*/
	private void seek0Time (GpxPoint p, TimePoint t) {
		boolean transformedStart = false; 
		boolean transformedEnd = false; 
		if (p!=null){
			if (starttime == null){
				starttime = p.time;
				transformedStart = true;
			} else {
				if (starttime.after(p.time)) {
						starttime = p.time;
						transformedStart = true;
				}
			}
									
			if (endtime == null){
				endtime = p.time;
				transformedEnd = true; 
			} else {
				if (endtime.before(p.time)) {
					endtime = p.time;
					transformedEnd = true;
				}
			}
		}
		if (transformedStart == true || currentStart == null){
			currentStart = t;
		}
		if (transformedEnd == true || currentEnd == null){
			currentEnd = t;
		}
	}
	/**
	* Parsing the bounds of the Simulation Area from every Input File and write them 
	* into a HashMap
	*/
	private void parseBound(NodeList boundList) {
		double minlat = 0f;
		double minlon = 0f;
		double maxlat = 0f;
		double maxlon = 0f;
		Element bound = (Element) boundList.item(0);
		// parse bounds from file
		minlat = Double.parseDouble(bound.getAttribute("minlat"));
		maxlat = Double.parseDouble(bound.getAttribute("maxlat"));
		minlon = Double.parseDouble(bound.getAttribute("minlon"));
		maxlon = Double.parseDouble(bound.getAttribute("maxlon"));

		// transform bounds
		Point2D.Double dst = transformation.transform(minlon, minlat);
		
		// add bounds to HashMap
		bounds.get("min_x").add(dst.x);
		bounds.get("min_y").add(dst.y);

		dst = transformation.transform(maxlon, maxlat);
			
		bounds.get("max_x").add(dst.x);
		bounds.get("max_y").add(dst.y);
	}
	
	
	public static void printHelp() {
        System.out.println(getInfo().toDetailString());
		System.out.println(getInfo().name);
		System.out.println("\t-f <GPX file> ... (GPX input file(s))");
		System.out.println("\t[-c] compress output");
	    System.out.println("\t[-h] import elevation from input as z-coordinate");
	    System.out.println("\t[-H] default height (double)");
		System.out.println("\t[-w] process waypoint entries");
		System.out.println("\t[-r] process route entries");
		System.out.println("\t-p <projected CRS name> (Name of the projected CRS for coordinate transformation)");
		System.out.println("\t-F <output filename> (mandatory if there are multiple input files) ");
	}
	/**
	* calculate differents between starttime and endtime in milliseconds
	*/
	private long calculateDateDiff(Date starttime, Date endtime) {
		GregorianCalendar cal1 = new GregorianCalendar();
		GregorianCalendar cal2 = new GregorianCalendar();
		
		cal1.setTime(endtime);
		cal2.setTime(starttime);
		
		long delta = cal1.getTime().getTime() - cal2.getTime().getTime();
		
		return delta;
	}
	
	/** 
	* Print finally Start and Endtime
	*/
	private void printTime(TimePoint st, TimePoint et){
		if (starttime != null && endtime != null){
			if ((st.RteTrkWp).equals("trk")){
				System.out.println("Starttime: " + starttime+ " found at File " + st.FileName + " Trk " + st.numberOf + " TrkSeg " + st.numberOfSegment + " TrkPoint " + st.numberOfPoint);
			}
			if ((st.RteTrkWp).equals("rte")){
				System.out.println("Starttime: " + starttime + " found at File " + st.FileName + " Rte " + st.numberOf + " RtePoint " + st.numberOfPoint);
			}
			if ((st.RteTrkWp).equals("wpt")){
				System.out.println("Starttime: " + starttime + " found at File " + st.FileName + " Wpt " + st.numberOf);
			}
			if ((et.RteTrkWp).equals("wpt")){
				System.out.println("Endtime: " + endtime + " found at File " + et.FileName + " Wpt " + et.numberOf);
			}
			if ((et.RteTrkWp).equals("trk")){
				System.out.println("Endtime: " + endtime + " found at File " + et.FileName + " Trk " + et.numberOf + " TrkSeg " + et.numberOfSegment + " TrkPoint " + et.numberOfPoint);
			}
			if ((et.RteTrkWp).equals("rte")){
				System.out.println("Endtime: " + endtime + " found at File " + et.FileName + " Rte " + et.numberOf + " RtePoint " + et.numberOfPoint);
			}
		}

	}
	
	
	/**
	* creates Movement File and calls printMovements to print Movements
	*/
	private void createMovements() {
		System.out.println(" ");
		printTime(currentStart, currentEnd);
		PrintWriter movements = null;
		if (trks.size()>0 || (wps.size()>0 && waypoint == true) || (rtes.size()>0 && route == true)) {
			if (this.compress) {
				try {
					movements = 
						new PrintWriter(
							new GZIPOutputStream(
								new FileOutputStream(this.fileName + ".movements.gz")));
				} catch (Exception e) {
					App.exceptionHandler("Error opening ", e);
				}
			} else {
				movements = App.openPrintWriter(this.fileName + ".movements");
			}
		
			if (importHeight) {
				movements.println("#3D");
			}
			
			// if bounds changed this throws a note
			double result = (this.bounds.get("min_x")).get(0);
			boolean boundsChanged = false;
			if ((this.bounds.get("min_x")).size() > 1 ) {
				Iterator<Double> it = (this.bounds.get("min_x")).iterator();
				while (it.hasNext()) {
					double n = it.next();
					if (n != result) {
						boundsChanged =  true;
					}
				}
				if (boundsChanged) {
					System.out.println("Note: bounds changed to contain all movements");
				}
			}
			Set<String> trkkeys = this.trks.keySet();
			for (String s : trkkeys){
			
				ArrayList<GpxPoint> coordlist = trks.get(s);
				printMovements(coordlist, movements);
				
			}
			Set<String> wpkeys = this.wps.keySet();
			for (String s : wpkeys){
			
				ArrayList<GpxPoint> coordlist = wps.get(s);
				printMovements(coordlist, movements);
				
			}
			Set<String> rtekeys = this.rtes.keySet();
			for (String s : rtekeys){
			
				ArrayList<GpxPoint> coordlist = rtes.get(s);
				printMovements(coordlist, movements);
				
			}
			movements.close();
			if (this.compress) {
				System.out.println("File [" + this.fileName + ".movements.gz] created.");
			} else {
				System.out.println("File [" + this.fileName + ".movements] created.");
			}
		} 
	}
	
	/**
	* Prints movements to File
	*/
	private void printMovements(ArrayList<GpxPoint> coordlist, PrintWriter movements){
		double min_x = this.minFromDoubleList(this.bounds.get("min_x"));
		double min_y = this.minFromDoubleList(this.bounds.get("min_y"));
		long delta = 0;
		for (GpxPoint p : coordlist) {
			// check if this entry is outside duration range
			if (p.time.after(this.endtime)) {
				break;
			}
			// check if this entry is after starttime
			if (p.time.before(this.starttime)) {
				continue;
			}
            
			delta = this.calculateDateDiff(this.starttime, p.time);
			long time = delta / 1000l;
            
			p.convertedTime = time;
		}
		// This checks if multiple waypoints occupy the same timestamp and if so
		// diversifies the positions upon the second.
		for (int entry = 0; entry < coordlist.size()-1; entry++) {
			GpxPoint p = coordlist.get(entry);
            
			int occurrences = 0;
			for (GpxPoint x : coordlist) {
				if (x.convertedTime == p.convertedTime) {
					occurrences++;
				}
			}
            
			if (occurrences > 1) {
				for (int i = 1; i < occurrences; i++) {
					coordlist.get(entry+i-1).convertedTime += i*(1./occurrences);
				}
			}
		}
        
		// Probably due to double inaccuracy it can happen that the min_x/min_y shifts 
		// too little, resulting in negative coordinates. 
		// This is checked here and the shift is corrected if necessary
		for (GpxPoint p : coordlist) {
			double x = p.x - min_x;
			double y = p.y - min_y;
			if (x < 0) {
				min_x -= x + 0.001;
			}
			if (y < 0) {
				min_y -= y + 0.001;
			}
		}
		this.bubbleSort(coordlist);
		for (GpxPoint p : coordlist) {
			double x = p.x - min_x;
			double y = p.y - min_y;
			if (!importHeight) {
				movements.print(p.convertedTime + " " + x + " " + y + " ");
			} else {
				movements.print(p.convertedTime + " " + x + " " + y + " " + p.z + " ");
			}
		}
		
		movements.println();
	}

	
	private double maxFromDoubleList(ArrayList<Double> arr) {
		double result = arr.get(0);
		Iterator<Double> it = arr.iterator();
		while (it.hasNext()) {
			double n = it.next();
			if (n > result) {
				result = n;
			}
		}
		
		return result;
	}
	
	private double minFromDoubleList(ArrayList<Double> arr) {
		double result = arr.get(0);
		Iterator<Double> it = arr.iterator();
		while (it.hasNext()) {
			double n = it.next();
			if (n < result) {
				result = n;
			}
		}
		
		return result;
	}
	
	/**
	* Creates the Params File
	*/
	private void createParams() {
		double max_x = 
			this.maxFromDoubleList(this.bounds.get("max_x")) 
			- this.minFromDoubleList(this.bounds.get("min_x"));
		double max_y = 
			this.maxFromDoubleList(this.bounds.get("max_y"))
			- this.minFromDoubleList(this.bounds.get("min_y"));
			
		if (trks.size()>0 || (wps.size()>0 && waypoint == true) || (rtes.size()>0 && route == true)) {
			long delta = this.calculateDateDiff(this.starttime, this.endtime);
			long duration = delta / 1000l - 1l;
			
			if (delta < 0) {
				System.out.println("Timestamp of the end of simulation is earlier than of its start!");
				System.exit(0);
			}
		
			PrintWriter params = App.openPrintWriter(this.fileName + ".params");
			params.println("model=" + "GPXImport "+ getInfo().getFormattedVersion()); 
			params.println("x=" + max_x);
			params.println("y=" + max_y);
			if (importHeight) {
				params.println("z=" + bounds.get("max_z").get(0)); 
			}
			params.println("duration=" + duration);
			if (duration == -1) {
				System.out.println("Warning: No more than one different timestamp found!");
			}
			int nn = 0;
			nn = trks.size();
			if (waypoint) {
				nn += wps.size();
			}
			if (route) {
				nn += rtes.size();
			}
			params.println("nn=" + nn);
			params.println("crs=" + this.projCRSName);
			params.close();
			System.out.println("File [" + this.fileName + ".params] created");
		} 
		if (trks.size() <= 0 || trks == null){
			System.out.println("Waring: No <trk> Element found");
		}

	}
	private String[] tmp; 
	public boolean parseArg(char key, String val) {
		switch (key) {
		case 'f':
			tmp = val.split(" ");
			for (int i=0; i< tmp.length; i++){
				filenames.add(tmp[i]);
			}
			System.out.println(filenames.size() + " files found to parse");
			return true;
		case 'c':
			this.compress = true;
			return true;
	    case 'h':
            this.importHeight = true;
            return true;
        case 'H':
            this.defaultHeight = Double.parseDouble(val);
            return true;
		case 'w':
			this.waypoint = true;
			return true;
		case 'r':
			this.route = true;
			return true;
		case 'p':
			this.projCRSName = val;
			return true;
		case 'F':
			this.fileName = val;
			return true;
		}

		return super.parseArg(key, val);
	}
}//end class
