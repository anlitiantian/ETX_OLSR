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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import edu.bonn.cs.iv.bonnmotion.apps.sql.DatabaseInfos;

public class Writer_MySQL {

	// This demo code is based on :
	// http://dev.mysql.com/doc/refman/5.0/en/connector-j-usagenotes-last-insert-id.html#connector-j-examples-autoincrement-updateable-resultsets
	
	private Connection dbConnection = null;
	private Statement dbstatement = null;

	public Writer_MySQL(DatabaseInfos dbi) {
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			dbConnection = DriverManager.getConnection("jdbc:mysql://"+dbi.hostname+"/"+dbi.database+"?user="+dbi.user+"&password="+dbi.password);
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found exception: " + e);
		} catch (IllegalAccessException e) {
			System.out.println("IllegalAccessException: " + e);
		} catch(InstantiationException e) {
			System.out.println("InstantiationException: " + e);
		}
		catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
		}
	}

	public void writeStatement(String statement) {
		try {
			dbstatement = dbConnection.createStatement();
			dbstatement.executeUpdate(statement);
			dbstatement.close();
		} catch(SQLException e) {
			System.out.println("SQLException: " + e);
			System.exit(1);
		}
	}
	
	public void close() {
		try {		
			dbConnection.close();
		} catch (SQLException e) {
			System.out.println("SQLException (close): " + e);
		}
	}
	
	public int getPrimaryKey(String tableName, String idColumnName, String dataColumnName, String dataValue) {
		int primaryKey = -1;
		
		final String statement = "SELECT " + idColumnName + " FROM " + tableName + " WHERE " + dataColumnName + " LIKE '" + dataValue + "'";
				
		try {
			dbstatement = dbConnection.createStatement();	
			java.sql.ResultSet dbResults = dbstatement.executeQuery(statement);
						
			if(dbResults.next()) {
				primaryKey = dbResults.getInt(1);
			} else {
				primaryKey = insertDataAndGetPrimaryKey(tableName,dataColumnName,dataValue);
			}
			
			return primaryKey;
			
		} catch (SQLException e) {
			System.out.println(e);
			System.exit(1);
		}
		
		System.out.println("Failed to get primaryKey");
		System.exit(1);
		return primaryKey;
	}
	
	protected int insertDataAndGetPrimaryKey(String tableName, String dataColumnName, String dataValue) {
		int primaryKey = -1;
		final String statement = "INSERT INTO " + tableName + "(" + dataColumnName + ") VALUES ('" + dataValue + "')"; 
		
		try {
			dbstatement.executeUpdate(statement);
			java.sql.ResultSet dbResults = dbstatement.executeQuery("SELECT LAST_INSERT_ID()");
			if (dbResults.next()) {
				primaryKey = dbResults.getInt(1);
				return primaryKey;
			}
		} catch (SQLException e) {
			System.out.println("SQLException: " + e);
			System.exit(1);
		}
		
		System.out.println("Failed to get last_insert_id()");
		System.exit(1);
		return primaryKey;
	}
	
}