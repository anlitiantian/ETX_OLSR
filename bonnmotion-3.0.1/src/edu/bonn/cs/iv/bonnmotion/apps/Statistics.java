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

import edu.bonn.cs.iv.bonnmotion.*;
import edu.bonn.cs.iv.graph.*;
import edu.bonn.cs.iv.util.*;

import java.io.*;
import java.util.Vector;

/** Application that calculates various statistics for movement scenarios. */

public class Statistics extends App {

    private static ModuleInfo info;
    
    static {
        info = new ModuleInfo("Statistics");
        info.description = "Application that calculates various statistics for movement scenarios";
        
        info.major = 1;
        info.minor = 0;
        info.revision = ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("University of Bonn");
		info.affiliation = ModuleInfo.UNIVERSITY_OF_BONN;
    }
    
    public static ModuleInfo getInfo() {
        return info;
    }
    
	public static final int STATS_NODEDEG = 0x00000001;
	public static final int STATS_PARTITIONS = 0x00000002;
	public static final int STATS_MINCUT = 0x00000004;
	public static final int STATS_STABILITY = 0x00000008;
	public static final int STATS_UNIDIRECTIONAL = 0x00000010;
	public static final int STATS_PARTDEG = 0x00000020; // to what degree is the network
														// partitioned?
	protected static boolean calc_velo_over_time = false;
	protected static boolean calc_and_distri = false;

	protected static double secP = 0;
	protected static double secM = 0;
	protected static double secN = 0;
	protected static double secS = 0;
	protected static double secU = 0;
	protected static double secG = 0;
	protected static double secV = 0;
	protected static double[] secA = null;

	protected static boolean printTime = false;

	protected String name = null;
	protected double[] radius = null;
	protected int flags = 0;

	protected static double temporalDependenceC = 100;

	public Statistics(String[] args) throws FileNotFoundException, IOException {
		go(args);
	}

	public void go(String[] _args) throws FileNotFoundException, IOException {
		parse(_args);
		if ((name == null) || ((radius == null) && !calc_velo_over_time && !calc_and_distri)) {
			printHelp();
			System.exit(0);
		}

		System.out.println("reading scenario data");
		Scenario s = Scenario.getScenario(name);
		System.out.println("name " + s.getModelName());

        if (radius != null) {
            if (flags > 0) {
                for (int i = 0; i < radius.length; i++) {
                    Heap sched = new Heap();
                    Heap oosched = new Heap();
                    System.out.println("radius=" + radius[i]);
                    schedule(s, sched, radius[i], false, name, oosched);
                    String basename = name + ".stats_" + radius[i];
                    if (basename.endsWith(".0"))
                        basename = basename.substring(0, basename.length() - 2);
                    progressive(s.nodeCount(s.getModelName(), basename), s.getDuration(), s, sched, true, flags, basename, oosched);
                }
            } else {
                overall(s, radius, name); 
            }
        }

        if (calc_velo_over_time) {
            calcVelocity(name, s);
        }
        if (calc_and_distri) {
            calcAverageNodeDegDistri(name, s);
        }
    }

	/** Calculates statistics' devolution over time. */
	public static void progressive(int nodes, double duration, Scenario s, Heap sched, boolean bidirectional,
			int which, String basename, Heap oosched) throws FileNotFoundException, IOException {

		Graph topo = new Graph();
		for (int i = 0; i < nodes; i++)
			topo.checkNode(i);

		// verwalte zweiten Heap fr On/Off-Events der Knoten
		// basierend auf dieser wird dann Kopie des Graphen verwaltet, aus dem
		// smtliche Off-Nodes jeweils gelscht werden
		boolean off_nodes_exist = (oosched.size() > 0);
		Graph topo_complete = (Graph)topo.clone();
		boolean[] node_off = new boolean[nodes];
		for (int off_init = 0; off_init < nodes; off_init++)
			node_off[off_init] = false;

		double time = 0.0;

		int unicnt = -1;
		int unisrc = -1;
		int unidst = -1;
		int mincut = -1;
		int stability = -1;
		int edges = -1;
		int part = -1;
		double partdeg = -1;

		double tNextDeg = 0.0;
		double tNextMinCut = 0.0;
		double tNextPart = 0.0;
		double tNextStability = 0.0;
		double tNextPartDeg = 0.0;
		double tNextUni = 0.0;

		// target files for stats output
		PrintWriter fDeg = null;
		if ((which & STATS_NODEDEG) > 0)
			fDeg = new PrintWriter(new FileOutputStream(basename + ".nodedeg"));

		PrintWriter fUni = null;
		if ((which & STATS_UNIDIRECTIONAL) > 0)
			fUni = new PrintWriter(new FileOutputStream(basename + ".uni"));

		PrintWriter fPart = null;
		if ((which & STATS_PARTITIONS) > 0)
			fPart = new PrintWriter(new FileOutputStream(basename + ".part"));

		PrintWriter fMinCut = null;
		if ((which & STATS_MINCUT) > 0) {
			fMinCut = new PrintWriter(new FileOutputStream(basename + ".mincut"));
		}

		PrintWriter fStability = null;
		if ((which & STATS_STABILITY) > 0) {
			fStability = new PrintWriter(new FileOutputStream(basename + ".stability"));
		}

		PrintWriter fPartDeg = null;
		if ((which & STATS_PARTDEG) > 0) {
			fPartDeg = new PrintWriter(new FileOutputStream(basename + ".partdeg"));
		}

		double n1 = (double)(nodes - 1);
		int[] uni = new int[4];
		int progress = -1;
		int done = 0;
		while (sched.size() > 0) {
			double ntime = sched.minLevel();
			int nProg = (int)(100.0 * (double)done / (double)(sched.size() + done) + 0.5);
			if (nProg > progress) {
				progress = nProg;
				System.out.print("calculating... " + progress + "% done.\r");
			}
			done++;
			if (ntime > time) {
				if (printTime)
					System.out.println("t=" + time);
				Graph g;

				if (bidirectional) {
					g = topo;
					if (((which & STATS_NODEDEG) > 0) && (time >= tNextDeg)) {
						tNextDeg += secN;

						int ne = 0;
						for (int i = 0; i < g.nodeCount(); i++) {
							Node n = g.nodeAt(i);
							ne += n.outDeg();
						}

						fDeg.println(time + " " + ((double)ne / (double)g.nodeCount()));
					}
				}
				else {
					g = (Graph)topo.clone();
					g.unidirRemove(uni);
					if (((which & STATS_UNIDIRECTIONAL) > 0) && (time >= tNextUni)) {
						tNextUni += secU;

						if (uni[0] != unicnt) {
							unicnt = uni[0];
							fUni.println("unicnt " + time + " " + unicnt);
						}
						if (uni[1] != unisrc) {
							unisrc = uni[1];
							fUni.println("unisrc " + time + " " + unisrc);
						}
						if (uni[2] != unidst) {
							unidst = uni[2];
							fUni.println("unidst " + time + " " + unidst);
						}
					}
					if (((which & STATS_NODEDEG) > 0) && (time >= tNextDeg) && (uni[3] != edges)) {
						tNextDeg += secN;

						edges = uni[3];
						fDeg.println(time + " " + ((double)edges / n1));
					}
				}
				if (((which & STATS_PARTITIONS) > 0) && (time >= tNextPart)) {
					tNextPart += secP;

					int npart = g.partitions(0);
					if (part != npart) {
						part = npart;
						fPart.println(time + " " + part);
					}
				}
				if (((which & STATS_PARTDEG) > 0) && (time >= tNextPartDeg)) {
					tNextPartDeg += secG;

					double npartdeg = g.partdeg(0);
					if (partdeg != npartdeg) {
						partdeg = npartdeg;
						fPartDeg.println(time + " " + partdeg);
					}
				}
				if (((which & STATS_MINCUT) > 0) && (time >= tNextMinCut)) {
					tNextMinCut += secM;

					Graph h = Graph.buildSeperatorTree(g);
					Edge minedge = h.findMinEdge();
					int nmincut = 0;
					if (minedge != null)
						nmincut = minedge.weight;
					if (mincut != nmincut) {
						mincut = nmincut;
						fMinCut.println(time + " " + mincut);
					}
				}
				if (((which & STATS_STABILITY) > 0) && (time >= tNextStability)) {
					tNextStability += secS;

					int nstability = g.stability();
					if (stability != nstability) {
						stability = nstability;
						fStability.println(time + " " + stability);
					}
				}
			}

			if (off_nodes_exist)
				topo = topo_complete; // complete Graph with nodes that are switched off

			time = ntime;

			IndexPair idx = (IndexPair)sched.deleteMin();
			if (idx.i >= 0) { // hack: stopper
				Node src = topo.getNode(idx.i);
				if (src.getSucc(idx.j) == null) {
					Node dst = topo.getNode(idx.j);
					src.addSucc(dst, 1);
					if (bidirectional)
						dst.addSucc(src, 1);
				}
				else {
					src.delSucc(idx.j);
					if (bidirectional)
						topo.getNode(idx.j).delSucc(idx.i);
				}
			}

			if (off_nodes_exist) {
				// update node_off array
				while ((oosched.size() > 0) && (oosched.minLevel() <= time)) {
					Integer off_index = (Integer)oosched.deleteMin();
					node_off[off_index.intValue()] = !node_off[off_index.intValue()];
				}
				// del nodes that are switched off
				topo_complete = (Graph)topo.clone();
				for (int off_i = nodes - 1; off_i >= 0; off_i--) {
					if (node_off[off_i]) {
						Node todell = topo.getNode(off_i);
						topo.delNode(todell);
					}
				}
			}

		}
		System.out.println();

		if (fDeg != null)
			fDeg.close();
		if (fUni != null)
			fUni.close();
		if (fPart != null)
			fPart.close();
		if (fMinCut != null)
			fMinCut.close();
		if (fStability != null)
			fStability.close();
		if (fPartDeg != null)
			fPartDeg.close();
	}

	public static void calcVelocity(String basename, Scenario s) {
		MobileNode[] node = s.getNode();
		double duration = s.getDuration();
		double[][] velos_over_time = new double[node.length][];
		PrintWriter fVelo = null;

		try {
			fVelo = new PrintWriter(new FileOutputStream(basename + ".velocity_" + secV));
		} catch (IOException ie) {
			System.err.println("Error when opening file: " + basename);
		}
		
        for (int i = 0; i < node.length; i++) {
            velos_over_time[i] = MobileNode.getSpeedoverTime(node[i], 0.0, duration, secV);
        }

		int l = (int)((duration / secV) + 1);

		for (int j = 0; j < l; j++) {

			// calc t_on and d_on
			double t_on = 0.0;
			double d_on = 0.0;
			for (int i = 0; i < node.length; i++) {
				t_on = t_on + velos_over_time[i][(2 * j) + 1];
				d_on = d_on + velos_over_time[i][2 * j];
			}

			double av_speed = d_on / t_on;
			double time = j * secV;

			fVelo.println(time + " " + av_speed);
		}
		
		fVelo.close();
	}

	public static void calcAverageNodeDegDistri(String basename, Scenario s) {
		MobileNode[] node = s.getNode();
		double duration = s.getDuration();
		double[] and_per_node = new double[node.length];
		double[] conn_time_help;
		PrintWriter fANDDistri = null;

		for (int r = 0; r < secA.length; r++) {
			System.out.println("Starting with calculation of Average Node Deg. Distri Radius " + secA[r]);

			try {
				fANDDistri = new PrintWriter(new FileOutputStream(basename + ".and_distri_" + secA[r]));
			}
			catch (IOException ie) {
				System.err.println("Error when opening file: " + basename);
			}

			for (int i = 0; i < node.length; i++) {
				and_per_node[i] = 0.0;
				for (int j = 0; j < node.length; j++) {
					if (i != j) {
						conn_time_help = MobileNode.getConnectionTime(node[i], node[j], 0.0, duration, secA[r], s.getScenarioParameters().calculationDim);
						and_per_node[i] = and_per_node[i] + (conn_time_help[1] / conn_time_help[0]);
					}
				}
				fANDDistri.println(i + " " + and_per_node[i]);
			}
			fANDDistri.close();
		}
	}

	/** Put LinkStatusChange-events into a heap. */
	public static double[] schedule(Scenario s, Heap sched, double radius, boolean calculateMobility, String basename, Heap onoffsched) {
		MobileNode[] node = s.getNode();
		double duration = s.getDuration();
		double mobility = 0.0;
		double on_time = 0.0; // on time for links
		double on_time_node = 0.0;
		double mobility_pairs[][] = new double[node.length][node.length];
		double on_time_pairs[][] = new double[node.length][node.length];
		double D_spatial = 0.0;
		int D_spatial_count = 0;
		double Relative_speed = 0.0;
		int Relative_speed_count = 0;
		int connectedPairs = 0;
		int total = (node.length - 1) * node.length / 2;
		int done = 0;
		int progress = -1;
		double result[] = new double[5];

		for (int i = 0; i < node.length; i++) {
			// put on off events in a seperate heap
			double[] onoffChanges = MobileNode.getOnOffChanges(node[i]);
			
			Integer ooidx = new Integer(i);
			for (int m = 0; m < onoffChanges.length; m++) {
				onoffsched.add(ooidx, onoffChanges[m]);
			}
			for (int j = i + 1; j < node.length; j++) {
				int nProg = (int)(100.0 * (double)done / (double)total + 0.5);
				if (nProg > progress) {
					progress = nProg;
					System.err.print("scheduling... " + progress + "% done.\r");
				}
				done++;
				IndexPair idx = new IndexPair(i, j);
				double[] linkStatusChanges;
				linkStatusChanges = MobileNode.pairStatistics(node[i], node[j], 0.0, duration, radius, calculateMobility, s.getScenarioParameters().calculationDim);
	            
				mobility_pairs[i][j] = linkStatusChanges[0];
				on_time_pairs[i][j] = linkStatusChanges[1];
				D_spatial += linkStatusChanges[2];
				D_spatial_count += linkStatusChanges[3];
				Relative_speed += linkStatusChanges[4];
				Relative_speed_count += linkStatusChanges[5];

				if (linkStatusChanges.length > 6) {
					connectedPairs++;
				}

				for (int l = 6; l < linkStatusChanges.length; l++)
					sched.add(idx, linkStatusChanges[l]);
				if ((linkStatusChanges.length & 1) == 0) {
					// explicitely add "disconnect" at the end
					sched.add(idx, duration);
				}
			}
			
		}

		// calc mobility
		for (int i = 0; i < node.length; i++) {
			for (int j = i + 1; j < node.length; j++) {
				on_time = on_time + on_time_pairs[i][j];
				mobility = mobility + mobility_pairs[i][j];
			}
		}

        for (int i = 0; i < node.length; i++) {
            on_time_node = on_time_node + MobileNode.getNodesOnTime(node[i], duration);
        }

		result[0] = mobility / on_time;
		result[1] = on_time_node;
		result[2] = D_spatial / D_spatial_count;
		result[3] = Relative_speed / Relative_speed_count;
		result[4] = connectedPairs;

		System.out.println();
		return result;
	}

	/** Helper function for overall(), merge two partitions. */
	protected static void pmerge(int[] idx, int[] size, int i, int j) {
		int old = idx[j];
		for (int k = 0; k < idx.length; k++)
			if (idx[k] == old)
				idx[k] = idx[i];
		size[idx[i]] += size[old];
		size[old] = 0;
	}

	/** Calc count of nodes that are off at time t **/
	protected static int count_of_offline_nodes_at_time(Scenario s, double time) {

		int count = 0;
		MobileNode[] node = s.getNode();

		for (int i = 0; i < node.length; i++) {
			if (MobileNode.isNodeOffAtTime(node[i], time))
				count++;
		}
		return count;
	}

	/** Calculate statistics averaged over the whole simulation time. */
	public static void overall(Scenario s, double[] radius, String basename) throws FileNotFoundException {
		MobileNode[] node = s.getNode();
		System.out.println("calculation of overall stats started");
		double duration = s.getDuration();
		double[][] ls = null;
		PrintWriter stats = new PrintWriter(new FileOutputStream(basename + ".stats"));
		// check next two lines for ExtendedCatastrophe
		int tEdges = (node.length * (node.length - 1)) / 2;
		double normFact = (double)tEdges * duration;
		Heap heap = null;
		Heap ooheap = null;
		int[] pIdx = null; // partition index
		int[] pSize = null; // partition sizes
		boolean[] isolation = null;

		ls = new double[node.length - 1][];
		for (int i = 0; i < ls.length; i++)
			ls[i] = new double[node.length - i - 1];
		heap = new Heap();
		ooheap = new Heap();
		pIdx = new int[node.length]; // partition index
		pSize = new int[node.length]; // partition sizes
		isolation = new boolean[node.length];

		String metrics[] = {"\"tx range\"", "\"avg. degree of spatial dependence\"", "\"avg. degree\"",
				"\"avg. num. of partitions\"", "\"partitioning degree\"", "\"avg. time to link break\"",
				"\"std. deviation of time to link break\"", "\"link breaks\"", "\"avg. link duration\"",
				"\"total links\"", "\"avg. relative speed\"", "\"avg. path availability\"",
				"\"avg. number of link changes\""};
		String values[][] = new String[radius.length][metrics.length];

		// get temporal dependence
		double D_temporal = getAverageDegreeOfTemporalDependence(node, s);

		// calculate average node speed
		double averageSpeed = 0;
		for (int i = 0; i < node.length; i++) {
			averageSpeed += averageSpeed(i, s);
		}
		averageSpeed = averageSpeed / node.length;

		for (int k = 0; k < radius.length; k++) {
			System.out.println("transmission range=" + radius[k]);

			int partitions = node.length;
			int partitionsOld = node.length;
			int partitions_corrected = node.length;
			int partitions_corrected_old = node.length;
			double pSince = 0.0;
			double avgPart = 0.0;

			double partDeg = 1.0;
			double partDegOld = 1.0;
			double pdSince = 0.0;
			double avgPartDeg = 0.0;

			int linkbreaks = 0;
			double timeToLinkBreak = 0.0;
			int links = 0;
			double linkDuration = 0.0;
			double mobility = 0.0;
			double on_time = 0.0;
			int connections = 0;
			Vector<Double> linkDurations = new Vector<Double>();

			// variables to be used in calculating path availability
			double available[][] = new double[node.length][];

			for (int i = 0; i < node.length; i++) {
				pIdx[i] = i;
				pSize[i] = 1;
				isolation[i] = true;
				for (int j = i + 1; j < node.length; j++)
					ls[i][j - i - 1] = -1.0;

				available[i] = new double[node.length];
				for (int j = 0; j < node.length; j++)
					available[i][j] = 0.0;
			}

			double res_help[] = null;
			res_help = schedule(s, heap, radius[k], (k == 0), basename, ooheap);
			mobility = res_help[0];
			on_time = res_help[1];

			if (k == 0) {
				stats.println("# relative mobility = " + (mobility / normFact));
				stats.println("# average node speed = " + averageSpeed);
				stats.println("# average degree of temporal dependence = " + D_temporal);

				stats.println();
			}
			double tOld = 0.0;
			int progress = -1;
			int done = 0;
			while (heap.size() > 0) {
				int nProg = (int)(100.0 * (double)done / (double)(heap.size() + done) + 0.5);
				if (nProg > progress) {
					progress = nProg;
					System.err.print("calculating... " + progress + "% done.\r");
				}
				done++;
				double tNew = heap.minLevel();
				IndexPair idx = (IndexPair)heap.deleteMin();

				// for calculating path availability
				if (tNew > tOld)
					pathAvailability(node.length, available, tNew - tOld, pIdx);

				if (((tNew > tOld) && (partitions != partitionsOld)) || (heap.size() == 0)) {
					if (heap.size() != 0) {
						avgPart += (double)partitions_corrected_old * (tOld - pSince);
					}
					else {
						avgPart += (double)partitions_corrected * (tNew - pSince);
					}
					partitions_corrected_old = partitions_corrected;
					partitionsOld = partitions;
					pSince = tOld;
				}
				if (((tNew > tOld) && (partDeg != partDegOld)) || (heap.size() == 0)) {
					if (heap.size() != 0) {
						avgPartDeg += partDegOld * (tOld - pdSince);
					}
					else {
						avgPartDeg += partDeg * (tNew - pdSince);
					}
					partDegOld = partDeg;
					pdSince = tOld;
				}
				if (ls[idx.i][idx.j - idx.i - 1] < 0.0) { // connect
					if (tNew < duration) {
					    connections++;
					    ls[idx.i][idx.j - idx.i - 1] = tNew;
					    if (pIdx[idx.i] != pIdx[idx.j]) {
						    partitions--;
						    pmerge(pIdx, pSize, idx.i, idx.j);
					    }
					}
				} else { // disconnect
					connections--;
					double tUp = ls[idx.i][idx.j - idx.i - 1];
					double tConn = tNew - tUp;
					ls[idx.i][idx.j - idx.i - 1] = -1.0;
					linkDuration += tConn;
					links++;
					if ((tNew < duration) && (tUp > 0.0)) {
						linkDurations.addElement(new Double(tConn));
						timeToLinkBreak += tConn;
						linkbreaks++;
						// rebuild pIdx
						if (partitions == 1) {
							partitions = node.length;
							for (int i = 0; i < node.length; i++) {
								pIdx[i] = i;
								pSize[i] = 1;
							}
						} else {
							int split = pIdx[idx.i];
							for (int i = 0; i < node.length; i++)
								if (pIdx[i] == split) {
									partitions++;
									pIdx[i] = i;
									pSize[i] = 1;
								}
							partitions--;
						}
						for (int i = 0; i < ls.length; i++)
							for (int j = i + 1; j < node.length; j++)
								if ((pIdx[i] != pIdx[j]) && (ls[i][j - i - 1] >= 0.0)) {
									partitions--;
									pmerge(pIdx, pSize, i, j);
								}
					}
				}

				partitions_corrected_old = partitions_corrected;
				partitions_corrected = partitions - count_of_offline_nodes_at_time(s, tNew);

				partDeg = 0.0;
				for (int i = 0; i < pSize.length; i++)
					if (pSize[i] > 0)
						partDeg += (double)(pSize[i] * (node.length - pSize[i]));
				tOld = tNew;
			}
			System.err.println();
			double expDuration = timeToLinkBreak / (double)linkbreaks;
			double varDuration = 0.0;
			for (int i = 0; i < linkbreaks; i++) {
				double tmp = linkDurations.elementAt(i).doubleValue() - expDuration;
				varDuration += tmp * tmp;
			}
			varDuration = Math.sqrt(varDuration / (double)(linkbreaks - 1));

			// get spatial dependence
			double D_spatial = res_help[2];
			// get relative speed
			double R_speed = res_help[3];
			// get the number of node pairs that were ever linked
			double connectedPairs = res_help[4];
			// get path availability
			double P_availability = getAveragePathAvailability(available, node.length, duration);

			values[k][0] = Double.toString(radius[k]);
			values[k][1] = Double.toString(D_spatial);
			values[k][2] = Double.toString((2. * linkDuration / on_time));
			values[k][3] = Double.toString((avgPart / duration));
			// TODO: correct it for off-line nodes
			values[k][4] = Double.toString((avgPartDeg / (duration * (double)((node.length - 1) * node.length))));
			values[k][5] = Double.toString(expDuration);
			values[k][6] = Double.toString(varDuration);
			values[k][7] = Integer.toString(linkbreaks);
			values[k][8] = Double.toString((linkDuration / (double)links));
			values[k][9] = Integer.toString(links);
			values[k][10] = Double.toString(R_speed);
			values[k][11] = Double.toString(P_availability);
			values[k][12] = Double.toString(links / connectedPairs);
		}

		printOverall(metrics, values, stats);
		stats.close();
	}

	/***
	 * Checks if there is a path between each pair of nodes and adds that to the available array.
	 * 
	 * @param nodes The number of nodes
	 * @param available 2D array to be updated with availability time
	 * @param time_to_next Time between changes in connectivity
	 * @param pIdx Partitions
	 * */
	protected static void pathAvailability(int nodes, double available[][], double time_to_next, int pIdx[]) {
		for (int i = 0; i < nodes; i++)
			for (int j = i + 1; j < nodes; j++) {
				if (pIdx[i] == pIdx[j])
					available[i][j] += time_to_next;
			}
	}

	/*** 
	 * Calculates the total path availability.
	 * 
	 * @param g Graph
	 * @param available 2D array with availability time
	 * @param nodes The number of nodes
	 * @param duration Total simulation time
	 * @return double containing the total path availability
	 * */
	protected static double getAveragePathAvailability(double[][] available, int nodes, double duration) {
		double total_available = 0.0;
		int num_pairs = 0;
		for (int i = 0; i < nodes; i++)
			for (int j = i + 1; j < nodes; j++) {
				if (available[i][j] > 0.0) {
					total_available += available[i][j];
					num_pairs++;
				}
			}
		return total_available / (num_pairs * duration);
	}

	/*** 
	 * Gets the temporal dependence for the simulation.
	 * 
	 * @param nodes The mobile nodes in the simulation
	 * @param s The scenario of the simulation
	 * @return double of the total temporal dependence
	 * */
	protected static double getAverageDegreeOfTemporalDependence(MobileNode nodes[], Scenario s) {
		double temp[] = new double[2];
		double D_temporal = 0.0;
		double D_temporal_count = 0.0;
		for (int i = 0; i < nodes.length; i++) {
			temp = MobileNode.getDegreeOfTemporalDependence(nodes[i], 0.0, s.getDuration(), temporalDependenceC);
			D_temporal += temp[0];
			D_temporal_count += temp[1];
		}
		return D_temporal / D_temporal_count;
	}

	/**
	* Calculates average speed of given node
	*
	* @param nodeIdx Node index
	* @param s Scenario to retrieve node from
	* @return Double containing all changes in average speed 
	*/
	protected static double averageSpeed(int nodeIdx, Scenario s) {
		double distance = 0;
		double time = 0;

		MobileNode node = s.getNode(nodeIdx);
		int numWaypoints = node.getNumWaypoints();

		Waypoint last = node.getWaypoint(0);
		Waypoint actual;

		for (int i = 0; i < numWaypoints; i++) {
			actual = node.getWaypoint(i);
			distance += actual.pos.distance(last.pos);
			time += actual.time - last.time;
			last = actual;
		}

		return distance / time;
	}

	/**
	 * Prints the calculated metrics
	 * 
	 * @param metrics Array with the metric names
	 * @param values 2D array of calculated values
	 * @param stats PrintWriter
	 */
	protected static void printOverall(String[] metrics, String[][] values, PrintWriter stats) {
		String temp;
		for (int i = 0; i < metrics.length; i++) {
			temp = String.format("%1$-" + 40 + "s", metrics[i]);
			for (int j = 0; j < values.length; j++) {
				if (i == 0 && (metrics.length != values[j].length)) {
					System.err.println("Not the same number of metrics and metric value sets.");
					return;
				}
				temp += String.format("%1$-" + 30 + "s", values[j][i]);
			}
			stats.println(temp);
		}
	}

	protected boolean parseArg(char key, String val) {
		switch (key) {
			case 'A': // Average Node Degree Distribution
				calc_and_distri = true;
				secA = App.parseDoubleArray(val);
				return true;
			case 'c': // c value for temporal dependence
				temporalDependenceC = Double.parseDouble(val);
				return true;
			case 'f':
				name = val;
				return true;
			case 'G': // Partitioning Degree
				flags = flags ^ STATS_PARTDEG;
				if (val.length() != 0)
					secG = Double.parseDouble(val);
				return true;
			case 'N': // Node Degree
				flags = flags ^ STATS_NODEDEG;
				if (val.length() != 0)
					secN = Double.parseDouble(val);
				return true;
			case 'P': // Partitions
				flags = flags ^ STATS_PARTITIONS;
				if (val.length() != 0)
					secP = Double.parseDouble(val);
				return true;
			case 'M': // MinCut
				flags = flags ^ STATS_MINCUT;
				if (val.length() != 0)
					secM = Double.parseDouble(val);
				return true;
			case 'r': // radius
				radius = App.parseDoubleArray(val);
				return true;
			case 'S': // Stability
				flags = flags ^ STATS_STABILITY;
				if (val.length() != 0)
					secS = Double.parseDouble(val);
				return true;
			case 't':
				printTime = true;
				return true;
			case 'U': // Unidirectional
				flags = flags ^ STATS_UNIDIRECTIONAL;
				if (val.length() != 0)
					secU = Double.parseDouble(val);
				return true;
			case 'V': // Velocity
				calc_velo_over_time = true;
				if (val.length() != 0)
					secV = Double.parseDouble(val);
				return true;
			default:
				return super.parseArg(key, val);
		}
	}

	public static void printHelp() {
        System.out.println(getInfo().toDetailString());
		App.printHelp();
		System.out.println("Statistics:");
		System.out.println("\t-f <scenario name>");
		System.out.println("\t-r <list of transmission ranges>");
		System.out.println("\t-t [print time on stdout (in progressive mode)]");
		System.out.println("\t-G <sec> Partitioning Degree (progressive mode)");
		System.out.println("\t-M <sec> MinCut (progressive mode)");
		System.out.println("\t-N <sec> Node Degree (progressive mode)");
		System.out.println("\t-P <sec> Partitions (progressive mode)");
		System.out.println("\t-S <sec> Stability (progressive mode)");
		System.out.println("\t-U <sec> Unidirectional (progressive mode)");
		System.out.println("\t-V <sec> Velocity over Time (progressive mode)");
		System.out.println("\t-A <list of radii> Average Node Degree Distribution");
		System.out.println("\t-c <sec> Average Temporal Dependence c value (default: 100)");
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		new Statistics(args);
	}
}
