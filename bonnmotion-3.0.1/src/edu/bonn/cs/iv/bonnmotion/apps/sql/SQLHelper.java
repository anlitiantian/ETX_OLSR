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

package edu.bonn.cs.iv.bonnmotion.apps.sql;

public class SQLHelper {
	public static DatabaseInfos parseCommandline(String commandLineValue) {
		String[] databaseConfiguration = commandLineValue.split(",");
		return new DatabaseInfos(databaseConfiguration[0], databaseConfiguration[1], databaseConfiguration[2], databaseConfiguration[3]);
	}
	
	public static String helpText(String commandLineSwitch) {
		String retVal = commandLineSwitch + " <database storage information>";
		retVal += "Database Storage Information: hostname,databasename,username,password";
						
		return retVal;
	}
}
