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

import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Waypoint;
import edu.bonn.cs.iv.bonnmotion.models.SMOOTH;
import edu.bonn.cs.iv.bonnmotion.models.SMOOTH.PrintTypes;

/**
 * CLASS THAT IMPLEMENTS THE CODE FOR THE FILE locationmap.c FROM THE SMOOTH C
 * SOURCE CODE
 * 
 */
public class LocationMap {

	private double[] param;
	private OneDDoubleWrapper pause_end_time;
	private int iterationCount;
	private long time;
	private OneDIntWrapper paused;
	private OneDDoubleWrapper prev_start_time;
	//private Vector<Position> prev_xy;
	private TwoDDoubleWrapper prev_xy;
	
	//private Vector<Position> next_xy;
	private TwoDDoubleWrapper next_xy;
	
	private int max_locations;
//	private Vector<MobileNode> locationsHistory;
	private ThreeDDoubleArrayWrapper locations;
	private int sim_dim_x;
	private int sim_dim_y;
	private TwoDIntWrapper node_location;
	private OneDDoubleWrapper pause_start_time;
	private TwoDDoubleWrapper crt_xy;
	private OneDDoubleWrapper speed;
	private int nodes;
	private int range;
	private TwoDIntWrapper status;
	private TwoDDoubleWrapper cn;
	private TwoDDoubleWrapper fct;
	private TwoDDoubleWrapper lct;
	private TwoDDoubleWrapper ict;
	private TwoDDoubleWrapper ct;
	private SMOOTH smooth;

	/**
	 * CONSTRUCTOR THAT TAKES IN INPUT FROM Smooth.java, THAT WILL BE USED BY
	 * locationMap()
	 */
	public LocationMap(double[] param, OneDDoubleWrapper pause_end_time, int i,
			long t, OneDIntWrapper paused, OneDDoubleWrapper prev_start_time,
			TwoDDoubleWrapper prev_xy, TwoDDoubleWrapper next_xy,
			int max_locations, ThreeDDoubleArrayWrapper locations, int sim_dim_x,
			int sim_dim_y, TwoDIntWrapper node_location,
			OneDDoubleWrapper pause_start_time, TwoDDoubleWrapper crt_xy2,
			OneDDoubleWrapper speed, int nodes, int range,
			TwoDIntWrapper status, TwoDDoubleWrapper cn, TwoDDoubleWrapper fct,
			TwoDDoubleWrapper lct, TwoDDoubleWrapper ict, TwoDDoubleWrapper ct,
			SMOOTH smooth) {
		super();
		this.param = param;
		this.pause_end_time = pause_end_time;
		this.iterationCount = i;
		this.time = t;
		this.paused = paused;
		this.prev_start_time = prev_start_time;
		this.prev_xy = prev_xy;
		this.next_xy = next_xy;
		this.max_locations = max_locations;
		this.locations = locations;
		this.sim_dim_x = sim_dim_x;
		this.sim_dim_y = sim_dim_y;
		this.node_location = node_location;
		this.pause_start_time = pause_start_time;
		this.crt_xy = crt_xy2;
		this.speed = speed;
		this.nodes = nodes;
		this.range = range;
		this.status = status;
		this.cn = cn;
		this.fct = fct;
		this.lct = lct;
		this.ict = ict;
		this.ct = ct;
		this.smooth = smooth;
	}

	/**
	 * METHOD THE IMPLEMENTS THE MAIN BODY OF CODE FROM locationmap.c
	 */
	public void locationMap() throws SmoothException{
		double alpha = param[0] - 1;
		double beta = (param[1]) - 1;
		int f_min = (int) (param[2]);
		int f_max = (int) (param[3]);
		int p_min = (int) (param[4]);
		int p_max = (int) (param[5]);

		int ko = 0, C2 = 0, C3 = 0;

		double velocity = 1.0, p_time = 0, f_len = 0, x_2 = -1, y_2 = -1;
		/*
		 * CASE 1: PAUSE PERIOD FOR THE MOBILE NODE HAS ENDED; DECIDE WHERE TO
		 * GO NEXT (PREVIOUS LOCATIONS OR EXPLORE A NEW LOCATION?)
		 */
		while (time > pause_end_time.get(iterationCount)) {

			paused.set(iterationCount, 0);

			prev_start_time.set(iterationCount,
					pause_end_time.get(iterationCount));
			//prev_xy.remove(iterationCount);
			//prev_xy.add(iterationCount, next_xy.get(iterationCount));
			prev_xy.set(iterationCount, 0, next_xy.get(iterationCount, 0));
			prev_xy.set(iterationCount, 1, next_xy.get(iterationCount, 1));
			
			double term1 = 0, term2 = 0, term3 = 0, term4 = 0, angle = 0;

			double u = smooth.getNextDouble();

			/* GENERATE THE NEXT PAUSE TIME */

			term1 = (u * Math.pow(p_max, beta)) - (u * Math.pow(p_min, beta))
					- Math.pow(p_max, beta);
			term2 = Math.pow(p_max, beta) * Math.pow(p_min, beta);
			term3 = -(term1 / term2);
			term4 = Math.pow(term3, (-1 / beta));
			p_time = term4;

			/* C2 KEEPS TRACK OF THE NUMBER OF UNIQUE LOCATIONS VISITED SO FAR */

//			for (ko = 0; ko < locations.get(iterationCount).getNumWaypoints(); ko++) {
//				Position pos = locations.get(iterationCount).getWaypoint(ko).pos;
//				if (pos.x != -1 && pos.y != -1) {
//					C2++;
//				}
//
//			}
			for (ko = 0; ko < max_locations; ko++) {
				if (locations.get(iterationCount, ko, 0) != -1 && locations.get(iterationCount, ko, 1) != -1) {
					C2++;
				}

			}

			int done = 0;
			while (done == 0) {
				/* CONFIGURE THIS BASED ON A SCENARIO. SEE PAPER FOR DETAILS. */
				double prob_explore = 0.95 * Math.pow(C2, -0.01);

				double temp1 = smooth.getNextDouble();

				/* MOVE TO A NEW LOCATION */
				if (temp1 < prob_explore) {

					u = smooth.getNextDouble();

					term1 = (u * Math.pow(f_max, alpha))
							- (u * Math.pow(f_min, alpha))
							- Math.pow(f_max, alpha);
					term2 = Math.pow(f_max, alpha) * Math.pow(f_min, alpha);
					term3 = -(term1 / term2);
					term4 = Math.pow(term3, (-1 / alpha));
					f_len = term4;

					angle = smooth.getNextDouble() * 360.0;
					/*
					 * GENERATE NEW LOCATION BASED ON F_LEN AND THE CURRENT
					 * LOCATION OF THE MOBILE NODE.
					 */
					//x_2 = prev_xy.get(iterationCount).x + f_len * Math.cos(angle);
					//y_2 = prev_xy.get(iterationCount).y + f_len * Math.sin(angle);
					x_2 = prev_xy.get(iterationCount, 0) + f_len * Math.cos(angle);
					y_2 = prev_xy.get(iterationCount, 1)  + f_len * Math.sin(angle);

					int tryouts = 0;

					/*
					 * MAKE SURE X AND Y COORDINATES FOR THE NEXT LOCATION FALL
					 * WITHIN SIMULATION BOUNDARY.
					 */
					while (x_2 < 0 || x_2 > sim_dim_x || y_2 < 0
							|| y_2 > sim_dim_y) {
						tryouts++;

						/*
						 * CHANGE THIS VALUE FOR EXPLORING MORE LOCATIONS WITH
						 * THE SAME FLIGHT LENGTH
						 */
						if (tryouts == 100) {
							u = smooth.getNextDouble();
							term1 = (u * Math.pow(f_max, alpha))
									- (u * Math.pow(f_min, alpha))
									- Math.pow(f_max, alpha);
							term2 = Math.pow(f_max, alpha)
									* Math.pow(f_min, alpha);
							term3 = -(term1 / term2);
							term4 = Math.pow(term3, (-1 / alpha));
							f_len = term4;

							tryouts = 0;
						}
						angle = smooth.getNextDouble() * 360.0;
//						x_2 = prev_xy.get(iterationCount).x + f_len	* Math.cos(angle);
//						y_2 = prev_xy.get(iterationCount).y + f_len	* Math.sin(angle);
						x_2 = prev_xy.get(iterationCount, 0) + f_len * Math.cos(angle);
						y_2 = prev_xy.get(iterationCount, 1)  + f_len * Math.sin(angle);
						

					}
//					next_xy.remove(iterationCount);
//					next_xy.add(iterationCount, new Position(x_2, y_2));
					try {
						next_xy.set(iterationCount, 0, x_2);
						next_xy.set(iterationCount, 1, y_2);
					} catch (WrapperMaximumLengthExceededException e1) {
						
					}
					
					

//					smooth.locationHistory.get(iterationCount).add(smooth.getCurrentTime(),	new Position(x_2, y_2));
					Waypoint lastWaypoint = smooth.locationHistory.get(iterationCount).getLastWaypoint();
					Position newPosition = new Position(x_2, y_2);
					if(lastWaypoint.time != time){
						smooth.locationHistory.get(iterationCount).add(time, newPosition);
					} else{
						smooth.locationHistory.get(iterationCount).removeLastElement();
						smooth.locationHistory.get(iterationCount).add(time, newPosition);
					}
					
					try{
						locations.set(iterationCount, C2, 0, x_2);
						locations.set(iterationCount, C2, 1, y_2);
					}catch(WrapperMaximumLengthExceededException e){
						throw new MaximumLocationsExceededException("Maximum Length should be increased. Iteration Count: "
								+ iterationCount + "C2: " + C2);
					}
					node_location.set(iterationCount, C2, 1);
					done = 1;
				} else /* RETURN TO A PREVIOUSLY VISITED LOCATION */
				{

					for (ko = 0; ko < max_locations; ko++) {
						if (node_location.get(iterationCount, ko) != 0) {
							C3 += node_location.get(iterationCount, ko);
						} else {
							break;
						}
					}

					/*
					 * CREATE AN ARRAY THAT HOLDS LOCATIONS BASED ON THEIR
					 * VISITED FREQUENCY. WHEN A MOBILE NODE NEEDS TO CHOOSE ONE
					 * OF THESE, THE LOCATION WITH HIGHER FREQUENCY HAS A HIGHER
					 * CHANCE TO BE REVISITED.
					 */

					int array[] = new int[C3];
					int an = 0, len = 0;

					for (ko = 0; ko < max_locations; ko++) {
						if (node_location.get(iterationCount, ko) != 0) {
							for (an = 0; an < node_location.get(iterationCount,
									ko); an++) {
								array[len] = ko;
								len++;
							}
						} else {
							break;
						}
					}

					int selected = -1;
					
					int temp_an = -1;
					while(temp_an < 0){
						temp_an = (int) (Math
							.ceil(smooth.getNextDouble() * len) - 1);
					}
					/*
					 * SELECT A LOCATION FROM PREVIOUSLY VISITED ONES WITH
					 * PROBABILITY PROPORTIONAL TO THE NUMBER OF TIMES THE
					 * LOCATION HAS BEEN VISITED
					 */
//					System.out.println("Temp an: "+temp_an);
					selected = array[temp_an];
//					x_2 = locations.get(iterationCount).getWaypoint(selected).pos.x;
//					y_2 = locations.get(iterationCount).getWaypoint(selected).pos.y;
					x_2 = locations.get(iterationCount, selected, 0);
					y_2 = locations.get(iterationCount, selected, 1);
					
					if (x_2 == prev_xy.get(iterationCount,0)
							&& y_2 == prev_xy.get(iterationCount,1)) {
						done = 0;
					}

					else {
						double norm = Math.sqrt(Math.pow((x_2 - prev_xy.get(iterationCount, 0)), 2) + Math.pow((y_2 - prev_xy.get(iterationCount,1)), 2));
						if (norm < 1 || norm > f_max) {
							done = 0; /* FLIGHT LENGTH RESTRICTIONS APPLY */
						} else {
//							next_xy.remove(iterationCount);
//							next_xy.add(iterationCount, new Position(x_2, y_2));
							next_xy.set(iterationCount, 0, x_2);
							next_xy.set(iterationCount, 1, y_2);
							
							node_location
									.set(iterationCount, selected,
											node_location.get(iterationCount,
													selected) + 1);
							done = 1;

						}
					}/* END ELSE */

				}/* END ELSE PREV */

			}/* END WHILE */

//			double first = next_xy.get(iterationCount).x
//					- prev_xy.get(iterationCount).x;
//			double second = next_xy.get(iterationCount).y
//					- prev_xy.get(iterationCount).y;
			double first = next_xy.get(iterationCount, 0) - prev_xy.get(iterationCount, 0);
			double second = next_xy.get(iterationCount, 1) - prev_xy.get(iterationCount, 1);
			double sum_ = Math.pow(first, 2) + Math.pow(second, 2);
			double norm = Math.sqrt(sum_);

			/*
			 * PRINT THE LENGTH OF THE FLIGHT MADE BY THE MOBILE NODE; USED TO
			 * PLOT FLIGHT LENGTHS DISTRIBUTION
			 */

			/* ESTIMATE FLIGHT TIME/VELOCITY BASED ON FLIGHT LENGTH (=norm) */

			double k = 18.72, rho = 0.79;
			if (norm >= 500) {
				k = 1.37;
				rho = 0.36;
			}
			double flight_time = k * Math.pow(norm, (1 - rho));
			if (norm == 0 && flight_time == 0)
				velocity = 0;
			else
				velocity = norm / flight_time;

			speed.set(0, velocity);
			flight_time = norm / velocity;

			pause_start_time.set(iterationCount,
					prev_start_time.get(iterationCount) + flight_time);
			pause_end_time.set(iterationCount,
					pause_start_time.get(iterationCount) + p_time);
		}

		/*
		 * CASE 2: THE MOBILE NODE IS MOVING; CALCULATE THE REMAINING LENGTH
		 * STILL TO BE COVERED TO REACH THE DESTINATION
		 */

		if (time >= prev_start_time.get(iterationCount)
				&& time < pause_start_time.get(iterationCount)) {
			paused.set(iterationCount, 0);

			remaining_distance(prev_xy, next_xy, crt_xy, prev_start_time,
					pause_start_time, time, iterationCount);

		}

		/*
		 * CASE 3: THE MOBILE NODE HAS EITHER JUST REACHED THE DESTINATION OR IS
		 * BEEN PAUSED FOR SOMETIME.
		 */

		else if (time >= pause_start_time.get(iterationCount)
				&& time <= pause_end_time.get(iterationCount)) {

			if (paused.get(iterationCount) == 0)
			/* JUST REACHED THE DESTINATION NODE */
			{
				paused.set(iterationCount, 1);
				int try1 = iterationCount, try2 = 0;
				for (try2 = 0; try2 < nodes; try2++) {
					if (try1 != try2) {
						double distance2 = Math
								.sqrt(Math.pow(
										(crt_xy.get(try1, 0) - crt_xy.get(try2, 0)),
										2)
										+ Math.pow((crt_xy.get(try1, 1) - crt_xy
												.get(try2, 1)), 2));

						if (distance2 <= range) {
							if (status.get(try1, try2) == -1) {
								cn.set(try1, try2, cn.get(try1, try2) + 1);
								status.set(try1, try2, 1);
								fct.set(try1, try2, time);
								lct.set(try1, try2, time);
							} else if (status.get(try1, try2) == 0) {
								cn.set(try1, try2, cn.get(try1, try2) + 1);
								double ict1 = time - lct.get(try1, try2);
								fct.set(try1, try2, time);
								ict.set(try1, try2, ict1);
								lct.set(try1, try2, time);
								status.set(try1, try2, 1);
								SMOOTH.output(PrintTypes.ICT, ict1);
							} else {
								continue;
							}
						} else if (status.get(try1, try2) == 1) {
							lct.set(try1, try2, time);
							status.set(try1, try2, 0);
							ct.set(try1,
									try2,
									ct.get(try1, try2)
											+ (time - fct.get(try1, try2)));
							/* USE FOR DISTRIBUTION OF CONTACT DURATIONS */
							SMOOTH.output(PrintTypes.CT, ct.get(try1, try2));
						}
					}/* IF try1 != try2 */
				}/* FOR try2 */
			}/* END IF */
			//crt_xy.remove(iterationCount);
			//crt_xy.add(iterationCount, next_xy.get(iterationCount));
			crt_xy.set(iterationCount, 0, next_xy.get(iterationCount, 0));
			crt_xy.set(iterationCount, 1, next_xy.get(iterationCount, 1));
		}

	}

	/**
	 * IMPLEMENTS THE FUNCTION REMAINING_DISTANCE FROM THE FILE LOCATIONMAP.C.
	 * THE FUNCTION DOES NOT MODIFY THE POINTERS PASSED INTO IT, SO THE
	 * IMPLEMENTATION DOES NOT NEED TO BE CHANGED
	 * 
	 * @param prev_xy2 2-D ARRAY HOLDING 1ST LOCATION
	 * @param next_xy2 2-D ARRAY HOLDING 2ND LOCATION
	 * @param crt_xy2 CRT_XY FIELD
	 * @param prev_start_time2 PREV_START_TIME FIELD
	 * @param pause_start_time2 PAUSE_START_TIME FIELD
	 * @param t TIME
	 * @param i INDEX
	 * @return THE REMAINING DISTANCE
	 * @throws WrapperMaximumLengthExceededException 
	 */
	private double remaining_distance(TwoDDoubleWrapper prev_xy2,
			TwoDDoubleWrapper next_xy2, TwoDDoubleWrapper crt_xy2,
			OneDDoubleWrapper prev_start_time2,
			OneDDoubleWrapper pause_start_time2, double t, int i) throws WrapperMaximumLengthExceededException {
		double rem_length = 0;
		double t_gap = pause_start_time2.get(i) - prev_start_time2.get(i);
		if (t_gap == 0) {
//			crt_xy2.remove(i);
//			crt_xy2.add(i, prev_xy2.get(i));
			crt_xy2.set(i, 0, prev_xy2.get(i, 0));
			crt_xy2.set(i, 1, prev_xy2.get(i, 1));
			rem_length = 0;
			return (rem_length);
		}
		double t_deg = t - prev_start_time2.get(i);
		double difference[] = { next_xy2.get(i,  0) - prev_xy2.get(i,  0),
				next_xy2.get(i,  1) - prev_xy2.get(i, 1) };
		double tmp[] = { difference[0] * t_deg / t_gap,
				difference[1] * t_deg / t_gap };
//		crt_xy2.remove(i);
//		crt_xy2.add(i, prev_xy2.get(i));
		crt_xy2.set(i, 0, prev_xy2.get(i,  0) + tmp[0]);
		crt_xy2.set(i, 1, prev_xy2.get(i,  1) + tmp[1]);
		double norm = Math.pow((next_xy2.get(i, 0) - crt_xy2.get(i, 0)), 2)
				+ Math.pow((next_xy2.get(i,1) - crt_xy2.get(i,1)), 2);
		rem_length = Math.sqrt(norm);
		return (rem_length);
	}

	public double[] getParam() {
		return param;
	}

	public OneDDoubleWrapper getPause_end_time() {
		return pause_end_time;
	}

	public int getIterationCount() {
		return iterationCount;
	}

	public long getT() {
		return time;
	}

	public OneDIntWrapper getPaused() {
		return paused;
	}

	public OneDDoubleWrapper getPrev_start_time() {
		return prev_start_time;
	}

	public TwoDDoubleWrapper getPrev_xy() {
		return prev_xy;
	}

	public TwoDDoubleWrapper getNext_xy() {
		return next_xy;
	}

	public int getMax_locations() {
		return max_locations;
	}

	public ThreeDDoubleArrayWrapper getLocations() {
		return locations;
	}

	public int getSim_dim_x() {
		return sim_dim_x;
	}

	public int getSim_dim_y() {
		return sim_dim_y;
	}

	public TwoDIntWrapper getNode_location() {
		return node_location;
	}

	public OneDDoubleWrapper getPause_start_time() {
		return pause_start_time;
	}

	public TwoDDoubleWrapper getCrt_xy() {
		return crt_xy;
	}

	public OneDDoubleWrapper getSpeed() {
		return speed;
	}

	public int getNodes() {
		return nodes;
	}

	public int getRange() {
		return range;
	}

	public TwoDIntWrapper getStatus() {
		return status;
	}

	public TwoDDoubleWrapper getCn() {
		return cn;
	}

	public TwoDDoubleWrapper getFct() {
		return fct;
	}

	public TwoDDoubleWrapper getLct() {
		return lct;
	}

	public TwoDDoubleWrapper getIct() {
		return ict;
	}

	public TwoDDoubleWrapper getCt() {
		return ct;
	}
}
