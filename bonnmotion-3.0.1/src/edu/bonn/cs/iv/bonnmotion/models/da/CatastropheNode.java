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

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Waypoint;

/** Catastrophe node. */

public class CatastropheNode extends MobileNode {
	public int belongsto;
	public int type;
	public Position start;
	public Position end;
	public CatastropheNode group;
	
	public CatastropheNode(int belongsto, int type, Position start, Position end) {
		super();
		this.belongsto = belongsto;
		this.type = type;
		this.start = start;
		this.end = end;
	}
	
	public CatastropheNode(CatastropheNode group) {
		super();
		this.belongsto = group.belongsto;
		this.type = group.type;
		this.start = group.start;
		this.end = group.end;
		this.group = group;
	}
	
	public void add(Position start, Position end) {
		this.start = start;
		this.end = end;
	}
	
	public CatastropheNode group() {
		return this.group;
	}
	
	public void print() {
		System.out.println("Knoten " + belongsto + " start " + start.toString() + " end " + end.toString());
	}

	public String movementString() {
		StringBuffer sb = new StringBuffer(140*waypoints.size());
		for (int i = 0; i < waypoints.size(); i++) {
			Waypoint w = waypoints.elementAt(i);
			sb.append("\n");
			sb.append(w.time);
			sb.append("\n");
			sb.append(w.pos.x);
			sb.append("\n");
			sb.append(w.pos.y);
			sb.append("\n");
			sb.append(w.pos.status);
		}
		sb.deleteCharAt(0);
		return sb.toString();
	}

}
