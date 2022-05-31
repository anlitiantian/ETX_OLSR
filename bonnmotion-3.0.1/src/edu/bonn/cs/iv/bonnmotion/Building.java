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

import edu.bonn.cs.iv.bonnmotion.printer.Dimension;

public class Building{
	
	
	protected double x1 = 0;
	protected double x2 = 0;
	protected double y1 = 0;
	protected double y2 = 0;
	protected double z1 = -1.0;
	protected double z2 = 1.0;
	protected double doorx = 0;
	protected double doory = 0;
	protected double doorz = 0;
	
	public Building(double x1, double x2, double y1, double y2, double doorx, double doory) {
		this(x1, x2, y1, y2, -1.0, 1.0, doorx, doory, 0.0);
	}
	
	public Building(double x1, double x2, double y1, double y2, double z1, double z2, double doorx, double doory, double doorz) {
		
		this.x1 = x1;
		this.x2 = x2;
		this.y1 = y1;
		this.y2 = y2;
		this.z1 = z1;
		this.z2 = z2;
		this.doorx = doorx;
		this.doory = doory;
		this.doorz = doorz;
	}


	/**
	 * Checks if the given position is inside the building
	 * @param pos
	 * @return true if the position is inside the building, false otherwise
	 */
	public boolean isInside(Position pos, Dimension dim) {
		if (dim == Dimension.THREED){
			if(pos.x > this.x1 && pos.x < this.x2 
					&& pos.y > this.y1 && pos.y < this.y2
					&& pos.z > this.z1 && pos.z < this.z2)
				return true;
		} else {
			if(pos.x > this.x1 && pos.x < this.x2 
					&& pos.y > this.y1 && pos.y < this.y2)
				return true;
		}
		return false;
	}
	
	public boolean canCommunicateThroughDoor(Position pos1, Position pos2, Dimension dim) {
		if (dim == Dimension.THREED){
			if (this.x1 == this.doorx) {
				// door is on the left
				if (pos2.x > this.doorx - 10
						&& pos2.y == this.doory
						&& pos1.x < this.doorx + 10
						&& pos1.y == this.doory
						&& pos1.z == this.doorz
						&& pos2.z == this.doorz) {
					return true;
				}
			} else if (this.x2 == this.doorx) {
				// door is on the right
				if (pos2.x < this.doorx + 10
						&& pos2.y == this.doory
						&& pos1.x > this.doorx - 10
						&& pos1.y == this.doory
						&& pos1.z == this.doorz
						&& pos2.z == this.doorz) {
					return true;
				}
			} else if (this.y1 == this.doory) {
				// door is on the south
				if (pos2.y > this.doory - 10
						&& pos2.x == this.doorx
						&& pos1.x == this.doorx
						&& pos1.y < this.doory + 10
						&& pos1.z == this.doorz
						&& pos2.z == this.doorz) {
					return true;
				}
			} else if (this.y2 == this.doory) {
				// door is on the north
				if (pos2.y < this.doory + 10
						&& pos2.x == this.doorx
						&& pos1.x == this.doorx
						&& pos1.y > this.doory - 10
						&& pos1.z == this.doorz
						&& pos2.z == this.doorz) {
					return true;
				}
			} else if (this.z1 == this.doorz) {
				// door is on the bottom
				if (pos1.x == this.doorx
						&& pos2.x == this.doorx
						&& pos1.y == this.doory
						&& pos2.y == this.doory
						&& pos1.z < this.doorz + 10
						&& pos2.z > this.doorz - 10) {
					return true;
				}
			} else if (this.z2 == this.doorz) {
				// door is on the top
				if (pos1.x == this.doorx
						&& pos2.x == this.doorx
						&& pos1.y == this.doory
						&& pos2.y == this.doory
						&& pos1.z > this.doorz - 10
						&& pos2.z < this.doorz + 10) {
					return true;
				}
			}
		} else {
			if (this.x1 == this.doorx) {
				// door is on the left
				if (pos2.x > this.doorx - 10
						&& pos2.y == this.doory
						&& pos1.x < this.doorx + 10
						&& pos1.y == this.doory) {
					return true;
				}
			} else if (this.x2 == this.doorx) {
				// door is on the right
				if (pos2.x < this.doorx + 10
						&& pos2.y == this.doory
						&& pos1.x > this.doorx - 10
						&& pos1.y == this.doory) {
					return true;
				}
			} else if (this.y1 == this.doory) {
				// door is on the south
				if (pos2.y > this.doory - 10
						&& pos2.x == this.doorx
						&& pos1.x == this.doorx
						&& pos1.y < this.doory + 10) {
					return true;
				}
			} else if (this.y2 == this.doory) {
				// door is on the north
				if (pos2.y < this.doory + 10
						&& pos2.x == this.doorx
						&& pos1.x == this.doorx
						&& pos1.y > this.doory - 10) {
					return true;
				}
			}
		}
		return false;
	}
}
