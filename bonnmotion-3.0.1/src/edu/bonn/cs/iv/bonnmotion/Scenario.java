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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;



//import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import edu.bonn.cs.iv.bonnmotion.models.DisasterArea;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;
import edu.bonn.cs.iv.bonnmotion.printer.Printer;
import edu.bonn.cs.iv.util.IntegerHashSet;

public class Scenario  extends App implements Model, ScenarioLink {
	
	protected ScenarioParameters parameterData = new ScenarioParameters();
	protected Building[] buildings = new Building[0]; /** Buildings */
	protected Random rand;
	public long count_rands = 0;
	private boolean isOutputDimSetExplicitly = false;
	private static boolean negativeHeightWarningShowed = false;
	
	/** if true generate() first must do transition */
	protected boolean isTransition = false;
	protected int transitionMode = 0;
	protected Scenario predecessorScenario = null;

	/** caches movements from last read(basename). null if read(basename) was not executed yet */
	public String movements = null;

	public Scenario() {
	}
	
	public Scenario(int nodes, double x, double y, double duration, double ignore, long randomSeed) {
		this(nodes, x, y, 0.0, duration, ignore, randomSeed);
	}
	
	public Scenario(int nodes, double x, double y, double z, double duration, double ignore, long randomSeed) {
		parameterData.nodes = new MobileNode[nodes];
		this.parameterData.x = x;
		this.parameterData.y = y;
		this.parameterData.z = z;
		this.parameterData.duration = duration;
		this.parameterData.ignore = ignore;
		setRand(new Random(this.parameterData.randomSeed = randomSeed));
	}

	protected Scenario(String basename) throws FileNotFoundException, IOException {
		read(basename);
		
	}

	protected Scenario(String basename, boolean haveLD) throws FileNotFoundException, IOException {
		read(basename, haveLD);
	}

	protected Scenario(String args[], Scenario _pre, Integer _transitionMode) {
		// we've got a predecessor, so a transition is needed
		predecessorScenario = _pre;
		transitionMode = _transitionMode.intValue();
		isTransition = true;
		
	}

	public static Scenario getScenario(String basename) throws FileNotFoundException, IOException {
		return new Scenario(basename);
	}



	public double getZ() {
		return parameterData.z;
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
		case 'a':
			parameterData.aFieldParams = parseDoubleArray(val);
			return true;
		case 'c' : // "circular"
			parameterData.circular = true;
			return true;
		case 'd' : // "duration"
			parameterData.duration = Double.parseDouble(val);
			return true;
		case 'i' : // "ignore" (Einschwingphase)
			parameterData.ignore = Double.parseDouble(val);
			if ( isTransition && predecessorScenario != null && parameterData.ignore != 0 ) // Req. for chainscenarios
				System.out.println( "warning: Ingore is set to " + parameterData.ignore + ". Are you sure you want this in a chained Scenario?" );
			return true;
		case 'n': // "nodes"
			parameterData.nodes = new MobileNode[Integer.parseInt(val)];
			return true;
		case 'J': // "Output-dimension"
			if (val.equals("2D") || val.equals("2d")){
				isOutputDimSetExplicitly = true;
				setOutputDimension2D();
				return true;
			}else if (val.equals("3D") || val.equals("3d")){
				isOutputDimSetExplicitly = true;
				setOutputDimension3D();
				return true;
			}else{
				return false;
			}
		case 'x' : // "x"
			parameterData.x = Double.parseDouble(val);
			return true;
		case 'y' : // "y"
			parameterData.y = Double.parseDouble(val);
			return true;
		case 'z': // "z"
			setCalculationDimension3D();
			// if z is given set Output also to 3D (standard) but only when J is not present
			if (!isOutputDimSetExplicitly){
				setOutputDimension3D();
			}
			parameterData.z = Double.parseDouble(val);
			if (parameterData.z==0){
				System.out.println("warning: Calculations are now performed in 3D, even though z == 0.\nIf you don't want this behaviour, remove z-flag.");
			}
			return true;
		case 'R' :
			parameterData.randomSeed = Long.parseLong(val);
			return true;
		default :
			return super.parseArg(key, val);
		}
	}
	
	protected boolean parseArg(String key, String val) {
		if (key.equals("model")) {
			parameterData.modelName = val;
			return true;
		} else if (key.equals("ignore") ) {
			parameterData.ignore = Double.parseDouble(val);
			return true;
		} else if (	key.equals("randomSeed") ) {
			System.out.println("randomSeed (String):"+val);
			parameterData.randomSeed = Long.parseLong(val);
			System.out.println("randomSeed (Long):"+parameterData.randomSeed);
			return true;
		} else if (	key.equals("x") ) {
			parameterData.x = Double.parseDouble(val);
			return true;
		} else if (	key.equals("y") ) {
			parameterData.y = Double.parseDouble(val);
			return true;
		} else if (key.equals("z")) {
			setCalculationDimension3D();
			// if z is given set Output also to 3D (standard) but only when J is not present
			if (!isOutputDimSetExplicitly){
				setOutputDimension3D();
			}
			parameterData.z = Double.parseDouble(val);
			return true;
		} else if(key.equals("J")){ // "Output-dimension"
			if (val.equals("2D") || val.equals("2d")){
				isOutputDimSetExplicitly = true;
				setOutputDimension2D();
				return true;
			}else if (val.equals("3D") || val.equals("3d")){
				isOutputDimSetExplicitly = true;
				setOutputDimension3D();
				return true;
			}else{
				return false;
			}
		} else if (key.equals("nn")) {
			parameterData.nodes = new MobileNode[Integer.parseInt(val)];
			return true;
		} else if (	key.equals("duration") ) {
			parameterData.duration = Double.parseDouble(val);
			return true;
		} else if (	key.equals("nbuildings") ) {
			buildings = new Building[Integer.parseInt(val)];
			return true;
		} else if (	key.equals("circular") ) {
			if (val.equals("true"))
				parameterData.circular = true;
			return true;
		} else if (key.equals("aFieldParams")) {
			parameterData.aFieldParams = parseDoubleArray(val);
			return true;
		} else {
			return false;
		}
	}

	/** Helper function for creating scenarios. */
	public Position randomNextPosition() {
		Position pos = randomNextPosition(-1., -1., -1.);
		return pos;

	}



	public Position randomNextPosition(double fx, double fy, double fz) {
		
		double x2 = 0., y2 = 0., z2 = 0, r = 0., rx = 0., ry = 0., rz = 0.;
		if (parameterData.circular) {
			x2 = parameterData.x / 2.0;
			y2 = parameterData.y / 2.0;
			if (this.parameterData.calculationDim == Dimension.THREED){
				z2 = parameterData.z / 2.0;
				r = (x2 < y2) ? ((x2 < z2) ? x2 : z2) : ((y2 < z2) ? y2 : z2);
			} else {
				z2 = 0.0;
				r = (x2 < y2) ? x2 : y2;
			}
			
		}
		Position pos = null;
		do {
			if (parameterData.aField == null) {
				rx = (fx < 0.) ? parameterData.x * randomNextDouble() : fx;
				ry = (fy < 0.) ? parameterData.y * randomNextDouble() : fy;
				rz = 0.0;
				if (this.parameterData.calculationDim == Dimension.THREED){
					rz = (fz < 0.) ? parameterData.z * randomNextDouble() : fz;
				}
				
			}
			else {
				if (this.parameterData.calculationDim == Dimension.THREED){
					/*
					 * pos = aField.getPos(randomNextDouble(), randomNextGaussian(),
					 * randomNextGaussian()); if (pos != null) { rx = pos.x; ry = pos.y;
					 */
					throw new RuntimeException("Not Implemented");
				} else {
					pos = parameterData.aField.getPos(randomNextDouble(), randomNextGaussian(), randomNextGaussian());
					if (pos != null) {
						rx = pos.x;
						ry = pos.y;
					}
				}
			}
		}
		while (((parameterData.aField != null) && (pos == null))
				|| (parameterData.circular && (Math.sqrt((rx - x2) * (rx - x2) + (ry - y2) * (ry - y2) + (rz - z2) * (rz - z2)) > r)));
		
		if (pos == null) 
			return new Position(rx, ry, rz);
		else 
			return pos;
	}
	
	

	public static void printHelp() {
		App.printHelp();
		System.out.println("Scenario:");
		System.out.println("\t-a <attractor parameters (if applicable for model)>");
		System.out.println("\t-c [use circular shape (if applicable for model)]");
		System.out.println("\t-d <scenario duration>");
		System.out.println("\t-i <number of seconds to skip>");
		System.out.println("\t-n <number of nodes>");
		System.out.println("\t-x <width of simulation area>");
		System.out.println("\t-y <height of simulation area>");
		System.out.println("\t-z <depth of simulation area>");
		System.out.println("\t-R <random seed>");
		System.out.println("\t-J <2D, 3D> Dimension of movement output");

	}


	/**
	 * Reads the base information of a scenario from a
	 * file.
	 * It is typically invoked by application to re-read the processing
	 * scenario from a generated file.
	 * @param basename Basename of the scenario
	 */
	protected String read(String basename) throws FileNotFoundException, IOException {
		String help = read(basename, false);
		return help;
	}

	/**
	 * Reads the base information of a scenario from a file. It is typically invoked by application
	 * to re-read the processing scenario from a generated file.
	 * 
	 * @param basename
	 *            Basename of the scenario
	 * @param haveLD
	 *            have pre-computed link dump or read movements.gz
	 */
	protected String read(String basename, boolean hasPrecomputedLinkDump) throws FileNotFoundException, IOException {

		paramFromFile(basename+".params");

		// read buildings
		if (buildings.length > 0) {
			readBuildings(basename);
		}

		// read movements
		StringBuilder movements = new StringBuilder();

		if (!hasPrecomputedLinkDump) {
			if (parameterData.outputDim == Dimension.THREED){
				buildScenarioFromMovementFile3D(basename, movements);
			} else {
				buildScenarioFromMovementFile2D(basename, movements);
			}
		}
		this.movements = movements.toString();
		return this.movements;
	}

	private void buildScenarioFromMovementFile3D(String basename, StringBuilder movements) throws IOException, FileNotFoundException {
		String line;
		double extendedtime = 0.0;
		double xpos = 0.0;
		double ypos = 0.0;
		double zpos = 0.0;
		double status = 0.0;
		boolean nodestart = false;
		boolean nodestop = false;
		BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(basename + ".movements.gz"))));
		int i = 0;
		int j = 0;
		while ((line = in.readLine()) != null) {
			//comment prefix
			if (line.startsWith("#")) {
				continue;
			}

			if (!(getModelName().equals(DisasterArea.getInfo().name))) {
				parameterData.nodes[i] = new MobileNode();
			}
			StringTokenizer st = new StringTokenizer(line);
			while (st.hasMoreTokens()) {
				if (getModelName().equals(DisasterArea.getInfo().name)) {
					switch (i % 5) {
					case 0:
						extendedtime = Double.parseDouble(st.nextToken());
						if(extendedtime == 0.0){
							nodestart = true;
						}
						else{
							nodestart = false;
						}
						if(extendedtime == parameterData.duration){
							nodestop = true;
						}
						else{
							nodestop = false;
						}
						break;
					case 1:
						xpos = Double.parseDouble(st.nextToken());
						break;
					case 2:
						ypos = Double.parseDouble(st.nextToken());
						break;
					case 3:
						zpos = Double.parseDouble(st.nextToken());
						break;
					case 4:
						status = Double.parseDouble(st.nextToken());
						if(nodestart){
							parameterData.nodes[j] = new MobileNode();
						}
						Position extendedpos = new Position(xpos, ypos, zpos, status);
						if(!parameterData.nodes[j].add(extendedtime, extendedpos)){
							System.out.println(extendedtime + ": " + extendedpos.x + "/" + extendedpos.y + "/" + extendedpos.z);
							throw new RuntimeException("Error while adding waypoint.");
						}
						if(nodestop){
							j++;
						}

						movements.append(" ");
						movements.append(extendedtime);
						movements.append(" ");
						movements.append(xpos);
						movements.append(" ");
						movements.append(ypos);
						movements.append(" ");
						movements.append(zpos);
						movements.append(" ");
						movements.append(status);

						if(parameterData.duration == extendedtime){
							movements.append("\n");
						}

						break;
					default:
						break;
					}
				}
				else{
					double time = Double.parseDouble(st.nextToken());
					Position pos = new Position(Double.parseDouble(st.nextToken()),
							Double.parseDouble(st.nextToken()),
							Double.parseDouble(st.nextToken()));

					if(!negativeHeightWarningShowed && pos.z < 0){
						System.err.printf("NOTE: your input contains a node with a negative z-value (%f;%f;%f).\n"+
								"The following behaviour is not tested enough. Especially be careful with the resulting statistics!\n", pos.x, pos.y, pos.z);
						negativeHeightWarningShowed = true;
					}

					if(!parameterData.nodes[i].add(time, pos)){
						System.out.println(time + ": " + pos.x + "/" + pos.y + "/" + pos.z);
						in.close();
						throw new RuntimeException("Error while adding waypoint.");
					}
				}
			}
			i++;
		}
		in.close();
	}

	private void buildScenarioFromMovementFile2D(String basename, StringBuilder movements) throws IOException, FileNotFoundException {
		String line;

		double extendedtime = 0.0;
		double xpos = 0.0;
		double ypos = 0.0;
		double status = 0.0;
		boolean nodestart = false;
		boolean nodestop = false;
		BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(basename + ".movements.gz"))));
		int i = 0;
		int j = 0;
		while ((line = in.readLine()) != null) {

			//comment prefix
			if (line.startsWith("#")) {
				continue;
			}

			if(!(getModelName().equals(DisasterArea.getInfo().name))){
				parameterData.nodes[i] = new MobileNode();
			}
			StringTokenizer st = new StringTokenizer(line);
			while (st.hasMoreTokens()) {
				if(getModelName().equals(DisasterArea.getInfo().name)){
					switch(i%4) {
					case 0:
						extendedtime = Double.parseDouble(st.nextToken());
						if(extendedtime == 0.0){
							nodestart = true;
						}
						else{
							nodestart = false;
						}
						if(extendedtime == parameterData.duration){
							nodestop = true;
						}
						else{
							nodestop = false;
						}
						break;
					case 1:
						xpos = Double.parseDouble(st.nextToken());
						break;
					case 2:
						ypos = Double.parseDouble(st.nextToken());
						break;
					case 3:
						status = Double.parseDouble(st.nextToken());
						if(nodestart){
							parameterData.nodes[j] = new MobileNode();
						}
						Position extendedpos = new Position(xpos, ypos, 0, status);
						if (!parameterData.nodes[j].add(extendedtime, extendedpos)) {
							System.out.println( extendedtime + ": " + extendedpos.x + "/" + extendedpos.y );
							in.close();
							throw new RuntimeException("Error while adding waypoint.");
						}
						if(nodestop){
							j++;
						}

						movements.append(" ");
						movements.append(extendedtime);
						movements.append(" ");
						movements.append(xpos);
						movements.append(" ");
						movements.append(ypos);
						movements.append(" ");
						movements.append(status);
						if(parameterData.duration == extendedtime){
							movements.append("\n");
						}

						break;
					default:
						break;
					}
				}
				else{
					double time = Double.parseDouble(st.nextToken());
					Position pos = new Position(Double.parseDouble(st.nextToken()),Double.parseDouble(st.nextToken()));
					if (!parameterData.nodes[i].add(time, pos)) {
						System.out.println( time + ": " + pos.x + "/" + pos.y );
						in.close();
						throw new RuntimeException("Error while adding waypoint.");
					}
				}
			}
			i++;
		}
		in.close();
	}

	private void readBuildings(String basename) throws FileNotFoundException, IOException {
		int i = 0;
		String line;
		BufferedReader buildingsReader = new BufferedReader(new InputStreamReader(new FileInputStream(basename + ".buildings")));
		// XXX: do sanity check that number of lines matches number of buildings
		while ((line = buildingsReader.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line);
			buildings[i] = new Building(Double.parseDouble(st.nextToken()),
					Double.parseDouble(st.nextToken()),
					Double.parseDouble(st.nextToken()),
					Double.parseDouble(st.nextToken()),
					Double.parseDouble(st.nextToken()),
					Double.parseDouble(st.nextToken()));
			i++;
		}
		buildingsReader.close();
	}

	public Building[] getBuilding() {
		Building[] b = new Building[buildings.length];
		System.arraycopy(buildings, 0, b, 0, buildings.length);
		return b;
	}

	public MobileNode[] getNode() {
		MobileNode[] r = new MobileNode[this.parameterData.nodes.length];
		System.arraycopy(this.parameterData.nodes, 0, r, 0, this.parameterData.nodes.length);
		return r;
	}

	// vanishes ambulace parking point nodes
	public MobileNode[] getNode(String Modelname, String basename) {
		if (Modelname.equals(DisasterArea.getInfo().name)) {
			IntegerHashSet VanishingNodes = searchVanishing(basename);

			int writtenNodes = 0;
			MobileNode[] r = new MobileNode[parameterData.nodes.length - VanishingNodes.size()];
			for (int i = 0; i < parameterData.nodes.length; i++) {
				boolean vanish = false;
				Integer nodeaddress = new Integer(i);
				if (VanishingNodes.contains(nodeaddress)) {
					vanish = true;
				}
				if (!vanish) {
					System.arraycopy(parameterData.nodes, i, r, writtenNodes, 1);
					writtenNodes++;
				}
			}
			return r;
		}
		return null;
	}

	public MobileNode getNode(int n) {
		try {
			if (parameterData.nodes[n] == null) {
				parameterData.nodes[n] = new MobileNode();
			}
			return parameterData.nodes[n];
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("Fatal error: Requesting non-existing node" + e.getLocalizedMessage());
			System.exit(-1);
			return null;
		}

	}
	
	/**
	 * Returns random double from the RandomSeed.
	 * @return double
	 */
	public double randomNextDouble() {
		count_rands++;
		return getRand().nextDouble();
	}
	
	public double randomNextDouble(final double value) {
		count_rands++;
		return (getRand().nextDouble()*value);
	}

	/**
	 * Returns 1.0 or -1.0
	 * @return 1.0 or -1.0
	 */
	public double randomNextPlusOrMinusOne() {
		count_rands++;
		if(getRand().nextBoolean()) { return 1.0; }
		else { return -1.0; }
	}

	/**
	 * Returns a pseudorandom, uniformly distributed int value between 0 (inclusive) and the specified value (exclusive), drawn from this random number generator's sequence. (Docu taken from: http://download.oracle.com/javase/1.4.2/docs/api/java/util/Random.html#nextInt%28int%29)
	 * @param n the bound on the random number to be returned. Must be positive.
	 * @return a pseudorandom, uniformly distributed int value between 0 (inclusive) and n (exclusive).
	 */
	public int randomNextInt(int n) {
		count_rands++;
		return getRand().nextInt(n);
	}

	public int randomNextInt() {
		count_rands++;
		return getRand().nextInt();
	}

	/**
	 * Returns random Gaussian from the RandomSeed
	 * @return double
	 */
	public double randomNextGaussian() {
		count_rands++;
		return getRand().nextGaussian();
	}

	/**
	 * Returns random Weibull from the RandomSeed
	 * @return double
	 */
	public double randomNextWeibull(double shape, double scale) {
		count_rands++;
		return scale * java.lang.Math.pow(-java.lang.Math.log(getRand().nextDouble()), 1.0 / shape);
	}
	
	/** Called by subclasses before they generate node movements. */
	protected void preGeneration() {
		if (this.parameterData.outputDim == Dimension.THREED){
			System.out.println("note: Output is now in 3D.");
		}
		
		parameterData.duration += parameterData.ignore;
		setRand(new Random(parameterData.randomSeed));

		if (parameterData.aFieldParams != null) {
			parameterData.aField = new AttractorField(parameterData.x, parameterData.y);
			parameterData.aField.add(parameterData.aFieldParams);
		}

		String myClass = this.getClass().getName();
		myClass = myClass.substring(myClass.lastIndexOf('.') + 1);

		if (parameterData.modelName == null) {
			parameterData.modelName = myClass;
		}
		else if (! parameterData.modelName.equals(myClass)) {
			System.out.println("model mismatch: modelName=" + parameterData.modelName + " myClass=" + myClass);
			System.exit(0);
		}
	}


	/** Called by subclasses after they generate node movements. */
	protected void postGeneration() {
		if (parameterData.ignore < 600.0 && !isTransition ) {// this is a somewhat arbitrary value :)
			System.out.println("warning: setting the initial phase to be cut off to be too short may result in very weird scenarios");
		}
		if (parameterData.ignore > 0.0) {
			cut(parameterData.ignore, parameterData.duration);
		}
		long next_seed = getRand().nextLong();
		while (Long.signum(next_seed) < 0) {
			next_seed = getRand().nextLong();
		}
		System.out.println("Next RNG-Seed =" + next_seed+ " | #Randoms = "+count_rands);
	}
	
	/** Extract a certain time span from the scenario. */
	public void cut(double begin, double end) {
		if ((begin >= 0.0) && (end <= parameterData.duration) && (begin <= end)) {
			for (int i = 0; i < parameterData.nodes.length; i++) {
				parameterData.nodes[i].cut(begin, end);
			}
			parameterData.duration = end - begin;
		}
	}

	/**
	 * @see edu.bonn.cs.iv.bonnmotion.App#go(String[])
	 */
	public void go ( String[] _args ) {
		String paramFile = _args[0];
		String[] args = new String[_args.length - 1];
		System.arraycopy(_args, 1, args, 0, args.length);
		if (paramFile != null) {
			try {
				paramFromFile(paramFile, true);
			} catch (Exception e) {
				App.exceptionHandler( "Could not open parameter file", e );
			}
		}
		parse(args);
		if ( parameterData.nodes == null ) {
			System.out.println("Please define the number of nodes.");
			System.exit(0);
		}
	}
	
	public String getModelName() {
		return parameterData.modelName;
	}

	public void setModelName( String _modelName ) {
		parameterData.modelName = _modelName;
	}
	
	public IntegerHashSet searchVanishing(String basename){

		IntegerHashSet VanishingNodes = new IntegerHashSet();
		/*
		String line;
		String fromFile;
		try{
		BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream( basename+".changes" ) ) );
		while ( (line=in.readLine()) != null ) {
			StringTokenizer st = new StringTokenizer(line);
			while(st.hasMoreTokens()){
				fromFile = st.nextToken();
				//System.out.println(fromFile);
				Integer node = new Integer(fromFile);
				VanishingNodes.add(node);
			}
		}
		in.close();
		}
		catch(Exception e){
			System.out.println("Error in searchVanishing of Scenario.java");
			System.exit(0);
		}
		 */
		return VanishingNodes;
	}
	
	public double getX() {
		return parameterData.x;
	}

	public double getY() {
		return parameterData.y;
	}

	public double getIgnore() {
		return parameterData.ignore;
	}

	public long getRandomSeed() {
		return parameterData.randomSeed;
	}

	public void setNode( MobileNode[] _node ) {
		parameterData.nodes = _node;
	}

	public int nodeCount() {
		return parameterData.nodes.length;
	}

	public int nodeCount(String Modelname, String basename){
		if(Modelname.equals(DisasterArea.getInfo().name)){
			return parameterData.nodes.length - searchVanishing(basename).size();
		}
		return parameterData.nodes.length;
	}
	
	/**
	 * Does the same job as paramFronFile but w/o showing warnings.
	 * @see Scenario#paramFromFile(String _fn)
	 * @param _fn Filename
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void paramFromFile(String _fn) throws FileNotFoundException, IOException {
		paramFromFile( _fn, false );
	}

	/**
	 * Reads arguments from specific file. Then processes
	 * the command line arguments (overrides arguments from file).<br>
	 * This Method must be implemented in every subclass.
	 * @param _fn Filename
	 * @param _warn if warnings should be shown during parsing
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void paramFromFile(String _fn, boolean _warn) throws FileNotFoundException, IOException {
		String line;
		BufferedReader in = new BufferedReader( new InputStreamReader( new FileInputStream( _fn ) ) );
		while ( (line=in.readLine()) != null ) {
			StringTokenizer st = new StringTokenizer(line, "=");
			String key = st.nextToken();
			String value = st.nextToken();
			if (! parseArg(key, value) && (_warn) ) {
				System.out.println("warning: unknown key \"" + key + "\" while parsing arguments from \"" + _fn + "\"");
			}
		}
		in.close();
	}

	
	public void setDuration(double _duration) {
		parameterData.duration = _duration;
	}
	
	public void write( String _name ) throws FileNotFoundException, IOException {
		writeParametersAndMovement(_name, null);

	}
	
	/**
	 * Writes the generated scenario and the scenario
	 * parameters to files.
	 * @param basename Basename of the output files
	 * @param params Individual parameters for each model
	 */
	public void writeParametersAndMovement(String basename, String[] params) throws FileNotFoundException, IOException {
		Printer printer = new Printer(parameterData.outputDim);
		printer.writeOutsourced(basename, parameterData, params);
	}


	public Waypoint transition(Scenario _pre, int _mode, int _nn) throws ScenarioLinkException {
		if (_pre == null) // No predecessor => We start an 0/0 @ 0.0
			return new Waypoint( 0, randomNextPosition() );

		if (_pre.parameterData.nodes.length != parameterData.nodes.length)
			throw new ScenarioLinkException("#Node does not match");

		MobileNode[] preNodes = null;
		Waypoint w = null, nw = null;

		preNodes = _pre.getNode();
		w = preNodes[_nn].getLastWaypoint();
		switch (_mode) {
		case LINKMODE_FAST :
			nw = transitionWaypointFast( w );
			break;
		case LINKMODE_MOVE :
			nw = transitionWaypointMove( w, _nn );
			break;
		default :
			throw new ScenarioLinkException("Unknown Mode");
		}
		parameterData.nodes[_nn].add(nw.time, nw.pos);
		return nw;
	}

	public Waypoint transitionWaypointFast( Waypoint _w) {
		Waypoint w = null;

		//		The predecessor Scenario is greater: if the node is outside the new field: realocate the node 
		if ( (_w.pos.x > parameterData.x) || (_w.pos.y > parameterData.y) ) {
			System.out.println( "\t\tOut!!!!  X: "+ _w.pos.x +" / Y: "+ _w.pos.y );
			double xRe =  _w.pos.x - (int)(_w.pos.x / parameterData.x) * (_w.pos.x%parameterData.x); 
			double yRe =  _w.pos.y - (int)(_w.pos.y / parameterData.y) * (_w.pos.y%parameterData.y);
			System.out.println( "\t\tNew Pos: X: " + xRe + " / Y: " + yRe);
			w = new Waypoint( 0.0, new Position( xRe, yRe) );
		} else {
			w = new Waypoint( 0.0, _w.pos );
		}

		return w;
	}

	public Waypoint transitionWaypointMove( Waypoint _w, int _nn) {
		Waypoint w = transitionWaypointFast( _w );

		if ( (w.pos.x != _w.pos.x) || (w.pos.y != _w.pos.y) ) {
			parameterData.nodes[_nn].add( 0.0, _w.pos );
			return new Waypoint(2.0, w.pos);
		} else {
			return new Waypoint( 0.0, _w.pos );
		}
	}

	public Random getRand() {
		return rand;
	}

	public void setRand(Random rand) {
		this.rand = rand;
	}
	
	public void setOutputDimension3D() {
		parameterData.outputDim = Dimension.THREED;
	}

	public void setOutputDimension2D() {
		parameterData.outputDim = Dimension.TWOD;
	}
	
	public void setCalculationDimension3D() {
		System.out.println("note: Calculations are now performed in 3D, since depth value z is provided.");
		parameterData.calculationDim = Dimension.THREED;
	}

	public void setCalculationDimension2D() {
		parameterData.calculationDim = Dimension.TWOD;
	}
	
	public ScenarioParameters getScenarioParameters() {
		return this.parameterData;
	}
	
	public double getDuration() {
		return parameterData.duration;
	}
	
	public void writeCoordinates(PrintWriter info) {
		info.println("x=" + parameterData.x);
		info.println("y=" + parameterData.y);
		info.println("z=" + parameterData.z);
	}


}
