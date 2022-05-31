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

/**
 * Implements a heap that stores objects with a priority. 
 * The element with the lowest priority can be deleted.
 * Add and delete are in O(log n)
 */
public class Heap {
	/**
	 * Class helps to store objects with a priority in the heap.
	 */
	class Record {
		Object data;
		double prio;
		
		public Record(Object data, double prio) {
			this.data = data;
			this.prio = prio;
		}
	}

	protected Record[] list = new Record[8];
	protected int count = 0;
	protected boolean minTop;
	
	public Heap(boolean minTop) {
		this.minTop = minTop;
	}
	
	public Heap() {
		this(true);
	}
	
	/**
	 * Changes the internal array size
	 * @param nsize new array size
	 */
	protected void resize(int nsize) {
		Record[] nl = new Record[nsize];
		System.arraycopy(list, 0, nl, 0, count);
		list = nl;
	}

	/**
	 * swaps two elements of the internal array
	 * @param i first array position
	 * @param j second array position
	 */
	protected void swap(int i, int j) {
		Record r = list[i];
		list[i] = list[j];
		list[j] = r;
	}

	/**
	 * gets the position of the parent of an array element
	 * @param p position of the array element
	 * @return the position of the parent element
	 */
	protected static int father(int p) {
		return ((p + 1) / 2) - 1;
	}
	
	/**
	 * gets the position of the left child of an array element.
	 * @param p position of the array element
	 * @return the position of the left child
	 */
	protected static int left(int p) {
		return (2 * (p + 1)) - 1;
	}
	
	/**
	 * gets the position of the right child of an array element.
	 * @param p position of the array element
	 * @return the position of the right child
	 */
	protected static int right(int p) {
		return 2 * (p + 1);
	}

	protected boolean smaller(double a, double b) {
		if (minTop)
			return a < b;
		else
			return a > b;
	}

	/**
	 * Rebuilds the heap property
	 * @param p Position where the heap property might not be valid
	 */
	protected void reheapify(int p) {
		while (true) {
			int l = left(p);
			int r = right(p);
			int s = -1;
			if (l < count)
				if (r < count)
					if (smaller(list[l].prio, list[r].prio))
						s = l;
					else
						s = r;
				else
					s = l;
			else
				s = -1;
			if (! ((s == -1) || smaller(list[p].prio, list[s].prio))) {
				swap(p, s);
				p = s;
			}
			else
				break;
		}
	}

	/**
	 * Adds a element.
	 * @param data element to add
	 * @param prio priority of the element
	 */
	public void add(Object data, double prio) {
		if (count == list.length)
			resize(list.length * 2);
		int p = count++;
		list[p] = new Record(data, prio);
		while ((p > 0) && (smaller(list[p].prio, list[father(p)].prio))) {
			swap(p, father(p));
			p = father(p);
		}
	}
	
	/**
	 * Copies the heap. The stored objects will not be cloned; only the references are copied.
	 * @return A copy of the heap.
	 */
	public Object clone() {
		Heap h = new Heap();
		h.list = new Record[list.length];
		System.arraycopy(list, 0, h.list, 0, count);
		h.count = count;
		return h;
	}

	/**
	 * Gets and removes the element with the smallest priority from the heap.
	 * @return the element with the smallest priority.
	 */
	public Object deleteMin() {
		return removeElementAt(0);
	}
	
	/**
	 * Gets a element with a specific position in the list.
	 * @param pos The position in the list.
	 * @return The element at the given position.
	 */
	public Object elementAt(int pos) {
		return list[pos].data;
	}

	public double level(int pos) {
		return list[pos].prio;
	}

	/**
	 * @return The smallest priority of the stored elements.
	 */
	public double minLevel() {
		return list[0].prio;
	}
	
	/**
	 * Removes the element at a specific position in the list.
	 * @param pos The position of the element.
	 * @return The removed element.
	 */
	public Object removeElementAt(int pos) {
		Object rVal = list[pos].data;
		list[pos] = list[--count];
		reheapify(pos);
		return rVal;
	}

	/**
	 * Gets the number of stored elements.
	 * @return The number of stored elements.
	 */
	public int size() {
		return count;
	}
}
