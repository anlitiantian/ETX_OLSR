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

package edu.bonn.cs.iv.bonnmotion.models;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.models.smooth.Initialize;
import edu.bonn.cs.iv.bonnmotion.models.smooth.LocationMap;
import edu.bonn.cs.iv.bonnmotion.models.smooth.MaximumLocationsExceededException;
import edu.bonn.cs.iv.bonnmotion.models.smooth.OneDDoubleWrapper;
import edu.bonn.cs.iv.bonnmotion.models.smooth.OneDIntWrapper;
import edu.bonn.cs.iv.bonnmotion.models.smooth.SmoothException;
import edu.bonn.cs.iv.bonnmotion.models.smooth.ThreeDDoubleArrayWrapper;
import edu.bonn.cs.iv.bonnmotion.models.smooth.TwoDDoubleWrapper;
import edu.bonn.cs.iv.bonnmotion.models.smooth.TwoDIntWrapper;
import edu.bonn.cs.iv.bonnmotion.models.smooth.WrapperMaximumLengthExceededException;

/**
 * TOP-LEVEL DRIVER CLASS FOR SMOOTH THAT RUNS THE MODEL USING THE MAIN METHOD,
 * BASED ON THE CODE FROM smooth.c IN THE SMOOTH C SOURCE CODE
 * 
 */
public class SMOOTH extends Scenario {

	private static ModuleInfo info;
	static {
		info = new ModuleInfo("SMOOTH");
		info.description = "Application to construct SMOOTH mobility scenarios";
		info.major = 0;
		info.minor = 1;
		info.affiliation = ModuleInfo.TOILERS;
		info.contacts.add(ModuleInfo.BM_MAILINGLIST);
		info.authors.add("Michael Coughlin");
		info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
	}

	public static ModuleInfo getInfo() {
		return info;
	}

	private boolean setDir;
	private static Initialize init;
	private int range;
	private int clusters;
	private double alpha;
	private double beta;
	private int f_min;
	private int f_max;
	private int p_min;
	private int p_max;
	private int waypoints;
	private int max_locations;
	private double locationmapTimeInterval;
	private OneDIntWrapper n_wps;
	private double[] param;
	private OneDDoubleWrapper prev_start_time;
	private OneDDoubleWrapper pause_start_time;
	private OneDDoubleWrapper pause_end_time;
	private TwoDDoubleWrapper prev_xy;
	private TwoDDoubleWrapper next_xy;
	//private Vector<Position> crt_xy;
	private TwoDDoubleWrapper crt_xy;
	
	public static Vector<MobileNode> locationHistory;
	private ThreeDDoubleArrayWrapper locations;
	
	
	private TwoDIntWrapper node_location;
	private TwoDDoubleWrapper p;
	private int number;
	private OneDDoubleWrapper x_position;
	private OneDDoubleWrapper y_position;
	private long locationmapCurrentTime;
	private TwoDIntWrapper status;
	private OneDIntWrapper paused;
	private int locationmapStartTime;
	private TwoDDoubleWrapper ict;
	private TwoDDoubleWrapper lct;
	private TwoDDoubleWrapper cn;
	private TwoDDoubleWrapper ct;
	private TwoDDoubleWrapper fct;
	private OneDDoubleWrapper speed;
	//private Vector<Position> tmp_crnt_xy;
	private TwoDDoubleWrapper tmp_crnt_xy;
	
	private int locationmapGenIterationCount;
	private double v_velocity;
	private LocationMap locationMap;
	private static boolean printCN;
	private static boolean printICT;
	private static boolean printCT;
	private static ArrayList<Double> cnData = null;
	private static ArrayList<Double> ctData = null;
	private static ArrayList<Double> ictData = null;
	//private int currentTime;
	private int i;
	private static long next = 1;
	private static int count=0;

	/**
	 * ENUM TO HOLD THE DIFFERENT TYPES OF INPUT VALUES
	 */
	public enum ArgumentTypes {
		RANGE, CLUSTERS, ALPHA, F_MIN, F_MAX, BETA, P_MIN, P_MAX, PRINT, MAX_LOCATIONS;
	}

	/**
	 * ENUM TO HOLD THE DIFFERENT TYPES OF VARIABLES THAT CAN BE PRINTED
	 */
	public enum PrintTypes {
		CT, ICT, CN;
	}

	/**
	 * CONSTRUCTOR THAT IS CALLED BY THE MAIN METHOD TO CREATED A SMOOTH OBJECT
	 * 
	 * @param internal
	 *            BOOLEAN TO DIFFERENTIATE FROM THE NO-ARGUMENTS CONSTRUCTOR
	 */
	private SMOOTH(boolean internal) {
		smooth_default_parameter_initialization();
	}

	/**
	 * CONSTRUCTOR CALLED WHEN ARGUMENTS ARE PROVIDED DIRECTLY TO THE
	 * CONSTRUCTOR RATER THAN ON THE COMMAND LINE
	 * 
	 * @param args
	 *            ARGUMENTS TO BE PROVIDED TO SMOOTH
	 */
	public SMOOTH(String[] args) {
		smooth_default_parameter_initialization();
		initializeClusterArguments(args);
		go(args);
	}

	private void smooth_default_parameter_initialization() {
		waypoints = 1100;
		max_locations = 20000;
		locationmapTimeInterval = 1.0;
		n_wps = null;
		param = null;
		printCN = false;
		printCT = false;
		printICT = false;
//		currentTime = 1;
		alpha = -1;
		beta = -1;
		range = -1;
		clusters = -1;
		f_max = -1;
		f_min = -1;
		p_max = -1;
		p_min = -1;
		setDir = false;
	}

	/**
	 * CONSTRUCTOR THAT EXPLICITLY TAKES IN THE INPUT PARAMETERS THAT ARE USED
	 * TO INITIALIZE THE SCENARIO
	 */
	public SMOOTH(String nodes, String sim_dim_x, String sim_dim_y,
			String range, String clusters, String duration, String alpha,
			String f_min, String f_max, String beta, String p_min, String p_max) {
		smooth_default_parameter_initialization();
		String[] args = { nodes, sim_dim_x, sim_dim_y, range, clusters,
				duration, alpha, f_min, f_max, beta, p_min, p_max };
		initializeClusterArguments(args);
		go(args);
	}

	/**
	 * go METHOD TO PROVIDE SIMILARITY TO OTHER MODELS, AND TO CALL Scenario.go
	 */
	public void go(String[] args) {
		super.go(args);
		generate();
	}

	/**
	 * METHOD REQUIRED BY BONNOTION TO PARSE AN ARGUMENT BASED ON A PROVIDED KEY
	 * STRING
	 */
	protected boolean parseArg(String key, String value) {
		try {
			ArgumentTypes type = ArgumentTypes
					.valueOf(key.trim().toUpperCase());
			switch (type) {
			case RANGE:
				range = Integer.parseInt(value);
				return true;
			case CLUSTERS:
				clusters = Integer.parseInt(value);
				return true;
			case ALPHA:
				alpha = Double.parseDouble(value);
				return true;
			case F_MIN:
				f_min = Integer.parseInt(value);
				return true;
			case F_MAX:
				f_max = Integer.parseInt(value);
				return true;
			case BETA:
				beta = Double.parseDouble(value);
				return true;
			case P_MAX:
				p_max = Integer.parseInt(value);
				return true;
			case P_MIN:
				p_min = Integer.parseInt(value);
				return true;
			case PRINT:
				if (value.toUpperCase().compareTo("CN") == 0) {
					printCN = true;
				} else if (value.toUpperCase().compareTo("CT") == 0) {
					printCT = true;
				} else if (value.toUpperCase().compareTo("ICT") == 0) {
					printICT = true;
				} else if (value.toUpperCase().compareTo("ALL") == 0) {
					printICT = true;
					printCN = true;
					printCT = true;
				} else {
					System.out
							.println("Incorrect console print out argument. Most be one of: CN, CT, ICT, ALL. Exiting");
					System.exit(0);
				}
				return true;
			case MAX_LOCATIONS:
				max_locations = Integer.parseInt(value);
				return true;
			default:
				return super.parseArg(key, value);
			}
		} catch (IllegalArgumentException e) {
			return super.parseArg(key, value);
		}
	}

	/**
	 * METHOD REQUIRED BY BONNMOTION TO PARSE AN ARGUMENT BASED ON A GIVEN CHAR
	 * KEY
	 */
	protected boolean parseArg(char key, String val) {
		switch (key) {
		case 'g':
			return parseArg("range", val);
		case 'h':
			return parseArg("clusters", val);
		case 'k':
			return parseArg("alpha", val);
		case 'l':
			return parseArg("f_min", val);
		case 'm':
			return parseArg("f_max", val);
		case 'o':
			return parseArg("beta", val);
		case 'p':
			return parseArg("p_min", val);
		case 'q':
			return parseArg("p_max", val);
		case 's':
			return parseArg("print", val);
		case 'd':
			return parseArg("duration", val);
		case 't':
			return parseArg("max_locations", val);
		default:
			return super.parseArg(key, val);
		}
	}

	/**
	 * METHOD REQUIRED BY BONNMOTION THAT PRINTS A DESCRIPTION OF POSSIBLE INPUT
	 * ARGUMENTS
	 */
	public static void printHelp() {
		System.out.println(getInfo().toDetailString());
		System.out
				.println("An input file may be specified with one variable assignment on each line.\nEach line must follow the format:\n\n\tvariableName=variableValue\n");
		System.out
				.println("To specify a file, use the command line option:\n\n\t-I <fileName>\n");
		System.out.println("The arguments that must be specified are:\n");
		System.out.println("\tnn");
		System.out.println("\tx");
		System.out.println("\ty");
		System.out.println("\tduration");
		System.out.println("\trange");
		System.out.println("\tclusters");
		System.out.println("\talpha");
		System.out.println("\tf_min");
		System.out.println("\tf_max");
		System.out.println("\tbeta");
		System.out.println("\tp_min");
		System.out.println("\tp_max\n");
		System.out.println("Optional arguments:\n");
		System.out.println("\tprint\n");
		System.out.println("\tmaximum locations\n\n");
		System.out
				.println("The following information applies to command-line options.\n");
		Scenario.printHelp();
		System.out.println(getInfo().name + ":");
		System.out.println("\t-g <range>");
		System.out.println("\t-h <clusters>");
		System.out.println("\t-k <alpha>");
		System.out.println("\t-l <f_min>");
		System.out.println("\t-m <f_max>");
		System.out.println("\t-o <beta>");
		System.out.println("\t-p <p_min>");
		System.out.println("\t-q <p_max>");
		System.out
				.println("\t-s <print> -options to print to the console. May be one of CN, CT, ICT, or ALL. Is not required.");
		System.out.println("\t-t <maximum_locations> -option to specify the maximum number of locations. Is not required. The default is 20000.");
	}

	/**
	 * MAIN METHOD FOR SIMILAR TO IN C. INITIALIZES VARIABLES FROM COMMAND LINE
	 * ARGUMENTS AND MAKES CALLS TO GEN, CLUSTER AND LOCATIONMAP
	 * 
	 * @param args
	 *            ARRAY CONTAINING COMMAND LINE ARGUMENTS
	 */
	public static void main(String[] args) {
		SMOOTH smooth;
		smooth = new SMOOTH(true);
		smooth.mysrand(smooth.parameterData.randomSeed);
		smooth.initializeClusterArguments(args);
		smooth.generate();
	}

	public double getNextDouble() {
		return randomNextDouble();
//		return rand()/(32767 + 1.0);
	}

	/**
	 * METHOD THAT IS CALLED TO GENERATE THE MODEL. ALSO CALLS
	 * Scenario.preGeneration TO INITIALIZE THE RANDOM NUMBER GENERATORS
	 */
	public void generate() {
		convertDurationToHours();
		preGeneration();
		parameterData.duration -= parameterData.ignore;
		mysrand(parameterData.randomSeed);
		runSmooth();
		//System.out.println(randomSeed);
	}

	/**
	 * METHOD TO RUN THE DIFFERENT ELEMENTS OF SMOOTH, BASED ON AN INPUT ARRAY
	 * OF STRINGS
	 * 
	 * @param args
	 *            ARRAY CONTAINING SMOOTH ARGUMENTS
	 */
	private void runSmooth() {
		/*
		 * CALCULATE WEIGHT FOR EVERY CLUSTER AND ASSIGN LANDMARKS BY CALLING
		 * "cluster.c"
		 */
		try {
			runCluster();
		} catch (WrapperMaximumLengthExceededException e) {
			encounteredIncorrectlyInitializedWrapper(e);
		}
		/*
		 * INITIAL PLACEMENT OF MOBILE NODES OVER THE SIMULATION ARE; W.R.T.
		 * LANDMARKS CREATED FOR CLUSTERS
		 */
		try {
			runGenCode();
		} catch (WrapperMaximumLengthExceededException e) {
			encounteredIncorrectlyInitializedWrapper(e);
		}
		/* INITIAL PLACEMENT DONE */
		try {
			initializeLocationmapArguments();
		} catch (WrapperMaximumLengthExceededException e) {
			encounteredIncorrectlyInitializedWrapper(e);
		}
		try {
			runLocationmapCode();
		} catch (WrapperMaximumLengthExceededException e) {
			encounteredIncorrectlyInitializedWrapper(e);
		} catch(MaximumLocationsExceededException m) {
			System.out.println("The maximum number of locations for this scenario has been exceeded.\n" +
					"Please re-run with a larger maximum number of locations or a shorter duration.\n" +
					"All data collected to this point has been saved.");
			locationmapCurrentTime = (long) parameterData.duration;
			printLocationMapResults(true);
		} catch (SmoothException e) {
			System.out.println("Encountered unknown exception");
			return;
		}

	}

	/**
	 * METHOD TO INITIALIZE VARIABLES NEEDED BY locationmap, AND TO CALL
	 * locationmap
	 * @throws SmoothException 
	 */
	private void runLocationmapCode() throws SmoothException {
		for (locationmapCurrentTime = locationmapStartTime; locationmapCurrentTime <= parameterData.duration; locationmapCurrentTime += locationmapTimeInterval) {

			/* CALL locationmap, EXTRACT DATA, AND SET UP TEMPORARY VALUES */
			runAndExtractLocationMap();
			/* PRINT OUT DATA FROM locationmap, SPECIFICALLY CN */
			printLocationMapResults(false);

		}/* END FOR duration */
	}

	/**
	 * PRINTS THE CALCULATED CONTACT NUMBERS FOR EACH NODE AFTER LOCATIONMAP HAS
	 * BEEN CALLED COMPLETELY
	 */
	private void printLocationMapResults(boolean forceOutput) {
		if (locationmapCurrentTime == parameterData.duration || forceOutput) /*
												 * USE IF NEED TO CALCULATE THE
												 * CONTACT NUMBER (CNS)
												 * DISTRIBUTION.
												 */
		{

			int try1 = 0, try2 = 0;

			for (try1 = 0; try1 < parameterData.nodes.length; try1++) {

				for (try2 = 0; try2 < parameterData.nodes.length; try2++) {
					if (try1 != try2) {
						/*
						 * PRINT NUMBER OF CONTACTS MADE BY EVERY PAIR OF MOBILE
						 * NODES
						 */
						if (cn.get(try1, try2) > 0) {
							output(PrintTypes.CN, cn.get(try1, try2));
						}
					}/* END IF */
				}/* END FOR try2 */
			}/* END FOR try1 */

		}/* END IF duration */
	}

	/**
	 * CALLS runLocationmap AND SETS UP THE TEMP VARIABLES THAT WILL BE USED IN
	 * PRINTLOCATIONMAPRESULTS
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private void runAndExtractLocationMap() throws SmoothException {
		for (locationmapGenIterationCount = 0; locationmapGenIterationCount < parameterData.nodes.length; locationmapGenIterationCount++) {
//			updateCurrentTime(locationmapGenIterationCount);
			runLocationmap();
			if (locationmapCurrentTime == locationmapStartTime) {
				/*tmp_crnt_xy.add(locationmapGenIterationCount,
						crt_xy.get(locationmapGenIterationCount));*/
				tmp_crnt_xy.set(locationmapGenIterationCount, 0, crt_xy.get(locationmapGenIterationCount, 0));
				tmp_crnt_xy.set(locationmapGenIterationCount, 1, crt_xy.get(locationmapGenIterationCount, 1));
			}

			else {
				v_velocity = speed.get(0);
				if (v_velocity > 0 && v_velocity < 1) {
					v_velocity = 1;
				}
				/*tmp_crnt_xy.remove(locationmapGenIterationCount);
				tmp_crnt_xy.add(locationmapGenIterationCount,
						crt_xy.get(locationmapGenIterationCount));*/
				tmp_crnt_xy.set(locationmapGenIterationCount, 0, crt_xy.get(locationmapGenIterationCount, 0));
				tmp_crnt_xy.set(locationmapGenIterationCount, 1, crt_xy.get(locationmapGenIterationCount, 1));
			}
		}
	}

	/**
	 * METHOD TO OUTPUT A DOUBLE TO EITHER THE CONSOLE OF TO THE CORRECT OUTPUT
	 * FILE
	 * 
	 * @param cn2
	 *            THE TYPE OF VARIABLE TO BE PRINTED
	 * @param d
	 *            THE DOUBLE TO BE PRINTED
	 */
	public static void output(PrintTypes cn2, double d) {
		if (ctData == null) {
			ctData = new ArrayList<Double>();
		}
		if (cnData == null) {
			cnData = new ArrayList<Double>();
		}
		if (ictData == null) {
			ictData = new ArrayList<Double>();
		}
		switch (cn2) {
		case CN:
			if (printCN) {
				if (printCT && printCN && printICT) {
					System.out.println("CN: " + d);
				} else {
					System.out.println(d);
				}
			}
			cnData.add(d);
			break;
		case CT:
			if (printCT) {
				if (printCT && printCN && printICT) {
					System.out.println("CT: " + d);
				} else {
					System.out.println(d);
				}
			}
			ctData.add(d);
			break;
		case ICT:
			if (printICT) {
				if (printCT && printCN && printICT) {
					System.out.println("ict: " + d);
				} else {
					System.out.println(d);
				}
			}
			ictData.add(d);
			break;
		}
	}

	/**
	 * METHOD REQUIRED BY BONNMOTION TO WRITE PARAMETERS TO A USER-SPECIFIED
	 * FILE WILL ALSO PRINT AN OUTPUT FILE, basename.output THAT IS FORMATTED
	 * FOR IMPORT INTO Excel OR Matlab, THAT CONTAINS THE VALUES ACCUMULATED In
	 * CT, CN and ICT
	 */
	public void write(String basename) throws FileNotFoundException,
			IOException {
		writeOutputFile(basename);
		for (int j = 0; j < locationHistory.size(); j++) {
			parameterData.nodes[j] = locationHistory.get(j);
		}
		writeParametersFile(basename);
	}

	/**
	 * CREATES THE PARAMETER FILE, CONTAINING THE INPUT PARAMETERS TO SMOOTH
	 * 
	 * @param basename
	 *            basename OF OUTPUT FILE
	 * @throws FileNotFoundException
	 *             THROWN IF THE FILE CANNOT BE FOUND
	 * @throws IOException
	 */
	private void writeParametersFile(String basename)
			throws FileNotFoundException, IOException {
		String print;
		if (printCT && printCN && printICT) {
			print = "ALL";
		} else if (printCN) {
			print = "CN";
		} else if (printICT) {
			print = "ICT";
		} else if (printCT) {
			print = "CT";
		} else {
			print = "Not specified";
		}
		String[] parameters = { "range=" + range, "clusters=" + clusters,
				"alpha=" + alpha, "f_min=" + f_min, "f_max=" + f_max,
				"beta=" + beta, "p_min=" + p_min, "p_max=" + p_max,
				"print=" + print, "max_locations=" + max_locations };
		super.writeParametersAndMovement(basename, parameters);
	}

	/**
	 * WRITES DATA TO THE SUPPLIED OUTPUT FILE NAME OF FORMAT: basename.output
	 * VALUES ARE WRITTEN AS TAB-SEPERATED VALUES, AND SO WILL NOT BE EASY TO
	 * READ ARE MEANT TO BE IMPORTED INTO Excel OR Matlab, AND EXTRA SPACING
	 * WILL CAUSE THE DATA TO NOT BE IMPORTED CORRECTLY.
	 * 
	 * @param basename
	 *            basename OF OUTPUT FILE
	 * @throws FileNotFoundException
	 *             THROWN IF THE FILE CANNOT BE FOUND
	 */
	private void writeOutputFile(String basename) throws FileNotFoundException {
		PrintWriter info = new PrintWriter(new FileOutputStream(basename
				+ ".results"));
		String[] p;
		int cnLength = cnData.size();
		int ctLength = ctData.size();
		int ictLength = ictData.size();
		int maxSize = Math.max(cnLength, ictLength);
		maxSize = Math.max(maxSize, ctLength);
		p = new String[maxSize + 1];
		p[0] = "CN\tICT\tCT";
		for (int i = 1; i < maxSize + 1; i++) {
			String line = "";
			if (i < cnData.size()) {
				if (cnData.get(i) < 9) {
					line = line + cnData.get(i) + "\t";
				} else {
					line = line + cnData.get(i) + "\t";
				}
			} else {
				line = line + "\t";
			}
			if (i < ictData.size()) {
				if (ictData.get(i) < 100000)
					line = line + ictData.get(i) + "\t";
				else
					line = line + ictData.get(i) + "\t";
			} else {
				line = line + "\t";
			}
			if (i < ctData.size()) {
				line = line + ctData.get(i);
			}
			p[i] = line;
		}
		for (int i = 0; i < p.length; i++) {
			info.println(p[i]);
		}
		info.close();
		File outFile = new File(basename + ".results");
		System.out.println("SMOOTH data written to file: "
				+ outFile.getAbsolutePath());
	}

	/**
	 * METHOD RESPONSIBLE FOR BOTH RUNNING locationmap, AND EXTRACTING THE DATA
	 * FROM locationmap
	 * @throws SmoothException 
	 */
	private void runLocationmap() throws SmoothException {
		
		locationMap = new LocationMap(param, pause_end_time,
				locationmapGenIterationCount, locationmapCurrentTime, paused,
				prev_start_time, prev_xy, next_xy, max_locations, locations,
				(int) parameterData.x, (int) parameterData.y, node_location, pause_start_time, crt_xy,
				speed, parameterData.nodes.length, range, status, cn, fct, lct, ict, ct, this);
		locationMap.locationMap();
		extractLocationmapFields();
	}

//	private void updateCurrentTime(double newTime) {
//		currentTime = (int) newTime;		
//	}

	/**
	 * METHOD THAT PERFORMS THE EXTRACTION OF THE FIELDS STORED IN LOCATIONMAP
	 */
	private void extractLocationmapFields() {
		pause_end_time = locationMap.getPause_end_time();
		node_location = locationMap.getNode_location();
		paused = locationMap.getPaused();
		prev_start_time = locationMap.getPrev_start_time();
		prev_xy = locationMap.getPrev_xy();
		next_xy = locationMap.getNext_xy();
		locations = locationMap.getLocations();
		pause_start_time = locationMap.getPause_start_time();
		crt_xy = locationMap.getCrt_xy();
		speed = locationMap.getSpeed();
		status = locationMap.getStatus();
		cn = locationMap.getCn();
		fct = locationMap.getFct();
		lct = locationMap.getLct();
		ict = locationMap.getIct();
		ct = locationMap.getCt();
	}

	/**
	 * RECORDS THE CURRENT NUMBER OF CALLS TO THIS METHOD SINCE THE START OF THE
	 * MODEL. IS USED TO PROVIDE UNIQUE TIME VALUES TO THE CREATION OF WAYPOINTS
	 * FOR THE MOBILE NODES
	 */
//	public int getCurrentTime() {
//		return currentTime;
//	}

	/**
	 * METHOD THAT INITIALIZES THE VARIABLES THAT ARE NEEDED TO CONSTRUCT A
	 * LOCATIONMAP OBJECT
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private void initializeLocationmapArguments() throws WrapperMaximumLengthExceededException {
		locationmapCurrentTime = 0;
		status = new TwoDIntWrapper(parameterData.nodes.length);
		int h1 = 0;
		int h2 = 0;
		paused = new OneDIntWrapper(parameterData.nodes.length);
		ict = new TwoDDoubleWrapper(parameterData.nodes.length);
		lct = new TwoDDoubleWrapper(parameterData.nodes.length);
		cn = new TwoDDoubleWrapper(parameterData.nodes.length);
		ct = new TwoDDoubleWrapper(parameterData.nodes.length);
		fct = new TwoDDoubleWrapper(parameterData.nodes.length);
		v_velocity = 1.0;
		double vel = 0;
		speed = new OneDDoubleWrapper(1);
		speed.set(0, vel);
		//tmp_crnt_xy = new Vector<Position>(); 
		/*
											 * KEEPS TRACK OF THE CURRENT
											 * LOCATION RETURNED BY
											 * "locationmap.c"
											 */
		tmp_crnt_xy = new TwoDDoubleWrapper(parameterData.nodes.length, 2);
		for (h1 = 0; h1 < parameterData.nodes.length; h1++) {
			paused.set(h1, 0);
			for (h2 = 0; h2 < parameterData.nodes.length; h2++) {
				status.set(h1, h2, -1);
				lct.set(h1, h2, 0); /*
									 * lct DENOTES THE LAST CONTACT TIME FOR A
									 * PAIR OF MOBILE NODES
									 */
				ict.set(h1, h2, 0); /*
									 * ict DENOTES THE INTER-CONTACT TIME FOR A
									 * PAIR OF MOBILE NODES
									 */
				ct.set(h1, h2, 0); /*
									 * ct DENOTES THE DURATION OF THE LAST
									 * CONTACT BETWEEN A PAIR OF MOBILE NODES
									 */
				cn.set(h1, h2, 0); /*
									 * cn DENOTES THE NUMBER OF TIMES A PAIR OF
									 * MOBILE NODES HAVE CONNECTED DURING THE
									 * SIMULATION TIME
									 */
				fct.set(h1, h2, 0); /*
									 * fct DENOTES THE FIRST TIME A PAIR OF
									 * MOBILE NODES CONNECTED AFTER A
									 * DISCONNECTION
									 */
			}
		}
	}

	/**
	 * INITIALIZES THE ARGUMENTS NEEDED TO CREATE AN INITIALIZE OBJECT AND THEN
	 * PERFORM A CORRECT CALL TO INITALIZE.CLUSTER
	 * 
	 * @param args
	 *            THE COMMAND LINE ARGUMENTS NEEDED FOR INITIAL EXTRACTION
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private void initializeClusterArguments(String[] args) {
		if (args == null || args.length == 0) {
			printIncorrectArgumentMessage();
		} else if (args[0] == null) {
			if (args.length < 25 || args.length > 39 || args.length % 2 == 0) {
				printIncorrectArgumentMessage();
			}
			for (int i = 1; i < args.length; i = i + 2)
				parseArg(args[i].substring(1).charAt(0), args[i + 1]);
		} else if (args.length == 1) {
			try {
				paramFromFile(args[0], true);
			} catch (FileNotFoundException e) {
				System.out.println("Could not find file: " + args[0]
						+ "\nPlease verify file location and rerun.");
				System.exit(0);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (args.length != 8 && args.length != 9 && args.length != 12
				&& args.length != 13) {
			printIncorrectArgumentMessage();
		} else {
			/*
			 * EXECUTE IF THE PARAMETERS ARE PROVIDED DIRECTLY TO SMOOTH ON THE
			 * COMMAND LINE THEREFORE, THE ORDERING OF THE ARGUMENTS IS FIXED,
			 * AND THE USER KNOWS WHAT THE ORDER IS
			 */
			/* CASE 1: ALL ARGUMENTS SENT DIRECTLY TO SMOOTH */
			if (args.length == 12 || args.length == 13 || args.length == 14) {
				parseArg('n', args[0]);
				parseArg('x', args[1]);
				parseArg('y', args[2]);
				parseArg("range", args[3]);
				parseArg("clusters", args[4]);
				parseArg('d', args[5]);
				parseArg("alpha", args[6]);
				parseArg("f_min", args[7]);
				parseArg("f_max", args[8]);
				parseArg("beta", args[9]);
				parseArg("p_min", args[10]);
				parseArg("p_max", args[11]);
				if (args.length == 13){
					//check if the optional 13th parameter is max_locations or print type
					try{
						//if it is an integer, parse as max_locations
						Integer.parseInt(args[12]);
						parseArg("max_locations", args[12]);
					} catch(NumberFormatException e){
						//if it is not an integer, asssume that it is the print option
						parseArg("print", args[12]);
					}
					
				}
				//max_locations provided as optional 14th parameter. 
				if(args.length == 14){
					parseArg("max_locations", args[12]);
				}
			}
			/* CASE 2: ONLY SOME ARGUMENTS SENT TO SMOOTH */
			else {
				parseArg("range", args[0]);
				parseArg("clusters", args[1]);
				parseArg("alpha", args[2]);
				parseArg("f_min", args[3]);
				parseArg("f_max", args[4]);
				parseArg("beta", args[5]);
				parseArg("p_min", args[6]);
				parseArg("p_max", args[7]);
				if (args.length == 9){
					//check if the optional 9th parameter is max_locations or print type
					try{
						//if it is an integer, parse as max_locations
						Integer.parseInt(args[8]);
						parseArg("max_locations", args[8]);
					} catch(NumberFormatException e){
						//if it is not an integer, asssume that it is the print option
						parseArg("print", args[8]);
					}
					
				}
				//max_locations provided as optional 10th parameter. 
				if(args.length == 10){
					parseArg("max_locations", args[9]);
				}
			}
		}
		if (range < 0 || clusters < 0 || alpha < 0 || f_min < 0 || f_max < 0
				|| beta < 0 || p_min < 0 || p_max < 0) {
			printIncorrectArgumentMessage();
		}
		param = new double[6];
		param[0] = alpha;
		param[1] = beta;
		param[2] = f_min;
		param[3] = f_max;
		param[4] = p_min;
		param[5] = p_max; /* SEND IT DIRECTLY TO locationmap.c */
		i = 0;
		int j = 0;
		p = new TwoDDoubleWrapper(clusters, 2);
		/*
		 * CONTAINS START TIME (IN SECONDS) OF THE CURRENT FLIGHT FOR A MOBILE
		 * NODE
		 */
		prev_start_time = new OneDDoubleWrapper(parameterData.nodes.length);
		/*
		 * CONTAINS STARTING TIME (IN SECONDS) OF THE CURRENT PAUSE PERIOD
		 * STARTED FOR A MOBILE NODE
		 */
		pause_start_time = new OneDDoubleWrapper(parameterData.nodes.length);
		/*
		 * CONTAINS ENDING TIME (IN SECONDS) OF THE CURRENT PAUSE PERIOD FOR A
		 * MOBILE NODE
		 */
		pause_end_time = new OneDDoubleWrapper(parameterData.nodes.length);
		/* CONTAINS THE PREVIOUS LOCATION VISITED BY A MOBILE NODE */
//		prev_xy = new Vector<Position>();
		prev_xy = new TwoDDoubleWrapper(parameterData.nodes.length, 2);
		
		/* CONTAINS THE NEW LOCATION TO BE VISITED BY A MOBILE NODE */
//		next_xy = new Vector<Position>();
		next_xy = new TwoDDoubleWrapper(parameterData.nodes.length, 2);
		
		/* CONTAINS THE CURRENT LOCATION OF A MOBILE NODE */
		//crt_xy = new Vector<Position>();
		crt_xy = new TwoDDoubleWrapper(parameterData.nodes.length, 2);

		/*
		 * "locations" STORES THE (X,Y) COORDINATES FOR EVERY LOCATION VISITED
		 * BY A MOBILE NODE.
		 */
		locationHistory = new Vector<MobileNode>();
		
		locations = new ThreeDDoubleArrayWrapper(parameterData.nodes.length, max_locations, 2);
		
		for (int l = 0; l < parameterData.nodes.length; l++) {
			MobileNode current = new MobileNode();
			locationHistory.add(current);
		}

		/*
		 * "node_location" STORES THE NUMBER OF TIMES EACH LOCATION IS VISITED
		 * BY A MOBILE NODE.
		 */
		node_location = new TwoDIntWrapper(parameterData.nodes.length, max_locations);

		/* INITIALIZE "locations" AND "node_location" */
		try{
			for (i = 0; i < parameterData.nodes.length; i++) {
				for (j = 0; j < max_locations; j++) {
					locations.set(i, j, 0, -1);
					locations.set(i, j, 1, -1);
					node_location.set(i, j, 0);
				}
				//removing addition of initial location
//				locationHistory.get(i).add(0, new Position(-1, -1));
			}
	
			for (i = 0; i < parameterData.nodes.length; i++) {
				prev_start_time.set(i, 0);
				pause_start_time.set(i, 0);
				pause_end_time.set(i, 0);
			}
			n_wps = new OneDIntWrapper(clusters);
		} catch(WrapperMaximumLengthExceededException e){
			encounteredIncorrectlyInitializedWrapper(e);
		}
	}

	private void encounteredIncorrectlyInitializedWrapper(
			WrapperMaximumLengthExceededException e) {
		System.out.println("An array was not intialized correctly. Please re-run");
		System.exit(0);		
	}

	private void convertDurationToHours() {
		if (!setDir) {
			//original line from C source:
			//duration=atoi(argv[6])*3600;
			
			//assuming that duration is specified to SMOOTH in hours:
//			this.duration = this.duration * 3600;
			
			//assuming that duration is specified to SMOOTH in seconds
			this.parameterData.duration = this.parameterData.duration;
		}
		setDir = true;
	}

	/**
	 * PRINTS A MESSAGE WHEN INCORRECT ARGUMENTS ARE DETECTED
	 */
	private void printIncorrectArgumentMessage() {
		System.out.print("\n\n\n");
		System.out.print("*****************************\n");
		System.out
				.println("ERROR: SMOOTH USAGE\nThe number of arguments is incorrect");
		System.out.println("Please reference following help information:\n\n");
		printHelp();
		System.exit(0);

	}

	/**
	 * METHOD THAT INITIALIZES THE VARIABLES IN ORDER TO CALL Initialize.gen, AS
	 * WELL AS CALL Initialize.gen AND THEN EXTRACT THE DATA GENERATED BY THE
	 * CALL
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private void runGenCode() throws WrapperMaximumLengthExceededException {
		for (locationmapGenIterationCount = 0; locationmapGenIterationCount < parameterData.nodes.length; locationmapGenIterationCount++) {
			x_position = new OneDDoubleWrapper(1);
			y_position = new OneDDoubleWrapper(1);

			/*
			 * *********GROUPING OF MOBILE NODES DONE FOR INFOCOM'05 AND '06
			 * SCENARIOS***********
			 */
			number = -1;
			if (locationmapGenIterationCount >= 0
					&& locationmapGenIterationCount < 10) {
				number = 0;
			} else if (locationmapGenIterationCount >= 10
					&& locationmapGenIterationCount < 16) {
				number = 1;
			} else if (locationmapGenIterationCount >= 16
					&& locationmapGenIterationCount < 20) {
				number = 2;
			} else if (locationmapGenIterationCount >= 20
					&& locationmapGenIterationCount < 24) {
				number = 3;
			} else {
				number = 4;
			}

			/*
			 * *********** CALL "gen.c" for INITIAL PLACEMENT OF MOBILE
			 * NODES****************
			 */
			runGen();
			locationHistory.get(locationmapGenIterationCount).add(0,
					new Position(x_position.get(0), y_position.get(0)));
			locations.set(locationmapGenIterationCount, 0, 0, x_position.get(0));
			locations.set(locationmapGenIterationCount, 0, 1, y_position.get(0));
			node_location.set(locationmapGenIterationCount, 0,
					node_location.get(locationmapGenIterationCount, 0) + 1);

			/* INITIALIZE ALL THREE; CHANGED LATER IN "locationmap.c" */
			/*crt_xy.add(locationmapGenIterationCount,
					new Position(x_position.get(0), y_position.get(0)));*/
			crt_xy.set(locationmapGenIterationCount, 0, x_position.get(0));
			crt_xy.set(locationmapGenIterationCount, 1, y_position.get(0));
			
//			prev_xy.add(locationmapGenIterationCount,
//					new Position(x_position.get(0), y_position.get(0)));
//			next_xy.add(locationmapGenIterationCount,
//					new Position(x_position.get(0), y_position.get(0)));
			prev_xy.set(locationmapGenIterationCount, 0, x_position.get(0));
			prev_xy.set(locationmapGenIterationCount, 1, y_position.get(0));
			next_xy.set(locationmapGenIterationCount, 0, x_position.get(0));
			next_xy.set(locationmapGenIterationCount, 1, y_position.get(0));
			
			
		}// END for

	}

	/**
	 * METHOD THAT CONSTRUCTS A NEW INITIALIZE OBJECT AND RUNS Initialize.gen,
	 * AND THEN EXTRACTS THE DATA
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private void runGen() throws WrapperMaximumLengthExceededException {
		init = new Initialize(clusters, n_wps, number, (int) parameterData.x, (int) parameterData.y, range,
				p, x_position, y_position, waypoints, this);
		init.gen();
		extractInitFields();
	}

	/**
	 * METHOD THAT CONSTRUCTS A NEW Initialize OBJECT, CALLS Initialize.cluster,
	 * AND THEN EXTRACTS THE DATA FROM THE OBJECT
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private void runCluster() throws WrapperMaximumLengthExceededException {
		init = new Initialize(clusters, n_wps, number, (int) parameterData.x, (int) parameterData.y, range,
				p, x_position, y_position, waypoints, this);
		init.cluster();
		extractInitFields();
	}

	/**
	 * METHOD THAT EXTRACTS THE DATA STORED IN THE FIELDS OF THE Initialize
	 * OBJECT
	 */
	private void extractInitFields() {
		clusters = init.getClusters();
		n_wps = init.getN_wps();
		p = init.getP();
		x_position = init.getX_position();
		y_position = init.getY_position();
	}

	private static int rand() {
		count++;
		next = next * 1103515245 + 12345;
		int retur = (int) ((next/65536) % 32768); 
		if(retur < 0) {
			retur = -retur;
		}
		System.out.println(count);
//		System.out.println(retur);
		if(count==41844)
			count=41844;
	    return retur;
	}
	
	
	private void mysrand(long seed) {
		next = seed;
	}
}