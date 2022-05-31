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

package edu.bonn.cs.iv.bonnmotion.models.slaw;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;

public class SLAWBase {
    /**
     * makes the selection of new clusters and waypoints from passed clusters
     * 
     * @param clusters
     *            array of clusters to make selection from
     * @param change_one
     *            changes only one of the clusters randomly and then selects new waypoints from all
     *            clusters
     * @return array of selected clusters
     */
    public static Cluster[] make_selection(Cluster[] clusters, Cluster[] cur_list, boolean change_one,
    									   int cluster_ratio, int noOfWaypoints, int waypoint_ratio, Scenario s) {
        ArrayList<Integer> cluster_selection;
        
        if (!change_one) {
            // Number of clusters to select
            int num_clusters = (int)Math.ceil((double)clusters.length / (double)cluster_ratio);
            
            cluster_selection = new ArrayList<Integer>(num_clusters);
            for (int i = 0; i < num_clusters; i++) {
                cluster_selection.add(i, -1);
            }
            
            int[] total_list = new int[noOfWaypoints];
            int counter = 0;
            // probability array
            for (int i = 0; i < clusters.length; i++) {
                for (int j = 0; j < clusters[i].members.length; j++) {
                    total_list[counter++] = clusters[i].index;
                }
            }

            // select clusters randomly with weights
            int t = total_list[(int)Math.floor(s.randomNextDouble() * noOfWaypoints)];
            
            for (int i = 0; i < cluster_selection.size(); i++) {
                while (cluster_selection.contains(t)) {
                    t = total_list[(int)Math.floor(s.randomNextDouble() * noOfWaypoints)];
                }
                cluster_selection.set(i, t);
            }
        }
        else {// just need to change one randomly
            cluster_selection = new ArrayList<Integer>(cur_list.length);
            for (Cluster cluster : cur_list) {
                cluster_selection.add(cluster.index);
            }
        }

        // change one cluster without weight consideration
        cluster_selection = change_one_random(cluster_selection, clusters.length, s);

        // select waypoints from selected clusters.
        Cluster[] result = new Cluster[cluster_selection.size()];
        double numberOfWaypoints;
        Cluster cluster_iterator = null;
        
        for (int i = 0; i < cluster_selection.size(); i++) {
            //find Cluster object in clusters array
            for (int j = 0; j < clusters.length; j++) {
                if (cluster_selection.get(i) == clusters[j].index) {
                    cluster_iterator = clusters[j];
                    break;
                }
            }

            result[i] = new Cluster(cluster_iterator.index);
            numberOfWaypoints = (double)cluster_iterator.members.length / (double)waypoint_ratio;
            int[] waypoint_selection;
            
            if (numberOfWaypoints < 1) {
                waypoint_selection = select_uniformly(cluster_iterator.members.length, 1, s);
            } else {
                if (s.randomNextDouble() < numberOfWaypoints % 1) {
                    waypoint_selection = select_uniformly(cluster_iterator.members.length, (int)(Math.floor(numberOfWaypoints) + 1), s);
                } else {
                    waypoint_selection = select_uniformly(cluster_iterator.members.length, (int)Math.floor(numberOfWaypoints), s);
                }
            }
            
            result[i].members = new ClusterMember[waypoint_selection.length];
            for (int j = 0; j < waypoint_selection.length; j++) {
                result[i].members[j] = cluster_iterator.members[waypoint_selection[j]].clone();
            } 
        }
        return result;
    }    
    
    /**
     * Generates random values from power-law distribution.
     * 
     * @param powerlaw_step
     *            the total numbers to generate. Returns an array of this size.
     * @param levy_scale_factor
     *            levy scaling factor of distribution
     * @param powerlaw_mode
     *            1: stabrnd 2: reverse computation
     * @return double array of powerlaw_step length
     **/
    public static double[] random_powerlaw(int powerlaw_step, int levy_scale_factor, int powerlaw_mode,
    									   double minpause, double maxpause, double beta, Scenario s) {
        double[] result = new double[powerlaw_step];

        for (int xi = 0; xi < powerlaw_step;) {
            if (powerlaw_mode == 1) { // stabrnd
                double[] stabrnd_result = stabrnd(0, levy_scale_factor, 0, powerlaw_step, beta, s);

                ArrayList<Double> temp = new ArrayList<Double>();

                for (int i = 0; i < stabrnd_result.length; i++) {
                    if (stabrnd_result[i] > minpause && stabrnd_result[i] < maxpause) {
                        temp.add(new Double(stabrnd_result[i]));
                    }
                }

                if (temp.size() > 0) {
                    for (Double d : temp) {
                        result[xi++] = d;
                        if (xi > powerlaw_step)
                            break;
                    }
                }
            }
            else if (powerlaw_mode == 2) { // reverse computation
                double temp = Math.pow(s.randomNextDouble(), 1 / (1 - (beta + 1))) * minpause;
                if (temp < maxpause) {
                    result[xi++] = temp;
                }
            }
        }
        return result;
    }

    /**
     * Returns array of randomly generated n numbers
     * based on the method of J.M. Chambers, C.L. Mallows and B.W. Stuck,
     * "A Method for Simulating Stable Random Variables," JASA 71 (1976): 340-4.
     * 
     * @param b                     beta factor
     * @param levy_scale_factor
     * @param delta
     * @param n
     *            count of random numbers to generate
     * @return double array of n length
     */
    public static double[] stabrnd(double b, int levy_scale_factor, double delta, int n,
    							   double beta, Scenario s) {
        if (beta < .1 || beta > 2) {
            throw new RuntimeException("stabrnd(): Beta value must be in [.1,2]");
        }

        if (Math.abs(b) > 1) {
            throw new RuntimeException("stabrnd(): local beta value must be in [-1,1]");
        }

        // Generate exponential w and uniform phi
        double[] w = new double[n];
        double[] x = new double[n];
        double[] phi = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = -Math.log(s.randomNextDouble());
            phi[i] = (s.randomNextDouble() - 0.5) * Math.PI;
        }

        // Gaussian case (Box-Muller)
        if (beta == 2) {
            for (int i = 0; i < n; i++) {
                x[i] = 2 * Math.sqrt(w[i]) * Math.sin(phi[i]);
                x[i] = delta + levy_scale_factor * x[i];
            }
        }
        else if (b == 0) { // Symmetrical cases
            if (beta == 1) { // Cauchy case
                for (int i = 0; i < n; i++) {
                    x[i] = Math.tan(phi[i]);
                }
            }
            else {
                for (int i = 0; i < n; i++) {
                    x[i] = Math.pow(Math.cos((1 - beta) * phi[i]) / w[i], 1 / beta - 1) * Math.sin(beta * phi[i])
                            / Math.pow(Math.cos(phi[i]), 1 / beta);
                }
            }
        }
        else { // General cases
            double cosphi, zeta, aphi, a1phi, bphi;

            if (Math.abs(beta - 1) > 0.00000001) {
                for (int i = 0; i < n; i++) {
                    cosphi = Math.cos(phi[i]);
                    zeta = b * Math.tan(Math.PI * beta / 2);
                    aphi = beta * phi[i];
                    a1phi = (1 - beta) * phi[i];
                    x[i] = (Math.sin(aphi) + zeta * Math.cos(aphi)) / cosphi
                            * Math.pow((Math.cos(a1phi) + zeta * Math.sin(a1phi)) / (w[i] * cosphi), (1 - beta) / beta);
                }
            }
            else {
                for (int i = 0; i < n; i++) {
                    cosphi = Math.cos(phi[i]);
                    bphi = Math.PI / 2 + b * phi[i];
                    x[i] = 2 / Math.PI * (bphi * Math.tan(phi[i]) - b * Math.log((Math.PI / 2) * w[i] * cosphi / bphi));
                }
                if (beta != 1) {
                    for (int i = 0; i < n; i++) {
                        x[i] += b * Math.tan(Math.PI * beta / 2);
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                x[i] = delta + levy_scale_factor * x[i];
            }
        }
        return x;
    }

    /**
     * returns aggregated list of cluster members from all passed clusters
     * 
     * @param clusters
     *            clusters list
     * @return array of ClusterMember type
     */
    public static ClusterMember[] get_waypoint_list(Cluster[] clusters) {
        ArrayList<ClusterMember> result = new ArrayList<ClusterMember>();

        for (Cluster cluster : clusters) {
            for (ClusterMember clustermember : cluster.members) {
                result.add(clustermember);
            }
        }

        return result.toArray(new ClusterMember[0]);
    }

    /**
     * selects k numbers out of n uniformly
     * 
     * @param n
     * @param k
     * @return array of k integers
     */
    public static int[] select_uniformly(int n, int k, Scenario s) {
        if (k > n)
            throw new RuntimeException("select_uniformly(): value of k must not be larger than n.");

        int t;
        int[] list = new int[k];
        for (int i = 0; i < k; i++) {
            list[i] = -1;
        }
        boolean is_in;
        int count = 0;
        while (count < k) {
            is_in = false;
            t = (int)Math.floor(s.randomNextDouble() * n);
            for (int i = 0; i < list.length; i++) {
                if (list[i] == t) {
                    is_in = true;
                    break;
                }
            }
            if (!is_in) {
                list[count++] = t;
            }
        }
        return list;
    }

    /**
     * generates clusters
     * 
     * @param waypoints
     *            list of waypoint positions
     * @return array of clusters
     */
    public static Cluster[] generate_clusters(PositionInterface[] waypoints,
    										  double cluster_range) {
        Vector<PositionInterface> all_points = new Vector<PositionInterface>();
        Vector<PositionInterface> new_points = new Vector<PositionInterface>();
        Vector<PositionInterface> members = new Vector<PositionInterface>();

        Vector<Cluster> clusters = new Vector<Cluster>();
        Vector<ClusterMember> cluster_members = new Vector<ClusterMember>();

        for (int i = 0; i < waypoints.length; i++) {
            all_points.add(waypoints[i]);
        }

        PositionInterface init_pos = null;
        int cluster_count = 0;

        while (!all_points.isEmpty()) {
            if (init_pos == null) {
                init_pos = all_points.firstElement();
                all_points.remove(0);
                members.add(init_pos);
            }

            for (int i = 0; i < all_points.size(); i++) {
            	PositionInterface new_pos = all_points.elementAt(i);

                if (init_pos.distance(new_pos) <= cluster_range) {
                    new_points.add(new_pos);
                    members.add(new_pos);
                    all_points.remove(i--);
                }
            }// for all_points

            if (!new_points.isEmpty() && !all_points.isEmpty()) {
                init_pos = new_points.firstElement();
                new_points.remove(0);
            }
            else {
                for (int i = 0; i < members.size(); i++) {
                    cluster_members.add(new ClusterMember(cluster_count, members.elementAt(i), false));
                }

                clusters.add(new Cluster(++cluster_count, cluster_members.toArray(new ClusterMember[0])));

                cluster_members.clear();
                new_points.clear();
                members.clear();
                init_pos = null;
            }

        }// while all_points

        return clusters.toArray(new Cluster[0]);
    }

    /**
     * Divides the waypoints according to variance and returns four numbers
     * 
     * @param wp
     *            Number of waypoints
     * @param var
     *            variance
     * @return array of divided waypoints of length four
     */
    public static int[] divide_waypoints(int wp, double var, Scenario s) {
        double gran = 0.01;
        double Error = 0.03;
        int Thresh = 31;
        int[] num = {0, 0, 0, 0};

        if (var > 4) {
            num[(int)Math.floor(s.randomNextDouble() * 4)] = wp;
        }
        else if (var <= 0) {
            int i = 0;
            while (i < wp) {
                num[i % 4] += 1;
                i++;
            }
        }
        else {
            int count = 0;
            double[][] arrays = new double[Thresh][4];

            for (double i = 0; i <= 1; i += gran) {
                for (double j = (1 - i) / 3; j <= 1 - i; j += gran) {
                    for (double k = (1 - i - j) / 2; k <= 1 - i - j; k += gran) {
                        double l = 1 - i - j - k;
                        double[] arr = {i, j, k, l};
                        double avg = calculate_average(arr);
                        double[] arr2 = {arr[0] / avg, arr[1] / avg, arr[2] / avg, arr[3] / avg};

                        if (Math.abs(var - calculate_var(arr2)) < Error * var) {
                            for (int x = 0; x < 4; x++) {
                                arrays[count][x] = arr[x];
                            }

                            if (++count >= Thresh) {
                                break;
                            }
                         }
                    }

                    if (count >= Thresh) {
                        break;
                    }
                }

                if (count >= Thresh) {
                    break;
                }
            }

            // pick a random row
            int row = (int)Math.floor(Thresh * s.randomNextDouble());
            double[] rand_arr = {s.randomNextDouble(), s.randomNextDouble(), s.randomNextDouble(), s.randomNextDouble()};
            double[] rand_arr2 = rand_arr.clone();
            Arrays.sort(rand_arr2);
            double[] row_rand = new double[4];

            // randomize probability
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    if (rand_arr2[k] == rand_arr[j]) {
                        row_rand[k] = arrays[row][j];
                        break;
                    }
                }
            }

            // distribute waypoints
            double[] prob = {row_rand[3], row_rand[3] + row_rand[2], row_rand[3] + row_rand[2] + row_rand[1],
                    row_rand[0] + row_rand[1] + row_rand[2] + row_rand[3]};
                    
            if (row_rand[0] <= 0 && row_rand[1] <= 0 && row_rand[2] <= 0 && row_rand[3] <= 0) {
            	    System.out.println("Error: Variance too large, try again!");
                    System.exit(0);   
            }
            for (int i = 0; i < wp;) {
                double rand = s.randomNextDouble();
                for (int j = 0; j < 4; j++) {
                    if (rand <= prob[j]) {
                        num[j]++;
                        i++;
                        break;
                    }
                }
            }
        }// else
        return num;
    }

    /**
     * Calculates the variance of passed array
     * 
     * @param list
     *            double array
     * @return double variance
     */
    public static double calculate_var(double[] list) {
        double sum = 0;
        double avg = calculate_average(list);

        for (int i = 0; i < list.length; i++) {
            sum += Math.pow(Math.abs(list[i] - avg), 2);
        }

        return sum / list.length;
    }

    /**
     * Calculates the average of passed array
     * 
     * @param list
     *            double array
     * @return double average
     */
    public static double calculate_average(double[] list) {
        double sum = 0;

        for (int i = 0; i < list.length; i++) {
            sum += list[i];
        }

        return sum / list.length;
    }

    /**
     * changes one of the numbers randomly from the passed array. The new changed number is in range
     * [1,n]
     * 
     * @param list
     *            array of integers
     * @param n
     *            range of numbers
     * @return array of integers with one element changed randomly
     */
    public static ArrayList<Integer> change_one_random(ArrayList<Integer> list, int n, Scenario s) {
        int index = (int)Math.floor(s.randomNextDouble() * list.size());
        int value = (int)Math.floor(s.randomNextDouble() * n) + 1;

        while (list.contains(value)) {
            value = (int)Math.floor(s.randomNextDouble() * n) + 1;
        }
        list.set(index, value);

        return list;
    }
    
    /**
     * reads a file with waypoints and returns them in a Position array
     * 
     * the expected format is:
     * x1 y1
     * x2 y2
     * ...
     * 
     * @param filename
     * @return array of positions
     */
    public static Position[] readWaypointsFromFile(String filename) {
        String line = "";
        BufferedReader reader = null;
        List<Position> result = new ArrayList<Position>();
        
        try {
            reader = new BufferedReader(new FileReader(filename));

            while ((line = reader.readLine()) != null) {
                String[] tmp = line.split(" ");
                result.add(new Position(Double.parseDouble(tmp[0]),Double.parseDouble(tmp[1])));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return result.toArray(new Position[]{});
    }
}
