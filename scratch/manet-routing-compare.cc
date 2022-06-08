/* -*-  Mode: C++; c-file-style: "gnu"; indent-tabs-mode:nil; -*- */
/*
 * Copyright (c) 2011 University of Kansas
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation;
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Author: Justin Rohrer <rohrej@ittc.ku.edu>
 *
 * James P.G. Sterbenz <jpgs@ittc.ku.edu>, director
 * ResiliNets Research Group  http://wiki.ittc.ku.edu/resilinets
 * Information and Telecommunication Technology Center (ITTC)
 * and Department of Electrical Engineering and Computer Science
 * The University of Kansas Lawrence, KS USA.
 *
 * Work supported in part by NSF FIND (Future Internet Design) Program
 * under grant CNS-0626918 (Postmodern Internet Architecture),
 * NSF grant CNS-1050226 (Multilayer Network Resilience Analysis and Experimentation on GENI),
 * US Department of Defense (DoD), and ITTC at The University of Kansas.
 */

/*
 * This example program allows one to run ns-3 DSDV, AODV, or OLSR under
 * a typical random waypoint mobility model.
 *
 * By default, the simulation runs for 200 simulated seconds, of which
 * the first 50 are used for start-up time.  The number of nodes is 50.
 * Nodes move according to RandomWaypointMobilityModel with a speed of
 * 20 m/s and no pause time within a 300x1500 m region.  The WiFi is
 * in ad hoc mode with a 2 Mb/s rate (802.11b) and a Friis loss model.
 * The transmit power is set to 7.5 dBm.
 *
 * It is possible to change the mobility and density of the network by
 * directly modifying the speed and the number of nodes.  It is also
 * possible to change the characteristics of the network by changing
 * the transmit power (as power increases, the impact of mobility
 * decreases and the effective density increases).
 *
 * By default, OLSR is used, but specifying a value of 2 for the protocol
 * will cause AODV to be used, and specifying a value of 3 will cause
 * DSDV to be used.
 *
 * By default, there are 10 source/sink data pairs sending UDP data
 * at an application rate of 2.048 Kb/s each.    This is typically done
 * at a rate of 4 64-byte packets per second.  Application data is
 * started at a random time between 50 and 51 seconds and continues
 * to the end of the simulation.
 *
 * The program outputs a few items:
 * - packet receptions are notified to stdout such as:
 *   <timestamp> <node-id> received one packet from <src-address>
 * - each second, the data reception statistics are tabulated and output
 *   to a comma-separated value (csv) file
 * - some tracing and flow monitor configuration that used to work is
 *   left commented inline in the program
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
using namespace dsr;
using namespace std;

NS_LOG_COMPONENT_DEFINE("manet-routing-compare");

class RoutingExperiment
{
public:
    RoutingExperiment();
    void Run(int nSinks, double txp, std::string CSVfileName);
    // static void SetMACParam (ns3::NetDeviceContainer & devices,
    //                                  int slotDistance);
    std::string CommandSetup(int argc, char **argv);

private:
    Ptr<Socket> SetupPacketReceive(Ipv4Address addr, Ptr<Node> node);
    void ReceivePacket(Ptr<Socket> socket);
    void CheckThroughput();
    void showPosition(Ptr<Node> node);

    void countTime();
    void showRoutingTable(Ptr<Node> node);

    uint32_t port;
    uint32_t bytesTotal;
    uint32_t packetsReceived;

    std::string m_CSVfileName;
    int m_nSinks;
    std::string m_protocolName;
    double m_txp;
    bool m_traceMobility;
    uint32_t m_protocol;

    uint32_t m_distance;                    // 指定最大通信半径，用于计算链路维持时间
};

RoutingExperiment::RoutingExperiment()
    : port(9),
      bytesTotal(0),
      packetsReceived(0),
      m_CSVfileName("manet-routing.output.csv"),
      m_traceMobility(false),
      m_protocol(1), // olsr
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
        // NS_LOG_UNCOND(PrintReceivedPacket(socket, packet, senderAddress));
    }
}

void RoutingExperiment::CheckThroughput() //该类中统计吞吐量和接收到的数据包量的方法
{
    double kbs = (bytesTotal * 8.0) / 1000;
    bytesTotal = 0;

    std::ofstream out(m_CSVfileName.c_str(), std::ios::app);

    out << (Simulator::Now()).GetSeconds() << ","
        << kbs << ","
        << packetsReceived << ","
        << m_nSinks << ","
        << m_protocolName << ","
        << m_txp << ""
        << std::endl;

    out.close();
    packetsReceived = 0;
    Simulator::Schedule(Seconds(1.0), &RoutingExperiment::CheckThroughput, this);
}

void RoutingExperiment::countTime(){
    cout<<"进行到："<< Simulator::Now().GetSeconds()<<"s"<<endl;
    Simulator::Schedule(Seconds(5.0), &RoutingExperiment::countTime, this);
}

void RoutingExperiment::showRoutingTable(Ptr<Node> node){
    Ptr<ns3::olsr::RoutingProtocol> protocol = node->GetObject<ns3::olsr::RoutingProtocol>();
    protocol->printRoutingTables();
    Simulator::Schedule(Seconds(2.0), &RoutingExperiment::showRoutingTable, this, node);
}

void RoutingExperiment::showPosition(Ptr<Node> node)
{
    uint32_t nodeId = node->GetId();
    Ptr<GaussMarkovMobilityModel> mobModel = node->GetObject<GaussMarkovMobilityModel>();
    Vector3D pos = mobModel->GetPosition();
    Vector3D speed = mobModel->GetVelocity();
    //   double v = mobModel->GetVelocityNum();
    std::cout << "At " << Simulator::Now().GetSeconds() << " node " << nodeId
              << ": Position(" << pos.x << ", " << pos.y << ", " << pos.z
              << ");   Speed(" << speed.x << ", " << speed.y << ", " << speed.z
              << ")" << std::endl;
    //   std::cout<<v<<std::endl;
    Simulator::Schedule(Seconds(2.0), &RoutingExperiment::showPosition, this, node);
}

Ptr<Socket>
RoutingExperiment::SetupPacketReceive(Ipv4Address addr, Ptr<Node> node)
{
    TypeId tid = TypeId::LookupByName("ns3::UdpSocketFactory");
    Ptr<Socket> sink = Socket::CreateSocket(node, tid);
    InetSocketAddress local = InetSocketAddress(addr, port);
    sink->Bind(local);
    sink->SetRecvCallback(MakeCallback(&RoutingExperiment::ReceivePacket, this));

    return sink;
}

std::string
RoutingExperiment::CommandSetup(int argc, char **argv)
{
    CommandLine cmd(__FILE__);
    cmd.AddValue("CSVfileName", "The name of the CSV output file name", m_CSVfileName);
    cmd.AddValue("traceMobility", "Enable mobility tracing", m_traceMobility);
    cmd.AddValue("protocol", "1=OLSR;2=AODV;3=DSDV;4=DSR;5=lpolsr", m_protocol);
    cmd.AddValue("maxDistance","节点通信的最大距离（由txp测得）", m_distance);
    cmd.Parse(argc, argv);
    return m_CSVfileName;
}

int main(int argc, char *argv[])
{
    RoutingExperiment experiment;
    std::string CSVfileName = experiment.CommandSetup(argc, argv);

    // blank out the last output file and write the column headers
    std::ofstream out(CSVfileName.c_str());
    out << "SimulationSecond,"
        << "ReceiveRate,"
        << "PacketsReceived,"
        << "NumberOfSinks,"
        << "RoutingProtocol,"
        << "TransmissionPower" << std::endl;
    out.close();

    int nSinks = 10;
    double txp = 15;

    experiment.Run(nSinks, txp, CSVfileName);
}

void RoutingExperiment::Run(int nSinks, double txp, std::string CSVfileName)
{
    Packet::EnablePrinting();
    m_nSinks = nSinks;
    m_txp = txp;
    m_CSVfileName = CSVfileName;

    int nWifis = 50;

    double TotalTime = 100.0;
    std::string rate("100kb/s");
    std::string phyMode("DsssRate11Mbps");
    std::string tr_name("manet-routing-compare");
    int nodeSpeed = 20; // in m/s
    int nodePause = 0;  // in s
    m_protocolName = "protocol";

    Config::SetDefault("ns3::OnOffApplication::PacketSize", StringValue("1024"));
    Config::SetDefault("ns3::OnOffApplication::DataRate", StringValue(rate));

    // Set Non-unicastMode rate to unicast mode
    Config::SetDefault("ns3::WifiRemoteStationManager::NonUnicastMode", StringValue(phyMode));

    NodeContainer adhocNodes;
    adhocNodes.Create(nWifis);

    // setting up wifi phy and channel using helpers
    WifiHelper wifi;
    wifi.SetStandard(WIFI_STANDARD_80211b);

    YansWifiPhyHelper wifiPhy;
    YansWifiChannelHelper wifiChannel;
    wifiChannel.SetPropagationDelay("ns3::ConstantSpeedPropagationDelayModel");
    wifiChannel.AddPropagationLoss("ns3::FriisPropagationLossModel");
    wifiPhy.SetChannel(wifiChannel.Create());

    // Add a mac and disable rate control
    WifiMacHelper wifiMac;
    wifi.SetRemoteStationManager("ns3::ConstantRateWifiManager",
                                 "DataMode", StringValue(phyMode),
                                 "ControlMode", StringValue(phyMode));

    wifiPhy.Set("TxPowerStart", DoubleValue(txp));
    wifiPhy.Set("TxPowerEnd", DoubleValue(txp));

    wifiMac.SetType("ns3::AdhocWifiMac");
    NetDeviceContainer adhocDevices = wifi.Install(wifiPhy, wifiMac, adhocNodes);

    //  MobilityHelper mobilityAdhoc;
    //  int64_t streamIndex = 0; // used to get consistent mobility across scenarios

    //  ObjectFactory pos;
    //  pos.SetTypeId ("ns3::RandomRectanglePositionAllocator");
    //  pos.Set ("X", StringValue ("ns3::UniformRandomVariable[Min=0.0|Max=300.0]"));
    //  pos.Set ("Y", StringValue ("ns3::UniformRandomVariable[Min=0.0|Max=1500.0]"));

    //  Ptr<PositionAllocator> taPositionAlloc = pos.Create ()->GetObject<PositionAllocator> ();
    //  streamIndex += taPositionAlloc->AssignStreams (streamIndex);

    //  std::stringstream ssSpeed;
    //  ssSpeed << "ns3::UniformRandomVariable[Min=0.0|Max=" << nodeSpeed << "]";
    //  std::stringstream ssPause;
    //  ssPause << "ns3::ConstantRandomVariable[Constant=" << nodePause << "]";
    //  mobilityAdhoc.SetMobilityModel ("ns3::RandomWaypointMobilityModel",
    //                                  "Speed", StringValue (ssSpeed.str ()),
    //                                  "Pause", StringValue (ssPause.str ()),
    //                                  "PositionAllocator", PointerValue (taPositionAlloc));
    //  mobilityAdhoc.SetPositionAllocator (taPositionAlloc);
    //  mobilityAdhoc.Install (adhocNodes);
    //  streamIndex += mobilityAdhoc.AssignStreams (adhocNodes, streamIndex);
    //  NS_UNUSED (streamIndex); // From this point, streamIndex is unused

    MobilityHelper mobility;
    mobility.SetMobilityModel("ns3::GaussMarkovMobilityModel",
                              "Bounds", BoxValue(Box(0, 2000, 0, 2000, 0, 500)),
                              "TimeStep", TimeValue(Seconds(1)),
                              "Alpha", DoubleValue(0.8),
                              "MeanVelocity", StringValue("ns3::UniformRandomVariable[Min=20|Max=30]"),
                              "MeanDirection", StringValue("ns3::UniformRandomVariable[Min=0|Max=6.283185307]"),
                              //  "MeanPitch", StringValue ("ns3::UniformRandomVariable[Min=-0.01|Max=0.01]"),				//占用一个位置，先用默认值
                              "NormalVelocity", StringValue("ns3::NormalRandomVariable[Mean=0.0|Variance=9|Bound=10]"),
                              "NormalDirection", StringValue("ns3::NormalRandomVariable[Mean=0.0|Variance=0.25|Bound=1.5]"),
                              "NormalPitch", StringValue("ns3::NormalRandomVariable[Mean=0.0|Variance=0.02|Bound=0.04]"),
                              "MaxAngularVelocity", DoubleValue(0.3));
    mobility.SetPositionAllocator("ns3::RandomBoxPositionAllocator",
                                  "X", StringValue("ns3::UniformRandomVariable[Min=200|Max=1800]"),
                                  "Y", StringValue("ns3::UniformRandomVariable[Min=200|Max=1800]"),
                                  "Z", StringValue("ns3::UniformRandomVariable[Min=100|Max=400]"));
    mobility.Install(adhocNodes);

    AodvHelper aodv;
    OlsrHelper olsr;
    olsr.Set("MaxCommunicationRadius", IntegerValue(m_distance));

    DsdvHelper dsdv;
    DsrHelper dsr;
    DsrMainHelper dsrMain;
    Ipv4ListRoutingHelper list;
    InternetStackHelper internet;

    switch (m_protocol)
    {
    case 1:
        list.Add(olsr, 100);
        m_protocolName = "OLSR";
        break;
    case 2:
        list.Add(aodv, 100);
        m_protocolName = "AODV";
        break;
    case 3:
        list.Add(dsdv, 100);
        m_protocolName = "DSDV";
        break;
    // case 4:
    //   list.Add (lpolsr, 100);
    //   m_protocolName = "lpolsr";
    //   break;
    case 5:
        m_protocolName = "DSR";
        break;
    default:
        NS_FATAL_ERROR("No such protocol:" << m_protocol);
    }

    if (m_protocol < 5)
    {
        internet.SetRoutingHelper(list);
        internet.Install(adhocNodes);
    }
    else if (m_protocol == 5)
    {
        internet.Install(adhocNodes);
        dsrMain.Install(dsr, adhocNodes);
    }

    NS_LOG_INFO("assigning ip address");

    Ipv4AddressHelper addressAdhoc;
    addressAdhoc.SetBase("10.1.1.0", "255.255.255.0");
    Ipv4InterfaceContainer adhocInterfaces;
    adhocInterfaces = addressAdhoc.Assign(adhocDevices);

    OnOffHelper onoff1("ns3::UdpSocketFactory", Address());
    onoff1.SetAttribute("OnTime", StringValue("ns3::ConstantRandomVariable[Constant=1.0]"));
    onoff1.SetAttribute("OffTime", StringValue("ns3::ConstantRandomVariable[Constant=0.0]"));

    for (int i = 0; i < nSinks; i++)
    {
        Ptr<Socket> sink = SetupPacketReceive(adhocInterfaces.GetAddress(i), adhocNodes.Get(i));

        AddressValue remoteAddress(InetSocketAddress(adhocInterfaces.GetAddress(i), port));
        onoff1.SetAttribute("Remote", remoteAddress);

        Ptr<UniformRandomVariable> var = CreateObject<UniformRandomVariable>();
        ApplicationContainer temp = onoff1.Install(adhocNodes.Get(i + nSinks));
        temp.Start(Seconds(var->GetValue(20.0, 21.0)));
        temp.Stop(Seconds(TotalTime));
    }

    std::stringstream ss;
    ss << nWifis;
    std::string nodes = ss.str();

    std::stringstream ss2;
    ss2 << nodeSpeed;
    std::string sNodeSpeed = ss2.str();

    std::stringstream ss3;
    ss3 << nodePause;
    std::string sNodePause = ss3.str();

    std::stringstream ss4;
    ss4 << rate;
    std::string sRate = ss4.str();

    // NS_LOG_INFO ("Configure Tracing.");
    // tr_name = tr_name + "_" + m_protocolName +"_" + nodes + "nodes_" + sNodeSpeed + "speed_" + sNodePause + "pause_" + sRate + "rate";

    // AsciiTraceHelper ascii;
    // Ptr<OutputStreamWrapper> osw = ascii.CreateFileStream ( (tr_name + ".tr").c_str());
    // wifiPhy.EnableAsciiAll (osw);
    AsciiTraceHelper ascii;
    MobilityHelper::EnableAsciiAll(ascii.CreateFileStream(tr_name + ".mob"));

    uint32_t SentPackets = 0;
    uint32_t ReceivedPackets = 0;
    uint32_t LostPackets = 0;
    // Gnuplot parameters
    string fileNameWithNoExtension = "FlowVSThroughput";
    string graphicsFileName = fileNameWithNoExtension + ".png";
    string plotFileName = fileNameWithNoExtension + ".plt";
    string plotTitle = "Flow vs Throughput";
    string dataTitle = "Throughput";

    // Instantiate the plot and set its title.
    Gnuplot gnuplot(graphicsFileName);
    gnuplot.SetTitle(plotTitle);

    // Make the graphics file, which the plot file will be when it
    // is used with Gnuplot, be a PNG file.
    gnuplot.SetTerminal("png");

    // Set the labels for each axis.
    gnuplot.SetLegend("Flow", "Throughput");

    Gnuplot2dDataset dataset;
    dataset.SetTitle(dataTitle);
    dataset.SetStyle(Gnuplot2dDataset::LINES_POINTS);

    FlowMonitorHelper flowmon;
    Ptr<FlowMonitor> monitor = flowmon.InstallAll();

    NS_LOG_INFO("Run Simulationdsada");

    CheckThroughput();
    countTime();
    // showRoutingTable(adhocNodes.Get(0));
    // showPosition(adhocNodes.Get(0));

    AnimationInterface anim("manet-routing-compare.xml"); //生成动画演示kdosfs

    Simulator::Stop(Seconds(TotalTime));
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
        dataset.Add((double)iter->first, (double)Throughput);

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

    // flowmon->SerializeToXmlFile ((tr_name + ".flowmon").c_str(), false, false);
    NS_LOG_UNCOND("Done");

    // Gnuplot ...continued
    gnuplot.AddDataset(dataset);

    // Open the plot file.
    ofstream plotFile(plotFileName.c_str());

    // Write the plot file.
    gnuplot.GenerateOutput(plotFile);

    // Close the plot file.
    plotFile.close();

    Simulator::Destroy();
}
