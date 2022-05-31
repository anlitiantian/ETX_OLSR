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

package edu.bonn.cs.iv.bonnmotion.models.da;

import java.util.LinkedList;
import java.awt.Polygon;

import edu.bonn.cs.iv.bonnmotion.Position;

public abstract class CatastropheArea extends Polygon {
	private static final long serialVersionUID = 1524633469689511126L;

	protected static boolean debug = false;

	double[] Positions = null;
	public Position entry;
	public Position exit;
	public Position borderentry;
	public Position borderexit;
	public Position[] corners = null;
	public int type;
	public int wantedgroups;
	public int locatedgroups;
	public int[] groupsize = new int[2];
	public double[] minspeed = new double[2];
	public double[] maxspeed = new double[2];
	public int numtransportgroups;
	public int assignedtransportgroups;
	public Integer neighborAreaPos = null;
	public LinkedList<LinkedList<Position>> allways = new LinkedList<LinkedList<Position>>();
	
	/*
		type == 0: "incident location" / Schadensstelle
		type == 1: "patients waiting for treatment area" / Verletztenablage
		type == 2: "casualties clearing station" / Behandlungsplatz
		type == 3: "technical operational command" / technische Einsatzleitung
		type == 4: "ambulance parking point" / Rettungsmittelhalteplatz
	 */
	
	/**
	 * Returns a CatastropheArea instance of the correct type for Positions
	 */
	public static CatastropheArea GetInstance(double[] Positions) {
		int type = (int)Positions[Positions.length - 3];
		switch (type) {
			case 0: 
				return new IncidentLocation(Positions);
			case 1:
				return new PatientsWaitingForThreatmentArea(Positions);
			case 2:
				return new CasualtiesClearingStation(Positions);
			case 3:
				return new TechnicalOperationalCommand(Positions);
			case 4:
				return new AmbulanceParkingPoint(Positions);
			default:
				throw new IllegalArgumentException("Unknown Type");
		}
	}

	protected void InitializeSpecificValues(double[] Positions) {
		if (Positions.length < 7) {
			System.out.println("Please specify more positions for area!\naborting...");
			System.exit(0);
		}
		// 2 Positions before values are entry and exit point of the area
		corners = new Position[(Positions.length - 7)/2];
		for(int i = 0; i < Positions.length - 7; i = i+2){
			this.addPoint((int)Positions[i], (int)Positions[i+1]);
			corners[i/2] = new Position((int)Positions[i], (int)Positions[i+1]);
			if (debug) System.out.println ("("+(int)Positions[i]+";"+(int)Positions[i+1]+")");
		}
	}
	
	/**
	 * Sets default values for groupsize, minspeed and maxspeed
	 */
	protected abstract void SetDefaultValues();
	
	protected CatastropheArea(double[] Positions) {
		super();
		
		// last 3 values are type, wanted groups and number of transportgroups
		this.type = (int)Positions[Positions.length - 3];
		
		this.InitializeSpecificValues(Positions);
		
		this.Positions = Positions;
		this.entry = new Position(Positions[Positions.length - 7], Positions[Positions.length - 6]);
		this.exit = new Position(Positions[Positions.length - 5], Positions[Positions.length - 4]);
		this.wantedgroups = (int)Positions[Positions.length - 2];
		this.locatedgroups = 0;
		this.numtransportgroups = (int)Positions[Positions.length - 1];
		this.assignedtransportgroups = 0;
		if (debug) System.out.println ("Entry:("+this.entry.x+";"+this.entry.y+")|Exit:("+this.exit.x+";"+this.exit.y+")\n");
		if(wantedgroups < numtransportgroups){
			System.out.println("There can't be more transport groups than total groups, here: transport groups " + numtransportgroups + " total " + wantedgroups);
			System.exit(0);
		}
		
		this.SetDefaultValues();
	}

	public double[] getPolygonParams() {
		double[] params = new double[8];
		for (int i = 0; i < xpoints.length; i++){
			params[2*i]   = (double)xpoints[i];
			params[2*i+1] = (double)ypoints[i];
		}
		return params;
	}

	public void print(){
		System.out.println("Coordinates of CatastropheArea ");
		for (int i=0; i < Positions.length - 7; i++) {
			System.out.print(Positions[i] + ",");
			if (i == (Positions.length - 8)) {
				System.out.println("");
			}
		}
		System.out.println("entry " + entry.x + " " + entry.y + " exit " + exit.x + " " + exit.y + " type " + type + " wanted groups " + wantedgroups + " groupsize " + groupsize + " minspeed " + minspeed + " maxspeed " + maxspeed);
	}

	public String VerticesToString(){
		String represent = new String();
		for(int i = 0; i < Positions.length - 7; i++){
			Double help = new Double(Positions[i]);
			represent = represent + " " + help.toString();
		}
		return represent;
	}

	public boolean equals(CatastropheArea other) {
		if(type != other.type) {
			return false;
		}

		for(int i = 0; i < Positions.length - 7; i++) {
			if(Positions[i] != other.Positions[i]){
				return false;
			}
		}
		return true;
	}
}
