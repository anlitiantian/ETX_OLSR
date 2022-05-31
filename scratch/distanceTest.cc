/**
 * distanceTest.cc
 * 用两个点，不断增大两者间间距，看是否能通信
 *
 *  Created on: 2021年9月27日
 *      Author: mrliu
 */
#include <fstream>
#include <iostream>
#include "ns3/core-module.h"
#include "ns3/network-module.h"
#include "ns3/internet-module.h"
#include "ns3/mobility-module.h"
#include "ns3/aodv-module.h"
#include "ns3/olsr-module.h"
#include "ns3/dsdv-module.h"
// #include "ns3/lp-olsr-module.h"
#include "ns3/dsr-module.h"
#include "ns3/applications-module.h"
#include "ns3/yans-wifi-helper.h"

#include "ns3/flow-monitor-module.h"
#include "ns3/ipv4-flow-classifier.h"
#include "ns3/gnuplot.h"
#include "ns3/netanim-module.h"

using namespace ns3;
using namespace std;

NS_LOG_COMPONENT_DEFINE("distanceExample");

class RoutingExperiment
{
public:
	RoutingExperiment();
	void Run();
	void CommandSetup(int argc, char **argv);

private:
	Ptr<Socket> SetupPacketReceive(Ipv4Address addr, Ptr<Node> node);
	void ReceivePacket(Ptr<Socket> socket);
	void CheckThroughput();

	uint32_t port;
	uint32_t bytesTotal;
	uint32_t packetsReceived;

	int m_nSinks;
	double m_txp;
	bool m_traceMobility;
	int m_distance;
};

RoutingExperiment::RoutingExperiment()
	: port(9),
	  bytesTotal(0),
	  packetsReceived(0),
	  m_txp(7.5),
	  m_traceMobility(false),
	  m_distance(200)
{
}

static inline std::string
PrintReceivedPacket(Ptr<Socket> socket, Ptr<Packet> packet, Address senderAddress)
{
	std::ostringstream oss;

	oss << Simulator::Now().GetSeconds() << " " << socket->GetNode()->GetId();

	if (InetSocketAddress::IsMatchingType(senderAddress))
	{
		InetSocketAddress addr = InetSocketAddress::ConvertFrom(senderAddress);
		oss << " received one packet from " << addr.GetIpv4();
	}
	else
	{
		oss << " received one packet!";
	}
	return oss.str();
}

void RoutingExperiment::ReceivePacket(Ptr<Socket> socket)
{
	Ptr<Packet> packet;
	Address senderAddress;
	while ((packet = socket->RecvFrom(senderAddress)))
	{
		bytesTotal += packet->GetSize();
		packetsReceived += 1;
		NS_LOG_UNCOND(PrintReceivedPacket(socket, packet, senderAddress));
	}
}

void RoutingExperiment::CheckThroughput() //该类中统计吞吐量和接收到的数据包量的方法
{
	// double kbs = (bytesTotal * 8.0) / 1000;
	bytesTotal = 0;

	// cout << "开始统计（1s 统计一次）" << endl;
	// cout << "kbs:" << kbs << endl;
	// cout << "packetsReceived:" << packetsReceived << endl;
	// cout << "m_txp:" << m_txp << endl;
	// cout << "统计结束" << endl;

	packetsReceived = 0;
	Simulator::Schedule(Seconds(1.0), &RoutingExperiment::CheckThroughput, this);
}

Ptr<Socket> RoutingExperiment::SetupPacketReceive(Ipv4Address addr, Ptr<Node> node)
{
	TypeId tid = TypeId::LookupByName("ns3::UdpSocketFactory");
	Ptr<Socket> sink = Socket::CreateSocket(node, tid);
	InetSocketAddress local = InetSocketAddress(addr, port);
	sink->Bind(local);
	sink->SetRecvCallback(MakeCallback(&RoutingExperiment::ReceivePacket, this));

	return sink;
}

void RoutingExperiment::CommandSetup(int argc, char **argv)
{
	CommandLine cmd(__FILE__);
	cmd.AddValue("traceMobility", "Enable mobility tracing", m_traceMobility);
	cmd.AddValue("distance", "两个节点间的距离", m_distance);
	cmd.AddValue("txp", "两个节点间的距离", m_txp);
	cmd.Parse(argc, argv);
}

int main(int argc, char *argv[])
{
	RoutingExperiment experiment;
	experiment.CommandSetup (argc,argv);

	experiment.Run();
}

void RoutingExperiment::Run()
{
	Packet::EnablePrinting();

	double TotalTime = 50.0;
	std::string rate("2048bps");
	std::string phyMode("DsssRate11Mbps");
	std::string tr_name("manet-routing-compare");

	Config::SetDefault("ns3::OnOffApplication::PacketSize", StringValue("64"));
	Config::SetDefault("ns3::OnOffApplication::DataRate", StringValue(rate));

	// Set Non-unicastMode rate to unicast mode
	Config::SetDefault("ns3::WifiRemoteStationManager::NonUnicastMode", StringValue(phyMode));

	NodeContainer adhocNodes;
	adhocNodes.Create(4);

	// setting up wifi phy and channel using helpers
	WifiHelper wifi;
	wifi.SetStandard(WIFI_STANDARD_80211b);

	YansWifiPhyHelper wifiPhy;
	YansWifiChannelHelper wifiChannel;
	wifiChannel.SetPropagationDelay("ns3::ConstantSpeedPropagationDelayModel");
	wifiChannel.AddPropagationLoss("ns3::FriisPropagationLossModel");
	wifiPhy.SetChannel(wifiChannel.Create());
	wifiPhy.Set("TxPowerStart", DoubleValue(m_txp));
	wifiPhy.Set("TxPowerEnd", DoubleValue(m_txp));

	// Add a mac and disable rate control
	WifiMacHelper wifiMac;
	wifi.SetRemoteStationManager("ns3::ConstantRateWifiManager",
								 "DataMode", StringValue(phyMode),
								 "ControlMode", StringValue(phyMode));

	wifiMac.SetType("ns3::AdhocWifiMac");
	NetDeviceContainer adhocDevices = wifi.Install(wifiPhy, wifiMac, adhocNodes);

	MobilityHelper mobilityAdhoc;
	mobilityAdhoc.SetPositionAllocator("ns3::GridPositionAllocator",
									   "MinX", DoubleValue(0.0),
									   "MinY", DoubleValue(0.0),
									   "DeltaX", DoubleValue(m_distance),
									   "DeltaY", DoubleValue(0.0),
									   "GridWidth", UintegerValue(5),
									   "LayoutType", StringValue("RowFirst"));
	mobilityAdhoc.SetMobilityModel("ns3::ConstantPositionMobilityModel");
	mobilityAdhoc.Install(adhocNodes);

	OlsrHelper olsr;
	Ipv4ListRoutingHelper list;
	InternetStackHelper internet;

	list.Add(olsr, 100);
	internet.SetRoutingHelper(list);
	internet.Install(adhocNodes);

	NS_LOG_INFO("assigning ip address");

	Ipv4AddressHelper addressAdhoc;
	addressAdhoc.SetBase("10.1.1.0", "255.255.255.0");
	Ipv4InterfaceContainer adhocInterfaces;
	adhocInterfaces = addressAdhoc.Assign(adhocDevices);

	OnOffHelper onoff1("ns3::UdpSocketFactory", Address());
	onoff1.SetAttribute("OnTime", StringValue("ns3::ConstantRandomVariable[Constant=1.0]"));
	onoff1.SetAttribute("OffTime", StringValue("ns3::ConstantRandomVariable[Constant=0.0]"));

	Ptr<Socket> sink = SetupPacketReceive(adhocInterfaces.GetAddress(3), adhocNodes.Get(3));

	AddressValue remoteAddress(InetSocketAddress(adhocInterfaces.GetAddress(3), port));
	onoff1.SetAttribute("Remote", remoteAddress);

	Ptr<UniformRandomVariable> var = CreateObject<UniformRandomVariable>();
	ApplicationContainer temp = onoff1.Install(adhocNodes.Get(0));
	temp.Start(Seconds(var->GetValue(10.0, 11.0)));
	temp.Stop(Seconds(TotalTime));

	uint32_t SentPackets = 0;
	uint32_t ReceivedPackets = 0;
	uint32_t LostPackets = 0;

	FlowMonitorHelper flowmon;
	Ptr<FlowMonitor> monitor = flowmon.InstallAll();

	NS_LOG_INFO("Run Simulation.");

	CheckThroughput();

	AnimationInterface anim("manet-routing-compare.xml"); //生成动画演示

	Simulator::Stop(Seconds(TotalTime));

	wifiPhy.EnablePcapAll("distanceTest");
	Simulator::Run();

	int j = 0;
	float Throughput = 0;
	float AvgThroughput = 0;
	Time Jitter;
	Time Delay;

	Ptr<Ipv4FlowClassifier> classifier = DynamicCast<Ipv4FlowClassifier>(flowmon.GetClassifier());
	std::map<FlowId, FlowMonitor::FlowStats> stats = monitor->GetFlowStats();

	for (std::map<FlowId, FlowMonitor::FlowStats>::const_iterator iter = stats.begin(); iter != stats.end(); ++iter)
	{
		Ipv4FlowClassifier::FiveTuple t = classifier->FindFlow(iter->first);

		NS_LOG_UNCOND("----Flow ID:" << iter->first);
		NS_LOG_UNCOND("Src Addr" << t.sourceAddress << "Dst Addr " << t.destinationAddress);
		NS_LOG_UNCOND("Sent Packets=" << iter->second.txPackets);
		NS_LOG_UNCOND("Received Packets =" << iter->second.rxPackets);
		NS_LOG_UNCOND("Lost Packets =" << iter->second.txPackets - iter->second.rxPackets);
		NS_LOG_UNCOND("Packet delivery ratio =" << iter->second.rxPackets * 100 / iter->second.txPackets << "%");
		NS_LOG_UNCOND("Packet loss ratio =" << (iter->second.txPackets - iter->second.rxPackets) * 100 / iter->second.txPackets << "%");
		NS_LOG_UNCOND("Delay =" << iter->second.delaySum);
		NS_LOG_UNCOND("Jitter =" << iter->second.jitterSum);
		Throughput = iter->second.rxBytes * 8.0 / (iter->second.timeLastRxPacket.GetSeconds() - iter->second.timeFirstTxPacket.GetSeconds()) / 1024;
		NS_LOG_UNCOND("Throughput =" << Throughput << "Kbps");

		SentPackets = SentPackets + (iter->second.txPackets);
		ReceivedPackets = ReceivedPackets + (iter->second.rxPackets);
		LostPackets = LostPackets + (iter->second.txPackets - iter->second.rxPackets);
		AvgThroughput = AvgThroughput + Throughput;
		Delay = Delay + (iter->second.delaySum);
		Jitter = Jitter + (iter->second.jitterSum);

		j = j + 1;
	}

	AvgThroughput = AvgThroughput / j;
	NS_LOG_UNCOND("--------Total Results of the simulation----------" << std::endl);
	NS_LOG_UNCOND("Total sent packets = " << SentPackets);
	NS_LOG_UNCOND("Total Received Packets = " << ReceivedPackets);
	NS_LOG_UNCOND("Total Lost Packets = " << LostPackets);
	NS_LOG_UNCOND("Packet Loss ratio = " << ((LostPackets * 100) / SentPackets) << "%");
	NS_LOG_UNCOND("Packet delivery ratio = " << ((ReceivedPackets * 100) / SentPackets) << "%");
	NS_LOG_UNCOND("Average Throughput = " << AvgThroughput << "Kbps");
	NS_LOG_UNCOND("End to End Delay = " << Delay.GetMilliSeconds() << "ms");
	NS_LOG_UNCOND("End to End Jitter delay = " << Jitter.GetMilliSeconds() << "ms");
	NS_LOG_UNCOND("Total Flod id " << j);
	monitor->SerializeToXmlFile("manet-routing.xml", true, true);

	NS_LOG_UNCOND("Done");

	Simulator::Destroy();
}