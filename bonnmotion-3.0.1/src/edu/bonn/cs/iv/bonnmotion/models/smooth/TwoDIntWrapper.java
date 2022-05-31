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

package edu.bonn.cs.iv.bonnmotion.models.smooth;

public class TwoDIntWrapper {
	private int[][] data;
	private int x;
	private int y;

	public TwoDIntWrapper(int length) {
		data = new int[length][length];
		this.x = length;
		this.y = length;
	}

	public TwoDIntWrapper(int[][] data) {
		this.data = data;
		this.x = data.length;
		this.y = data[0].length;
	}

	public TwoDIntWrapper(int x, int y) {
		data = new int[x][y];
		this.x = x;
		this.y = y;
	}

	public int get(int x, int y) {
		return data[x][y];
	}

	public void set(int x, int y, int newInt) throws WrapperMaximumLengthExceededException {
		if(x>=data.length){
			throw new WrapperMaximumLengthExceededException("X index too large");
		} else if(y>=data[x].length){
			throw new WrapperMaximumLengthExceededException("Y index too large"); 
		}
		data[x][y] = newInt;
	}

	public int[][] getData() {
		return data;
	}

	public void setData(int[][] data) {
		this.data = data;
	}

	public boolean isSquare() {
		return x == y;
	}
}
