/*******************************************************************************
 ** BonnMotion - a mobility scenario generation and analysis tool             **
 ** Copyright (C) 2014      University of Osnabrueck                          **
 ** Code: Jan-Hendrik Bolte                                                   **
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

package edu.bonn.cs.iv.bonnmotion.models;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.models.slaw.SLAWBase;

/** 
 * Code from "Single user Truncated Levy Walk (TLW) generator" 
 * (http://research.csc.ncsu.edu/netsrv/?q=content/human-mobility-models-download-tlw-slaw)
 *   By Seongik Hong (NCSU), Minsu Shin (Currently, SKT Broadband)
 *   Last revision 3/11/2009
 *   
 * Based on the Paper "On the Levy-Walk Nature of Human Mobility"
 * (http://ieeexplore.ieee.org/xpl/articleDetails.jsp?arnumber=5750071)
*/	

public class TLW extends Scenario {
	
    private static ModuleInfo info;
   
    static {
        info = new ModuleInfo("TLW");
        info.description = "Application to construct mobility scenarios according to the Truncated Levy Walk model";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Jan-Hendrik Bolte");
        info.references.add("I. Rhee, M. Shin, S. Hong, K. Lee, S.J. Kim and S. Chong, \"On the Levy-Walk Nature of Human Mobility,\", In IEEE/ACM Transactions on Networking, Volume 19, Number 3, Pages 630-643, June 2011");
		info.affiliation = ModuleInfo.UOS_SYS;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
    /** TLW parameters */
    private double alpha;    						// Levy exponent for flight length distribution, 0 < alpha <= 2
    private double beta;     						// Levy exponent for pause time distribution, 0 < beta <= 2
    private double maximum_size; 					// size of simulation area
    private double minimum_pause_time = 10;  		// min pause time (seconds)
    private double maximum_pause_time = 7200;  		// max pause time (seconds)
    private double minimum_flight_length = 5;  		// min flight length (meters)
    private double maximum_flight_length = 1000;  	// max flight length (meters)
    private double boundary_condition;  			// boundary condition

    public TLW(int nodes, double x, double y, double duration, double ignore, long randomSeed, double alpha, double beta, double size_max, double s_min, double s_max, double f_min, double f_max, double b_c) {
        super(nodes, x, y, duration, ignore, randomSeed);
        this.alpha = alpha;
        this.beta = beta;
        this.maximum_size = size_max;
        this.minimum_pause_time = s_min;
        this.maximum_pause_time = s_max;
        this.minimum_flight_length = f_min;
        this.maximum_flight_length = f_max;
        this.boundary_condition = b_c;
        generate();
    }

    public TLW(String[] args) {
        go(args);
    }
    
    public void go(String[] args) {
        super.go(args);
        generate();
    }

    public void generate() {
    	
        preGeneration();
		
		// variables
        int pt_scale = 1;
        int fl_scale = 10;       
        int num_step = 50000;
        double mu = 0;
        double time_size = 60;
        parameterData.duration = parameterData.duration - time_size; // TODO: because matlab starts with time_size instead of zero
        double end_time = parameterData.duration;
        double max_x = maximum_size;
        double max_y = maximum_size;
        double[] times = new double[num_step];
        double[] x_values = new double[num_step];
        double[] y_values = new double[num_step];
        double[] pause_times = new double[num_step];
        double[] flight_lengths = new double[num_step];
        double x_mobile[] = new double[(int)parameterData.duration + 1];
		double y_mobile[] = new double[(int)parameterData.duration + 1];
		double t_mobile[] = new double[(int)parameterData.duration + 1];
	
		// iteration for each node
		int node_count = nodeCount();
		for (int mn = 0; mn < node_count; mn++) {
			
			parameterData.nodes[mn] = new MobileNode();
			
	        // generate flight length
	        for (int k = 0; k < num_step; ) {
	        	
	        	double[] stabrnd_result = SLAWBase.stabrnd(0, fl_scale, 0, num_step, alpha, this);

	        	ArrayList<Double> temp = new ArrayList<Double>();
	        	
                for (int i = 0; i < stabrnd_result.length; i++) {
                    if (stabrnd_result[i] > minimum_flight_length && stabrnd_result[i] < maximum_flight_length) {
                        temp.add(new Double(stabrnd_result[i]));
                    }
                }
                if (temp.size() > 0) {
                    for (Double d : temp) {
                    	flight_lengths[k++] = d;
                        if (k >= num_step) {
                        	break;
                        }
                    }
                }
	        }
	        for (int i = 0; i < num_step; i++) {
	        	flight_lengths[i] = Math.round(flight_lengths[i]);
	        }
	        
	        // generate pause time
	        for (int k = 0; k < num_step; ) {
	        	
	        	double[] stabrnd_result = SLAWBase.stabrnd(0, pt_scale, 0, num_step, beta, this);
	        	
	        	ArrayList<Double> temp = new ArrayList<Double>();
	        	
                for (int i = 0; i < stabrnd_result.length; i++) {
                    if (stabrnd_result[i] >= minimum_pause_time && stabrnd_result[i] < maximum_pause_time) {
                        temp.add(new Double(stabrnd_result[i]));
                    }
                }
                if (temp.size() > 0) {
                    for (Double d : temp) {
                    	pause_times[k++] = d;
                        if (k >= num_step) {
                        	break;
                        }
                    }
                }
	        }
	        for (int i = 0; i < num_step; i++) {
	        	pause_times[i] = Math.round(pause_times[i]);
	        }
	        
	        // generate random values for first entries
	        x_values[0] = this.randomNextDouble() * max_x;
	        y_values[0] = this.randomNextDouble() * max_y;
	        times[0] = 0.0;
	        
	        int j = 0;
	        for (int i = 1; i < num_step - 1; i = i + 2) {
	        	
	        	double theta = 2 * Math.PI * this.randomNextDouble();
	        	double next_x = Math.round( x_values[i-1] + flight_lengths[i/2] * Math.cos( theta ) );
	        	double next_y = Math.round( y_values[i-1] + flight_lengths[i/2] * Math.sin( theta ) );
	        	
	            // wrap around
	        	if (boundary_condition == 1.0) {
	        		if (next_x < 0) {
	        			x_values[i] = max_x + next_x;
	        		} else if (next_x > max_x) {
	        			x_values[i] = next_x - max_x;
	        		} else {
	        			x_values[i] = next_x;
	        		}
	        		
	        		if (next_y < 0) {
	        			y_values[i] = max_y + next_y;
	        		} else if (next_y > max_y) {
	        			y_values[i] = next_y - max_y;
	        		} else {
	        			y_values[i] = next_y;
	        		}
	        	}
	        	// reflection
	        	else if (boundary_condition == 2.0) {
	        		if (next_x < 0) {
	        			x_values[i] = -next_x;
	        		} else if (next_x > max_x) {
	        			x_values[i] = max_x - (next_x - max_x);
	        		} else {
	        			x_values[i] = next_x;
	        		}
	        		
	        		if (next_y < 0) {
	        			y_values[i] = -next_y;
	        		} else if (next_y > max_y) {
	        			y_values[i] = max_y - (next_y - max_y);
	        		} else {
	        			y_values[i] = next_y;
	        		}
	        	}
	        	else {
	        		System.err.println("Error@TLW: invalid boundary condition");
	        	}
	        	
	        	double dist = Math.sqrt( (double) Math.pow( (next_x - x_values[i-1]), 2 ) + Math.pow( (next_y - y_values[i-1]), 2 ) );
	    		times[i] = times[i-1] + Math.pow(dist, 1-mu);
	    		times[i+1] = times[i] + Math.abs(pause_times[i/2]);
	    		x_values[i+1] = x_values[i];
	    		y_values[i+1] = y_values[i];
	    				
	    		while (j * time_size < times[i+1]) {
	    			
	    			if (j * time_size < times[i]) {
	    				
	    				double p_ratio = (j * time_size - times[i-1]) / (times[i] - times[i-1]); 
	    				double x_temp = next_x * p_ratio + x_values[i-1] * (1 - p_ratio);
	    				double y_temp = next_y * p_ratio + y_values[i-1] * (1 - p_ratio);
	    				
	    	            // wrap around
	    				if (boundary_condition == 1.0) {
	    	        		if (x_temp < 0) {
	    	        			x_temp = max_x + x_temp;
	    	        		} else if (x_temp > max_x) {
	    	        			x_temp = x_temp - max_x;
	    	        		} 
	    	        		
	    	        		if (y_temp < 0) {
	    	        			y_temp = max_y + y_temp;
	    	        		} else if (y_temp > max_y) {
	    	        			y_temp = y_temp - max_y;
	    	        		} 
	    	        	}
	    	        	// reflection
	    	        	else if (boundary_condition == 2.0) {
	    	        		if (x_temp < 0) {
	    	        			x_temp = -x_temp;
	    	        		} else if (x_temp > max_x) {
	    	        			x_temp = max_x - (x_temp - max_x);
	    	        		} 
	    	        		
	    	        		if (y_temp < 0) {
	    	        			y_temp = -next_y; 
	    	        		} else if (next_y > max_y) {
	    	        			y_temp = max_y - (next_y - max_y);
	    	        		} 
	    	        	}
	    	        	else {
	    	        		System.err.println("Error@TLW: invalid boundary condition");
	    	        	}
	    				
	    				parameterData.nodes[mn].add(j * time_size, new Position( Math.round( x_temp ), Math.round( y_temp ) ));
	    				x_mobile[j] = x_temp;
	    				y_mobile[j] = y_temp;
	    				t_mobile[j] = j * time_size;
	    				
	    			} else {
	    				
	    				parameterData.nodes[mn].add(j * time_size, new Position( Math.round( x_values[i] ), Math.round( y_values[i] ) ));
	    				x_mobile[j] = x_values[i];
	    				y_mobile[j] = y_values[i];
	    				t_mobile[j] = j * time_size;
	    				
	    			}
	    			j = j + 1;
	    			
	    		}
	    		if (times[i+1] > end_time) {
	    			break;
	    		}
	        }
		}
       
        postGeneration();
        
        // restore duration, such that it is printed correctly in the parameter file 
        parameterData.duration += time_size;
    }

    public void write(String _name) throws FileNotFoundException, IOException {
    	
        String[] p = new String[8];
        
        p[0] = "alpha=" + this.alpha;
        p[1] = "beta=" + this.beta;
        p[2] = "size_max=" + this.maximum_size;
        p[3] = "s_min=" + this.minimum_pause_time;
        p[4] = "s_max=" + this.maximum_pause_time;
        p[5] = "f_min=" + this.minimum_flight_length;
        p[6] = "f_max=" + this.maximum_flight_length;
        p[7] = "boundary_condition=" + this.boundary_condition;
        
        super.writeParametersAndMovement(_name, p);

    }
    
    protected boolean parseArg(String key, String value) {
        if (key.equals("alpha")) {
        	alpha = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("beta")) {
        	beta = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("size_max")) {
        	maximum_size = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("s_min")) {
        	minimum_pause_time = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("s_max")) {
        	maximum_pause_time = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("f_min")) {
        	minimum_flight_length = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("f_max")) {
        	maximum_flight_length = Double.parseDouble(value);
            return true;
        }
        else if (key.equals("boundary_condition")) {
        	boundary_condition = Double.parseDouble(value);
            return true;
        }
        else {
        	return super.parseArg(key, value);
        }
    }

    protected boolean parseArg(char key, String value) {
        switch (key) {
            case 'a': 
                alpha = Double.parseDouble(value);
                return true;
            case 'b': 
                beta = Double.parseDouble(value);
                return true;
            case 's':
                maximum_size = Double.parseDouble(value);
                return true;
            case 'm':
                minimum_pause_time = Double.parseDouble(value);
                return true;
            case 'M':
                maximum_pause_time = Double.parseDouble(value);
                return true;
            case 'l':
                minimum_flight_length = Double.parseDouble(value);
                return true;
            case 'L':
                maximum_flight_length = Double.parseDouble(value);
                return true;
            case 'c': 
                boundary_condition = Double.parseDouble(value);
                return true;
            default:
                return super.parseArg(key, value);
        }
    }

    public static void printHelp() {
        System.out.println(getInfo().toDetailString());
        Scenario.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-a <Alpha Value>\n" +
                		   "\t-b <Beta Value>\n" +
                		   "\t-s <Maximum Simulation Size>\n" +
                		   "\t-m <Minimum Pause Time (Seconds)>\n" +
                		   "\t-M <Maximum Pause Time (Seconds)>\n" +
                		   "\t-l <Minimum Flight Length (Meters)>\n" +
                		   "\t-L <Maximum Flight Length (Meters)>\n" +
                		   "\t-c <Boundary Condition>\n\t\tb_c = 1  ==>  wrap-around\n\t\tb_c = 2  ==>  reflection boundary\n");
    }
  
}