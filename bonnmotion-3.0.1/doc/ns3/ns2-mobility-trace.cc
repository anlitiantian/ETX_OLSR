/*******************************************************************************
 ** BonnMotion - a mobility scenario generation and analysis tool             **
 ** ns3-Example								      **
 ** Copyright (C) 2002-2010 University of Bonn                                **
 ** Code: R. Ernst		                                              **
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

/*
 * This example demonstrates the use of BonnMotion generated traces within ns3.
 * 
 * 1. Generate movement, e.g. 'bm -f example RandomWaypoint -i 0 -n 1 -d 100'
 * 2. Convert into ns2 file format bm 'NSFile -f example'
 * 3. Run ns-3 './waf --run "scratch/ns3movement --traceFile=/path/to/example/example.ns_movements" 
 * 
 * The script is based on M. Giachino's example from ns-3.10 which can be found
 * in the ns-3.10 source examples/mobility/ns2-mobility-trace.cc
 *
*/

#include "ns3/core-module.h"
#include "ns3/helper-module.h"
#include "ns3/mobility-module.h"

using namespace ns3;

static void CourseChange (std::string context, Ptr<const MobilityModel> mobility) {
	Vector pos = mobility->GetPosition ();
 	Vector vel = mobility->GetVelocity ();

  	std::cout << Simulator::Now () << " " << context << " Position: x=" << pos.x << ", y=" << pos.y  << ", z=" << pos.z << " Velocity: x=" << vel.x << ", y=" << vel.y << ", z=" << vel.z << std::endl;
}

int main(int argc, char* argv[]) {
	std::string traceFile;

	int nodeNum = 1;
	double duration = 100;

	CommandLine cmd;
	cmd.AddValue("traceFile","NS2 movement trace file",traceFile);
	cmd.Parse(argc,argv);

	if(traceFile.empty()) {
		std::cout << "Usage of " << argv[0] << ":\n" << "./waf --run \"script --traceFile=/path/to/tracefile\"\n";
	}

	Ns2MobilityHelper ns2mobility = Ns2MobilityHelper(traceFile);

	NodeContainer nodes;
	nodes.Create(nodeNum);
	
	ns2mobility.Install();
	Config::Connect("/NodeList/*/$ns3::MobilityModel/CourseChange",MakeCallback(&CourseChange));

	Simulator::Stop(Seconds(duration));
	Simulator::Run();
	Simulator::Destroy();

	return 0;
}
