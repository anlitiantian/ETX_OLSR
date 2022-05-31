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

package edu.bonn.cs.iv.bonnmotion.apps;

import java.io.*;
import java.util.Vector;

import edu.bonn.cs.iv.bonnmotion.*;
import edu.bonn.cs.iv.bonnmotion.printer.Dimension;
import edu.bonn.cs.iv.util.maps.*;

/** Application that dumps the link durations in a movement scenario to the standard output. */

public class LinkDump extends App {
    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("LinkDump");
        info.description = "Application that dumps informations about the links";
        
        info.major = 1;
        info.minor = 1;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	protected String name = null;
	protected double radius = 0.0;
	protected double begin = 0.0;
	protected double end = Double.MAX_VALUE;
	protected boolean donly = false;
	protected boolean all = true;
	protected boolean inter_contact = false;
	protected boolean inter_contact_only = false;
	protected boolean show_time = false;
	protected boolean useGeo = false;

	protected double duration = 0;
	protected MobileNode node[] = null;

	public LinkDump(String[] args) throws FileNotFoundException, IOException {
		go( args );
	}

	public void go( String[] args ) throws FileNotFoundException, IOException {
		parse(args);
		if ((name == null) || (radius == 0.0)) {
			printHelp();
			System.exit(0);
		}

		Scenario s = (useGeo? MapScenario.getScenario(name) : Scenario.getScenario(name));
		duration = s.getDuration();
		node = s.getNode();

		if (duration < end)
			end = duration;
		
		PrintWriter fICT = null;
		if (inter_contact_only) {
		    fICT = new PrintWriter(new FileOutputStream(name + (useGeo? ".ict_geo_" : ".ict_") + radius));
		}

		PrintWriter fLD = null;
		if (donly) {
		    fLD = new PrintWriter(new FileOutputStream(name + (useGeo? ".ld_geo_" : ".ld_") + radius));
		}

		for (int j = 0; j < node.length; j++) {
			for (int k = j+1; k < node.length; k++) {
			    double[] lsc = null;
			    
			    if (s.getScenarioParameters().calculationDim == Dimension.THREED) {
			        lsc = MobileNode.pairStatistics(node[j], node[k], 0.0, duration, radius, false, s.getBuilding(), Dimension.THREED);
			    } else if (s instanceof MapScenario) {
			    	lsc = pairStatisticsGeo((MapScenario)s, j, k, 0.0, duration, radius);
			    } else {
			        lsc = MobileNode.pairStatistics(node[j], node[k], 0.0, duration, radius, false, s.getBuilding(), Dimension.TWOD);
			    }
			    
				boolean first = true;
				boolean first_inter_contact = true;
				boolean first_inter_contact_print = true;
				double last_linkDown = 0.0;
				for (int l = 6; l < lsc.length; l += 2) {
					double linkUp = lsc[l];
					double linkDown = (l+1 < lsc.length) ? lsc[l+1] : end;
					if ((all && (linkUp <= end) && (linkDown >= begin)) || ((! all) && (linkUp > begin) && (linkDown < end))) {
						if (inter_contact) {						    
						    if (first_inter_contact) {
					    		last_linkDown = linkDown; first_inter_contact = false;
				    		} else {
								if (linkUp - last_linkDown > 0.0) {
									if (inter_contact_only) {
										if (show_time) {
											fICT.println(last_linkDown + " " + (linkUp - last_linkDown));
										} else {
											fICT.println(linkUp - last_linkDown);
										}
								    } else {
								    	if (first_inter_contact_print & !inter_contact_only) {
								    		System.out.println("");
								    		System.out.print(j + " " + k);
								    		first_inter_contact_print = false;
										}
								    	if (show_time) {
								    		System.out.print(" " + last_linkDown + " " + (linkUp - last_linkDown));
										} else {
											System.out.print(" " + (linkUp - last_linkDown));
										}
								    }
								    last_linkDown = linkDown;
								}
						    }
						    if (donly) {
						    	fLD.println(linkDown - linkUp);
						    }
						} else {
							if (all) {
								if (linkUp < begin)
									linkUp = begin;
								if (linkDown > end)
									linkDown = end;
							}
							if (donly) {
								fLD.println(linkDown - linkUp);
							} else {
								if (first) {
									System.out.print(j + " " + k);
									first = false;
								}
								System.out.print(" " + linkUp + "-" + linkDown);
							}
						}
					}
				}
				if (!first)
					System.out.println("");
			}
		}
		if (inter_contact) 
		    System.out.println(""); 
		if (fICT != null)
			fICT.close();
		if (fLD != null)
			fLD.close();
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'b':
				begin = Double.parseDouble(val);
				return true;
			case 'd':
				donly = true;
				return true;
			case 'e':
				end = Double.parseDouble(val);
				return true;
			case 'f':
				name = val;
				return true;
			case 'g':
				useGeo = true;
				return true;
			case 'i':
				inter_contact = true;
				return true;
			case 'j':
				inter_contact = true;
				inter_contact_only = true;
				return true;
			case 't':
				show_time = true;
				return true;
			case 'r':
				radius = Double.parseDouble(val);
				return true;
			case 'w':
				all = false;
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static  void printHelp() {
        System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("LinkDump:");
		System.out.println("\t-b <begin of time span>");
		System.out.println("\t-d [print link durations only]");
		System.out.println("\t-e <end of time span>");
		System.out.println("\t-f <scenario name>");
		System.out.println("\t-g [use GPS (geo) coordinates]");
		System.out.println("\t-i [print intercontact times]");
		System.out.println("\t-j [print intercontact times only]");
		System.out.println("\t-t [print start time of inter-contact times]");
		System.out.println("\t-r <transmission range>");
		System.out.println("\t-w [print only links that go up and down after begin and before end of time span]");
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		new LinkDump(args);
	}
	
	/**
	 * Dirty Code for contacts based on GPS coordinates and geodesics.
	 * 
	 * @param s		MapScenario instance
	 * @param idx1	Index of node 1
	 * @param idx2	Index of node 2
	 * @param start
	 * @param duration
	 * @param range
	 * @return
	 */
    private double[] pairStatisticsGeo(MapScenario s, int idx1, int idx2, double start, double duration, double range)
    {    
        double[] ch1 = s.changeTimes(idx1);
        double[] ch2 = s.changeTimes(idx2);

        Vector<Double> changes = new Vector<Double>();
        int i1 = 0;
        int i2 = 0;
        double t0 = start;
        PositionGeo o1 = s.posGeoAt(idx1, start);
        PositionGeo o2 = s.posGeoAt(idx2, start);

        boolean connected = false;

        while (t0 < duration) {
            double t1;
            if (i1 < ch1.length) {
                if (i2 < ch2.length) {
                    t1 = (ch1[i1] < ch2[i2]) ? ch1[i1++] : ch2[i2++];
                } else {
                    t1 = ch1[i1++];
                }
            } else if (i2 < ch2.length) {
                t1 = ch2[i2++];
            } else {
                t1 = duration;
            }
            
            if (t1 > duration) {
                t1 = duration;
            }
            
            if (t1 > t0) {
                PositionGeo n1 = s.posGeoAt(idx1, t1);
                PositionGeo n2 = s.posGeoAt(idx2, t1);
                boolean conn_t0 = (o1.distance(o2) <= range);
                boolean conn_t1 = (n1.distance(n2) <= range);

                if ((!connected) && conn_t0) {
                    // either we just started, or some floating point op went wrong in the last epoch.
                    changes.addElement(new Double(t0));
                    connected = true;
                }

                double dt = t1 - t0; // time
                double dxo = o1.distanceX(o2.x()) * Math.signum(o1.x() - o2.x()); // distance x at t0
                double dxn = n1.distanceX(n2.x()) * Math.signum(n1.x() - n2.x()); // distance x at t1
                double dyo = o1.distanceY(o2.y()) * Math.signum(o1.y() - o2.y()); // distance y at t0
                double dyn = n1.distanceY(n2.y()) * Math.signum(n1.y() - n2.y()); // distance y at t1
                double c1 = (dxn - dxo) / dt;
                double c0 = (dxo * t1 - dxn * t0) / dt;
                double d1 = (dyn - dyo) / dt;
                double d0 = (dyo * t1 - dyn * t0) / dt;

                if ((c1 != 0.0) || (d1 != 0.0)) { // we have relative movement
                    double m = -1.0 * (c0 * c1 + d0 * d1) / (c1 * c1 + d1 * d1);

                    double m2 = m * m;
                    double q = (c0 * c0 + d0 * d0 - range * range) / (c1 * c1 + d1 * d1);
                    if (m2 - q > 0.0) {
                        double d = Math.sqrt(m2 - q);
                        double min = m - d;
                        double max = m + d;

                        if ((min >= t0) && (min <= t1)) {
                            if (d < 0.01) {
                                System.out.println("---------------");
                                System.out.println("pairStatisticsGeo: The time span these 2 nodes are in range seems very");
                                System.out.println("  short. Might this be an error or a bad choice of parameters?");
                                System.out.println("o1=" + o1);
                                System.out.println("n1=" + n1);
                                System.out.println("o2=" + o2);
                                System.out.println("n2=" + n2);
                                System.out.println("[" + t0 + ";" + t1 + "]:[" + m + "-" + d + "=" + min + ";" + m + "+" + d + "=" + max + "]");
                                System.out.println("---------------");
                            }
                            
                            if (!connected) {
                                changes.addElement(new Double(min));
                                connected = true;
                            } else if (min - t0 > 0.001 && !conn_t0) {
                                System.out.println("pairStatisticsGeo: sanity check failed (1)");
                                System.out.println("t0: " + t0 + ", t1: " + t1);
                                System.out.println("min: " + min + ", d: " + d + ", max: " + max);
                                System.out.println("last connect at " + changes.lastElement());
                                System.out.println("n1 (t0): " + s.posGeoAt(idx1, t0).toString() + ", n2 (t0): " + s.posGeoAt(idx2, t0).toString());
                                System.out.println("dist: " + s.posGeoAt(idx1, t0).distance(s.posGeoAt(idx2, t0)));
                                System.out.println("n1 (t1): " + s.posGeoAt(idx1, t1).toString() + ", n2 (t1): " + s.posGeoAt(idx2, t1).toString());
                                System.out.println("dist: " + s.posGeoAt(idx1, t1).distance(s.posGeoAt(idx2, t1)));
                                System.exit(0);
                            } else {
                                System.out.println("pairStatisticsGeo: connect too late: t=" + min + " t0=" + t0);
                                assert(conn_t0);
                            }
                        }
                        if ((max >= t0) && (max <= t1)) {
                            if (connected) {
                                changes.addElement(new Double(max));
                                connected = false;
                            } else if (t0 - min < 0.001 && max - t0 > 0.001) { // MS: contact starts just before t0 but boolean says unconnected -> fp error?!
                            	if (t0 - changes.lastElement() <= 0.001) { // MS: fp correction 2 has been applied prematurely in previous epoch
                                    System.out.println("pairStatisticsGeo: fp correction 2a: distance " + o1.distance(o2));
                            		assert(o1.distance(o2) - range < 0.001);
                            		// replace last disconnect time
                            		changes.removeElementAt(changes.size() - 1);
                            		changes.addElement(new Double(max));
//                                    System.out.println("t0: " + t0 + ", t1: " + t1);
//                                    System.out.println("min: " + min + ", d: " + d + ", max: " + max);
//                                    System.out.println("last disconnect at " + changes.lastElement());
//                                    System.out.println("last connect at " + changes.get(changes.size() - 2));
//                                    System.out.println("n1 (t0): " + s.posGeoAt(idx1, t0).toString() + ", n2 (t0): " + s.posGeoAt(idx2, t0).toString());
//                                    System.out.println("dist: " + s.posGeoAt(idx1, t0).distance(s.posGeoAt(idx2, t0)));
//                                    System.out.println("n1 (t1): " + s.posGeoAt(idx1, t1).toString() + ", n2 (t1): " + s.posGeoAt(idx2, t1).toString());
//                                    System.out.println("dist: " + s.posGeoAt(idx1, t1).distance(s.posGeoAt(idx2, t1)));
                            	} else {
                                	changes.addElement(new Double(t0));
                                	changes.addElement(new Double(max));
                            	}
                            } else if (max - t0 > 0.001 && conn_t0) {
                                System.out.println("pairStatisticsGeo: sanity check failed (2)");
                                System.out.println("t0: " + t0 + ", t1: " + t1);
                                System.out.println("min: " + min + ", d: " + d + ", max: " + max);
                                System.out.println("last disconnect at " + changes.lastElement());
                                System.out.println("n1 (t0): " + s.posGeoAt(idx1, t0).toString() + ", n2 (t0): " + s.posGeoAt(idx2, t0).toString());
                                System.out.println("dist: " + s.posGeoAt(idx1, t0).distance(s.posGeoAt(idx2, t0)));
                                System.out.println("n1 (t1): " + s.posGeoAt(idx1, t1).toString() + ", n2 (t1): " + s.posGeoAt(idx2, t1).toString());
                                System.out.println("dist: " + s.posGeoAt(idx1, t1).distance(s.posGeoAt(idx2, t1)));
                                System.exit(0);
                            } else {
                                System.out.println("pairStatisticsGeo: disconnect too late: t=" + max + " t0=" + t0);
                                assert(!conn_t0);
                            }
                        }
                    }
                }
                t0 = t1;
                o1 = n1;
                o2 = n2;

                // floating point inaccuracy detection:
                if (connected) {
                    if (!conn_t1) {
                        changes.addElement(new Double(t1));
                        connected = false;
                        System.out.println("pairStatisticsGeo: fp correction 2: disconnect at " + t1);
                    }
                } else { // !connected
                    if (conn_t1) {
                        changes.addElement(new Double(t1));
                        connected = true;
                        System.out.println("pairStatisticsGeo: fp correction 3: connect at " + t1);
                    }
                }
            }
        }
        
        /* add disconnect at the end of time - for correct stats link is counted at link-break */
        /*
         * NA: I do not know, why this wasn't needed before. However, due to our changes we seem to
         * need it and it shouldn't change anything
         */
        if (connected) {
            changes.addElement(new Double(duration));
        }

        double[] result = new double[changes.size() + 6];
        for (int i = 0; i < result.length; i++) {
            if (i == 0) {
                result[i] = 0.0;
                result[i + 1] = 0.0;
                result[i + 2] = 0.0;
                result[i + 3] = 0.0;
                result[i + 4] = 0.0;
                result[i + 5] = 0.0;
                i += 5;
            }
            else {
                result[i] = changes.elementAt(i - 6).doubleValue();
            }
        }

        return result;
    }
}
