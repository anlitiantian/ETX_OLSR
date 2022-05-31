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

package edu.bonn.cs.iv.bonnmotion.models;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.PriorityQueue;

import edu.bonn.cs.iv.bonnmotion.MobileNode;
import edu.bonn.cs.iv.bonnmotion.ModuleInfo;
import edu.bonn.cs.iv.bonnmotion.Position;
import edu.bonn.cs.iv.bonnmotion.Scenario;
import edu.bonn.cs.iv.bonnmotion.models.SWIM.Event.Type;

import java.util.concurrent.atomic.AtomicReference;


public class SWIM extends Scenario
{
	// Possible states of a node.
	public enum State
	{
		NEW,		// The node has just been created
		MOVING,		// The node is moving from his current position to the destination
		WAITING		// The node is waiting at his current position
	};
		
	AtomicReference<Boolean>[][] meetInPlace;
	private static BufferedWriter bw = null;
	
	// Node variables
	private int[]					id;									// ID's
	private int[]					currentCell;						// Current cells
	private int[]					destinationCell;					// Destination cells
	private State[]					state;								// States
	private double[]				speed;								// Speeds
	private double[]				posTime;							// Position times
	private double[] 				density;							// Node densities
	private double[]				waitTime;							// Waiting times
	private double[][]				cellWeights;						// Cell weights
	private Position[]				pos;								// Current positions
	private Position[]				dest;								// Destination positions
	private Position[]				home;								// Home positions
	private int[][]					number_of_nodes_seen;				// Total number of nodes seen in a cell
	private int[][]					number_of_nodes_seen_last_visit;	// Number of nodes seen the last visit in the cell
	
	// Simulation Variables	
	private int						cellCount;							// Cells count
	private int						cellCountPerSide;					// Cells per side
	private double					cellLength;							// Cell length
	private double 					nodeRadius;							// Node/Meet Radius
	private double 					currentTime;						// Current time
	private double 					cellDistanceWeight;					// Cell distance weight (alpha)
	private double 					nodeSpeedMultiplier;				// Node speed multiplier
	private double 					waitingTimeExponent;				// Waiting time exponent
	private double 					waitingTimeUpperBound;				// Waiting time upper bound
	private PriorityQueue<Event> 	eventQueue;							// Event queue
		
	// Module info
	private static ModuleInfo info;

	// Initialize module info.
	static
	{
		info = new ModuleInfo("SWIM");
        info.description = "Application to construct mobility scenarios according to the Small World in Motion model";
        
        info.major		= 2;
        info.minor 		= 0;
        info.revision 	= ModuleInfo.getSVNRevisionStringValue("$LastChangedRevision: 650 $");
        
        info.contacts.add(ModuleInfo.BM_MAILINGLIST);
        info.authors.add("Jan-Hendrik Bolte");
        info.references.add("http://swim.di.uniroma1.it/files/SWIM-Simulator.tar.gz");
        info.references.add("http://swim.di.uniroma1.it/files/SWIM-Infocom09.pdf");
		info.affiliation = ModuleInfo.UOS_SYS;
	}

	public static ModuleInfo getInfo()
	{
		return info;
	}

	public SWIM(int _nodes, double _x, double _y, double _duration, double _ignore, long _randomSeed, double _nodeRadius, double _cellDistanceWeight, double _nodeSpeedMultiplier, double _waitingTimeExponent, double _waitingTimeUpperBound)
	{
		super(_nodes, _x, _y, _duration, _ignore, _randomSeed);
		
		this.nodeRadius 			= _nodeRadius;
		this.cellDistanceWeight 	= _cellDistanceWeight;
		this.nodeSpeedMultiplier 	= _nodeSpeedMultiplier;
		this.waitingTimeExponent 	= _waitingTimeExponent;
		this.waitingTimeUpperBound 	= _waitingTimeUpperBound;
				
		generate();
	}

	public SWIM(String[] _args)
	{
		go(_args);
	}

	public void go(String _args[])
	{
		super.go(_args);
		generate();
	}

	public void generate()
	{
		Locale.setDefault(Locale.ENGLISH);
		
		try  {
			bw = new BufferedWriter(new FileWriter(new File("SwimTrace.txt")));
			
			preGeneration();		
					
			int nodeCount 							= this.nodeCount();
			this.cellLength 						= nodeRadius / Math.sqrt(2.0);
			this.cellCountPerSide 					= (int) (Math.ceil(1.0 / cellLength));
			this.cellCount 		  					= cellCountPerSide * cellCountPerSide;
			this.id 								= new int[nodeCount]; 
			this.pos 								= new Position[nodeCount];	
			this.state								= new State[nodeCount];
			this.posTime							= new double[nodeCount];
			this.dest 								= new Position[nodeCount];	
			this.speed 								= new double[nodeCount];
			this.waitTime 							= new double[nodeCount];
			this.home 								= new Position[nodeCount];
			this.currentCell 						= new int[nodeCount]; 
			this.destinationCell 					= new int[nodeCount]; 
			this.density 							= new double[nodeCount];
			this.cellWeights 					 	= new double[nodeCount][];
			this.number_of_nodes_seen 			 	= new int[nodeCount][];
			this.number_of_nodes_seen_last_visit 	= new int[nodeCount][];
	
			meetInPlace = (AtomicReference<Boolean>[][]) new AtomicReference[nodeCount][];
			for (int i = 0; i < nodeCount; ++i)
			{
				meetInPlace[i] = (AtomicReference<Boolean>[]) new AtomicReference[nodeCount];
			}
			for (int i = 0; i < nodeCount; ++i)
			{
				for (int j = 0; j < nodeCount; ++j)
				{
					meetInPlace[i][j] = new AtomicReference<Boolean>(false);
					meetInPlace[j][i] = new AtomicReference<Boolean>(false);
				}
			}
			
			for (int i = 0; i < nodeCount; i++)
			{
				double y 		= this.randomNextDouble();
				double x 		= this.randomNextDouble();
				
				Position homePos 					= new Position(x, y);
				id[i]								= i;
				pos[i]								= homePos;
				state[i]							= State.NEW;			
				posTime[i]							= 0.0;
				dest[i]								= homePos;
				speed[i]							= 0.0;
				waitTime[i]							= 0.0;
				home[i]								= homePos;
				currentCell[i]						= this.getCellIndexFromPos(homePos);
				destinationCell[i]					= currentCell[i];
				density[i]							= Math.PI * nodeRadius * nodeRadius * nodeCount;
				cellWeights[i] 						= new double[cellCount];
				number_of_nodes_seen[i] 			= new int[cellCount];
				number_of_nodes_seen_last_visit[i]  = new int[cellCount];
				
				for (int j = 0; j < cellWeights[i].length; j++)
				{
					cellWeights[i][j] 						= 0.0;
					number_of_nodes_seen[i][j] 				= 0;
					number_of_nodes_seen_last_visit[i][j] 	= 0;
					
					if (cellDistanceWeight == 0.0)
					{
						number_of_nodes_seen[i][j] 				= 1;
						number_of_nodes_seen_last_visit[i][j]	= 1;
					}
				}
				
				parameterData.nodes[i] = new MobileNode();
			}
			
			this.initNodes();
				
			Comparator<Event> comp = new Comparator<Event>()
			{
				@Override
				public int compare(Event o1, Event o2)
				{
					// the event with greater time has lower priority
					if (o1.time >= o2.time)
					{
						return 1;
					} 
					else
					{
						return -1;
					}
				}
			};
			this.eventQueue = new PriorityQueue<>(101, comp);
	
			currentTime = 0;
	
			// check for initial contacts
			for (int i = 0; i < nodeCount; i++)
			{
				for (int j = i + 1; j < nodeCount; j++)
				{
					if (circles(new Position(getPosition(i).x, getPosition(i).y), nodeRadius, new Position(getPosition(j).x, getPosition(j).y), nodeRadius))
					{
						eventQueue.add(new Event(Type.MEET, i, j, 0));
					}
				}
			}
						
			// create initial events
			for (int i = 0; i < nodeCount; i++)
			{
				eventQueue.add(new Event(Type.START_WAITING, i, -1, 0));
			}
						
			while (true)
			{			
				if (eventQueue.size() == 0)
				{
					break;
				}
				
				Event e = eventQueue.poll();
				currentTime = e.time;
				
				if (currentTime >= parameterData.duration)
				{
					break;
				}
				
				if (currentTime > parameterData.ignore)
				{
					if (e.type == Type.MEET)
					{
						if ((getState(e.firstNode) == State.WAITING && getState(e.secondNode) == State.MOVING))
						{
							if (circles(getPosition(e.firstNode), nodeRadius, getDestination(e.secondNode), nodeRadius))
							{
								meetInPlace[e.firstNode][e.secondNode].set(true);
								meetInPlace[e.secondNode][e.firstNode].set(true);
								PrintEvent(e, "MP");
							} 
							else 
							{
								PrintEvent(e, "MM");
							}
						} 
						else
						{
							if ((getState(e.firstNode) == State.MOVING && getState(e.secondNode) == State.MOVING))
							{
								if (circles(getDestination(e.firstNode), nodeRadius, getDestination(e.secondNode), nodeRadius))
								{
									// Find the time in which at least one node
									// reaches the destinations
									double minTravelTime = Math.min(getPositionAbsoluteTime(e.firstNode) + getTravelTime(e.firstNode), getPositionAbsoluteTime(e.secondNode) + getTravelTime(e.secondNode));
	
									// If in that time the nodes are still seeing
									// each other we print MP, otherwise we print MM
									if (circles(computePositionAtTime(minTravelTime, e.firstNode), nodeRadius, computePositionAtTime(minTravelTime, e.secondNode), nodeRadius))
									{
										meetInPlace[e.firstNode][e.secondNode].set(true);
										meetInPlace[e.secondNode][e.firstNode].set(true);
										PrintEvent(e, "MP");
									} 
									else 
									{
										PrintEvent(e, "MM");
									}
								} 
								else 
								{
									PrintEvent(e, "MM");
								}
							}
							else if ((getState(e.firstNode) == State.MOVING && getState(e.secondNode) == State.WAITING))
							{
								if (circles(getDestination(e.firstNode), nodeRadius, getPosition(e.secondNode), nodeRadius))
								{
									meetInPlace[e.firstNode][e.secondNode].set(true);
									meetInPlace[e.secondNode][e.firstNode].set(true);
									PrintEvent(e, "MP");
								} 
								else 
								{
									PrintEvent(e, "MM");
								}
							}
						}
					} 
					else if (e.type == Type.LEAVE)
					{
						if (meetInPlace[e.firstNode][e.secondNode].get().booleanValue() || meetInPlace[e.secondNode][e.firstNode].get().booleanValue())
						{
							meetInPlace[e.firstNode][e.secondNode].set(false);
							meetInPlace[e.secondNode][e.firstNode].set(false);
							PrintEvent(e, "LP");
						} 
						else 
						{
							PrintEvent(e, "LM");
						}
					} 
					else 
					{
						PrintEvent(e);
					}
				}
				
				// handle event
				switch (e.type)
				{
					case START_MOVING:
						updatePosition(e.firstNode, getDestination(e.firstNode), currentTime);
						moveToRandomDestination(e.firstNode);
						eventQueue.add(new Event(Type.END_MOVING, e.firstNode, -1, e.time + getTravelTime(e.firstNode)));
						checkContacts(e.firstNode);
						break;
		
					case START_WAITING:
						updatePosition(e.firstNode, getDestination(e.firstNode), currentTime);
						waitRandomTime(e.firstNode);
						double timeToWait = getTravelTime(e.firstNode);
						eventQueue.add(new Event(Type.END_WAITING, e.firstNode, -1, e.time + timeToWait));
						checkContacts(e.firstNode);
						break;
		
					case END_MOVING:
						eventQueue.add(new Event(Type.START_WAITING, e.firstNode, -1, e.time));
						break;
		
					case END_WAITING:
						eventQueue.add(new Event(Type.START_MOVING, e.firstNode, -1, e.time));
						break;
		
					case MEET:
						meet(e.firstNode, e.secondNode);
						meet(e.secondNode, e.firstNode);
						break;
						
					case LEAVE:
						break;
		
					default:
						break;
				}
			}
			postGeneration();
			bw.flush();
		
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		} 
		finally 
		{
			try 
			{
				bw.close();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
	}

	protected boolean parseArg(char _key, String _value)
	{
		switch (_key)
		{
			case 'r':
				this.nodeRadius = Double.parseDouble(_value);
				return true;
			case 'c':
				this.cellDistanceWeight = Double.parseDouble(_value);
				return true;
			case 'm':
				this.nodeSpeedMultiplier = Double.parseDouble(_value);
				return true;
			case 'e':
				this.waitingTimeExponent = Double.parseDouble(_value);
				return true;
			case 'u':
				this.waitingTimeUpperBound = Double.parseDouble(_value);
				return true;
			default:
				return super.parseArg(_key, _value);

		}
	}

	protected boolean parseArg(String _key, String _value)
	{
		switch (_key)
		{
			case "nodeRadius":
				this.nodeRadius = Double.parseDouble(_value);
				return true;
			case "cellDistanceWeight":
				this.cellDistanceWeight = Double.parseDouble(_value);
				return true;
			case "nodeSpeedMultiplier":
				this.nodeSpeedMultiplier = Double.parseDouble(_value);
				return true;
			case "waitingTimeExponent":
				this.waitingTimeExponent = Double.parseDouble(_value);
				return true;
			case "waitingTimeUpperBound":
				this.waitingTimeUpperBound = Double.parseDouble(_value);
				return true;
			default:
				return super.parseArg(_key, _value);
		}
	}

	public void write(String _filename) throws FileNotFoundException, IOException
	{
		String[] p  = new String[5];
		int i  	    = 0;
        p[i++] 		= "nodeRadius=" 			+ this.nodeRadius;
        p[i++] 		= "cellDistanceWeight="  	+ this.cellDistanceWeight;
        p[i++] 		= "nodeSpeedMultiplier=" 	+ this.nodeSpeedMultiplier;
        p[i++] 		= "waitingTimeExponent="  	+ this.waitingTimeExponent;
        p[i++] 		= "waitingTimeUpperBound=" 	+ this.waitingTimeUpperBound;
        super.writeParametersAndMovement(_filename, p);
	}
	
	public static void printHelp()
	{
		System.out.println(getInfo().toDetailString());
        Scenario.printHelp();
        System.out.println(getInfo().name + ":");
        System.out.println("\t-r <node radius>\n" +
                		   "\t-c <cell distance weight>\n" +
                		   "\t-m <node speed multiplier>\n" +
                		   "\t-e <waiting time exponent>\n" +
                		   "\t-u <waiting time upper bound>\n");
	}
		
	public double getRandomDoubleIn(double _min, double _max)
	{
		double range  = _max - _min;
		double number = this.randomNextDouble();
		return (number * range) + _min;
	}
	
	public void initNodes()
	{
		for (int i = 0; i < parameterData.nodes.length; i++)
		{
			initCellWeights(i);
		}
	}

	public int getCellIndexFromPos(Position _pos)
	{
		int cellRow 	= (int) (_pos.y / cellLength);
		int cellColumn 	= (int) (_pos.x / cellLength);

		// cells are indexed in row-major order
		return (cellRow * cellCountPerSide) + cellColumn;
	}

	public Position getCellCenterPos(int _cellIndex) 
	{
		int cellRow 			= _cellIndex / cellCountPerSide;
		int cellColumn 			= _cellIndex % cellCountPerSide;
		double halfCellLength 	= cellLength / 2.0;
		
		return new Position((cellColumn * cellLength) + halfCellLength, (cellRow * cellLength) + halfCellLength);
	}

	public Position getRandomPointInCell(int _cellIndex)
	{
		Position cellCenter = getCellCenterPos(_cellIndex);

		double halfCellLength = cellLength / 2.0;

		double x = this.getRandomDoubleIn(-halfCellLength, halfCellLength);
		double y = this.getRandomDoubleIn(-halfCellLength, halfCellLength);

		Position point = new Position(x + cellCenter.x, y + cellCenter.y);
		if (point.x < 0)
		{
			point.x = 0;
		}
		else if (point.x >= 1)
		{
			point.x = 1;
		}
		
		if (point.y < 0)
		{
			point.y = 0;
		}
		else if (point.y >= 1)
		{
			point.y = 1;
		}

		return point;
	}
		
	public void updatePosition(int _index, Position _pos, double _time)
	{
		pos[_index] 		= _pos;
		posTime[_index] 	= _time;
	}

	public void moveToRandomDestination(int _index)
	{
		setCellWeight(_index, currentCell[_index], number_of_nodes_seen_last_visit[_index][currentCell[_index]]);
		number_of_nodes_seen_last_visit[_index][currentCell[_index]] = 0;

		destinationCell[_index] = chooseDestinationCell(_index);
					
		Position destinationPoint = this.getRandomPointInCell(destinationCell[_index]);

		state[_index]		= State.MOVING;
		dest[_index]		= destinationPoint;
		speed[_index]		= (destinationPoint.newShiftedPosition(-pos[_index].x, -pos[_index].y)).norm() * nodeSpeedMultiplier;
		waitTime[_index]	= 0.0;
	}

	public void waitRandomTime(int _index)
	{
		state[_index]	   		= State.WAITING;
		dest[_index]		    = pos[_index];
		speed[_index]	   		= 0.0;
		waitTime[_index]	    = computeRandomWaitingTime();
		currentCell[_index] 	= destinationCell[_index];
	}

	public void meet(int _index, int _other)
	{
		if (state[_index] == State.WAITING && getState(_other) == State.MOVING)
		{
			if (circles(new Position(pos[_index].x, pos[_index].y), nodeRadius, new Position(getDestination(_other).x, getDestination(_other).y), nodeRadius))
			{
				number_of_nodes_seen_last_visit[_index][currentCell[_index]]++;
			}
		}
		else
		{
			if (state[_index] == State.MOVING && getState(_other) == State.MOVING)
			{
				if (circles(new Position(dest[_index].x, dest[_index].y), nodeRadius, new Position(getDestination(_other).x, getDestination(_other).y), nodeRadius))
				{
					number_of_nodes_seen_last_visit[_index][destinationCell[_index]]++;
				}
			}
			
			if (state[_index] == State.MOVING && getState(_other) == State.WAITING)
			{
				if (circles(new Position(dest[_index].x, dest[_index].y), nodeRadius, new Position(getPosition(_other).x, getPosition(_other).y), nodeRadius))
				{
					number_of_nodes_seen_last_visit[_index][destinationCell[_index]]++;
				}
			}
		}
	}

	public State getState(int _index) 
	{
		return state[_index];
	}

	public Position getPosition(int _index) 
	{
		return pos[_index];
	}

	public Position getDestination(int _index) 
	{
		return dest[_index];
	}

	/**
	 * Return the travel time of the node. This is the time to reach the 
	 * destination if the node is moving, or the waiting time if the node
	 * is waiting.
	 * @param index
	 * @return
	 */
	public double getTravelTime(int _index) 
	{
		if (state[_index] == State.WAITING)
		{
			return waitTime[_index];
		}
		else if (state[_index] == State.MOVING)
		{
			double moveDistance = (dest[_index].newShiftedPosition(-pos[_index].x, -pos[_index].y)).norm();
			return moveDistance / speed[_index];
		}
		else
		{
			return 0.0;
		}
	}

	/**
	 * Init cell weights.
	 * @param index
	 */
	public void initCellWeights(int _index)
	{
		for (int i = 0; i < cellWeights[_index].length; i++)
		{
			setCellWeight(_index, i, 0);
		}
	}

	public void setCellWeight(int _index, int _cellIndex, int _seen)
	{
		number_of_nodes_seen[_index][_cellIndex] += _seen;
		
		double distanceValue 	= distanceFunction(_index, _cellIndex);
		double seenValue 		= seenFunction(_index, _cellIndex);

		cellWeights[_index][_cellIndex] = cellDistanceWeight * distanceValue + (1.0 - cellDistanceWeight) * seenValue;
	}
	

	/**
	 * Compute a random waiting time with an upper bounded power law distribution.
	 *					
	 *
	 * The distribution function is f(x) = (slope-1) * x^(-slope).
	 * The integral from 1 to +inf of f(x) is equal to 1.
	 *
	 * A random number y is uniformly chosen in [0,1]. This number
	 * represents the area under f(x) starting from x=1.
	 * This area is the integral from 1 to z of f(x) and is equal to
	 * 1 - z^( -slope+1 ). So:
	 *
	 *     y = 1 - z^( -slope+1 )
	 *
	 *     Thus:
	 *
	 *     z = ( 1-y )^( 1 / ( -slope+1 ) )
	 *
	 *     z is distributed in [1,+inf) as the power law f(x).
	 * @return
	 */
	public double computeRandomWaitingTime() 
	{
		double slope 		= this.waitingTimeExponent;
		double upperBound 	= this.waitingTimeUpperBound;

		// choose a random number uniformly in [0,1]
		double y = this.randomNextDouble();

		// convert to a random number in [1,+inf) with power law distribution
		double exponent = 1.0 / (-slope + 1);
		double time 	= Math.pow(1.0 - y, exponent);
		
		// apply an upper bound
		if (time > upperBound)
		{
			time = upperBound;
		}

		return time;
	}

	/**
	 * Compute the distance() function of SWIM
	 * @param index
	 * @param _cellIndex
	 * @return
	 */
	public double distanceFunction(int _index, int _cellIndex) 
	{
		double k 			= 1.0 / nodeRadius;
		double max 			= 0.0;
		double distance 	= (home[_index].newShiftedPosition(-this.getCellCenterPos(_cellIndex).x, -this.getCellCenterPos(_cellIndex).y)).norm();
		double denominator 	= 1.0 + (k * distance);
		
		denominator *= denominator;
		double inverse_distance_Ci = 1.0 / denominator;
		
		for (int j = 0; j < cellWeights[_index].length; j++)
		{
			double distance_Cj 			 = (home[_index].newShiftedPosition(-this.getCellCenterPos(j).x, -this.getCellCenterPos(j).y)).norm();
			double denominator_Cj 		 = 1.0 + (k * distance_Cj);
			denominator_Cj 				*= denominator_Cj;
			double inverse_distance_Cj 	 = 1.0 / denominator_Cj;
			
			if (inverse_distance_Cj > max)
			{
				max = inverse_distance_Cj;
			}
		}
		
		return inverse_distance_Ci / max;
	}

	/**
	 * Compute the seen() function of SWIM.
	 * @param index
	 * @param _cellIndex
	 * @return
	 */
	public double seenFunction(int _index, int _cellIndex) 
	{			
		int visti_Ci = number_of_nodes_seen[_index][_cellIndex];
		
		if (visti_Ci == 0)
		{
			return 1.0 / cellWeights[_index].length;
		}
		
		double nominator = 1.0 + visti_Ci / density[_index];
		
		double max = 0.0;
		for (int j = 0; j < cellWeights[_index].length; j++)
		{
			int visti_Cj = number_of_nodes_seen[_index][j];
			double nominator_j = 1.0 + visti_Cj / density[_index];
			
			if (nominator_j > max)
			{ 
				max = nominator_j;
			}
		}
			
		return nominator / max;
	}

	/**
	 * Choose a random cell proportionally with the cell weights.
	 * Assumes that the sum of the weights is 1.
	 * @param index
	 * @return
	 */
	public int chooseDestinationCell(int _index) 
	{
		int cell = 0;

		double weightSum = 0.0;
		for (int i  = 0; i < cellWeights[_index].length; i++)
		{
			if (i != currentCell[_index])
			{
				weightSum += cellWeights[_index][i];
			}
		}
		double randomReal = this.getRandomDoubleIn(0.0, weightSum);

		weightSum = 0.0;
		for (int i = 0; i < cellWeights[_index].length; i++)
		{
			if (i != currentCell[_index])
			{
				double prevWeightSum = weightSum;
				weightSum += cellWeights[_index][i];
				if (randomReal >= prevWeightSum && randomReal <= weightSum)
				{
					cell = i;
				}
			}
		}
		return cell;
	}
	
	/**
	 * Check if the node will meet other nodes in its current movement.
	 * @param node
	 */
	public void checkContacts(int _index) {
		for (int i = 0; i < parameterData.nodes.length; i++)
		{
			if (_index != i)
			{
				checkContactWithNode(_index, i);
			}
		}
	}
	
	/**
	 * Check for contact between two nodes. Add the MEET and LEAVE events in the queue.
	 * @param node
	 * @param otherNode
	 */
	public void checkContactWithNode(int _node, int _otherNode)
	{
		// compute the node start and end times
		double timeStartA 	= getPositionAbsoluteTime(_node);
		double timeStartB 	= getPositionAbsoluteTime(_otherNode);
		double timeEndA 	= timeStartA + getTravelTime(_node);
		double timeEndB 	= timeStartB + getTravelTime(_otherNode);

		// compute the common time area
		double timeStart 	= (timeStartA 	>= timeStartB 	? timeStartA 	: timeStartB);
		double timeEnd 		= (timeEndA 	<= timeEndB 	? timeEndA 		: timeEndB);

		// compute the node start and end positions
		Position startA = computePositionAtTime(timeStart, _node);
		Position endA 	= computePositionAtTime(timeEnd,   _node);
		Position startB = computePositionAtTime(timeStart, _otherNode);
		Position endB 	= computePositionAtTime(timeEnd,   _otherNode);

		AtomicReference<Double> meetFraction = new AtomicReference<Double>(0.0);
		AtomicReference<Double> leaveFraction = new AtomicReference<Double>(0.0);

		AtomicReference<Boolean> meet = new AtomicReference<Boolean>(false);
		AtomicReference<Boolean> leave = new AtomicReference<Boolean>(false);
		
		// check and handle contacts		
		if (movingCircles(startA, endA, nodeRadius, startB, endB, nodeRadius, meet, leave, meetFraction, leaveFraction).get().booleanValue())
		{
			if (meet.get().booleanValue())
			{
				double meetTime = meetFraction.get().doubleValue() * (timeEnd - timeStart);
				eventQueue.add(new Event(Type.MEET, _node, _otherNode, currentTime + meetTime));
			}
			if (leave.get().booleanValue())
			{
				double leaveTime = leaveFraction.get().doubleValue() * (timeEnd - timeStart);
				eventQueue.add(new Event(Type.LEAVE, _node, _otherNode, currentTime + leaveTime));
			}
		}
	}
		
	
	/**
	 * Return the absolute time when the node was in the current position.
	 * @param index
	 * @return
	 */
	public double getPositionAbsoluteTime(int _index) 
	{
		return posTime[_index];
	}
	
	/**
	 * Return the node position at the given absolute time.
	 * @param _absTime
	 * @param index
	 * @return
	 */
	public Position computePositionAtTime(double _absTime, int _index) 
	{
		Position dir = dest[_index].newShiftedPosition(-pos[_index].x, -pos[_index].y);
		double normalized = dir.norm();
		if (normalized != 0.0)
		{ 
			dir = new Position(dir.x/normalized, dir.y/normalized);
		}
		return pos[_index].newShiftedPosition((dir.x * speed[_index]) * (_absTime - posTime[_index]), (dir.y * speed[_index]) * (_absTime - posTime[_index]));
	}
	
	
	public AtomicReference<Boolean> movingCirclesBoundingBoxTest(Position _startA, Position _endA, double _radiusA, Position _startB, Position _endB, double _radiusB)
	{
		Position minA = new Position(0.0, 0.0);
		Position maxA = new Position(0.0, 0.0);
		Position minB = new Position(0.0, 0.0);
		Position maxB = new Position(0.0, 0.0);

		minA.x = Math.min(_startA.x, _endA.x) - _radiusA;
		minA.y = Math.min(_startA.y, _endA.y) - _radiusA;

		maxA.x = Math.max(_startA.x, _endA.x) + _radiusA;
		maxA.y = Math.max(_startA.y, _endA.y) + _radiusA;

		minB.x = Math.min(_startB.x, _endB.x) - _radiusB;
		minB.y = Math.min(_startB.y, _endB.y) - _radiusB;

		maxB.x = Math.max(_startB.x, _endB.x) + _radiusB;
		maxB.y = Math.max(_startB.y, _endB.y) + _radiusB;

		if ((maxA.x < minB.x) || (minA.x > maxB.x) || (maxA.y < minB.y)	|| (minA.y > maxB.y))
		{
			return new AtomicReference<Boolean>(false);
		}
		else
		{
			return new AtomicReference<Boolean>(true);
		}
	}

	public boolean circles(Position _posA, double _radiusA, Position _posB, double _radiusB)
	{
		Position v 		= _posB.newShiftedPosition(-_posA.x, -_posA.y);
		double radiiSum = _radiusA + _radiusB;
		
		return Position.scalarProduct(v, v) < radiiSum * radiiSum;
	}

	public AtomicReference<Boolean> movingCircles(Position _startA, Position _endA, double _radiusA, Position _startB, Position _endB, double _radiusB, AtomicReference<Boolean> _enter, AtomicReference<Boolean> _exit, AtomicReference<Double> _enterTime, AtomicReference<Double> _exitTime)
	{
		// fast bounding box test
		if (movingCirclesBoundingBoxTest(_startA, _endA, _radiusA, _startB,	_endB, _radiusB).get().booleanValue() == false)
		{
			return new AtomicReference<Boolean>(false);
		}

		// B becomes a sphere with the sum of the radii
		// A becomes a line that moves with velA - velB
		Position velA 		= _endA.newShiftedPosition(-_startA.x, -_startA.y);
		Position dirA 		= velA;
		double normalized 	= dirA.norm();
		if (normalized != 0.0)
		{ 
			dirA = new Position(dirA.x/normalized, dirA.y/normalized);
		}

		Position velB 			= _endB.newShiftedPosition(-_startB.x, -_startB.y);
		Position lineVelocity 	= velA.newShiftedPosition(-velB.x, -velB.y);
		double lineLength 		= lineVelocity.norm();
		Position lineDir 		= lineVelocity;
		if (lineLength != 0.0)
		{ 
			lineDir = new Position(lineDir.x/lineLength, lineDir.y/lineLength);
		}

		Position lineStart 	= _startA;
		Position lineEnd 	= _startA.newShiftedPosition(lineVelocity.x, lineVelocity.y);
		Position spherePos 	= _startB;
		double sphereRadius = _radiusA + _radiusB;

		AtomicReference<Boolean> lineEnters 		= new AtomicReference<Boolean>(false);
		AtomicReference<Boolean> lineExits 			= new AtomicReference<Boolean>(false);
		AtomicReference<Position> lineEnterPoint 	= new AtomicReference<Position>(new Position(0.0, 0.0));
		AtomicReference<Position> lineExitPoint 	= new AtomicReference<Position>(new Position(0.0, 0.0));
		
		if (lineCircle(lineStart, lineEnd, spherePos, sphereRadius, lineEnters,	lineExits, lineEnterPoint, lineExitPoint).get().booleanValue() == false)
		{
			return new AtomicReference<Boolean>(false);
		}
		
		if (_enter != null)
		{
			_enter.set(lineEnters.get().booleanValue());
		}
		if (_exit != null)
		{
			_exit.set(lineExits.get().booleanValue());
		}
		if (_enterTime != null)
		{
			double enterDistance = (lineEnterPoint.get().newShiftedPosition(-lineStart.x, -lineStart.y)).norm();
			_enterTime.set(enterDistance / lineLength);
		}
		if (_exitTime != null)
		{
			double exitDistance = (lineExitPoint.get().newShiftedPosition(-lineStart.x, -lineStart.y)).norm();
			_exitTime.set(exitDistance / lineLength);
		}
		return new AtomicReference<Boolean>(true);
	}

	public AtomicReference<Boolean> lineCircle(Position _lineStart, Position _lineEnd,	Position _circlePos, double _circleRadius, AtomicReference<Boolean> _enter, AtomicReference<Boolean> _exit, AtomicReference<Position> _enterPoint, AtomicReference<Position> _exitPoint)
	{
		if (_enter != null)
		{
			_enter.set(false);
		}

		if (_exit != null)
		{
			_exit.set(false);
		}

		// get line direction and length
		Position lineDir 	= _lineEnd.newShiftedPosition(-_lineStart.x, -_lineStart.y);
		double lineLength 	= lineDir.norm();
		if (lineLength != 0.0)
		{ 
			lineDir = new Position(lineDir.x/lineLength, lineDir.y/lineLength);
		}

		// project circle center on line
		Position lineStartToCirclePos 	= _circlePos.newShiftedPosition(-_lineStart.x, -_lineStart.y);
		double projectionLength 		= Position.scalarProduct(lineDir, lineStartToCirclePos);
		Position projectedCirclePos 	= _lineStart.newShiftedPosition(new Position(lineDir.x * projectionLength, lineDir.y * projectionLength).x, 
																		new Position(lineDir.x * projectionLength, lineDir.y * projectionLength).y);

		// check circle distance
		Position distanceVector = projectedCirclePos.newShiftedPosition(-_circlePos.x, -_circlePos.y);
		double distance 		= distanceVector.norm();

		if (distance > _circleRadius)
		{
			return new AtomicReference<Boolean>(false);
		}

		// get the intersection distances on the line
		double intersectionLength 	= Math.sqrt((_circleRadius * _circleRadius) - (distance * distance));
		double enterDistance 		= projectionLength - intersectionLength;
		double exitDistance 		= projectionLength + intersectionLength;

		// check if the intersection points are inside the line
		AtomicReference<Boolean> lineEnters = new AtomicReference<Boolean>(false);
		AtomicReference<Boolean> lineExits 	= new AtomicReference<Boolean>(false);

		if (enterDistance >= 0 && enterDistance < lineLength)
		{
			lineEnters.set(true);
		}
		if (exitDistance >= 0 && exitDistance < lineLength)
		{
			lineExits.set(true);
		}

		if (!lineEnters.get().booleanValue() && !lineExits.get().booleanValue())
		{
			// the line is inside the circle
			return new AtomicReference<Boolean>(false);
		}

		if (lineEnters.get().booleanValue())
		{
			if (_enterPoint != null)
			{
				_enterPoint.set(_lineStart.newShiftedPosition(new Position(lineDir.x * enterDistance, lineDir.y * enterDistance).x, 
															  new Position(lineDir.x * enterDistance, lineDir.y * enterDistance).y));
			} 
			
			if (_enter != null)
			{
				_enter.set(true);
			}
		}
		if (lineExits.get().booleanValue())
		{
			if (_exitPoint != null)
			{
				_exitPoint.set(_lineStart.newShiftedPosition(new Position(lineDir.x * exitDistance, lineDir.y * exitDistance).x, 
															 new Position(lineDir.x * exitDistance, lineDir.y * exitDistance).y));
			}
			
			if (_exit != null)
			{
				_exit.set(true);
			}
		}
		return new AtomicReference<Boolean>(true);
	}
	
	
	public static class Event
	{
		public enum Type
		{
			INVALID, 
			
			START_MOVING, 
			START_WAITING, 
			END_MOVING, 
			END_WAITING, 
			
			MEET, 
			LEAVE
		};

		private Type 	type;
		private int 	firstNode;
		private int 	secondNode;
		private double 	time;

		public Event(Type _type, int _n1, int _n2, double _time)
		{
			type 		= _type;
			firstNode 	= _n1;
			secondNode	= _n2;
			time 		= _time;
		}
	}

	public void PrintEvent(Event e, String eventType) throws IOException
	{
		String go = String.format("%.3f %s %4d %4d %.3f %.3f %.3f %.3f\n", e.time-parameterData.ignore, eventType, e.firstNode, e.secondNode, computePositionAtTime(currentTime, e.firstNode).x, computePositionAtTime(currentTime, e.firstNode).y, computePositionAtTime(currentTime, e.secondNode).x, computePositionAtTime(currentTime, e.secondNode).y);
		bw.write(go);
	}

	public void PrintEvent(Event e) throws IOException
	{
		String eventType = "NOT_INITIALIZED";
		switch (e.type)
		{
			case START_MOVING: {
				eventType = "SM";
				break;
			}
	
			case START_WAITING: {
				eventType = "SW";
				break;
			}
	
			case END_MOVING: {
				eventType = "EM";
				break;
			}
	
			case END_WAITING: {
				eventType = "EW";
				break;
			}
			default:
				break;
		}
		
		if (eventType.equals("SM") || eventType.equals("EM")) {
			Position curPos = computePositionAtTime(e.time, e.firstNode);
			parameterData.nodes[e.firstNode].add(e.time, new Position(curPos.x * this.parameterData.x, curPos.y * this.parameterData.y));
		}
		String go = String.format("%.3f %s %4d %.3f %.3f\n", e.time-parameterData.ignore, eventType, e.firstNode, computePositionAtTime(currentTime, e.firstNode).x, computePositionAtTime(currentTime, e.firstNode).y);
		bw.write(go);
	}
}