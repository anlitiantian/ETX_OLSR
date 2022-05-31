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
 * This class serves the efficient management of objects with an integer key.
 * These objects are stored sorted in an array.
 * Access is in O(log n)
 * Add and Delete are in O(n) because of the switching of array-elements.
 */
public class SortedList {
    protected Sortable[] list;
    protected int count;
    
	public SortedList() {
		list = new Sortable[8];
	}

    protected static int findPosHelper(Sortable[] list, int begin, int end, int key) {
        while (begin <= end) {
            int mid = (begin + end) / 2;
            int n = list[mid].getKey();
            if (key == n)
                return mid;
            else
                if (key < n)
                    end = mid - 1;
                else
                    begin = mid + 1;
        }
        return end;
    }

    /**
     * Gets the position of a element.
     * @param key The key of the element.
     * @return The position of the element. 
     * If the element is nonexistent it returns the element that woud be _behind_ the element with key. 
     */
    protected int findPos(int key) {
        return findPosHelper(list, 0, count - 1, key);
    }
	
    /**
     * Adds a element.
     * @param e The element to add.
     * @return Position, where the element was added. If the key already exists returns -1.
     */
    public int add(Sortable e) {
        Sortable[] src = list;
		Sortable[] dst;
        if (count == list.length)
            dst = new Sortable[list.length * 2];
        else
            dst = list;
        int p = findPos(e.getKey());
        if ((p >= 0) && (p < list.length) && (list[p].getKey() == e.getKey()))
            return -1;
        p++;
		if (src != dst)
	        System.arraycopy(src, 0, dst, 0, p);
        System.arraycopy(src, p, dst, p + 1, count - p);
        dst[p] = e;
        list = dst;
        count++;
		return p;
    }

    /**
     * Gets a copy of the list. Only references are cloned, not the stored objects themselves.
     * @return Copy of the list.
     */
	public Object clone() {
		SortedList s = new SortedList();
		s.list = new Sortable[list.length];
		System.arraycopy(list, 0, s.list, 0, count);
		s.count = count;
		return s;
	}
	
	/**
	 * Removes a element.
	 * @param key The key of the element to remove.
	 * @return The removed element.
	 */
    public Sortable delete(int key) {
        int p = indexOf(key);
        if (p == -1)
            return null;
		return deleteElementAt(p);
    }
	
    /**
     * Removes a list of elements.
     * @param l The list of elements to remove.
     */
	public void delete(SortedList l) {
		for (int i = 0; i < l.size(); i++) {
			Sortable e = l.elementAt(i);
			int idx = indexOf(e.getKey());
			if (idx >= 0)
				deleteElementAt(idx);
		}
	}

	public void deleteAllElements() {
		count = 0;
	}

	/**
	 * Removes the element at the given position.
	 * @param p The position of the element to remove.
	 * @return The removed element. null, if there was no element at the given position.
	 */
	public Sortable deleteElementAt(int p) {
		if (p >= count)
			return null;
		Sortable rVal = list[p];
        Sortable[] src = list;
		Sortable[] dst;
        if ((count < list.length / 4) && (count > 8))
            dst = new Sortable[list.length / 2];
        else
            dst = list;
		if (src != dst)
			System.arraycopy(src, 0, dst, 0, p);
        System.arraycopy(src, p + 1, dst, p, count - p - 1);
		list = dst;
        count--;
		return rVal;
	}
		
	/**
	 * Gets the element at a specific position.
	 * @param p Position of the element.
	 * @return The element at the position; null if there is no element.
	 */
	public Sortable elementAt(int p) {
		if ((p >= 0) && (p < count))
			return list[p];
		else
			return null;
	}
    
	/**
	 * Gets a stored element.
	 * @param key The key of the element
	 * @return The element with the key. Null, if no element with given key exists.
	 */
	public Sortable get(int key) {
		int p = indexOf(key);
        if (p == -1)
            return null;
		else
			return list[p];
	}

	/**
	 * Gets the position of a element.
	 * @param key The key of the element.
	 * @return The position of the element. If the element does not exist -1.
	 */
	public int indexOf(int key) {
        int p = findPos(key);
        if ((p == -1) || (list[p].getKey() != key))
            return -1;
		else
			return p;
	}
		
	/**
	 * Adds a list of elements.
	 * @param l The list of elements to add.
	 */
	public void merge(SortedList l) {
		int size = list.length;
		while (size < count + l.count)
			size *= 2;
		Sortable[] dst = new Sortable[size];
		int i = 0, j = 0, p = 0;
		while ((i < count) && (j < l.count))
			if (list[i].getKey() < l.list[j].getKey())
				dst[p++] = list[i++];
			else
				dst[p++] = l.list[j++];
		if (i < count)
			System.arraycopy(list, i, dst, p, count - i);
		else
			System.arraycopy(l.list, j, dst, p, l.count - j);
		list = dst;
		count += l.count;
	}

	/** 
	 * Gets the number of stored elements.
	 * @return The number of stored elements.
	 */
	public int size() {
		return count;
	}

	public String toString() {
		String s = "";
		for (int i = 0; i < count; i++)
			s += "[" + list[i].getKey() + "]";
		return s;
	}
}
