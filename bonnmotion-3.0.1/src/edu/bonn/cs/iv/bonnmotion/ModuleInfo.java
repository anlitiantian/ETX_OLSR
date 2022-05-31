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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModuleInfo {
    public final static String BM_MAILINGLIST = "bonnmotion@lists.iai.uni-bonn.de";
    public final static String TOILERS = "Toilers - Colorado School of Mines (http://toilers.mines.edu/)";
	public final static String UNIVERSITY_OF_BONN = "University of Bonn - Institute of Computer Science 4 (http://net.cs.uni-bonn.de/)";
	public final static String UOS_SYS = "University of Osnabrueck - Institute of Computer Science (http://cs.uos.de/sys/)";
    
    public final String name;
    public String description = "";
    public String affiliation = "";
    public List<String> authors;
    public List<String> contacts;
    public List<String> references;
    
    //Version number
    public int major;
    public int minor;
    public int revision;
    
    public ModuleInfo(String _name) {
        name = _name;
        authors = new ArrayList<String>();
        contacts = new ArrayList<String>();
        references = new ArrayList<String>();
    }
    
    /**
     * returns a formatted string with the version (e.g. 'v0.1-123')
     */
    public String getFormattedVersion() {
        //format with revision number:
        //return String.format("v%d.%d-%d", major, minor, revision);
        return String.format("v%d.%d", major, minor);
    }
    
    /**
     * Formats the given list (comma separated) and precedes the header.
     */
    private String getFormattedList(String header, List<String> list) {
        StringBuilder sb = new StringBuilder();
        if (!list.isEmpty()) {
            sb.append(header);
            for (String x : list) {
                sb.append(x + ", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Returns a detail string of the module info with the following format:
     * 
     * == RandomWaypoint ====================================================
     * Version:      v0.1-0
     * Description:  Application to construct RandomWaypoint mobility scenarios
     * Authors:      BonnMotion Team
     * Contacts:     bonnmotion@lists.iai.uni-bonn.de
     * 
     */
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        String header = String.format("== %s ====================================================================", name);
        sb.append(String.format("%.70s\nVersion:      %s\nDescription:  %s\n", header, getFormattedVersion(), description));
        if (!affiliation.isEmpty()) {
            sb.append(String.format("Affiliation:  %s\n", affiliation));  
        }
        sb.append(getFormattedList("Authors:      ", authors));
        sb.append(getFormattedList("Contacts:     ", contacts));
        sb.append(getFormattedList("References:   ", references));
        return sb.toString();
    }
    
    /**
     * Returns a short string of the module info:
     * 
     * + RandomWaypoint             v0.1-0      Application to construct RandomWaypoint mobility scenarios
     *
     */
    public String toShortString() {
        String firstpart = String.format("+ %-26s %s", name, getFormattedVersion());
        return String.format("%-40s %s", firstpart, description);
    }
    
    /**
     * SVN only allows keyword substitution which results in a string. 
     * (e.g. '$LastChangedRevision: 246 $')
     * This method extracts the revision number.
     * 
     * @return the extracted revision number
     */
    public static int getSVNRevisionStringValue(String str) {
        Pattern p = Pattern.compile("\\$LastChangedRevision:\\s(\\d+)\\s\\$");
        Matcher m = p.matcher(str);
        
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        } else {
            return 0;
        }
    }
    
    /**
     * Returns the maximum number from as many numbers as wanted.
     * Currently not used. Could be used to find max revision number from depending classes.
     * 
     * @param numbers
     * @return maximum number
     */
    public static int maxRevision(int... numbers) {
        int max = 0;
        
        for (int i : numbers) {
            max = Math.max(max, i);
        }
        
        return max;
    }
}