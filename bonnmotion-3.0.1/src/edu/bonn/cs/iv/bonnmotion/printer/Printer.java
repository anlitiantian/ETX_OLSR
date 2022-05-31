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

package edu.bonn.cs.iv.bonnmotion.printer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.ScenarioParameters;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

public class Printer {
	PrinterStyle ps = null;
	Dimension dim = null;
	
	public Printer(){
		this(PrinterStyle.MovementString, Dimension.TWOD);
	}

	public Printer(Dimension dim){
		this(PrinterStyle.MovementString, dim);
	}
	
	public Printer(PrinterStyle printerStyle, Dimension dim) {
		this.ps = printerStyle;
		this.dim = dim;
	}
	
	
	
	
	
	public String print(MobileNode mn) {
		if(this.ps == PrinterStyle.MovementString) {
			final int waypointCount = mn.getNumWaypoints();
			StringBuffer sb = new StringBuffer(100 * waypointCount);
	        for (int i = 0; i < waypointCount; i++) {
	            Waypoint w = mn.getWaypoint(i);
	            sb.append(" ");
	            sb.append(print(w));
	            //sb.append(w.getMovementStringPart());
	        }
	        sb.deleteCharAt(0);
	        return sb.toString();
		}
		
		return "";
	}
	
	
	
	public String print(Waypoint w) {
		if(this.ps == PrinterStyle.MovementString) {
			StringBuilder sb = new StringBuilder();
			
			sb.append(w.time);
			sb.append(" ");
			sb.append(print(w.pos));
			
			return sb.toString();
		}
		return "";
	}
	
	public String print(Position pos) {
		if(this.ps == PrinterStyle.MovementString) {
			StringBuilder sb = new StringBuilder();
			sb.append(pos.x);
			sb.append(" ");
			sb.append(pos.y);
			
			if (this.dim == Dimension.THREED) {
		        sb.append(" ");
		        sb.append(pos.z);   
			}
			
			return sb.toString();
		}
		
		return "";
	}
	
	
	public String movementStringDisasterArea(MobileNode node) {
		StringBuffer buffer = new StringBuffer(140*node.getNumWaypoints());
		for (int i = 0; i < node.getNumWaypoints(); i++) {
			Waypoint point = node.getWaypoint(i);
			buffer.append("\n");
			buffer.append(point.time);
			buffer.append("\n");
			buffer.append(point.pos.x);
			buffer.append("\n");
			buffer.append(point.pos.y);
			buffer.append("\n");
			buffer.append(point.pos.status);
		}
		buffer.deleteCharAt(0);
		return buffer.toString();
	}

    public void writeOutsourced(String basename, ScenarioParameters baseParameters, String[] additionalParameters) throws FileNotFoundException, IOException {
    	PrintWriter parameterWriter = new PrintWriter(new FileOutputStream(basename + ".params"));
    	ParameterParser parser = new ParameterParser();

    	parameterWriter.println(parser.scenarioParser(baseParameters));
		if (additionalParameters != null) {
			parameterWriter.println(parser.additionalParameterParser(additionalParameters));
		}
		if (baseParameters.aFieldParams != null) {
			parameterWriter.println(parser.attractorFieldParser(baseParameters));
		}

		parameterWriter.close();

    	PrintWriter movementWriter = new PrintWriter(new GZIPOutputStream(new FileOutputStream(basename + ".movements.gz")));
		
    	if (baseParameters.outputDim == Dimension.THREED) {
			movementWriter.println("#3D");
		}
		for (int i = 0; i < baseParameters.nodes.length; i++) {
			if (baseParameters.modelName.equals("DisasterArea")) {
				movementWriter.println(movementStringDisasterArea(baseParameters.nodes[i]));
			} else {
				movementWriter.println(print(baseParameters.nodes[i]));
			}
		}
		
    	movementWriter.close();
    }
}
