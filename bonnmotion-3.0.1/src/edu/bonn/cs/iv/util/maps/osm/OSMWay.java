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

package edu.bonn.cs.iv.util.maps.osm;

import java.util.ArrayList;

public class OSMWay
{
	private long id = -1;
	private ArrayList<Long> nodeRefs = null;
	private double length = -1;
	private int componentId = -1;
	
	public OSMWay(long id, ArrayList<Long> nodeRefs)
	{
		this.id = id;
		this.nodeRefs = nodeRefs;
	}
	
	public long id()
	{
		return this.id;
	}
	
	public ArrayList<Long> nodeRefs()
	{
		return this.nodeRefs;
	}
	
	public double length()
	{
		return this.length;
	}
	
	public int componentId()
	{
		return this.componentId;
	}
	
	public void setLength(double len)
	{
		this.length = len;
	}
	
	public void setComponentId(int id)
	{
		assert(componentId < 0);
		this.componentId = id;
	}
}
