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

package edu.bonn.cs.iv.util;

import java.util.HashMap;

import edu.bonn.cs.iv.bonnmotion.Position;

import java.util.Map;
import java.util.Iterator;

public class PositionHashMap extends HashMap<Position,Object> {
	private static final long serialVersionUID = 6541722900508992094L;

	public Object get(Position key){
		Iterator<?> it = this.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<?,?> entry = (Map.Entry<?,?>)it.next();
			Position k = ((Position)entry.getKey());
			if(key.equals(k)){
				return entry.getValue();
			}
		}
		return super.get(key);
	}
	
	public void changeto(Position key, Object value){
		Iterator<?> it = this.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<?,?> entry = (Map.Entry<?,?>)it.next();
			Position k = ((Position)entry.getKey());
			if(key.equals(k)){
				super.remove(k);
				break;
			}
		}
		super.put(key, value);
	}
}
