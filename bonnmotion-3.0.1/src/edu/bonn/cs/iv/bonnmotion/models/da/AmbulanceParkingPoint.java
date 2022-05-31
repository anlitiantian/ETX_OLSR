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

import edu.bonn.cs.iv.bonnmotion.Position;

public class AmbulanceParkingPoint extends CatastropheArea {
	private static final long serialVersionUID = 3251513172589859967L;

	protected AmbulanceParkingPoint(double[] Positions) {
		super(Positions);
		if (debug) System.out.println ("AreaType: AmbulanceParkingPoint");
	}
	
	protected void InitializeSpecificValues(double[] Positions) {
		if (Positions.length < 11) {
			System.out.println("Please specify more positions for area!\naborting...");
			System.exit(0);
		}
		//ambulance parking point additionally has borderentry and borderexit
		corners = new Position[(Positions.length - 11)/2];
		for(int i = 0; i < Positions.length - 11; i = i+2){
			this.addPoint((int)Positions[i], (int)Positions[i+1]);
			corners[i/2] = new Position((int)Positions[i], (int)Positions[i+1]);
			if (debug) System.out.println ("("+(int)Positions[i]+";"+(int)Positions[i+1]+")");
		}
		this.borderentry = new Position(Positions[Positions.length - 11], Positions[Positions.length - 10]);
		this.borderexit = new Position(Positions[Positions.length - 9], Positions[Positions.length - 8]);
	}
	
	protected void SetDefaultValues() {
		this.groupsize[0] = 1;
		this.groupsize[1] = 1;	
		this.minspeed[0] = 5.0;
		this.maxspeed[0] = 12.0;
		this.minspeed[1] = 1.0;
		this.maxspeed[1] = 2.0;
	}
	
	public void print() {
		for (int i = 0; i < Positions.length - 11; i++) {
			System.out.print(Positions[i] + ",");
			if (i == (Positions.length - 12)) {
				System.out.println();
			}
		}
		System.out.println("borderentry " + borderentry + " borderexit " + borderexit + " entry " + entry.x + " " + entry.y + " exit " + exit.x + " " + exit.y + " type " + type + " wanted groups " + wantedgroups + " groupsize " + groupsize + " minspeed " + minspeed + " maxspeed " + maxspeed);
	}
	
	public String VerticesToString() {
		String represent = new String();
		for (int i = 0; i < Positions.length - 11; i++){
			Double help = new Double(Positions[i]);
			represent = represent + " " + help.toString();
		}
		return represent;
	}
	
	public boolean equals(CatastropheArea other) {
		if(type != other.type) {
			return false;
		}
		for(int i = 0; i < Positions.length - 11; i++) {
			if(Positions[i] != other.Positions[i]){
				return false;
			}
		}
		return true;
	}
}