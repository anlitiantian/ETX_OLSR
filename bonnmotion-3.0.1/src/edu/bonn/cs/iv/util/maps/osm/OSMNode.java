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

import edu.bonn.cs.iv.util.maps.PositionGeo;

public class OSMNode
{
	private long id = -1;
	private PositionGeo pos = null;
	private ArrayList<Long> ways = null;
	private boolean isPhantomNode = false;
	private int degree = 0;
	
	public OSMNode(long id, PositionGeo pos)
	{
		this.id = id;
		this.pos = pos;
		this.ways = new ArrayList<Long>();
	}
	
	public OSMNode(long id, PositionGeo pos, boolean isPhantomNode)
	{
		this.id = id;
		this.pos = pos;
		this.ways = new ArrayList<Long>();
		this.isPhantomNode = isPhantomNode;
	}
	
	public long id()
	{
		return this.id;
	}
	
	public PositionGeo pos()
	{
		return this.pos;
	}
	
	public ArrayList<Long> ways()
	{
		return this.ways;
	}
	
	public boolean isPhantomNode()
	{
		return this.isPhantomNode;
	}
	
	public int degree()
	{
		return this.degree;
	}
	
	public void addWay(long id, boolean isFirstOrLastNode)
	{
		// a way might reference a node multiple times (-> cycle)
		if (!ways.contains(id)) {
			ways.add(id);
		}
		
		if (isFirstOrLastNode) {
			degree++;
		} else {
			degree += 2;
		}
	}
	
	public void removeWay(long id, boolean isFirstOrLastNode)
	{
		if (ways.contains(id)) {
			ways.remove(id);
		}
		
		if (isFirstOrLastNode) {
			degree--;
		} else {
			degree -= 2;
		}

		assert(degree >= 0);
	}
}
