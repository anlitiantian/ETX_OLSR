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

package edu.bonn.cs.iv.bonnmotion.models.timm;

/**
 * 
 * An exception class for exceptions taking place in settings classes. Most common: settings set
 * with wrong values.
 */
public class SettingsException extends Exception {
    private static final long serialVersionUID = 7400729020356176157L;

    public SettingsException() {
    }

    public SettingsException(String msg) {
        super(msg);
    }
    
    public SettingsException(String msg, boolean fatal) {
        super(msg);
        if (fatal) {
            System.exit(-1);
        }
    }

    public SettingsException(String name, String value, String howValueShouldBe) {
        super(String.format("Error: %s is %s but should be %s", name, value, howValueShouldBe));
    }
    
    public SettingsException(String name, String value, String howValueShouldBe, boolean fatal) {
        super(String.format("Error: %s is %s but should be %s", name, value, howValueShouldBe));
        if (fatal) {
            System.exit(-1);
        }
    }    
}