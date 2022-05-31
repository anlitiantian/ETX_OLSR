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

import edu.bonn.cs.iv.bonnmotion.models.SMOOTH;

/**
 * CLASS THAT IMPLEMENTS THE CODE FROM THE FILES GEN.C AND CLUSTER.C FROM THE
 * SMOOTH C SOURCE CODE
 * 
 */
public class Initialize {

	private int clusters;
	private OneDIntWrapper n_wps;
	private int number;
	private int sim_dim_x;
	private int sim_dim_y;
	private int range;
	private TwoDDoubleWrapper p;
	private OneDDoubleWrapper x_position;
	private OneDDoubleWrapper y_position;
	private int waypoints;
	private SMOOTH smooth;

	/**
	 * CONSTRUCTOR THAT TAKES IN PARAMETERS FROM SMOOTH.java THAT WILL BE USED
	 * BY cluster() AND gen()
	 */
	public Initialize(int clusters, OneDIntWrapper n_wps, int number,
			int sim_dim_x, int sim_dim_y, int range, TwoDDoubleWrapper p,
			OneDDoubleWrapper x_position, OneDDoubleWrapper y_position,
			int waypoints, SMOOTH smooth) {
		super();
		this.clusters = clusters;
		this.n_wps = n_wps;
		this.number = number;
		this.sim_dim_x = sim_dim_x;
		this.sim_dim_y = sim_dim_y;
		this.range = range;
		this.p = p;
		this.x_position = x_position;
		this.y_position = y_position;
		this.waypoints = waypoints;
		this.smooth = smooth;
	}

	/**
	 * IMPLEMENTS CODE FROM THE gen METHOD FROM gen.c
	 * @throws WrapperMaximumLengthExceededException 
	 */
	public void gen() throws WrapperMaximumLengthExceededException {

		int i = 0, total = 0;

		for (i = 0; i < clusters; i++) {
			total += n_wps.get(i);

		}

		int array_[] = new int[total];
		int length_ = 0, pr = 0;

		double x_2 = -1, y_2 = -1, f_len = 0;
		int c_selected = -1;
		for (pr = 0; pr < clusters; pr++) {
			int pr_ = 0;
			for (pr_ = 0; pr_ < n_wps.get(pr); pr_++) {
				array_[length_] = pr;
				length_++;
			}

		}

		/*
		 * NOW CHOOSE A CLUSTER BASED ON ITS WEIGHT; CLUSTER WITH MAXIMUM WEIGHT
		 * HAS BEEN ASSIGNED MAXIMUM LOCATIONS IN THE ARRAY; THUS, INCREASING
		 * ITS PROBABILITY OF SELECTION.
		 */

		int tem_ = (int) Math.ceil(smooth.getNextDouble() * (double) total);

		if (number == -1) {
			/* CHOOSE A CLUSTER/LANDMARK BASED ON ITS POPULARITY INDEX */
			c_selected = array_[tem_ - 1];
		} else {
			/* ASSIGN IT TO A PRESELECTED CLUSTER (E.G., IN INFOCOM SCENARIOS) */
			c_selected = number;

		}

		int tryouts = 0;

		/*
		 * USED IN INFOCOM SCENARIOS; MOBILE NODES THAT DO NOT BELONG TO ANY
		 * GROUP ARE PLACED RANDOMLY; SEE THE SMOOTH MSWIM PAPER FOR DETAILS
		 */
		if (c_selected == clusters) {
			double temp_x = smooth.getNextDouble() * (double) sim_dim_x;
			double temp_y = smooth.getNextDouble() * (double) sim_dim_y;
			x_2 = temp_x;
			y_2 = temp_y;
		} else {
			double angle = smooth.getNextDouble() * 360.0;
			double param = 0.5 * range;
			f_len = smooth.getNextDouble() * param;
			x_2 = p.get(c_selected, 0) + f_len * Math.cos(angle);
			y_2 = p.get(c_selected, 1) + f_len * Math.sin(angle);

			while (x_2 < 0 || x_2 > sim_dim_x || y_2 < 0 || y_2 > sim_dim_y) {
				if (tryouts == 200) {
					f_len = smooth.getNextDouble() * param;
					tryouts = 0;
				}
				angle = smooth.getNextDouble() * 360.0;
				x_2 = p.get(c_selected, 0) + f_len * Math.cos(angle);
				y_2 = p.get(c_selected, 1) + f_len * Math.sin(angle);
				tryouts++;
			}
		}
		x_position.set(0, x_2);
		y_position.set(0, y_2);
	}

	/**
	 * IMPLEMENTS CODE FOR THE cluster METHOD FROM cluster.c
	 * @throws WrapperMaximumLengthExceededException 
	 */
	public void cluster() throws WrapperMaximumLengthExceededException {
		//boolean retry = false;
		int i = 0, num = 0, min = 1, max = (int) (waypoints * 0.8), capture = 0, tryouts = 0, weight = 0;
		double temp_x = 0, temp_y = 0, exponent = 0.7, term1 = 0, term2 = 0, term3 = 0, term4 = 0;
		boolean n0 = true;
		while (n0) {
			if (tryouts == 500) {
				//retry = true;
				//n0 = false;
				System.out.println("simulation failed");
				System.exit(0);
			}

			while (num < clusters) {

				/*
				 * EACH CLUSTER IS ASSIGNED A WEIGHT (= A FRACTION OF THE TOTAL
				 * NUMBER OF LOCATIONS); WEIGHT VARIES BETWEEN MINIMUM = 1 AND
				 * MAXIMUM = 60% OF THE TOTAL LOCATIONS.
				 */

				double u = smooth.getNextDouble();

				term1 = (u * Math.pow(max, exponent))
						- (u * Math.pow(min, exponent))
						- Math.pow(max, exponent);
				term2 = Math.pow(max, exponent) * Math.pow(min, exponent);
				term3 = -(term1 / term2);
				term4 = Math.pow(term3, (-1 / exponent));
				weight = (int) term4;

				n_wps.set(num, weight);
				capture += weight;
				num++;
			}

			if (capture >= waypoints * 0.8 && capture <= waypoints) {
				n0 = false;
			} else {
				num = 0;
				capture = 0;
				tryouts++;
			}
		}
//		if (!retry) {
			i = 0;

			/*
			 * INCREASE THIS TO PLACE CLUSTER LANDMARKS AS FAR AS POSSIBLE
			 * (DEPENDS ON THE NUMBER OF CLUSTERS AND THE SIMULATION AREA SIZE)
			 */
			int space_constraint = 1;

			while (i < clusters) {
				int trial = 0, found = 0;

				temp_x = smooth.getNextDouble() * (double) sim_dim_x;
				temp_y = smooth.getNextDouble() * (double) sim_dim_y;

				for (trial = 0; trial < i; trial++) {
					double distance = Math.sqrt(Math.pow(
							(p.get(trial, 0) - temp_x), 2)
							+ Math.pow((p.get(trial, 1) - temp_y), 2));

					if (distance <= space_constraint * range) {
						/*
						 * THE CURRENT LANDMARK IS WITHIN THE VICINITY OF AT
						 * LEAST ONE OF THE PREVIOUSLY PLACED LANDMARKS.
						 */
						found = 1;
						break;
					}
				}

				/* NOT WITHIN RANGE OF ANY OF THE PREVIOUSLY PLACED LANDMARKS */
				if (found != 1) {
					p.set(i, 0, temp_x);
					p.set(i, 1, temp_y);
					i++;
				}

			}// for i
//			if (retry)
//				cluster();
//		}

	}

	public int getClusters() {
		return clusters;
	}

	public OneDIntWrapper getN_wps() {
		return n_wps;
	}

	public int getNumber() {
		return number;
	}

	public int getSim_dim_x() {
		return sim_dim_x;
	}

	public int getSim_dim_y() {
		return sim_dim_y;
	}

	public int getRange() {
		return range;
	}

	public TwoDDoubleWrapper getP() {
		return p;
	}

	public OneDDoubleWrapper getX_position() {
		return x_position;
	}

	public OneDDoubleWrapper getY_position() {
		return y_position;
	}

	public int getWaypoints() {
		return waypoints;
	}

}
