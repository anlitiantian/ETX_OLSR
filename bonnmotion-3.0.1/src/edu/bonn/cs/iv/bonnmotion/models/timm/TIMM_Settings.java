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
 * Wrapper class for user settings.
 * 
 */
public class TIMM_Settings {
    public static final double DEFAULT_MAX_DISTANCE_VERTICES = 1000;
    public static final int DEFAULT_MIN_GROUP_SIZE = 2;

    private String pathToGraph = null;
    private boolean enableGroupOneRules = false;
    private int groupSizes[] = null;
    private int minimalGroupSize = DEFAULT_MIN_GROUP_SIZE;
    private double maxDistanceBetweenVerticesInTheBuildingGraph = DEFAULT_MAX_DISTANCE_VERTICES;
    private double slowSpeed = -1;
    private double slowSpeedVariance = -1;
    private double fastSpeed = -1;
    private double fastSpeedVariance = -1;
    private double doorOpeningAndSecuring = -1;
    private double doorOpeningAndSecuringVariance = -1;
    private double starttime[] = null;
    private double endtime[] = null;
    private double maxWalkingDistance[] = null;

    public void setSlowSpeed(double slowSpeed) throws SettingsException {
        if (slowSpeed <= 0)
            throw new SettingsException("slowSpeed", Double.toString(slowSpeed), "> 0");
        this.slowSpeed = slowSpeed;
    }

    public double getSlowSpeed() {
        return slowSpeed;
    }

    public void setSlowSpeedVariance(double slowSpeedVariance) throws SettingsException {
        if (slowSpeedVariance < 0)
            throw new SettingsException("slowSpeedVariance", Double.toString(slowSpeedVariance), ">= 0");
        this.slowSpeedVariance = slowSpeedVariance;
    }

    public double getSlowSpeedVariance() {
        return slowSpeedVariance;
    }

    public void setFastSpeed(double fastSpeed) throws SettingsException {
        if (fastSpeed <= 0)
            throw new SettingsException("fastSpeed", Double.toString(fastSpeed), "> 0");
        this.fastSpeed = fastSpeed;
    }

    public double getFastSpeed() {
        return fastSpeed;
    }

    public void setFastSpeedVariance(double fastSpeedVariance) throws SettingsException {
        if (fastSpeedVariance < 0)
            throw new SettingsException("fastSpeedVariance", Double.toString(fastSpeedVariance), ">= 0");
        this.fastSpeedVariance = fastSpeedVariance;
    }

    public double getFastSpeedVariance() {
        return fastSpeedVariance;
    }

    public void setMinimalGroupSize(int minimalGroupSize) throws SettingsException {
        if (minimalGroupSize < 0)
            throw new SettingsException("minimalGroupSize", Integer.toString(minimalGroupSize), "> 0");
        this.minimalGroupSize = minimalGroupSize;
    }

    public int getMinimalGroupSize() {
        return minimalGroupSize;
    }

    public void setMaxDistanceBetweenVerticesInTheBuildingGraph(double maxDistanceBetweenVerticesInTheBuildingGraph)
            throws SettingsException {
        if (maxDistanceBetweenVerticesInTheBuildingGraph <= 0)
            throw new SettingsException("maxDistanceBetweenVerticesInTheBuildingGraph", Double
                    .toString(maxDistanceBetweenVerticesInTheBuildingGraph), "> 0");
        this.maxDistanceBetweenVerticesInTheBuildingGraph = maxDistanceBetweenVerticesInTheBuildingGraph;
    }

    public double getMaxDistanceBetweenVerticesInTheBuildingGraph() {
        return maxDistanceBetweenVerticesInTheBuildingGraph;
    }

    public void setPathToGraph(String pathToGraph) {
        this.pathToGraph = pathToGraph;
    }

    public String getPathToGraph() {
        return pathToGraph;
    }

    public void setDoorOpeningAndSecuring(double doorOpeningAndSecuring) throws SettingsException {
        if (doorOpeningAndSecuring < 0)
            throw new SettingsException("doorOpeningAndSecuring", Double.toString(doorOpeningAndSecuring), ">= 0");
        this.doorOpeningAndSecuring = doorOpeningAndSecuring;
    }

    public double getDoorOpeningAndSecuring() {
        return doorOpeningAndSecuring;
    }

    public void setDoorOpeningAndSecuringVariance(double doorOpeningAndSecuringVariance) throws SettingsException {
        if (doorOpeningAndSecuringVariance < 0)
            throw new SettingsException("doorOpeningAndSecuringVariance", Double.toString(doorOpeningAndSecuringVariance), ">= 0");
        this.doorOpeningAndSecuringVariance = doorOpeningAndSecuringVariance;
    }

    public double getDoorOpeningAndSecuringVariance() {
        return doorOpeningAndSecuringVariance;
    }

    public void setGroupSizes(int groupSizes[]) throws SettingsException {
        if (groupSizes.length < 1) {
            throw new SettingsException("At least one group required.");
        }

        for (int i = 0; i < groupSizes.length; i++) {
            if (groupSizes[i] <= 0) {
                throw new SettingsException("groupSize of group " + Integer.toString(i), Integer.toString(groupSizes[i]), "> 0");
            }
        }

        if (this.starttime != null) {
            if (this.starttime.length != groupSizes.length) {
                throw new SettingsException("Number of starttimes does not match number of groups");
            }
        }

        if (this.maxWalkingDistance != null) {
            if (this.maxWalkingDistance.length != groupSizes.length) {
                throw new SettingsException("Number of max distances does not match number of groups");
            }
        }

        this.groupSizes = groupSizes;
    }

    public int[] getGroupSizes() {
        return groupSizes;
    }

    public void setStarttime(double starttime[]) throws SettingsException {
        if (this.groupSizes != null) {
            if (this.groupSizes.length != starttime.length) {
                throw new SettingsException("Number of starttimes does not match number of groups");
            }
        }

        for (int i = 0; i < starttime.length; i++) {
            if (starttime[i] < 0) {
                throw new SettingsException("Starttime of group " + i + " is before simulation starts.");
            }
        }

        if (this.endtime != null) {
            if (this.endtime.length != starttime.length) {
                throw new SettingsException("Number of endtimes does not match number of groups.");
            }
        }

        this.starttime = starttime;
    }

    public double[] getStarttime() {
        return starttime;
    }

    public void setEndtime(double endtime[]) throws SettingsException {
        if (this.starttime != null) {
            if (this.starttime.length != endtime.length) {
                throw new SettingsException("Number of endtimes does not match number of starttimes.");
            }

            for (int i = 0; i < endtime.length; i++) {
                if (endtime[i] < starttime[i]) {
                    throw new SettingsException("Endtime of group " + i + " is before starttime.");
                }
            }
        }
        this.endtime = endtime;
    }

    public double[] getEndtime() {
        return endtime;
    }

    public void setMaxWalkingDistance(double maxWalkingDistance[]) throws SettingsException {
        for (int i = 0; i < maxWalkingDistance.length; i++) {
            if (maxWalkingDistance[i] <= 0) {
                throw new SettingsException("maxWalkingDistance of group " + Double.toString(i), Double.toString(maxWalkingDistance[i]),
                        "> 0");
            }
        }
        if (this.groupSizes != null) {
            if (this.groupSizes.length != maxWalkingDistance.length) {
                throw new SettingsException("Number of max distances does not match number of groups");
            }
        }
        this.maxWalkingDistance = maxWalkingDistance;
    }

    public double[] getMaxWalkingDistance() {
        return maxWalkingDistance;
    }

    public void setEnableGroupOneRules(boolean enableGroupOneRules) {
        this.enableGroupOneRules = enableGroupOneRules;
    }

    public boolean isEnableGroupOneRules() {
        return enableGroupOneRules;
    }
}
