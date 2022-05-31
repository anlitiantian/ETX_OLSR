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

import edu.bonn.cs.iv.bonnmotion.ScenarioParameters;

public class ParameterParser {

	public String scenarioParser(ScenarioParameters parameters) {
		String parameterString = "";
		parameterString += "model="+parameters.modelName+"\n";
		parameterString += "ignore="+parameters.ignore+"\n";
		parameterString += "randomSeed="+parameters.randomSeed+"\n";
		parameterString += "x="+parameters.x+"\n";
		parameterString += "y="+parameters.y+"\n";
		if (parameters.calculationDim == Dimension.THREED){
			parameterString += "z="+parameters.z+"\n";
		}
		parameterString += "duration="+parameters.duration+"\n";
		parameterString += "nn="+parameters.nodes.length+"\n";
		parameterString += "circular="+parameters.circular+"\n";
		parameterString += "J="+(parameters.outputDim == Dimension.THREED ? "3D" : "2D");
		return parameterString;
	}

	public String randomSpeedBaseParser(double minspeed, double maxspeed, double maxpause){
		String parameters = "";
		parameters += "minspeed="+minspeed+"\n";
		parameters += "maxspeed="+maxspeed+"\n";
		parameters += "maxpause="+maxpause+"\n";
		return parameters;
	}
	
	public String additionalParameterParser(String[] parameterArray){
		String parameterString = "";
		if ( parameterString != null ) {
			for (int i = 0; i < parameterArray.length - 1; i++) {
				parameterString += parameterArray[i] + "\n";
			}
			parameterString += parameterArray[parameterArray.length-1];
		}
		return parameterString;
	}
	
	public String attractorFieldParser(ScenarioParameters parameters) {
		String attractorFieldParameters = "";

		attractorFieldParameters += "aFieldParams=" + parameters.aFieldParams[0];
		for (int i = 1; i < parameters.aFieldParams.length; i++){
			attractorFieldParameters += "," + parameters.aFieldParams[i];
		}
		attractorFieldParameters += "\n";

		return attractorFieldParameters;
	}
}