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

package edu.bonn.cs.iv.util.maps.osm;

import crosby.binary.*;
import crosby.binary.Osmformat.*;

import java.util.*;

import edu.bonn.cs.iv.util.maps.BoundingBox;
import edu.bonn.cs.iv.util.maps.PositionGeo;


public class PBFParser extends BinaryParser
{
	private static String roadKey = "highway";
	private static String allowedBarrierValues = "kerb|block|bollard|border_control|cattle_grid|entrance|sally_port|toll_booth|cycle_barrier|stile|block|kissing_gate|turnstile|hampshire_gate";
	private static String allowedHighwayValues = "footway|cycleway|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|residential|unclassified|living_street|road|service|path|pedestrian|steps";
	private static String allowedAccessValues = "yes|permissive|designated|destination|delivery"; 

	private OSMGraph g = null;
	private BoundingBox bb = null;
	private ArrayList<Long> missingRefs = new ArrayList<Long>();
	private ArrayList<Long> barrierNodes = new ArrayList<Long>();
	
	public PBFParser(OSMGraph g, BoundingBox bb)
	{
		this.g = g;
		this.bb = bb;
	}
	
    @Override
    protected void parseRelations(List<Relation> rels)
    {
//        if (!rels.isEmpty()) System.out.println("Got some relations to parse.");
//        Relation r = null;
    }

    @Override
    protected void parseDense(DenseNodes nodes)
    {
        long lastId = 0;
        long lastLat = 0;
        long lastLon = 0;
        int keyIndex = 0;

        for (int i = 0; i < nodes.getIdCount(); i++) {
            lastId += nodes.getId(i);
            lastLat += nodes.getLat(i);
            lastLon += nodes.getLon(i);
            
            // see: http://wiki.openstreetmap.org/wiki/PBF_Format#Nodes
            Map<String, String> tags = new HashMap<String, String>();
            while (keyIndex < nodes.getKeysValsCount()) {
                int key_id = nodes.getKeysVals(keyIndex++);
                if (key_id == 0) { // end of current node's tags
                    break;
                } else if (keyIndex < nodes.getKeysValsCount()) {
                    int value_id = nodes.getKeysVals(keyIndex++);
                    tags.put(getStringById(key_id), getStringById(value_id));
                } else {
                    System.err.println("Invalid DenseNodes key/values table!");
                }
            }
            
            processNode(lastId, lastLat, lastLon, tags);
        }
    }

    @Override
    protected void parseNodes(List<Node> nodes)
    {
        for (Node n : nodes) {
        	Map<String, String> tags = new HashMap<String, String>();
            for (int i = 0; i < n.getKeysCount(); i++) {
                tags.put(getStringById(n.getKeys(i)), getStringById(n.getVals(i)));
            }
        	
        	processNode(n.getId(), n.getLat(), n.getLon(), tags);
        }
    }
    
    private void processNode(long id, long lat, long lon, Map<String, String> tags)
    {
    	// don't add to graph if node is forbidden barrier
    	if (tags.containsKey("barrier")) {
    		boolean forbiddenBarrier = false;
    		
    		if (!tags.get("barrier").matches(allowedBarrierValues)) {
    			forbiddenBarrier = true;
    		} else if (tags.containsKey("foot") && tags.get("foot").matches("no")) {
    			// unusual use of "foot:no" in combination with "barrier:entrance"
    			// cf. e.g. http://www.openstreetmap.org/node/2688035793
    			forbiddenBarrier = true;
    		}
    		
    		if (forbiddenBarrier) {
	    		barrierNodes.add(id);
	    		System.out.println("DEBUG: skipped barrier node (id " + id + ", value " + tags.get("barrier") + ")!");
	    		return;
    		}
    	}
    		
        PositionGeo p = new PositionGeo(parseLon(lon), parseLat(lat));
        if (bb == null || (bb != null && bb.contains(p))) {
            g.putNode(id, new OSMNode(id, p));
        }
    }

    @Override
    /**
     * @pre nodes have already been parsed before
     */
    protected void parseWays(List<Way> ways)
    {
    	for (Way w : ways) {
        	if (w.getRefsList().size() < 2) { // only consider ways with at least two nodes
        		continue;
        	}
        	
        	boolean accessAllowed = true;
        	
            for (int i = 0; i < w.getKeysCount(); i++) {
                if (getStringById(w.getKeys(i)).matches("access|foot")) { // check access tag
                	if (!getStringById(w.getVals(i)).matches(allowedAccessValues)) { // ignore ways where access is forbidden
                		System.out.println("DEBUG: skipped inaccessible way (id " + w.getId() + ", value " + getStringById(w.getVals(i)) + ")!");
                		accessAllowed = false;
                		break;
                	}
                }
            }
            
            if (!accessAllowed) {
            	continue;
            }
        	
            for (int i = 0; i < w.getKeysCount(); i++) { // for all OSM keys
                if (getStringById(w.getKeys(i)).matches(roadKey)) { // we only want ways with the correct key
                	if (!getStringById(w.getVals(i)).matches(allowedHighwayValues)) { // ignore forbidden highway types
                		System.out.println("DEBUG: skipped highway (id " + w.getId() + ", value " + getStringById(w.getVals(i)) + ")!");
                		break;
                	}
                	
                	ArrayList<Long> refs = new ArrayList<Long>();
                    long lastRef = 0;
                    
                    for (Long ref : w.getRefsList()) { // for all node references
                        lastRef += ref;
                        
                        if (barrierNodes.contains(lastRef)) {
                        	refs.clear();
                        	System.out.println("DEBUG: skipped highway " + w.getId() + " with barrier node " + lastRef + "!");
                        	break;
                        }
                        
                        if(!g.containsNode(lastRef)) { // reference to a node which is not in the graph -> discard remaining way segments
                        	if (!missingRefs.contains(lastRef)) {
                            	missingRefs.add(lastRef);
                        	}
                        	break;
                        }
                        
                		// a way might reference a node multiple times (-> cycle)
                        refs.add(lastRef);
                    }
                    
                    if (refs.size() > 1) { // only add to graph if at least two nodes remain for this way
                        g.putWay(w.getId(), new OSMWay(w.getId(), refs));
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void parse(HeaderBlock header)
    {
//    	HeaderBBox hbbox = header.getBbox();
//        g.setBoundingBox(new BoundingBox(parseLon(hbbox.getLeft()), parseLat(hbbox.getBottom()), parseLon(hbbox.getRight()), parseLat(hbbox.getTop())));
    }

    public void complete()
    {
        System.out.println("Ignored " + missingRefs.size() + " node references");
        for (long nr : missingRefs) {
        	assert(!g.containsNode(nr));
//        	System.out.print(nr + ", ");
        }
//        System.out.println("Check manually on: http://www.openstreetmap.org/node/<ID>");
        
        doPostProcessing();
    }
    
    private void doPostProcessing()
    {
//    	System.out.println("Total road network length before post-processing: " + g.roadNetworkLength());
    	System.out.println("Computing connected components...");
    	ArrayList<ArrayList<Long>> componentList = g.findComponents();
    	System.out.println("done");
    	
    	int maxVal = 0;
    	int maxId = -1;
    	int total = 0;
    	for (int i = 0; i < componentList.size(); i++) {
    		ArrayList<Long> comp = componentList.get(i);
    		assert(comp.size() > 0);
    		
    		System.out.println("Component " + i + " consists of " + comp.size() + " ways");
    		if (comp.size() == 1) {
    			System.out.println("\t" + comp.get(0));
    		}
    		
    		total += comp.size();
    		
    		if (comp.size() > maxVal) {
    			maxVal = comp.size();
    			maxId = i;
    		}
    	}
    	
    	assert(maxId >= 0);
    	System.out.println("Choosing component " + maxId + " with " + maxVal + " ways. Removing " + (total - maxVal) + " ways in " + (componentList.size() - 1) + " small components...");
    	for (int i = 0; i < componentList.size(); i++) {
    		if (i == maxId) {
    			continue;
    		}
    		ArrayList<Long> comp = componentList.get(i);
    		
    		for (long wr : comp) {
    			g.removeWay(wr);
    		}
    	}
    	System.out.println("done");
    	
    	System.out.println("Removing isolated nodes...");
    	g.removeIsolatedNodes();
    	System.out.println("done");
    }
}
