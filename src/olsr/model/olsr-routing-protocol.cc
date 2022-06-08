/* -*- Mode:C++; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/*
 * Copyright (c) 2004 Francisco J. Ros
 * Copyright (c) 2007 INESC Porto
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
 * Authors: Francisco J. Ros  <fjrm@dif.um.es>
 *          Gustavo J. A. M. Carneiro <gjc@inescporto.pt>
 */

///
/// \brief Implementation of OLSR agent and related classes.
///
/// This is the main file of this software because %OLSR's behaviour is
/// implemented here.
///

#define NS_LOG_APPEND_CONTEXT                                        \
    if (GetObject<Node>())                                           \
    {                                                                \
        std::clog << "[node " << GetObject<Node>()->GetId() << "] "; \
    }

#include "olsr-routing-protocol.h"
#include "ns3/socket-factory.h"
#include "ns3/udp-socket-factory.h"
#include "ns3/simulator.h"
#include "ns3/log.h"
#include "ns3/names.h"
#include "ns3/inet-socket-address.h"
#include "ns3/ipv4-routing-protocol.h"
#include "ns3/ipv4-routing-table-entry.h"
#include "ns3/ipv4-route.h"
#include "ns3/boolean.h"
#include "ns3/uinteger.h"
#include "ns3/enum.h"
#include "ns3/trace-source-accessor.h"
#include "ns3/ipv4-header.h"
#include "ns3/ipv4-packet-info-tag.h"

#include "ns3/integer.h"

/********** Useful macros **********/

///
/// \brief Gets the delay between a given time and the current time.
///
/// If given time is previous to the current one, then this macro returns
/// a number close to 0. This is used for scheduling events at a certain moment.
///
#define DELAY(time)                                    \
    (((time) < (Simulator::Now())) ? Seconds(0.000001) \
                                   : (time - Simulator::Now() + Seconds(0.000001)))

///
/// \brief Period at which a node must cite every link and every neighbor.
///
/// We only use this value in order to define OLSR_NEIGHB_HOLD_TIME.
///
#define OLSR_REFRESH_INTERVAL m_helloInterval

/********** Holding times **********/

// 平均邻居变化率的统计时间
#define OLSR_NEIGHBOR_CHANGE_RATE_TIME Time(3 * OLSR_REFRESH_INTERVAL)

/// Neighbor holding time.
#define OLSR_NEIGHB_HOLD_TIME Time(2 * OLSR_REFRESH_INTERVAL)
/// Top holding time.
#define OLSR_TOP_HOLD_TIME Time(1 * m_tcInterval)
/// Dup holding time.
#define OLSR_DUP_HOLD_TIME Seconds(30)
/// MID holding time.
#define OLSR_MID_HOLD_TIME Time(3 * m_midInterval)
/// HNA holding time.
#define OLSR_HNA_HOLD_TIME Time(3 * m_hnaInterval)

/********** Link types **********/

/// Unspecified link type.
#define OLSR_UNSPEC_LINK 0
/// Asymmetric link type.
#define OLSR_ASYM_LINK 1
/// Symmetric link type.
#define OLSR_SYM_LINK 2
/// Lost link type.
#define OLSR_LOST_LINK 3

/********** Neighbor types **********/

/// Not neighbor type.
#define OLSR_NOT_NEIGH 0
/// Symmetric neighbor type.
#define OLSR_SYM_NEIGH 1
/// Asymmetric neighbor type.
#define OLSR_MPR_NEIGH 2

/********** Willingness **********/

/// Willingness for forwarding packets from other nodes: never.
#define OLSR_WILL_NEVER 0
/// Willingness for forwarding packets from other nodes: low.
#define OLSR_WILL_LOW 1
/// Willingness for forwarding packets from other nodes: medium.
#define OLSR_WILL_DEFAULT 3
/// Willingness for forwarding packets from other nodes: high.
#define OLSR_WILL_HIGH 6
/// Willingness for forwarding packets from other nodes: always.
#define OLSR_WILL_ALWAYS 7

/********** Miscellaneous constants **********/

/// Maximum allowed jitter.
#define OLSR_MAXJITTER (m_helloInterval.GetSeconds() / 4)
/// Maximum allowed sequence number.
#define OLSR_MAX_SEQ_NUM 65535
/// Random number between [0-OLSR_MAXJITTER] used to jitter OLSR packet transmission.
#define JITTER (Seconds(m_uniformRandomVariable->GetValue(0, OLSR_MAXJITTER)))

/// Maximum number of messages per packet.
#define OLSR_MAX_MSGS 64

/// Maximum number of hellos per message (4 possible link types * 3 possible nb types).
#define OLSR_MAX_HELLOS 12

/// Maximum number of addresses advertised on a message.
#define OLSR_MAX_ADDRS 64

namespace ns3
{

    NS_LOG_COMPONENT_DEFINE("OlsrRoutingProtocol");

    namespace olsr
    {

        /********** OLSR class **********/

        NS_OBJECT_ENSURE_REGISTERED(RoutingProtocol);

        /* see https://www.iana.org/assignments/service-names-port-numbers */
        const uint16_t RoutingProtocol::OLSR_PORT_NUMBER = 698;

        TypeId
        RoutingProtocol::GetTypeId(void)
        {
            static TypeId tid =
                TypeId("ns3::olsr::RoutingProtocol")
                    .SetParent<Ipv4RoutingProtocol>()
                    .SetGroupName("Olsr")
                    .AddConstructor<RoutingProtocol>()
                    .AddAttribute("HelloInterval", "HELLO messages emission interval.",
                                  TimeValue(Seconds(2)),
                                  MakeTimeAccessor(&RoutingProtocol::m_helloInterval), MakeTimeChecker())
                    .AddAttribute("TcInterval", "TC messages emission interval.",
                                  TimeValue(Seconds(5)),
                                  MakeTimeAccessor(&RoutingProtocol::m_tcInterval), MakeTimeChecker())
                    .AddAttribute("MidInterval",
                                  "MID messages emission interval.  Normally it is equal to TcInterval.",
                                  TimeValue(Seconds(5)),
                                  MakeTimeAccessor(&RoutingProtocol::m_midInterval), MakeTimeChecker())
                    .AddAttribute("HnaInterval",
                                  "HNA messages emission interval.  Normally it is equal to TcInterval.",
                                  TimeValue(Seconds(5)),
                                  MakeTimeAccessor(&RoutingProtocol::m_hnaInterval), MakeTimeChecker())
                    .AddAttribute(
                        "Willingness", "Willingness of a node to carry and forward traffic for other nodes.",
                        EnumValue(OLSR_WILL_DEFAULT), MakeEnumAccessor(&RoutingProtocol::m_willingness),
                        MakeEnumChecker(OLSR_WILL_NEVER, "never", OLSR_WILL_LOW, "low", OLSR_WILL_DEFAULT,
                                        "default", OLSR_WILL_HIGH, "high", OLSR_WILL_ALWAYS, "always"))
                    .AddTraceSource("Rx", "Receive OLSR packet.",
                                    MakeTraceSourceAccessor(&RoutingProtocol::m_rxPacketTrace),
                                    "ns3::olsr::RoutingProtocol::PacketTxRxTracedCallback")
                    .AddTraceSource("Tx", "Send OLSR packet.",
                                    MakeTraceSourceAccessor(&RoutingProtocol::m_txPacketTrace),
                                    "ns3::olsr::RoutingProtocol::PacketTxRxTracedCallback")
                    .AddTraceSource("RoutingTableChanged", "The OLSR routing table has changed.",
                                    MakeTraceSourceAccessor(&RoutingProtocol::m_routingTableChanged),
                                    "ns3::olsr::RoutingProtocol::TableChangeTracedCallback")
                    .AddAttribute("MaxCommunicationRadius",
                                  "手动设置的通信半径（与wifi的最大传输水平有关）.",
                                  IntegerValue(300),
                                  MakeIntegerAccessor(&RoutingProtocol::m_commu_radius),
                                  MakeIntegerChecker<int64_t>());
            return tid;
        }

        RoutingProtocol::RoutingProtocol(void)
            : m_routingTableAssociation(0),
              m_ipv4(0),
              m_helloTimer(Timer::CANCEL_ON_DESTROY),
              m_tcTimer(Timer::CANCEL_ON_DESTROY),
              m_midTimer(Timer::CANCEL_ON_DESTROY),
              m_hnaTimer(Timer::CANCEL_ON_DESTROY),
              m_queuedMessagesTimer(Timer::CANCEL_ON_DESTROY)
        {
            m_uniformRandomVariable = CreateObject<UniformRandomVariable>();

            m_hnaRoutingTable = Create<Ipv4StaticRouting>();
        }

        RoutingProtocol::~RoutingProtocol(void)
        {
        }

        void
        RoutingProtocol::SetIpv4(Ptr<Ipv4> ipv4)
        {
            NS_ASSERT(ipv4 != 0);
            NS_ASSERT(m_ipv4 == 0);
            NS_LOG_DEBUG("Created olsr::RoutingProtocol");
            m_helloTimer.SetFunction(&RoutingProtocol::HelloTimerExpire, this);
            m_tcTimer.SetFunction(&RoutingProtocol::TcTimerExpire, this);
            m_midTimer.SetFunction(&RoutingProtocol::MidTimerExpire, this);
            m_hnaTimer.SetFunction(&RoutingProtocol::HnaTimerExpire, this);
            m_queuedMessagesTimer.SetFunction(&RoutingProtocol::SendQueuedMessages, this);

            m_packetSequenceNumber = OLSR_MAX_SEQ_NUM;
            m_messageSequenceNumber = OLSR_MAX_SEQ_NUM;
            m_ansn = OLSR_MAX_SEQ_NUM;

            m_linkTupleTimerFirstTime = true;

            m_ipv4 = ipv4;

            m_hnaRoutingTable->SetIpv4(ipv4);
        }

        Ptr<Ipv4>
        RoutingProtocol::GetIpv4(void) const
        {
            return m_ipv4;
        }

        void
        RoutingProtocol::DoDispose(void)
        {
            m_ipv4 = 0;
            m_hnaRoutingTable = 0;
            m_routingTableAssociation = 0;

            if (m_recvSocket)
            {
                m_recvSocket->Close();
                m_recvSocket = 0;
            }

            for (std::map<Ptr<Socket>, Ipv4InterfaceAddress>::iterator iter = m_sendSockets.begin();
                 iter != m_sendSockets.end(); iter++)
            {
                iter->first->Close();
            }
            m_sendSockets.clear();
            m_table.clear();

            Ipv4RoutingProtocol::DoDispose();
        }

        void RoutingProtocol::printRoutingTables()
        {
            std::cout << "我的地址为：" << m_mainAddress << ",现在 " << Simulator::Now().GetSeconds() << "s,现在打印路由表" << std::endl;
            for (std::map<Ipv4Address, RoutingTableEntry>::const_iterator iter = m_table.begin();
                 iter != m_table.end(); iter++)
            {
                std::cout << "目的地址为:" << iter->first << ",下一跳为:" << iter->second.nextAddr << ",etx:"
                          << iter->second.etxDistance << std::endl;
            }
            std::cout << std::endl;
        }

        void
        RoutingProtocol::PrintRoutingTable(Ptr<OutputStreamWrapper> stream, Time::Unit unit) const
        {
            std::ostream *os = stream->GetStream();

            *os << "Node: " << m_ipv4->GetObject<Node>()->GetId() << ", Time: " << Now().As(unit)
                << ", Local time: " << GetObject<Node>()->GetLocalTime().As(unit) << ", OLSR Routing table"
                << std::endl;

            *os << "Destination\t\tNextHop\t\tInterface\tDistance\tEtxDistance\n";

            for (std::map<Ipv4Address, RoutingTableEntry>::const_iterator iter = m_table.begin();
                 iter != m_table.end(); iter++)
            {
                *os << iter->first << "\t\t";
                *os << iter->second.nextAddr << "\t\t";
                if (Names::FindName(m_ipv4->GetNetDevice(iter->second.interface)) != "")
                {
                    *os << Names::FindName(m_ipv4->GetNetDevice(iter->second.interface)) << "\t\t";
                }
                else
                {
                    *os << iter->second.interface << "\t\t";
                }
                *os << iter->second.distance << "\t";
                *os << iter->second.etxDistance << "\t";
                *os << "\n";
            }

            // Also print the HNA routing table
            if (m_hnaRoutingTable->GetNRoutes() > 0)
            {
                *os << " HNA Routing Table: ";
                m_hnaRoutingTable->PrintRoutingTable(stream, unit);
            }
            else
            {
                *os << " HNA Routing Table: empty" << std::endl;
            }
        }

        void
        RoutingProtocol::DoInitialize()
        {
            // Simulator::Schedule(Seconds(1), &RoutingProtocol::printEtxDataTable, this);
            if (m_mainAddress == Ipv4Address())
            {
                Ipv4Address loopback("127.0.0.1");
                for (uint32_t i = 0; i < m_ipv4->GetNInterfaces(); i++)
                {
                    // Use primary address, if multiple
                    Ipv4Address addr = m_ipv4->GetAddress(i, 0).GetLocal();
                    if (addr != loopback)
                    {
                        m_mainAddress = addr;
                        break;
                    }
                }

                NS_ASSERT(m_mainAddress != Ipv4Address());
            }

            NS_LOG_DEBUG("Starting OLSR on node " << m_mainAddress);

            Ipv4Address loopback("127.0.0.1");

            bool canRunOlsr = false;
            for (uint32_t i = 0; i < m_ipv4->GetNInterfaces(); i++)
            {
                Ipv4Address addr = m_ipv4->GetAddress(i, 0).GetLocal();
                if (addr == loopback)
                {
                    continue;
                }

                if (addr != m_mainAddress)
                {
                    // Create never expiring interface association tuple entries for our
                    // own network interfaces, so that GetMainAddress () works to
                    // translate the node's own interface addresses into the main address.
                    IfaceAssocTuple tuple;
                    tuple.ifaceAddr = addr;
                    tuple.mainAddr = m_mainAddress;
                    AddIfaceAssocTuple(tuple);
                    NS_ASSERT(GetMainAddress(addr) == m_mainAddress);
                }

                if (m_interfaceExclusions.find(i) != m_interfaceExclusions.end())
                {
                    continue;
                }

                // Create a socket to listen on all the interfaces
                // 创建一个监听的socket
                if (m_recvSocket == 0)
                {
                    m_recvSocket = Socket::CreateSocket(GetObject<Node>(), UdpSocketFactory::GetTypeId());
                    m_recvSocket->SetAllowBroadcast(true);
                    InetSocketAddress inetAddr(Ipv4Address::GetAny(), OLSR_PORT_NUMBER);
                    m_recvSocket->SetRecvCallback(MakeCallback(&RoutingProtocol::RecvOlsr, this));
                    if (m_recvSocket->Bind(inetAddr))
                    {
                        NS_FATAL_ERROR("Failed to bind() OLSR socket");
                    }
                    m_recvSocket->SetRecvPktInfo(true);
                    m_recvSocket->ShutdownSend();
                }

                // Create a socket to send packets from this specific interfaces
                Ptr<Socket> socket =
                    Socket::CreateSocket(GetObject<Node>(), UdpSocketFactory::GetTypeId());
                socket->SetAllowBroadcast(true);
                socket->SetIpTtl(1);
                InetSocketAddress inetAddr(m_ipv4->GetAddress(i, 0).GetLocal(), OLSR_PORT_NUMBER);
                socket->SetRecvCallback(MakeCallback(&RoutingProtocol::RecvOlsr, this));
                socket->BindToNetDevice(m_ipv4->GetNetDevice(i));
                if (socket->Bind(inetAddr))
                {
                    NS_FATAL_ERROR("Failed to bind() OLSR socket");
                }
                socket->SetRecvPktInfo(true);
                m_sendSockets[socket] = m_ipv4->GetAddress(i, 0);

                canRunOlsr = true;
            }

            if (canRunOlsr)
            {
                HelloTimerExpire();
                TcTimerExpire();
                MidTimerExpire();
                HnaTimerExpire();
                NS_LOG_DEBUG("OLSR on node " << m_mainAddress << " started");
            }
        }

        void
        RoutingProtocol::SetMainInterface(uint32_t interface)
        {
            m_mainAddress = m_ipv4->GetAddress(interface, 0).GetLocal();
        }

        void
        RoutingProtocol::SetInterfaceExclusions(std::set<uint32_t> exceptions)
        {
            m_interfaceExclusions = exceptions;
        }

        //
        // \brief Processes an incoming %OLSR packet following \RFC{3626} specification.
        void
        RoutingProtocol::RecvOlsr(Ptr<Socket> socket)
        {
            Ptr<Packet> receivedPacket;
            Address sourceAddress;
            receivedPacket = socket->RecvFrom(sourceAddress);

            Ipv4PacketInfoTag interfaceInfo;
            if (!receivedPacket->RemovePacketTag(interfaceInfo))
            {
                NS_ABORT_MSG("No incoming interface on OLSR message, aborting.");
            }
            uint32_t incomingIf = interfaceInfo.GetRecvIf();
            Ptr<Node> node = this->GetObject<Node>();
            Ptr<NetDevice> dev = node->GetDevice(incomingIf);
            uint32_t recvInterfaceIndex = m_ipv4->GetInterfaceForDevice(dev);

            if (m_interfaceExclusions.find(recvInterfaceIndex) != m_interfaceExclusions.end())
            {
                return;
            }

            InetSocketAddress inetSourceAddr = InetSocketAddress::ConvertFrom(sourceAddress);
            Ipv4Address senderIfaceAddr = inetSourceAddr.GetIpv4();

            int32_t interfaceForAddress = m_ipv4->GetInterfaceForAddress(senderIfaceAddr);
            if (interfaceForAddress != -1)
            {
                NS_LOG_LOGIC("Ignoring a packet sent by myself.");
                return;
            }

            Ipv4Address receiverIfaceAddr = m_ipv4->GetAddress(recvInterfaceIndex, 0).GetLocal();
            NS_ASSERT(receiverIfaceAddr != Ipv4Address());
            NS_LOG_DEBUG("OLSR node " << m_mainAddress << " received a OLSR packet from " << senderIfaceAddr
                                      << " to " << receiverIfaceAddr);

            // All routing messages are sent from and to port RT_PORT,
            // so we check it.
            NS_ASSERT(inetSourceAddr.GetPort() == OLSR_PORT_NUMBER);

            Ptr<Packet> packet = receivedPacket;

            olsr::PacketHeader olsrPacketHeader;
            packet->RemoveHeader(olsrPacketHeader);
            NS_ASSERT(olsrPacketHeader.GetPacketLength() >= olsrPacketHeader.GetSerializedSize());
            uint32_t sizeLeft = olsrPacketHeader.GetPacketLength() - olsrPacketHeader.GetSerializedSize();

            MessageList messages;

            while (sizeLeft)
            {
                MessageHeader messageHeader;
                if (packet->RemoveHeader(messageHeader) == 0)
                {
                    NS_ASSERT(false);
                }

                sizeLeft -= messageHeader.GetSerializedSize();

                NS_LOG_DEBUG("Olsr Msg received with type "
                             << std::dec << int(messageHeader.GetMessageType())
                             << " TTL=" << int(messageHeader.GetTimeToLive())
                             << " origAddr=" << messageHeader.GetOriginatorAddress());
                messages.push_back(messageHeader);
            }

            m_rxPacketTrace(olsrPacketHeader, messages);

            for (MessageList::const_iterator messageIter = messages.begin(); messageIter != messages.end();
                 messageIter++)
            {
                const MessageHeader &messageHeader = *messageIter;
                // If ttl is less than or equal to zero, or
                // the receiver is the same as the originator,
                // the message must be silently dropped
                if (messageHeader.GetTimeToLive() == 0 ||
                    messageHeader.GetOriginatorAddress() == m_mainAddress)
                {
                    packet->RemoveAtStart(messageHeader.GetSerializedSize() -
                                          messageHeader.GetSerializedSize());
                    continue;
                }

                // If the message has been processed it must not be processed again
                bool do_forwarding = true;
                DuplicateTuple *duplicated = m_state.FindDuplicateTuple(
                    messageHeader.GetOriginatorAddress(), messageHeader.GetMessageSequenceNumber());

                // Get main address of the peer, which may be different from the packet source address
                //       const IfaceAssocTuple *ifaceAssoc = m_state.FindIfaceAssocTuple (inetSourceAddr.GetIpv4 ());
                //       Ipv4Address peerMainAddress;
                //       if (ifaceAssoc != NULL)
                //         {
                //           peerMainAddress = ifaceAssoc->mainAddr;
                //         }
                //       else
                //         {
                //           peerMainAddress = inetSourceAddr.GetIpv4 () ;
                //         }

                if (duplicated == NULL)
                {
                    switch (messageHeader.GetMessageType())
                    {
                    case olsr::MessageHeader::HELLO_MESSAGE:
                        NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                                     << " OLSR node " << m_mainAddress << " received HELLO message of size "
                                     << messageHeader.GetSerializedSize());
                        ProcessHello(messageHeader, receiverIfaceAddr, senderIfaceAddr);
                        SendHelloAck(messageHeader, senderIfaceAddr, receiverIfaceAddr);
                        break;

                    case olsr::MessageHeader::TC_MESSAGE:
                        NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                                     << " OLSR node " << m_mainAddress << " received TC message of size "
                                     << messageHeader.GetSerializedSize());
                        ProcessTc(messageHeader, senderIfaceAddr);
                        break;

                    case olsr::MessageHeader::MID_MESSAGE:
                        NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                                     << " OLSR node " << m_mainAddress << " received MID message of size "
                                     << messageHeader.GetSerializedSize());
                        ProcessMid(messageHeader, senderIfaceAddr);
                        break;
                    case olsr::MessageHeader::HNA_MESSAGE:
                        NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                                     << " OLSR node " << m_mainAddress << " received HNA message of size "
                                     << messageHeader.GetSerializedSize());
                        ProcessHna(messageHeader, senderIfaceAddr);
                        break;
                    case olsr::MessageHeader::HELLOACK_MESSAGE:
                        NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                                     << " OLSR node " << m_mainAddress << " received HELLOACK message of size "
                                     << messageHeader.GetSerializedSize());
                        ProcessHelloAck(messageHeader, receiverIfaceAddr, senderIfaceAddr);
                    default:
                        NS_LOG_DEBUG("OLSR message type " << int(messageHeader.GetMessageType())
                                                          << " not implemented");
                    }
                }
                else
                {
                    NS_LOG_DEBUG("OLSR message is duplicated, not reading it.");

                    // If the message has been considered for forwarding, it should
                    // not be retransmitted again
                    for (std::vector<Ipv4Address>::const_iterator it = duplicated->ifaceList.begin();
                         it != duplicated->ifaceList.end(); it++)
                    {
                        if (*it == receiverIfaceAddr)
                        {
                            do_forwarding = false;
                            break;
                        }
                    }
                }

                if (do_forwarding)
                {
                    // HELLO messages are never forwarded.
                    // TC and MID messages are forwarded using the default algorithm.
                    // Remaining messages are also forwarded using the default algorithm.
                    if (messageHeader.GetMessageType() != olsr::MessageHeader::HELLO_MESSAGE && messageHeader.GetMessageType() != olsr::MessageHeader::HELLOACK_MESSAGE)
                    {
                        ForwardDefault(messageHeader, duplicated, receiverIfaceAddr,
                                       inetSourceAddr.GetIpv4());
                    }
                }
            }

            // After processing all OLSR messages, we must recompute the routing table
            RoutingTableComputation();
        }

        ///
        /// \brief This auxiliary function (defined in \RFC{3626}) is used for calculating the MPR Set.
        ///
        /// \param tuple the neighbor tuple which has the main address of the node we are going to calculate its degree to.
        /// \return the degree of the node.
        ///
        int
        RoutingProtocol::Degree(NeighborTuple const &tuple)
        {
            int degree = 0;
            for (TwoHopNeighborSet::const_iterator it = m_state.GetTwoHopNeighbors().begin();
                 it != m_state.GetTwoHopNeighbors().end(); it++)
            {
                TwoHopNeighborTuple const &nb2hop_tuple = *it;
                if (nb2hop_tuple.neighborMainAddr == tuple.neighborMainAddr)
                {
                    const NeighborTuple *nb_tuple = m_state.FindNeighborTuple(nb2hop_tuple.twoHopNeighborAddr);
                    if (nb_tuple == NULL)
                    {
                        degree++;
                    }
                }
            }
            return degree;
        }

        namespace
        {
            ///
            /// \brief Remove all covered 2-hop neighbors from N2 set.
            /// This is a helper function used by MprComputation algorithm.
            //  把该邻居节点能到达的2-hop邻居从 N2 中删掉
            ///
            /// \param neighborMainAddr Neighbor main address.
            /// \param N2 Reference to the 2-hop neighbor set.
            ///
            void
            CoverTwoHopNeighbors(Ipv4Address neighborMainAddr, TwoHopNeighborSet &N2)
            {
                // first gather all 2-hop neighbors to be removed
                std::set<Ipv4Address> toRemove;
                for (TwoHopNeighborSet::iterator twoHopNeigh = N2.begin(); twoHopNeigh != N2.end();
                     twoHopNeigh++)
                {
                    if (twoHopNeigh->neighborMainAddr == neighborMainAddr)
                    {
                        toRemove.insert(twoHopNeigh->twoHopNeighborAddr);
                    }
                }
                // Now remove all matching records from N2
                for (TwoHopNeighborSet::iterator twoHopNeigh = N2.begin(); twoHopNeigh != N2.end();)
                {
                    if (toRemove.find(twoHopNeigh->twoHopNeighborAddr) != toRemove.end())
                    {
                        twoHopNeigh = N2.erase(twoHopNeigh);
                    }
                    else
                    {
                        twoHopNeigh++;
                    }
                }
            }
        } // unnamed namespace

        // 改
        void RoutingProtocol::MprComputation(void)
        {
            NS_LOG_FUNCTION(this);

            // MPR computation should be done for each interface. See section 8.3.1
            // (RFC 3626) for details.
            MprSet mprSet;

            // 将每个邻居的链路情况存在这里
            std::map<Ipv4Address, const LinkQosTuple*> map;
            for(LinkQosSet::const_iterator it = m_state.GetLinkQosSet().begin(); it!= m_state.GetLinkQosSet().end(); it++){
                if(GetMainAddress(it->localIfaceAddr) == m_mainAddress){
                    map[it->neighborIfaceAddr] = &(*it);
                }
            }

            // N is the subset of neighbors of the node, which are
            // neighbor "of the interface I"
            // 将对称邻居添加进N
            NeighborSet N;
            for (NeighborSet::const_iterator neighbor = m_state.GetNeighbors().begin();
                 neighbor != m_state.GetNeighbors().end(); neighbor++)
            {
                if (neighbor->status == NeighborTuple::STATUS_SYM) // I think that we need this check
                {
                    
                    N.push_back(*neighbor);
                }
            }

            // N2 is the set of 2-hop neighbors reachable from "the interface
            // I", excluding:
            // (i)   the nodes only reachable by members of N with willingness WILL_NEVER
            // (ii)  the node performing the computation
            // (iii) all the symmetric neighbors: the nodes for which there exists a symmetric
            //       link to this node on some interface.
            // 将2-hop邻居添加到N2
            TwoHopNeighborSet N2;
            for (TwoHopNeighborSet::const_iterator twoHopNeigh = m_state.GetTwoHopNeighbors().begin();
                 twoHopNeigh != m_state.GetTwoHopNeighbors().end(); twoHopNeigh++)
            {
                // excluding:
                // (ii)  the node performing the computation
                // 去除两跳邻居是自身的情况
                if (twoHopNeigh->twoHopNeighborAddr == m_mainAddress)
                {
                    continue;
                }

                //  excluding:
                // (i)   the nodes only reachable by members of N with willingness WILL_NEVER
                // 去除两跳邻居无转发意愿的情况
                bool ok = false;
                for (NeighborSet::const_iterator neigh = N.begin(); neigh != N.end(); neigh++)
                {
                    if (neigh->neighborMainAddr == twoHopNeigh->neighborMainAddr)
                    {
                        if (neigh->willingness == OLSR_WILL_NEVER)
                        {
                            ok = false;
                            break;
                        }
                        else
                        {
                            ok = true;
                            break;
                        }
                    }
                }
                if (!ok)
                {
                    continue;
                }

                // excluding:
                // (iii) all the symmetric neighbors: the nodes for which there exists a symmetric
                //       link to this node on some interface.
                // 去除两跳邻居是一跳邻居的情况
                for (NeighborSet::const_iterator neigh = N.begin(); neigh != N.end(); neigh++)
                {
                    if (neigh->neighborMainAddr == twoHopNeigh->twoHopNeighborAddr)
                    {
                        ok = false;
                        break;
                    }
                }

                if (ok)
                {
                    N2.push_back(*twoHopNeigh);
                }
            }

#ifdef NS3_LOG_ENABLE
            {
                std::ostringstream os;
                os << "[";
                for (TwoHopNeighborSet::const_iterator iter = N2.begin(); iter != N2.end(); iter++)
                {
                    TwoHopNeighborSet::const_iterator next = iter;
                    next++;
                    os << iter->neighborMainAddr << "->" << iter->twoHopNeighborAddr;
                    if (next != N2.end())
                    {
                        os << ", ";
                    }
                }
                os << "]";
                NS_LOG_DEBUG("N2: " << os.str());
            }
#endif // NS3_LOG_ENABLE

            //开始选MPR

            // 1. Start with an MPR set made of all members of N with
            // N_willingness equal to WILL_ALWAYS
            // 1. 先从邻居中选出转发意愿强烈的节点作为 MPR，同时将该节点能够到达的两跳邻居节点从两跳邻居节点表中删除
            for (NeighborSet::const_iterator neighbor = N.begin(); neighbor != N.end(); neighbor++)
            {
                if (neighbor->willingness == OLSR_WILL_ALWAYS)
                {
                    mprSet.insert(neighbor->neighborMainAddr);
                    // (not in RFC but I think is needed: remove the 2-hop
                    // neighbors reachable by the MPR from N2)
                    CoverTwoHopNeighbors(neighbor->neighborMainAddr, N2);
                }
            }

            // 2. Calculate D(y), where y is a member of N, for all nodes in N.
            // (we do this later)

            // 3. 将邻居节点中存在到达2-hop邻居节点只有唯一路径的节点加入mpr
            // 3. Add to the MPR set those nodes in N, which are the *only*
            // nodes to provide reachability to a node in N2.
            std::set<Ipv4Address> coveredTwoHopNeighbors;
            for (TwoHopNeighborSet::const_iterator twoHopNeigh = N2.begin(); twoHopNeigh != N2.end();
                 twoHopNeigh++)
            {
                bool onlyOne = true;
                // try to find another neighbor that can reach twoHopNeigh->twoHopNeighborAddr
                for (TwoHopNeighborSet::const_iterator otherTwoHopNeigh = N2.begin();
                     otherTwoHopNeigh != N2.end(); otherTwoHopNeigh++)
                {
                    if (otherTwoHopNeigh->twoHopNeighborAddr == twoHopNeigh->twoHopNeighborAddr &&
                        otherTwoHopNeigh->neighborMainAddr != twoHopNeigh->neighborMainAddr)
                    {
                        // 两跳邻居节点可以有多条路径走到
                        onlyOne = false;
                        break;
                    }
                }
                if (onlyOne)
                {
                    NS_LOG_LOGIC("Neighbor " << twoHopNeigh->neighborMainAddr
                                             << " is the only that can reach 2-hop neigh. "
                                             << twoHopNeigh->twoHopNeighborAddr << " => select as MPR.");

                    mprSet.insert(twoHopNeigh->neighborMainAddr);

                    // take note of all the 2-hop neighbors reachable by the newly elected MPR
                    for (TwoHopNeighborSet::const_iterator otherTwoHopNeigh = N2.begin();
                         otherTwoHopNeigh != N2.end(); otherTwoHopNeigh++)
                    {
                        if (otherTwoHopNeigh->neighborMainAddr == twoHopNeigh->neighborMainAddr)
                        {
                            coveredTwoHopNeighbors.insert(otherTwoHopNeigh->twoHopNeighborAddr);
                        }
                    }
                }
            }
            // Remove the nodes from N2 which are now covered by a node in the MPR set.
            // 将新覆盖的2-hop邻居从N2中移除
            for (TwoHopNeighborSet::iterator twoHopNeigh = N2.begin(); twoHopNeigh != N2.end();)
            {
                if (coveredTwoHopNeighbors.find(twoHopNeigh->twoHopNeighborAddr) !=
                    coveredTwoHopNeighbors.end())
                {
                    // This works correctly only because it is known that twoHopNeigh is reachable by exactly one neighbor,
                    // so only one record in N2 exists for each of them. This record is erased here.
                    NS_LOG_LOGIC("2-hop neigh. " << twoHopNeigh->twoHopNeighborAddr
                                                 << " is already covered by an MPR.");
                    twoHopNeigh = N2.erase(twoHopNeigh);
                }
                else
                {
                    twoHopNeigh++;
                }
            }

            // 4. While there exist nodes in N2 which are not covered by at
            // least one node in the MPR set:
            // 4. N2 中仍有节点未被覆盖
            while (N2.begin() != N2.end())
            {

#ifdef NS3_LOG_ENABLE
                {
                    std::ostringstream os;
                    os << "[";
                    for (TwoHopNeighborSet::const_iterator iter = N2.begin(); iter != N2.end(); iter++)
                    {
                        TwoHopNeighborSet::const_iterator next = iter;
                        next++;
                        os << iter->neighborMainAddr << "->" << iter->twoHopNeighborAddr;
                        if (next != N2.end())
                        {
                            os << ", ";
                        }
                    }
                    os << "]";
                    NS_LOG_DEBUG("Step 4 iteration: N2=" << os.str());
                }
#endif // NS3_LOG_ENABLE

                // 计算每个邻居节点的可达度。就是一个邻居能到达几个2-hop邻居
                // 4.1. For each node in N, calculate the reachability, i.e., the
                // number of nodes in N2 which are not yet covered by at
                // least one node in the MPR set, and which are reachable
                // through this 1-hop neighbor
                std::map<int, std::vector<const NeighborTuple *>> reachability;
                std::set<int> rs;
                for (NeighborSet::iterator it = N.begin(); it != N.end(); it++)
                {
                    NeighborTuple const &nb_tuple = *it;
                    int r = 0;
                    for (TwoHopNeighborSet::iterator it2 = N2.begin(); it2 != N2.end(); it2++)
                    {
                        TwoHopNeighborTuple const &nb2hop_tuple = *it2;
                        if (nb_tuple.neighborMainAddr == nb2hop_tuple.neighborMainAddr)
                        {
                            r++;
                        }
                    }
                    rs.insert(r);
                    reachability[r].push_back(&nb_tuple);
                }

                // 先选转发意愿最高的，然后选可达节点数最高的，最后选D(y)最高的。选一个去掉其可达的2-hop节点
                // 遍历，从所有的里面挑一个最优秀的。（优化点：可以将所有的邻居节点放在一个优先级队列中，每次只需要取最好的就可以）
                // 4.2. Select as a MPR the node with highest N_willingness among
                // the nodes in N with non-zero reachability. In case of
                // multiple choice select the node which provides
                // reachability to the maximum number of nodes in N2. In
                // case of multiple nodes providing the same amount of
                // reachability, select the node as MPR whose D(y) is
                // greater. Remove the nodes from N2 which are now covered
                // by a node in the MPR set.
                NeighborTuple const *max = NULL;
                int max_r = 0;
                for (std::set<int>::iterator it = rs.begin(); it != rs.end(); it++)
                {
                    int r = *it;
                    if (r == 0)
                    {
                        continue;
                    }
                    for (std::vector<const NeighborTuple *>::iterator it2 = reachability[r].begin();
                         it2 != reachability[r].end(); it2++)
                    {
                        const NeighborTuple *nb_tuple = *it2;
                        if (max == NULL || nb_tuple->willingness > max->willingness)
                        {
                            max = nb_tuple;
                            max_r = r;
                        }
                        else if (nb_tuple->willingness == max->willingness)
                        {
                            if (r > max_r)
                            {
                                max = nb_tuple;
                                max_r = r;
                            }
                            else if (r == max_r)
                            {
                                if (Degree(*nb_tuple) > Degree(*max))
                                {
                                    max = nb_tuple;
                                    max_r = r;
                                }
                            }
                        }
                    }
                }

                if (max != NULL)
                {
                    mprSet.insert(max->neighborMainAddr);
                    CoverTwoHopNeighbors(max->neighborMainAddr, N2);
                    NS_LOG_LOGIC(N2.size() << " 2-hop neighbors left to cover!");
                }
            }

#ifdef NS3_LOG_ENABLE
            {
                std::ostringstream os;
                os << "[";
                for (MprSet::const_iterator iter = mprSet.begin(); iter != mprSet.end(); iter++)
                {
                    MprSet::const_iterator next = iter;
                    next++;
                    os << *iter;
                    if (next != mprSet.end())
                    {
                        os << ", ";
                    }
                }
                os << "]";
                NS_LOG_DEBUG("Computed MPR set for node " << m_mainAddress << ": " << os.str());
            }
#endif // NS3_LOG_ENABLE

            m_state.SetMprSet(mprSet);

            // std::cout<<Simulator::Now().GetMilliSeconds()<< "ms,我是:"<<m_mainAddress<<",现在打印mpr表:"<<std::endl;
            // m_state.printMprSet();
        }

        Ipv4Address
        RoutingProtocol::GetMainAddress(Ipv4Address iface_addr) const
        {
            const IfaceAssocTuple *tuple = m_state.FindIfaceAssocTuple(iface_addr);

            if (tuple != NULL)
            {
                return tuple->mainAddr;
            }
            else
            {
                return iface_addr;
            }
        }

        void RoutingProtocol::RoutingTableComputation(void)
        {
            NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                         << " : Node " << m_mainAddress << ": RoutingTableComputation begin...");

            // 1. 删除路由表中所有记录
            Clear();

            // if(Simulator::Now().GetSeconds() > 2 && m_mainAddress == "10.1.1.10"){
            //     std::cout<<std::endl;
            // }

            // 先添加1-hop邻居(对称、链路没过期)——根据邻居表
            // 2. The new routing entries are added starting with the
            // symmetric neighbors (h=1) as the destination nodes.
            const NeighborSet &neighborSet = m_state.GetNeighbors();
            for (NeighborSet::const_iterator it = neighborSet.begin(); it != neighborSet.end(); it++)
            {
                NeighborTuple const &nb_tuple = *it;
                NS_LOG_DEBUG("Looking at neighbor tuple: " << nb_tuple);
                if (nb_tuple.status == NeighborTuple::STATUS_SYM)
                {
                    // 标记是否是主接口地址
                    bool nb_main_addr = false;
                    const LinkQosTuple *lt = NULL;
                    const LinkQosSet &linkQosSet = m_state.GetLinkQosSet();
                    // const LinkSet &linkSet = m_state.GetLinks();
                    // 找出正向和反向的链路质量,存一下,还有链接的本地接口
                    uint32_t etxf = 1;
                    uint32_t etxr = 1;
                    Ipv4Address localIfaceAddress;
                    Ipv4Address neighIfaceAddress;

                    for (LinkQosSet::const_iterator it2 = linkQosSet.begin(); it2 != linkQosSet.end(); it2++)
                    {
                        LinkQosTuple const &linkQos_tuple = *it2;
                        NS_LOG_DEBUG("Looking at linkQos tuple: "
                                     << linkQos_tuple
                                     << (linkQos_tuple.time >= Simulator::Now() ? "" : " (expired)"));
                        if ((GetMainAddress(linkQos_tuple.neighborIfaceAddr) == nb_tuple.neighborMainAddr) &&
                            linkQos_tuple.time >= Simulator::Now())
                        {
                            // 正向链路
                            NS_LOG_LOGIC("Link tuple matches neighbor "
                                         << nb_tuple.neighborMainAddr
                                         << " => adding routing table entry to neighbor");
                            etxf = etxf * linkQos_tuple.Etx;
                            // if (etxf == 0)
                            // {
                            //     std::cout << "正向链路etx为0" << std::endl;
                            // }

                            localIfaceAddress = linkQos_tuple.localIfaceAddr;
                            neighIfaceAddress = linkQos_tuple.neighborIfaceAddr;

                            lt = &linkQos_tuple;

                            if (linkQos_tuple.neighborIfaceAddr == nb_tuple.neighborMainAddr)
                            {
                                nb_main_addr = true;
                            }
                        }
                        else
                        {
                            NS_LOG_LOGIC("Link tuple: linkMainAddress= "
                                         << GetMainAddress(linkQos_tuple.neighborIfaceAddr)
                                         << "; neighborMainAddr =  " << nb_tuple.neighborMainAddr
                                         << "; expired=" << int(linkQos_tuple.time < Simulator::Now())
                                         << " => IGNORE");
                        }
                        if ((GetMainAddress(linkQos_tuple.localIfaceAddr) == nb_tuple.neighborMainAddr) &&
                            linkQos_tuple.time >= Simulator::Now())
                        {
                            // 反向链路
                            NS_LOG_LOGIC("Link tuple matches neighbor "
                                         << nb_tuple.neighborMainAddr
                                         << " => adding routing table entry to neighbor");
                            etxr = etxr * linkQos_tuple.Etx;
                            // if (etxr == 0)
                            // {
                            //     std::cout << "反向链路etx为0" << std::endl;
                            // }
                        }
                        else
                        {
                            NS_LOG_LOGIC("Link tuple: linkMainAddress= "
                                         << GetMainAddress(linkQos_tuple.neighborIfaceAddr)
                                         << "; neighborMainAddr =  " << nb_tuple.neighborMainAddr
                                         << "; expired=" << int(linkQos_tuple.time < Simulator::Now())
                                         << " => IGNORE");
                        }
                    }
                    uint32_t etx = etxf * etxr;
                    // NS_ASSERT(neighIfaceAddress != nullptr);
                    // NS_ASSERT(localIfaceAddress != nullptr);
                    AddEntry(neighIfaceAddress, neighIfaceAddress, localIfaceAddress, 1, etx);

                    // 若上面接口都不是邻居的主地址，那么也要添加一个目的地址为邻居主地址的 entry（该地址不在链路集中）
                    // If, in the above, no R_dest_addr is equal to the main
                    // address of the neighbor, then another new routing entry
                    // with MUST be added, with:
                    //      R_dest_addr  = main address of the neighbor;
                    //      R_next_addr  = L_neighbor_iface_addr of one of the
                    //                     associated link tuple with L_time >= current time;
                    //      R_dist       = 1;
                    //      R_iface_addr = L_local_iface_addr of the
                    //                     associated link tuple.
                    if (!nb_main_addr && lt != NULL)
                    {
                        NS_LOG_LOGIC("no R_dest_addr is equal to the main address of the neighbor "
                                     "=> adding additional routing entry");
                        // 这里是为一条邻居添加对应的ETX值
                        AddEntry(nb_tuple.neighborMainAddr, neighIfaceAddress, localIfaceAddress, 1, etx);
                    }
                }
            }

            // 从2-hop邻居中挑符合条件的（不是1-hop邻居、不是自身、有转发意愿）——根据2-hop邻居表
            //  3. for each node in N2, i.e., a 2-hop neighbor which is not a
            //  neighbor node or the node itself, and such that there exist at
            //  least one entry in the 2-hop neighbor set where
            //  N_neighbor_main_addr correspond to a neighbor node with
            //  willingness different of WILL_NEVER,
            const TwoHopNeighborSet &twoHopNeighbors = m_state.GetTwoHopNeighbors();
            for (TwoHopNeighborSet::const_iterator it = twoHopNeighbors.begin(); it != twoHopNeighbors.end(); it++)
            {
                TwoHopNeighborTuple const &nb2hop_tuple = *it;

                NS_LOG_LOGIC("Looking at two-hop neighbor tuple: " << nb2hop_tuple);

                // a 2-hop neighbor which is not a neighbor node or the node itself
                if (m_state.FindSymNeighborTuple(nb2hop_tuple.twoHopNeighborAddr))
                {
                    NS_LOG_LOGIC("Two-hop neighbor tuple is also neighbor; skipped.");
                    continue;
                }

                if (nb2hop_tuple.twoHopNeighborAddr == m_mainAddress)
                {
                    NS_LOG_LOGIC("Two-hop neighbor is self; skipped.");
                    continue;
                }

                // 判断是否存在至少一个entry满足2-hop邻居的邻居是本节点邻居，且转发意愿不为NEVER,没有换下一个2-hop邻居
                // ...and such that there exist at least one entry in the 2-hop
                // neighbor set where N_neighbor_main_addr correspond to a
                // neighbor node with willingness different of WILL_NEVER...
                bool nb2hopOk = false;
                for (NeighborSet::const_iterator neighbor = neighborSet.begin();
                     neighbor != neighborSet.end(); neighbor++)
                {
                    if (neighbor->neighborMainAddr == nb2hop_tuple.neighborMainAddr &&
                        neighbor->willingness != OLSR_WILL_NEVER)
                    {
                        nb2hopOk = true;
                        break;
                    }
                }
                if (!nb2hopOk)
                {
                    NS_LOG_LOGIC("Two-hop neighbor tuple skipped: 2-hop neighbor "
                                 << nb2hop_tuple.twoHopNeighborAddr << " is attached to neighbor "
                                 << nb2hop_tuple.neighborMainAddr
                                 << ", which was not found in the Neighbor Set.");
                    continue;
                }

                // 创建2-hop邻居的路由表
                // one selects one 2-hop tuple and creates one entry in the routing table with:
                //                R_dest_addr  =  the main address of the 2-hop neighbor;
                //                R_next_addr  = the R_next_addr of the entry in the
                //                               routing table with:
                //                                   R_dest_addr == N_neighbor_main_addr
                //                                                  of the 2-hop tuple;
                //                R_dist       = 2;
                //                R_iface_addr = the R_iface_addr of the entry in the
                //                               routing table with:
                //                                   R_dest_addr == N_neighbor_main_addr
                //                                                  of the 2-hop tuple;
                RoutingTableEntry entry;
                bool foundEntry = Lookup(nb2hop_tuple.neighborMainAddr, entry);
                if (foundEntry)
                {
                    NS_LOG_LOGIC("Adding routing entry for two-hop neighbor.");
                    uint32_t etx = entry.etxDistance;
                    uint32_t Etx = 1;

                    // 求1-hop到2-hop的etx
                    for (std::vector<LinkQosTuple>::const_iterator it = m_state.GetLinkQosSet().begin();
                         it != m_state.GetLinkQosSet().end(); it++)
                    {
                        if ((GetMainAddress(it->localIfaceAddr) == nb2hop_tuple.twoHopNeighborAddr && GetMainAddress(it->neighborIfaceAddr) == nb2hop_tuple.neighborMainAddr) || (GetMainAddress(it->localIfaceAddr) == nb2hop_tuple.neighborMainAddr && GetMainAddress(it->neighborIfaceAddr) == nb2hop_tuple.twoHopNeighborAddr))
                        {
                            Etx = Etx * it->Etx;
                        }
                    }
                    etx += std::min(Etx, (uint32_t)100 * 100);
                    AddEntry(nb2hop_tuple.twoHopNeighborAddr, entry.nextAddr, entry.interface, 2, etx);
                }
                else
                {
                    NS_LOG_LOGIC("NOT adding routing entry for two-hop neighbor ("
                                 << nb2hop_tuple.twoHopNeighborAddr << " not found in the routing table)");
                }
            }

            // key为mpr selector，value为 TopologyTuple 集合
            std::map<Ipv4Address, std::vector<TopologyTuple>> mapTmp;
            const TopologySet &topology = m_state.GetTopologySet();
            // 将记录先存在 map 中
            for (TopologySet::const_iterator it = topology.begin(); it != topology.end(); it++)
            {
                const TopologyTuple &topology_tuple = *it;
                mapTmp[topology_tuple.destAddr].push_back(*it);
            }
            bool addOrUpdate = true;
            // 当循环中未发生过更新或添加就退出
            while (addOrUpdate)
            {
                addOrUpdate = false;
                for (std::map<Ipv4Address, std::vector<TopologyTuple>>::iterator it = mapTmp.begin(); it != mapTmp.end(); it++)
                {
                    // 遍历vector
                    for (std::vector<TopologyTuple>::iterator tuple = it->second.begin(); tuple != it->second.end(); tuple++)
                    {
                        TopologyTuple topologyTuple = *tuple;
                        RoutingTableEntry destAddrEntry, lastAddrEntry;
                        bool have_destAddrEntry = Lookup(topologyTuple.destAddr, destAddrEntry);
                        bool have_lastAddrEntry = Lookup(topologyTuple.lastAddr, lastAddrEntry);
                        if (have_lastAddrEntry)
                        {
                            if (have_destAddrEntry)
                            {
                                // 存在目的地址和mpr selector相同的路由，比较etx花费
                                if (destAddrEntry.etxDistance > topologyTuple.Etx + lastAddrEntry.etxDistance)
                                {
                                    // 新的路由etx更小，替换
                                    AddEntry(topologyTuple.destAddr, lastAddrEntry.nextAddr, lastAddrEntry.interface,
                                             lastAddrEntry.distance + 1, lastAddrEntry.etxDistance + topologyTuple.Etx);
                                    addOrUpdate = true;
                                }
                            }
                            else
                            {
                                // 不存在，直接添加
                                AddEntry(topologyTuple.destAddr, lastAddrEntry.nextAddr, lastAddrEntry.interface,
                                         lastAddrEntry.distance + 1, lastAddrEntry.etxDistance + topologyTuple.Etx);
                                addOrUpdate = true;
                            }
                        }
                    }
                }
            }
            mapTmp.clear();

            // 路由表中主接口地址存了，但没存他的子接口地址作为目的的记录(没啥用，接口关联表没东西)。——根据接口关联表
            // 4. For each entry in the multiple interface association base
            // where there exists a routing entry such that:
            // R_dest_addr == I_main_addr (of the multiple interface association entry)
            // AND there is no routing entry such that:
            // R_dest_addr == I_iface_addr
            const IfaceAssocSet &ifaceAssocSet = m_state.GetIfaceAssocSet();
            for (IfaceAssocSet::const_iterator it = ifaceAssocSet.begin(); it != ifaceAssocSet.end(); it++)
            {
                IfaceAssocTuple const &tuple = *it;
                RoutingTableEntry entry1, entry2;
                bool have_entry1 = Lookup(tuple.mainAddr, entry1);
                bool have_entry2 = Lookup(tuple.ifaceAddr, entry2);
                if (have_entry1 && !have_entry2)
                {
                    // then a route entry is created in the routing table with:
                    //       R_dest_addr  =  I_iface_addr (of the multiple interface
                    //                                     association entry)
                    //       R_next_addr  =  R_next_addr  (of the recorded route entry)
                    //       R_dist       =  R_dist       (of the recorded route entry)
                    //       R_iface_addr =  R_iface_addr (of the recorded route entry).
                    AddEntry(tuple.ifaceAddr, entry1.nextAddr, entry1.interface, entry1.distance, entry1.etxDistance);
                }
            }

            // 根据关联表（网关、子网）中的每个元组找路由。——根据association表
            // 5. For each tuple in the association set,
            //    If there is no entry in the routing table with:
            //        R_dest_addr     == A_network_addr/A_netmask
            //   and if the announced network is not announced by the node itself,
            //   then a new routing entry is created.
            const AssociationSet &associationSet = m_state.GetAssociationSet();

            // Clear HNA routing table
            for (uint32_t i = 0; i < m_hnaRoutingTable->GetNRoutes(); i++)
            {
                m_hnaRoutingTable->RemoveRoute(0);
            }

            for (AssociationSet::const_iterator it = associationSet.begin(); it != associationSet.end();
                 it++)
            {
                AssociationTuple const &tuple = *it;

                // Test if HNA associations received from other gateways
                // are also announced by this node. In such a case, no route
                // is created for this association tuple (go to the next one).
                bool goToNextAssociationTuple = false;
                const Associations &localHnaAssociations = m_state.GetAssociations();
                NS_LOG_DEBUG("Nb local associations: " << localHnaAssociations.size());
                for (Associations::const_iterator assocIterator = localHnaAssociations.begin();
                     assocIterator != localHnaAssociations.end(); assocIterator++)
                {
                    Association const &localHnaAssoc = *assocIterator;
                    if (localHnaAssoc.networkAddr == tuple.networkAddr &&
                        localHnaAssoc.netmask == tuple.netmask)
                    {
                        NS_LOG_DEBUG("HNA association received from another GW is part of local HNA "
                                     "associations: no route added for network "
                                     << tuple.networkAddr << "/" << tuple.netmask);
                        goToNextAssociationTuple = true;
                    }
                }
                if (goToNextAssociationTuple)
                {
                    continue;
                }

                RoutingTableEntry gatewayEntry;

                bool gatewayEntryExists = Lookup(tuple.gatewayAddr, gatewayEntry);
                bool addRoute = false;

                uint32_t routeIndex = 0;

                for (routeIndex = 0; routeIndex < m_hnaRoutingTable->GetNRoutes(); routeIndex++)
                {
                    Ipv4RoutingTableEntry route = m_hnaRoutingTable->GetRoute(routeIndex);
                    if (route.GetDestNetwork() == tuple.networkAddr &&
                        route.GetDestNetworkMask() == tuple.netmask)
                    {
                        break;
                    }
                }

                if (routeIndex == m_hnaRoutingTable->GetNRoutes())
                {
                    addRoute = true;
                }
                else if (gatewayEntryExists &&
                         m_hnaRoutingTable->GetMetric(routeIndex) > gatewayEntry.distance)
                {
                    m_hnaRoutingTable->RemoveRoute(routeIndex);
                    addRoute = true;
                }

                if (addRoute && gatewayEntryExists)
                {
                    m_hnaRoutingTable->AddNetworkRouteTo(tuple.networkAddr, tuple.netmask,
                                                         gatewayEntry.nextAddr, gatewayEntry.interface,
                                                         gatewayEntry.distance);
                }
            }

            NS_LOG_DEBUG("Node " << m_mainAddress << ": RoutingTableComputation end.");

            m_routingTableChanged(GetSize());
        }

        void
        RoutingProtocol::ProcessHello(const olsr::MessageHeader &msg, const Ipv4Address &receiverIface,
                                      const Ipv4Address &senderIface)
        {
            NS_LOG_FUNCTION(msg << receiverIface << senderIface);

            const olsr::MessageHeader::Hello &hello = msg.GetHello();

            LinkSensing(msg, hello, receiverIface, senderIface);

#ifdef NS3_LOG_ENABLE
            {
                const LinkSet &links = m_state.GetLinks();
                NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                             << " ** BEGIN dump Link Set for OLSR Node " << m_mainAddress);
                for (LinkSet::const_iterator link = links.begin(); link != links.end(); link++)
                {
                    NS_LOG_DEBUG(*link);
                }
                NS_LOG_DEBUG("** END dump Link Set for OLSR Node " << m_mainAddress);

                const NeighborSet &neighbors = m_state.GetNeighbors();
                NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                             << " ** BEGIN dump Neighbor Set for OLSR Node " << m_mainAddress);
                for (NeighborSet::const_iterator neighbor = neighbors.begin(); neighbor != neighbors.end();
                     neighbor++)
                {
                    NS_LOG_DEBUG(*neighbor);
                }
                NS_LOG_DEBUG("** END dump Neighbor Set for OLSR Node " << m_mainAddress);
            }
#endif // NS3_LOG_ENABLE

            PopulateNeighborSet(msg, hello);
            PopulateTwoHopNeighborSet(msg, hello);

#ifdef NS3_LOG_ENABLE
            {
                const TwoHopNeighborSet &twoHopNeighbors = m_state.GetTwoHopNeighbors();
                NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                             << " ** BEGIN dump TwoHopNeighbor Set for OLSR Node " << m_mainAddress);
                for (TwoHopNeighborSet::const_iterator tuple = twoHopNeighbors.begin();
                     tuple != twoHopNeighbors.end(); tuple++)
                {
                    NS_LOG_DEBUG(*tuple);
                }
                NS_LOG_DEBUG("** END dump TwoHopNeighbor Set for OLSR Node " << m_mainAddress);
            }
#endif // NS3_LOG_ENABLE

            MprComputation();
            PopulateMprSelectorSet(msg, hello);
        }

        // 改
        void RoutingProtocol::ProcessTc(const olsr::MessageHeader &msg, const Ipv4Address &senderIface)
        {
            const olsr::MessageHeader::Tc &tc = msg.GetTc();
            Time now = Simulator::Now();

            // 1. If the sender interface of this message is not in the symmetric
            // 1-hop neighborhood of this node, the message MUST be discarded.
            const LinkTuple *link_tuple = m_state.FindSymLinkTuple(senderIface, now);
            if (link_tuple == NULL)
            {
                return;
            }

            // 2. If there exist some tuple in the topology set where:
            //    T_last_addr == originator address AND
            //    T_seq       >  ANSN,
            // then further processing of this TC message MUST NOT be
            // performed.
            const TopologyTuple *topologyTuple =
                m_state.FindNewerTopologyTuple(msg.GetOriginatorAddress(), tc.ansn);
            if (topologyTuple != NULL)
            {
                return;
            }

            // 3. All tuples in the topology set where:
            //    T_last_addr == originator address AND
            //    T_seq       <  ANSN
            // MUST be removed from the topology set.
            m_state.EraseOlderTopologyTuples(msg.GetOriginatorAddress(), tc.ansn);
            std::vector<uint32_t> etxDatas = tc.EtxData;
            // 4. 遍历广播的邻居地址
            for (uint32_t i = 0; i < tc.neighborAddresses.size(); i++)
            {
                Ipv4Address addr = tc.neighborAddresses[i];
                uint32_t etx = tc.EtxData[i];

                // 4.1. 若存在满足下述条件的
                //      T_dest_addr == advertised neighbor main address, AND
                //      T_last_addr == originator address,
                //      需要将时间和etx更新
                //      T_time      =  current time + validity time.

                TopologyTuple *topologyTuple = m_state.FindTopologyTuple(addr, msg.GetOriginatorAddress());

                if (topologyTuple != NULL)
                {
                    topologyTuple->expirationTime = now + msg.GetVTime();
                    topologyTuple->Etx = etx;
                    // m_state.InsertOrUpdateLinkQosTuple(msg.GetOriginatorAddress(), addr, etx, now + msg.GetVTime());
                }
                else
                {
                    // 4.2. 创建新的拓扑元组
                    //      T_dest_addr = advertised neighbor main address,
                    //      T_last_addr = originator address,
                    //      T_seq       = ANSN,
                    //      T_time      = current time + validity time.
                    TopologyTuple topologyTuple;
                    topologyTuple.destAddr = addr;
                    topologyTuple.lastAddr = msg.GetOriginatorAddress();
                    topologyTuple.sequenceNumber = tc.ansn;
                    topologyTuple.expirationTime = now + msg.GetVTime();
                    topologyTuple.Etx = etx;
                    AddTopologyTuple(topologyTuple);
                    // m_state.InsertOrUpdateLinkQosTuple(msg.GetOriginatorAddress(), addr, etx, now + msg.GetVTime());

                    // Schedules topology tuple deletion
                    m_events.Track(Simulator::Schedule(DELAY(topologyTuple.expirationTime),
                                                       &RoutingProtocol::TopologyTupleTimerExpire, this,
                                                       topologyTuple.destAddr, topologyTuple.lastAddr));
                }
            }

#ifdef NS3_LOG_ENABLE
            {
                const TopologySet &topology = m_state.GetTopologySet();
                NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                             << " ** BEGIN dump TopologySet for OLSR Node " << m_mainAddress);
                for (TopologySet::const_iterator tuple = topology.begin(); tuple != topology.end(); tuple++)
                {
                    NS_LOG_DEBUG(*tuple);
                }
                NS_LOG_DEBUG("** END dump TopologySet Set for OLSR Node " << m_mainAddress);
            }
#endif // NS3_LOG_ENABLE
        }

        void
        RoutingProtocol::ProcessMid(const olsr::MessageHeader &msg, const Ipv4Address &senderIface)
        {
            const olsr::MessageHeader::Mid &mid = msg.GetMid();
            Time now = Simulator::Now();

            NS_LOG_DEBUG("Node " << m_mainAddress << " ProcessMid from " << senderIface);
            // 1. If the sender interface of this message is not in the symmetric
            // 1-hop neighborhood of this node, the message MUST be discarded.
            const LinkTuple *linkTuple = m_state.FindSymLinkTuple(senderIface, now);
            if (linkTuple == NULL)
            {
                NS_LOG_LOGIC("Node " << m_mainAddress
                                     << ": the sender interface of this message is not in the "
                                        "symmetric 1-hop neighborhood of this node,"
                                        " the message MUST be discarded.");
                return;
            }

            // 2. For each interface address listed in the MID message
            for (std::vector<Ipv4Address>::const_iterator i = mid.interfaceAddresses.begin();
                 i != mid.interfaceAddresses.end(); i++)
            {
                bool updated = false;
                IfaceAssocSet &ifaceAssoc = m_state.GetIfaceAssocSetMutable();
                for (IfaceAssocSet::iterator tuple = ifaceAssoc.begin(); tuple != ifaceAssoc.end(); tuple++)
                {
                    if (tuple->ifaceAddr == *i && tuple->mainAddr == msg.GetOriginatorAddress())
                    {
                        NS_LOG_LOGIC("IfaceAssoc updated: " << *tuple);
                        tuple->time = now + msg.GetVTime();
                        updated = true;
                    }
                }
                if (!updated)
                {
                    IfaceAssocTuple tuple;
                    tuple.ifaceAddr = *i;
                    tuple.mainAddr = msg.GetOriginatorAddress();
                    tuple.time = now + msg.GetVTime();
                    AddIfaceAssocTuple(tuple);
                    NS_LOG_LOGIC("New IfaceAssoc added: " << tuple);
                    // Schedules iface association tuple deletion
                    Simulator::Schedule(DELAY(tuple.time), &RoutingProtocol::IfaceAssocTupleTimerExpire,
                                        this, tuple.ifaceAddr);
                }
            }

            // 3. (not part of the RFC) iterate over all NeighborTuple's and
            // TwoHopNeighborTuples, update the neighbor addresses taking into account
            // the new MID information.
            NeighborSet &neighbors = m_state.GetNeighbors();
            for (NeighborSet::iterator neighbor = neighbors.begin(); neighbor != neighbors.end();
                 neighbor++)
            {
                neighbor->neighborMainAddr = GetMainAddress(neighbor->neighborMainAddr);
            }

            TwoHopNeighborSet &twoHopNeighbors = m_state.GetTwoHopNeighbors();
            for (TwoHopNeighborSet::iterator twoHopNeighbor = twoHopNeighbors.begin();
                 twoHopNeighbor != twoHopNeighbors.end(); twoHopNeighbor++)
            {
                twoHopNeighbor->neighborMainAddr = GetMainAddress(twoHopNeighbor->neighborMainAddr);
                twoHopNeighbor->twoHopNeighborAddr = GetMainAddress(twoHopNeighbor->twoHopNeighborAddr);
            }
            NS_LOG_DEBUG("Node " << m_mainAddress << " ProcessMid from " << senderIface << " -> END.");
        }

        void
        RoutingProtocol::ProcessHna(const olsr::MessageHeader &msg, const Ipv4Address &senderIface)
        {

            const olsr::MessageHeader::Hna &hna = msg.GetHna();
            Time now = Simulator::Now();

            // 1. If the sender interface of this message is not in the symmetric
            // 1-hop neighborhood of this node, the message MUST be discarded.
            const LinkTuple *link_tuple = m_state.FindSymLinkTuple(senderIface, now);
            if (link_tuple == NULL)
            {
                return;
            }

            // 2. Otherwise, for each (network address, netmask) pair in the
            // message:

            for (std::vector<olsr::MessageHeader::Hna::Association>::const_iterator it =
                     hna.associations.begin();
                 it != hna.associations.end(); it++)
            {
                AssociationTuple *tuple =
                    m_state.FindAssociationTuple(msg.GetOriginatorAddress(), it->address, it->mask);

                // 2.1  if an entry in the association set already exists, where:
                //          A_gateway_addr == originator address
                //          A_network_addr == network address
                //          A_netmask      == netmask
                //      then the holding time for that tuple MUST be set to:
                //          A_time         =  current time + validity time
                if (tuple != NULL)
                {
                    tuple->expirationTime = now + msg.GetVTime();
                }

                // 2.2 otherwise, a new tuple MUST be recorded with:
                //          A_gateway_addr =  originator address
                //          A_network_addr =  network address
                //          A_netmask      =  netmask
                //          A_time         =  current time + validity time
                else
                {
                    AssociationTuple assocTuple = {msg.GetOriginatorAddress(), it->address, it->mask,
                                                   now + msg.GetVTime()};
                    AddAssociationTuple(assocTuple);

                    // Schedule Association Tuple deletion
                    Simulator::Schedule(DELAY(assocTuple.expirationTime),
                                        &RoutingProtocol::AssociationTupleTimerExpire, this,
                                        assocTuple.gatewayAddr, assocTuple.networkAddr, assocTuple.netmask);
                }
            }
        }

        // add
        void RoutingProtocol::ProcessHelloAck(const olsr::MessageHeader &msg, const Ipv4Address &receiverIface,
                                              const Ipv4Address &senderIface)
        {
            const olsr::MessageHeader::HelloAck &helloAck = msg.GetHelloAck();
            Time now = Simulator::Now();

            // 判断接收的helloAck中的receiveAddress是否是自身的一个接口
            Ipv4Address receiveAdd = helloAck.receiveAddress;
            bool isSendToMe = false;
            for (uint32_t i = 0; i < m_ipv4->GetNInterfaces(); i++)
            {
                Ipv4Address addr = m_ipv4->GetAddress(i, 0).GetLocal();
                if (addr == receiveAdd)
                {

                    isSendToMe = true;
                    break;
                }
            }
            if (!isSendToMe)
            {
                // 不是发给自己的，丢弃
                return;
            }

            LinkTuple *link_tuple = m_state.FindLinkTuple(senderIface);
            // 正向链路
            LinkQosTuple *linkQosTuple = m_state.FindLinkQosTuple(receiverIface, senderIface);

            bool createLinkQosTuple = false;
            if (linkQosTuple == NULL)
            {
                // std::cout<<"在processHelloAck里创建正向链路"<<std::endl;

                // 正向链路不存在，之前发的hello包是广播，未创建正向链路
                LinkQosTuple linkQosTupleTmp;
                linkQosTupleTmp.localIfaceAddr = receiverIface;
                linkQosTupleTmp.neighborIfaceAddr = senderIface;
                // 正向链路hello发送和接收hello确认数都为1
                linkQosTupleTmp.sendHelloSum = 1;
                linkQosTupleTmp.receiveAckSum = 0;
                linkQosTupleTmp.Etx = 0; // 一个不可能的值
                linkQosTuple = m_state.InsertLinkQosTuple(linkQosTupleTmp);
                createLinkQosTuple = true;
            }
            else if (linkQosTuple->Etx == 100)
            {
                // 说明是在linksensing里创建的正向链路，需要更新
                // 本次收到的ack是广播后邻居发来的，sendHello++
                linkQosTuple->sendHelloSum++;
            }
            linkQosTuple->receiveAckSum += 1;
            linkQosTuple->time = now + msg.GetVTime();
            linkQosTuple->Etx = (uint32_t)((double)linkQosTuple->sendHelloSum / linkQosTuple->receiveAckSum);

            if (m_mainAddress == "10.1.1.1")
            {
                if (linkQosTuple->Etx == 0)
                {
                    std::cout << "这里processHelloAck新建或更新正向链路时etx == 0"
                              << ",sendhello个数：" << linkQosTuple->sendHelloSum
                              << "，receiveHelloAck个数：" << linkQosTuple->receiveAckSum << std::endl;
                }
            }

            NS_ASSERT(linkQosTuple != NULL);

            if (createLinkQosTuple)
            {
                m_events.Track(Simulator::Schedule(DELAY(linkQosTuple->time),
                                                   &RoutingProtocol::LinkQosTupleTimerExpire, this,
                                                   linkQosTuple->localIfaceAddr, linkQosTuple->neighborIfaceAddr));
            }

            bool createLinkTuple = false;
            if (link_tuple == NULL)
            {
                LinkTuple newLinkTuple;
                // 可以在收到helloAck时就创建，也可以在收到对方的hello创建，这里尝试创建链路
                newLinkTuple.neighborIfaceAddr = senderIface;
                newLinkTuple.localIfaceAddr = receiverIface;
                newLinkTuple.symTime = now - Seconds(1);
                newLinkTuple.time = now + msg.GetVTime();

                link_tuple = &m_state.InsertLinkTuple(newLinkTuple);
                createLinkTuple = true;
            }
            link_tuple->Etx = linkQosTuple->Etx;
            link_tuple->asymTime = now + msg.GetVTime();
            link_tuple->time = std::max(link_tuple->time, link_tuple->asymTime);

            if (createLinkTuple)
            {
                Time minTime = std::min(link_tuple->time, link_tuple->symTime);
                LinkTupleAdded(*link_tuple, OLSR_WILL_HIGH);
                m_events.Track(Simulator::Schedule(DELAY(minTime),
                                                   &RoutingProtocol::LinkTupleTimerExpire, this,
                                                   link_tuple->neighborIfaceAddr));
            }
            else
            {
                LinkTupleUpdated(*link_tuple, OLSR_WILL_HIGH);
            }
        }

        void
        RoutingProtocol::ForwardDefault(olsr::MessageHeader olsrMessage, DuplicateTuple *duplicated,
                                        const Ipv4Address &localIface, const Ipv4Address &senderAddress)
        {
            Time now = Simulator::Now();

            // If the sender interface address is not in the symmetric
            // 1-hop neighborhood the message must not be forwarded
            const LinkTuple *linkTuple = m_state.FindSymLinkTuple(senderAddress, now);
            if (linkTuple == NULL)
            {
                return;
            }

            // If the message has already been considered for forwarding,
            // it must not be retransmitted again
            if (duplicated != NULL && duplicated->retransmitted)
            {
                NS_LOG_LOGIC(Simulator::Now()
                             << "Node " << m_mainAddress
                             << " does not forward a message received"
                                " from "
                             << olsrMessage.GetOriginatorAddress() << " because it is duplicated");
                return;
            }

            // If the sender interface address is an interface address
            // of a MPR selector of this node and ttl is greater than 1,
            // the message must be retransmitted
            bool retransmitted = false;
            if (olsrMessage.GetTimeToLive() > 1)
            {
                const MprSelectorTuple *mprselTuple =
                    m_state.FindMprSelectorTuple(GetMainAddress(senderAddress));
                if (mprselTuple != NULL)
                {
                    olsrMessage.SetTimeToLive(olsrMessage.GetTimeToLive() - 1);
                    olsrMessage.SetHopCount(olsrMessage.GetHopCount() + 1);
                    // We have to introduce a random delay to avoid
                    // synchronization with neighbors.
                    QueueMessage(olsrMessage, JITTER);
                    retransmitted = true;
                }
            }

            // Update duplicate tuple...
            if (duplicated != NULL)
            {
                duplicated->expirationTime = now + OLSR_DUP_HOLD_TIME;
                duplicated->retransmitted = retransmitted;
                duplicated->ifaceList.push_back(localIface);
            }
            // ...or create a new one
            else
            {
                DuplicateTuple newDup;
                newDup.address = olsrMessage.GetOriginatorAddress();
                newDup.sequenceNumber = olsrMessage.GetMessageSequenceNumber();
                newDup.expirationTime = now + OLSR_DUP_HOLD_TIME;
                newDup.retransmitted = retransmitted;
                newDup.ifaceList.push_back(localIface);
                AddDuplicateTuple(newDup);
                // Schedule dup tuple deletion
                Simulator::Schedule(OLSR_DUP_HOLD_TIME, &RoutingProtocol::DupTupleTimerExpire, this,
                                    newDup.address, newDup.sequenceNumber);
            }
        }

        void
        RoutingProtocol::QueueMessage(const olsr::MessageHeader &message, Time delay)
        {
            m_queuedMessages.push_back(message);
            if (not m_queuedMessagesTimer.IsRunning())
            {
                m_queuedMessagesTimer.SetDelay(delay);
                m_queuedMessagesTimer.Schedule();
            }
        }

        void
        RoutingProtocol::SendPacket(Ptr<Packet> packet, const MessageList &containedMessages)
        {
            NS_LOG_DEBUG("OLSR node " << m_mainAddress << " sending a OLSR packet");

            // Add a header
            olsr::PacketHeader header;
            header.SetPacketLength(header.GetSerializedSize() + packet->GetSize());
            header.SetPacketSequenceNumber(GetPacketSequenceNumber());
            packet->AddHeader(header);

            // Trace it
            m_txPacketTrace(header, containedMessages);

            // Send it
            for (std::map<Ptr<Socket>, Ipv4InterfaceAddress>::const_iterator i = m_sendSockets.begin();
                 i != m_sendSockets.end(); i++)
            {
                Ptr<Packet> pkt = packet->Copy();
                Ipv4Address bcast = i->second.GetLocal().GetSubnetDirectedBroadcast(i->second.GetMask());
                i->first->SendTo(pkt, 0, InetSocketAddress(bcast, OLSR_PORT_NUMBER));
            }
        }

        void
        RoutingProtocol::SendQueuedMessages(void)
        {
            Ptr<Packet> packet = Create<Packet>();
            int numMessages = 0;

            NS_LOG_DEBUG("Olsr node " << m_mainAddress << ": SendQueuedMessages");

            MessageList msglist;

            for (std::vector<olsr::MessageHeader>::const_iterator message = m_queuedMessages.begin();
                 message != m_queuedMessages.end(); message++)
            {
                Ptr<Packet> p = Create<Packet>();
                p->AddHeader(*message);
                packet->AddAtEnd(p);
                msglist.push_back(*message);
                if (++numMessages == OLSR_MAX_MSGS)
                {
                    SendPacket(packet, msglist);
                    msglist.clear();
                    // Reset variables for next packet
                    numMessages = 0;
                    packet = Create<Packet>();
                }
            }

            if (packet->GetSize())
            {
                SendPacket(packet, msglist);
            }

            m_queuedMessages.clear();
        }

        uint32_t RoutingProtocol::calANCR(Time now)
        {
            for (std::vector<std::pair<Time, Ipv4Address>>::iterator it = newConnected.begin(); it != newConnected.end();)
            {
                // 删除不在统计时间内的节点
                if (it->first < (now - OLSR_NEIGHBOR_CHANGE_RATE_TIME))
                {
                    it = newConnected.erase(it);
                }
                else
                {
                    // 可以直接break，以后尝试
                    // TODO
                    it++;
                }
            }
            for (std::vector<std::pair<Time, Ipv4Address>>::iterator it = newLoss.begin(); it != newLoss.end();)
            {
                // 删除不在统计时间内的节点
                if (it->first < (now - OLSR_NEIGHBOR_CHANGE_RATE_TIME))
                {
                    it = newLoss.erase(it);
                }
                else
                {
                    // 可以直接break，以后尝试
                    // TODO
                    it++;
                }
            }
            // 平均邻居变化率公式为 (时间区间内新增加邻居数 + 新断开邻居数)/统计时间    统计时间暂定为3*hello_interval，单位为s
            return newConnected.size() + newLoss.size();
        }

        void RoutingProtocol::SendHello(void)
        {
            NS_LOG_FUNCTION(this);

            olsr::MessageHeader msg;
            Time now = Simulator::Now();

            msg.SetVTime(OLSR_NEIGHB_HOLD_TIME);
            msg.SetOriginatorAddress(m_mainAddress);
            msg.SetTimeToLive(1);
            msg.SetHopCount(0);
            msg.SetMessageSequenceNumber(GetMessageSequenceNumber());
            olsr::MessageHeader::Hello &hello = msg.GetHello();

            uint32_t ancr = calANCR(now);
            // if(m_mainAddress == "10.1.1.14"){
            //     std::cout<<std::endl;
            // }
            hello.ANCR = ancr;

            hello.SetHTime(m_helloInterval);
            hello.willingness = m_willingness;

            // 向hello消息中添加位置和速度信息
            Ptr<Node> node = GetObject<Node>();
            Ptr<GaussMarkovMobilityModel> mobModel = node->GetObject<GaussMarkovMobilityModel>();
            Vector3D pos = mobModel->GetPosition();
            Vector3D vel = mobModel->GetVelocity();

            hello.x = (int32_t)pos.x;
            hello.y = (int32_t)pos.y;
            hello.z = (int16_t)pos.z;

            hello.vel_x = (int16_t)vel.x;
            hello.vel_y = (int16_t)vel.y;
            hello.vel_z = (int16_t)vel.z;

            std::vector<olsr::MessageHeader::Hello::LinkMessage> &linkMessages = hello.linkMessages;

            const LinkSet &links = m_state.GetLinks();
            for (LinkSet::const_iterator link_tuple = links.begin(); link_tuple != links.end();
                 link_tuple++)
            {
                if (!(GetMainAddress(link_tuple->localIfaceAddr) == m_mainAddress &&
                      link_tuple->time >= now))
                {
                    continue;
                }

                uint8_t link_type, nb_type = 0xff;

                // Establishes link type
                if (link_tuple->symTime >= now)
                {
                    link_type = OLSR_SYM_LINK;
                }
                else if (link_tuple->asymTime >= now)
                {
                    link_type = OLSR_ASYM_LINK;
                }
                else
                {
                    link_type = OLSR_LOST_LINK;
                }
                // Establishes neighbor type.
                if (m_state.FindMprAddress(GetMainAddress(link_tuple->neighborIfaceAddr)))
                {
                    nb_type = OLSR_MPR_NEIGH;
                    NS_LOG_DEBUG("I consider neighbor " << GetMainAddress(link_tuple->neighborIfaceAddr)
                                                        << " to be MPR_NEIGH.");
                }
                else
                {
                    bool ok = false;
                    for (NeighborSet::const_iterator nb_tuple = m_state.GetNeighbors().begin();
                         nb_tuple != m_state.GetNeighbors().end(); nb_tuple++)
                    {
                        if (nb_tuple->neighborMainAddr == GetMainAddress(link_tuple->neighborIfaceAddr))
                        {
                            if (nb_tuple->status == NeighborTuple::STATUS_SYM)
                            {
                                NS_LOG_DEBUG("I consider neighbor "
                                             << GetMainAddress(link_tuple->neighborIfaceAddr)
                                             << " to be SYM_NEIGH.");
                                nb_type = OLSR_SYM_NEIGH;
                            }
                            else if (nb_tuple->status == NeighborTuple::STATUS_NOT_SYM)
                            {
                                nb_type = OLSR_NOT_NEIGH;
                                NS_LOG_DEBUG("I consider neighbor "
                                             << GetMainAddress(link_tuple->neighborIfaceAddr)
                                             << " to be NOT_NEIGH.");
                            }
                            else
                            {
                                NS_FATAL_ERROR("There is a neighbor tuple with an unknown status!\n");
                            }
                            ok = true;
                            break;
                        }
                    }
                    if (!ok)
                    {
                        NS_LOG_WARN("I don't know the neighbor "
                                    << GetMainAddress(link_tuple->neighborIfaceAddr) << "!!!");
                        continue;
                    }
                }

                olsr::MessageHeader::Hello::LinkMessage linkMessage;
                linkMessage.linkCode = (link_type & 0x03) | ((nb_type << 2) & 0x0f);
                linkMessage.neighborInterfaceAddresses.push_back(link_tuple->neighborIfaceAddr);
                // 从linkQos表中找存在的一个正向链路元组，赋值给hello信息
                LinkQosTuple *linkQosTuple = m_state.FindLinkQosTuple(link_tuple->neighborIfaceAddr);

                // bool create = false;
                // if (linkQosTuple == NULL)
                // {
                //     std::cout << now.GetMilliSeconds() << "ms,我是：" << m_mainAddress << ",正在sendhello创建正向链路，本地："
                //               << link_tuple->localIfaceAddr << ",邻居：" << link_tuple->neighborIfaceAddr << std::endl;

                //     // 给链路创建linkQosTuple
                //     LinkQosTuple linkQosTupleTmp;
                //     linkQosTupleTmp.localIfaceAddr = link_tuple->localIfaceAddr;
                //     linkQosTupleTmp.neighborIfaceAddr = link_tuple->neighborIfaceAddr;
                //     linkQosTupleTmp.receiveAckSum = 1;
                //     linkQosTupleTmp.sendHelloSum = 1;
                //     linkQosTuple = m_state.InsertLinkQosTuple(linkQosTupleTmp);
                //     create = true;
                // }
                linkQosTuple->sendHelloSum += 1;
                linkQosTuple->time = now + msg.GetVTime() - m_helloInterval;
                linkQosTuple->Etx = linkQosTuple->receiveAckSum == 0 ? 100 : (uint32_t)((double)linkQosTuple->sendHelloSum / linkQosTuple->receiveAckSum);
                if (linkQosTuple->Etx == 0)
                {
                    std::cout << "这里sendHello新建或更新正向链路时etx == 0" << std::endl;
                }
                // if (create)
                // {
                //     m_events.Track(Simulator::Schedule(DELAY(linkQosTuple->time),
                //                                        &RoutingProtocol::LinkQosTupleTimerExpire, this,
                //                                        link_tuple->localIfaceAddr, link_tuple->neighborIfaceAddr));
                // }

                linkMessage.neighborEtxs.push_back(linkQosTuple->Etx);
                linkMessages.push_back(linkMessage);
            }

            NS_LOG_DEBUG("OLSR HELLO message size: " << int(msg.GetSerializedSize()) << " (with "
                                                     << int(linkMessages.size()) << " link messages)");
            QueueMessage(msg, JITTER);
        }

        // 改
        void RoutingProtocol::SendTc(void)
        {
            NS_LOG_FUNCTION(this);

            olsr::MessageHeader msg;

            msg.SetVTime(OLSR_TOP_HOLD_TIME);
            msg.SetOriginatorAddress(m_mainAddress);
            msg.SetTimeToLive(255);
            msg.SetHopCount(0);
            msg.SetMessageSequenceNumber(GetMessageSequenceNumber());

            olsr::MessageHeader::Tc &tc = msg.GetTc();
            tc.ansn = m_ansn;

            std::map<Ipv4Address, uint32_t> mapTmp;
            for (MprSelectorSet::const_iterator mprsel_tuple = m_state.GetMprSelectors().begin();
                 mprsel_tuple != m_state.GetMprSelectors().end(); mprsel_tuple++)
            {
                tc.neighborAddresses.push_back(mprsel_tuple->mainAddr);
                // 用map暂存mpr_sel的etx信息
                mapTmp.insert(std::pair<Ipv4Address, uint32_t>(mprsel_tuple->mainAddr, 1));
            }
            // 将所有MPR选择节点的ETX信息封装到TC信息包中,正向和反向的乘积
            LinkQosSet linkQosSet = m_state.GetLinkQosSet();
            for (LinkQosSet::const_iterator it = linkQosSet.begin(); it != linkQosSet.end(); it++)
            {
                Ipv4Address neighborMainAddress = GetMainAddress(it->neighborIfaceAddr);
                Ipv4Address localMainAddress = GetMainAddress(it->localIfaceAddr);
                if (mapTmp.find(neighborMainAddress) != mapTmp.end() && localMainAddress == m_mainAddress)
                {
                    // 正向链路
                    mapTmp[neighborMainAddress] = std::max(mapTmp[neighborMainAddress], it->Etx * mapTmp[neighborMainAddress]);
                }
                if (mapTmp.find(localMainAddress) != mapTmp.end() && neighborMainAddress == m_mainAddress)
                {
                    // 反向链路
                    mapTmp[localMainAddress] = std::max(mapTmp[localMainAddress], it->Etx * mapTmp[localMainAddress]);
                }
            }

            for (MprSelectorSet::const_iterator mprsel_tuple = m_state.GetMprSelectors().begin();
                 mprsel_tuple != m_state.GetMprSelectors().end(); mprsel_tuple++)
            {
                tc.EtxData.push_back(mapTmp[mprsel_tuple->mainAddr]);
            }
            QueueMessage(msg, JITTER);
        }

        void
        RoutingProtocol::SendMid(void)
        {
            olsr::MessageHeader msg;
            olsr::MessageHeader::Mid &mid = msg.GetMid();

            // A node which has only a single interface address participating in
            // the MANET (i.e., running OLSR), MUST NOT generate any MID
            // message.

            // A node with several interfaces, where only one is participating
            // in the MANET and running OLSR (e.g., a node is connected to a
            // wired network as well as to a MANET) MUST NOT generate any MID
            // messages.

            // A node with several interfaces, where more than one is
            // participating in the MANET and running OLSR MUST generate MID
            // messages as specified.

            // [ Note: assuming here that all interfaces participate in the
            // MANET; later we may want to make this configurable. ]

            Ipv4Address loopback("127.0.0.1");
            for (uint32_t i = 0; i < m_ipv4->GetNInterfaces(); i++)
            {
                Ipv4Address addr = m_ipv4->GetAddress(i, 0).GetLocal();
                if (addr != m_mainAddress && addr != loopback &&
                    m_interfaceExclusions.find(i) == m_interfaceExclusions.end())
                {
                    mid.interfaceAddresses.push_back(addr);
                }
            }
            if (mid.interfaceAddresses.size() == 0)
            {
                return;
            }

            msg.SetVTime(OLSR_MID_HOLD_TIME);
            msg.SetOriginatorAddress(m_mainAddress);
            msg.SetTimeToLive(255);
            msg.SetHopCount(0);
            msg.SetMessageSequenceNumber(GetMessageSequenceNumber());

            QueueMessage(msg, JITTER);
        }

        void
        RoutingProtocol::SendHna(void)
        {

            olsr::MessageHeader msg;

            msg.SetVTime(OLSR_HNA_HOLD_TIME);
            msg.SetOriginatorAddress(m_mainAddress);
            msg.SetTimeToLive(255);
            msg.SetHopCount(0);
            msg.SetMessageSequenceNumber(GetMessageSequenceNumber());
            olsr::MessageHeader::Hna &hna = msg.GetHna();

            std::vector<olsr::MessageHeader::Hna::Association> &associations = hna.associations;

            // Add all local HNA associations to the HNA message
            const Associations &localHnaAssociations = m_state.GetAssociations();
            for (Associations::const_iterator it = localHnaAssociations.begin();
                 it != localHnaAssociations.end(); it++)
            {
                olsr::MessageHeader::Hna::Association assoc = {it->networkAddr, it->netmask};
                associations.push_back(assoc);
            }
            // If there is no HNA associations to send, return without queuing the message
            if (associations.size() == 0)
            {
                return;
            }

            // Else, queue the message to be sent later on
            QueueMessage(msg, JITTER);
        }

        void RoutingProtocol::SendHelloAck(const MessageHeader &messageHeader,
                                           Ipv4Address receiverIfaceAddr, Ipv4Address senderIfaceAddr)
        {
            olsr::MessageHeader msg;

            msg.SetVTime(OLSR_NEIGHB_HOLD_TIME);
            msg.SetOriginatorAddress(m_mainAddress);
            msg.SetTimeToLive(1);
            msg.SetHopCount(0);
            msg.SetMessageSequenceNumber(GetMessageSequenceNumber());

            olsr::MessageHeader::HelloAck &helloAck = msg.GetHelloAck();
            helloAck.receiveAddress = receiverIfaceAddr;

            QueueMessage(msg, JITTER);
        }

        void
        RoutingProtocol::AddHostNetworkAssociation(Ipv4Address networkAddr, Ipv4Mask netmask)
        {
            // Check if the (networkAddr, netmask) tuple already exist
            // in the list of local HNA associations
            const Associations &localHnaAssociations = m_state.GetAssociations();
            for (Associations::const_iterator assocIterator = localHnaAssociations.begin();
                 assocIterator != localHnaAssociations.end(); assocIterator++)
            {
                Association const &localHnaAssoc = *assocIterator;
                if (localHnaAssoc.networkAddr == networkAddr && localHnaAssoc.netmask == netmask)
                {
                    NS_LOG_INFO("HNA association for network " << networkAddr << "/" << netmask
                                                               << " already exists.");
                    return;
                }
            }
            // If the tuple does not already exist, add it to the list of local HNA associations.
            NS_LOG_INFO("Adding HNA association for network " << networkAddr << "/" << netmask << ".");
            m_state.InsertAssociation((Association){networkAddr, netmask});
        }

        void
        RoutingProtocol::RemoveHostNetworkAssociation(Ipv4Address networkAddr, Ipv4Mask netmask)
        {
            NS_LOG_INFO("Removing HNA association for network " << networkAddr << "/" << netmask << ".");
            m_state.EraseAssociation((Association){networkAddr, netmask});
        }

        void
        RoutingProtocol::SetRoutingTableAssociation(Ptr<Ipv4StaticRouting> routingTable)
        {
            // If a routing table has already been associated, remove
            // corresponding entries from the list of local HNA associations
            if (m_routingTableAssociation != 0)
            {
                NS_LOG_INFO("Removing HNA entries coming from the old routing table association.");
                for (uint32_t i = 0; i < m_routingTableAssociation->GetNRoutes(); i++)
                {
                    Ipv4RoutingTableEntry route = m_routingTableAssociation->GetRoute(i);
                    // If the outgoing interface for this route is a non-olsr interface
                    if (UsesNonOlsrOutgoingInterface(route))
                    {
                        // remove the corresponding entry
                        RemoveHostNetworkAssociation(route.GetDestNetwork(), route.GetDestNetworkMask());
                    }
                }
            }

            // Sets the routingTableAssociation to its new value
            m_routingTableAssociation = routingTable;

            // Iterate over entries of the associated routing table and
            // add the routes using non-olsr outgoing interfaces to the list
            // of local HNA associations
            NS_LOG_DEBUG("Nb local associations before adding some entries from"
                         " the associated routing table: "
                         << m_state.GetAssociations().size());
            for (uint32_t i = 0; i < m_routingTableAssociation->GetNRoutes(); i++)
            {
                Ipv4RoutingTableEntry route = m_routingTableAssociation->GetRoute(i);
                Ipv4Address destNetworkAddress = route.GetDestNetwork();
                Ipv4Mask destNetmask = route.GetDestNetworkMask();

                // If the outgoing interface for this route is a non-olsr interface,
                if (UsesNonOlsrOutgoingInterface(route))
                {
                    // Add this entry's network address and netmask to the list of local HNA entries
                    AddHostNetworkAssociation(destNetworkAddress, destNetmask);
                }
            }
            NS_LOG_DEBUG("Nb local associations after having added some entries from "
                         "the associated routing table: "
                         << m_state.GetAssociations().size());
        }

        bool
        RoutingProtocol::UsesNonOlsrOutgoingInterface(const Ipv4RoutingTableEntry &route)
        {
            std::set<uint32_t>::const_iterator ci = m_interfaceExclusions.find(route.GetInterface());
            // The outgoing interface is a non-OLSR interface if a match is found
            // before reaching the end of the list of excluded interfaces
            return ci != m_interfaceExclusions.end();
        }

        // 改
        void RoutingProtocol::LinkSensing(const olsr::MessageHeader &msg,
                                          const olsr::MessageHeader::Hello &hello,
                                          const Ipv4Address &receiverIface, const Ipv4Address &senderIface)
        {
            Time now = Simulator::Now();
            bool updated = false;
            bool created = false;
            NS_LOG_DEBUG("@" << now.As(Time::S) << ": Olsr node " << m_mainAddress
                             << ": LinkSensing(receiverIface=" << receiverIface
                             << ", senderIface=" << senderIface << ") BEGIN");

            NS_ASSERT(msg.GetVTime() > Seconds(0));
            LinkTuple *link_tuple = m_state.FindLinkTuple(senderIface);

            // 正向链路质量元组
            LinkQosTuple *linkQostupleForw = m_state.FindLinkQosTuple(receiverIface, senderIface);
            // 反向链路质量元组
            LinkQosTuple *linkQostupleRev = m_state.FindLinkQosTuple(senderIface, receiverIface);
            uint32_t etxr = 1;

            if (link_tuple == NULL)
            {
                LinkTuple newLinkTuple;
                // We have to create a new tuple
                newLinkTuple.neighborIfaceAddr = senderIface;
                newLinkTuple.localIfaceAddr = receiverIface;
                newLinkTuple.symTime = now - Seconds(1);
                newLinkTuple.time = now + msg.GetVTime();
                newLinkTuple.Etx = 100; //新的链路没通信过，设置为最大值 100

                link_tuple = &m_state.InsertLinkTuple(newLinkTuple);

                // 记录新增加的邻居
                newConnected.push_back(std::pair<ns3::Time, ns3::Ipv4Address>(now, senderIface));

                created = true;
                NS_LOG_LOGIC("Existing link tuple did not exist => creating new one");
            }
            else
            {
                NS_LOG_LOGIC("Existing link tuple already exists => will update it");
                // 稍后更新
                updated = true;
            }
            link_tuple->asymTime = now + msg.GetVTime();
            for (std::vector<olsr::MessageHeader::Hello::LinkMessage>::const_iterator linkMessage =
                     hello.linkMessages.begin();
                 linkMessage != hello.linkMessages.end(); linkMessage++)
            {
                int lt = linkMessage->linkCode & 0x03;        // Link Type
                int nt = (linkMessage->linkCode >> 2) & 0x03; // Neighbor Type

#ifdef NS3_LOG_ENABLE
                const char *linkTypeName;
                switch (lt)
                {
                case OLSR_UNSPEC_LINK:
                    linkTypeName = "UNSPEC_LINK";
                    break;
                case OLSR_ASYM_LINK:
                    linkTypeName = "ASYM_LINK";
                    break;
                case OLSR_SYM_LINK:
                    linkTypeName = "SYM_LINK";
                    break;
                case OLSR_LOST_LINK:
                    linkTypeName = "LOST_LINK";
                    break;
                default:
                    linkTypeName = "(invalid value!)";
                }

                const char *neighborTypeName;
                switch (nt)
                {
                case OLSR_NOT_NEIGH:
                    neighborTypeName = "NOT_NEIGH";
                    break;
                case OLSR_SYM_NEIGH:
                    neighborTypeName = "SYM_NEIGH";
                    break;
                case OLSR_MPR_NEIGH:
                    neighborTypeName = "MPR_NEIGH";
                    break;
                default:
                    neighborTypeName = "(invalid value!)";
                }

                NS_LOG_DEBUG("Looking at HELLO link messages with Link Type "
                             << lt << " (" << linkTypeName << ") and Neighbor Type " << nt << " ("
                             << neighborTypeName << ")");
#endif // NS3_LOG_ENABLE

                // We must not process invalid advertised links
                if ((lt == OLSR_SYM_LINK && nt == OLSR_NOT_NEIGH) ||
                    (nt != OLSR_SYM_NEIGH && nt != OLSR_MPR_NEIGH && nt != OLSR_NOT_NEIGH))
                {
                    NS_LOG_LOGIC("HELLO link code is invalid => IGNORING");
                    continue;
                }

                std::vector<uint32_t> neighborEtx = linkMessage->neighborEtxs; // Neighbor hello received from senderIface -lmh
                std::vector<Ipv4Address> neiborAddress = linkMessage->neighborInterfaceAddresses;
                // 改
                for (uint32_t i = 0; i < neiborAddress.size(); i++)
                {
                    NS_LOG_DEBUG("   -> Neighbor: " << neiborAddress[i]);
                    if (neiborAddress[i] == receiverIface)
                    {
                        if (lt == OLSR_LOST_LINK)
                        {
                            NS_LOG_LOGIC("link is LOST => expiring it");
                            link_tuple->symTime = now - Seconds(1);
                            updated = true;
                        }
                        else if (lt == OLSR_SYM_LINK || lt == OLSR_ASYM_LINK)
                        {
                            NS_LOG_DEBUG(*link_tuple << ": link is SYM or ASYM => should become SYM now"
                                                        " (symTime being increased to "
                                                     << now + msg.GetVTime());
                            link_tuple->symTime = now + msg.GetVTime();
                            link_tuple->time = link_tuple->symTime + OLSR_NEIGHB_HOLD_TIME;

                            etxr = std::max(neighborEtx[i], etxr);
                            updated = true;
                        }
                        else
                        {
                            NS_FATAL_ERROR("bad link type");
                        }
                        break;
                    }
                    else
                    {
                        NS_LOG_DEBUG("     \\-> *neighIfaceAddr (" << neiborAddress[i]
                                                                   << " != receiverIface (" << receiverIface
                                                                   << ") => IGNORING!");
                    }
                }
                NS_LOG_DEBUG("Link tuple updated: " << int(updated));
            }
            bool createLinkQosRev = false;
            if (linkQostupleRev == NULL)
            {
                // 创建反向链路质量元组
                LinkQosTuple linkQostupleRevTmp;
                linkQostupleRevTmp.localIfaceAddr = senderIface;
                linkQostupleRevTmp.neighborIfaceAddr = receiverIface;
                linkQostupleRev = m_state.InsertLinkQosTuple(linkQostupleRevTmp);
                createLinkQosRev = true;
            }
            linkQostupleRev->time = now + msg.GetVTime();
            linkQostupleRev->Etx = etxr;

            bool createLinkQosForw = false;
            if (linkQostupleForw == NULL)
            {
                // 创建正向链路质量元组
                LinkQosTuple linkQostupleForwTmp;
                linkQostupleForwTmp.localIfaceAddr = receiverIface;
                linkQostupleForwTmp.neighborIfaceAddr = senderIface;
                linkQostupleForwTmp.sendHelloSum = 0;
                linkQostupleForwTmp.receiveAckSum = 0;
                linkQostupleForwTmp.Etx = 100; // 还没发送接收过，设为最大值
                linkQostupleForwTmp.time = now + msg.GetVTime();
                linkQostupleForw = m_state.InsertLinkQosTuple(linkQostupleForwTmp);
                createLinkQosForw = true;
            }
            linkQostupleForw->ancr = hello.ANCR;

            // 该节点的位置和速度信息
            Ptr<Node> node = GetObject<Node>();
            Ptr<GaussMarkovMobilityModel> mobModel = node->GetObject<GaussMarkovMobilityModel>();
            Vector3D pos = mobModel->GetPosition();
            Vector3D vel = mobModel->GetVelocity();

            // 相对位置
            int32_t b = hello.x - pos.x;
            int32_t d = hello.y - pos.y;
            int16_t f = hello.z - pos.z;
            // 两点间的距离
            int32_t distance = sqrt(b * b + d * d + f * f);
            // 相对速度
            int16_t a = hello.vel_x - vel.x;
            int16_t c = hello.vel_y - vel.y;
            int16_t e = hello.vel_z - vel.z;

            // 存相对位置和相对速度
            linkQostupleForw->pos_x = b;
            linkQostupleForw->pos_y = d;
            linkQostupleForw->pos_z = f;
            linkQostupleForw->vel_x = a;
            linkQostupleForw->vel_y = c;
            linkQostupleForw->vel_z = e;

            int32_t r = m_commu_radius;
            double_t lht = 0.0;
            int32_t mid = 0;
            if (a * a + c * c + e * e < 0.01)
            {
                lht = 1000.0;
            }
            else
            {
                if (distance > r)
                {
                    // 邻居此时在通信半径外，提供一个0.2s的缓冲时间，若在该时间内节点能到达通信半径，就选择，否则不选，lht值为-1
                    b = hello.x + 0.2 * hello.vel_x - pos.x;
                    d = hello.x + 0.2 * hello.vel_x - pos.x;
                    f = hello.x + 0.2 * hello.vel_x - pos.x;
                    distance = sqrt(b * b + d * d + f * f);
                    if (distance > r)
                    {
                        lht = -1.0;
                    }
                }
                if (lht != -1.0)
                {
                    mid = r * r * (a * a + c * c + e * e) - (pow(a * d - b * c, 2) + pow(a * f - b * e, 2) + pow(c * f - d * e, 2));
                    lht = double_t(-(a * b + c * d + e * f) + sqrt(mid)) / (a * a + c * c + e * e);
                }
            }
            linkQostupleForw->LHT = lht;

            std::vector<uint32_t>& oldDistance = linkQostupleForw->distance;
            if (oldDistance.size() >= 5)
            {
                oldDistance.erase(oldDistance.begin());
            }
            oldDistance.push_back(distance);

            uint32_t sum = 0;
            uint32_t squareSum = 0;
            for (std::vector<uint32_t>::iterator it = oldDistance.begin(); it != oldDistance.end(); it++)
            {
                sum += *it;
                squareSum += (*it) * (*it);
            }
            uint32_t n = oldDistance.size();
            // 求方差
            double_t lsd = double_t(squareSum) / n + pow(double_t(sum) / 5, 2);
            linkQostupleForw->LSD = lsd;

            if (m_mainAddress == "10.1.1.14")
            {
                std::cout << "现在是：" << now.GetSeconds() << "s,我监听到邻居的ancr:" << hello.ANCR
                          << ", 两者之间的相对距离：" << distance
                          << ", 与邻居的链路保持时间：" << lht << "s,与邻居的链路稳定度:" << lsd << std::endl;
            }

            if (createLinkQosForw || createLinkQosRev)
            {
                Time minTime = std::min(linkQostupleForw->time, linkQostupleRev->time);
                m_events.Track(Simulator::Schedule(DELAY(minTime),
                                                   &RoutingProtocol::LinkQosTupleTimerExpire, this,
                                                   linkQostupleForw->localIfaceAddr, linkQostupleForw->neighborIfaceAddr));
            }

            link_tuple->time = std::max(link_tuple->time, link_tuple->asymTime);
            if (updated)
            {
                LinkTupleUpdated(*link_tuple, hello.willingness);
            }

            // Schedules link tuple deletion
            if (created)
            {
                Time minTime = std::min(link_tuple->time, link_tuple->symTime);
                LinkTupleAdded(*link_tuple, hello.willingness);
                m_events.Track(Simulator::Schedule(DELAY(minTime),
                                                   &RoutingProtocol::LinkTupleTimerExpire, this,
                                                   link_tuple->neighborIfaceAddr));
            }
            NS_LOG_DEBUG("@" << now.As(Time::S) << ": Olsr node " << m_mainAddress << ": LinkSensing END");
        }

        void
        RoutingProtocol::PopulateNeighborSet(const olsr::MessageHeader &msg,
                                             const olsr::MessageHeader::Hello &hello)
        {
            NeighborTuple *nb_tuple = m_state.FindNeighborTuple(msg.GetOriginatorAddress());
            if (nb_tuple != NULL)
            {
                nb_tuple->willingness = hello.willingness;
            }
        }

        void
        RoutingProtocol::PopulateTwoHopNeighborSet(const olsr::MessageHeader &msg,
                                                   const olsr::MessageHeader::Hello &hello)
        {
            Time now = Simulator::Now();

            NS_LOG_DEBUG("Olsr node " << m_mainAddress << ": PopulateTwoHopNeighborSet BEGIN");

            for (LinkSet::const_iterator link_tuple = m_state.GetLinks().begin();
                 link_tuple != m_state.GetLinks().end(); link_tuple++)
            {
                NS_LOG_LOGIC("Looking at link tuple: " << *link_tuple);
                if (GetMainAddress(link_tuple->neighborIfaceAddr) != msg.GetOriginatorAddress())
                {
                    NS_LOG_LOGIC(
                        "Link tuple ignored: "
                        "GetMainAddress (link_tuple->neighborIfaceAddr) != msg.GetOriginatorAddress ()");
                    NS_LOG_LOGIC("(GetMainAddress("
                                 << link_tuple->neighborIfaceAddr
                                 << "): " << GetMainAddress(link_tuple->neighborIfaceAddr)
                                 << "; msg.GetOriginatorAddress (): " << msg.GetOriginatorAddress());
                    continue;
                }

                if (link_tuple->symTime < now)
                {
                    NS_LOG_LOGIC("Link tuple ignored: expired.");
                    continue;
                }

                typedef std::vector<olsr::MessageHeader::Hello::LinkMessage> LinkMessageVec;
                for (LinkMessageVec::const_iterator linkMessage = hello.linkMessages.begin();
                     linkMessage != hello.linkMessages.end(); linkMessage++)
                {
                    int neighborType = (linkMessage->linkCode >> 2) & 0x3;
#ifdef NS3_LOG_ENABLE
                    const char *neighborTypeNames[3] = {"NOT_NEIGH", "SYM_NEIGH", "MPR_NEIGH"};
                    const char *neighborTypeName =
                        ((neighborType < 3) ? neighborTypeNames[neighborType] : "(invalid value)");
                    NS_LOG_DEBUG("Looking at Link Message from HELLO message: neighborType="
                                 << neighborType << " (" << neighborTypeName << ")");
#endif // NS3_LOG_ENABLE

                    for (std::vector<Ipv4Address>::const_iterator nb2hop_addr_iter =
                             linkMessage->neighborInterfaceAddresses.begin();
                         nb2hop_addr_iter != linkMessage->neighborInterfaceAddresses.end();
                         nb2hop_addr_iter++)
                    {
                        Ipv4Address nb2hop_addr = GetMainAddress(*nb2hop_addr_iter);
                        NS_LOG_DEBUG("Looking at 2-hop neighbor address from HELLO message: "
                                     << *nb2hop_addr_iter << " (main address is " << nb2hop_addr << ")");
                        if (neighborType == OLSR_SYM_NEIGH || neighborType == OLSR_MPR_NEIGH)
                        {
                            // If the main address of the 2-hop neighbor address == main address
                            // of the receiving node, silently discard the 2-hop
                            // neighbor address.
                            if (nb2hop_addr == m_mainAddress)
                            {
                                NS_LOG_LOGIC("Ignoring 2-hop neighbor (it is the node itself)");
                                continue;
                            }

                            // Otherwise, a 2-hop tuple is created
                            TwoHopNeighborTuple *nb2hop_tuple =
                                m_state.FindTwoHopNeighborTuple(msg.GetOriginatorAddress(), nb2hop_addr);
                            NS_LOG_LOGIC("Adding the 2-hop neighbor"
                                         << (nb2hop_tuple ? " (refreshing existing entry)" : ""));
                            if (nb2hop_tuple == NULL)
                            {
                                TwoHopNeighborTuple new_nb2hop_tuple;
                                new_nb2hop_tuple.neighborMainAddr = msg.GetOriginatorAddress();
                                new_nb2hop_tuple.twoHopNeighborAddr = nb2hop_addr;
                                new_nb2hop_tuple.expirationTime = now + msg.GetVTime();
                                AddTwoHopNeighborTuple(new_nb2hop_tuple);
                                // Schedules nb2hop tuple deletion
                                m_events.Track(Simulator::Schedule(DELAY(new_nb2hop_tuple.expirationTime),
                                                                   &RoutingProtocol::Nb2hopTupleTimerExpire,
                                                                   this, new_nb2hop_tuple.neighborMainAddr,
                                                                   new_nb2hop_tuple.twoHopNeighborAddr));
                            }
                            else
                            {
                                nb2hop_tuple->expirationTime = now + msg.GetVTime();
                            }
                        }
                        else if (neighborType == OLSR_NOT_NEIGH)
                        {
                            // For each 2-hop node listed in the HELLO message
                            // with Neighbor Type equal to NOT_NEIGH all 2-hop
                            // tuples where: N_neighbor_main_addr == Originator
                            // Address AND N_2hop_addr == main address of the
                            // 2-hop neighbor are deleted.
                            NS_LOG_LOGIC(
                                "2-hop neighbor is NOT_NEIGH => deleting matching 2-hop neighbor state");
                            m_state.EraseTwoHopNeighborTuples(msg.GetOriginatorAddress(), nb2hop_addr);
                        }
                        else
                        {
                            NS_LOG_LOGIC("*** WARNING *** Ignoring link message (inside HELLO) with bad"
                                         " neighbor type value: "
                                         << neighborType);
                        }
                    }
                }
            }

            NS_LOG_DEBUG("Olsr node " << m_mainAddress << ": PopulateTwoHopNeighborSet END");
        }

        void RoutingProtocol::PopulateMprSelectorSet(const olsr::MessageHeader &msg,
                                                     const olsr::MessageHeader::Hello &hello)
        {
            NS_LOG_FUNCTION(this);

            Time now = Simulator::Now();

            typedef std::vector<olsr::MessageHeader::Hello::LinkMessage> LinkMessageVec;
            for (LinkMessageVec::const_iterator linkMessage = hello.linkMessages.begin();
                 linkMessage != hello.linkMessages.end(); linkMessage++)
            {
                int nt = linkMessage->linkCode >> 2;
                if (nt == OLSR_MPR_NEIGH)
                {
                    NS_LOG_DEBUG("Processing a link message with neighbor type MPR_NEIGH");

                    for (std::vector<Ipv4Address>::const_iterator nb_iface_addr =
                             linkMessage->neighborInterfaceAddresses.begin();
                         nb_iface_addr != linkMessage->neighborInterfaceAddresses.end(); nb_iface_addr++)
                    {
                        if (GetMainAddress(*nb_iface_addr) == m_mainAddress)
                        {
                            NS_LOG_DEBUG("Adding entry to mpr selector set for neighbor " << *nb_iface_addr);

                            // We must create a new entry into the mpr selector set
                            MprSelectorTuple *existing_mprsel_tuple =
                                m_state.FindMprSelectorTuple(msg.GetOriginatorAddress());
                            if (existing_mprsel_tuple == NULL)
                            {
                                MprSelectorTuple mprsel_tuple;

                                mprsel_tuple.mainAddr = msg.GetOriginatorAddress();
                                mprsel_tuple.expirationTime = now + msg.GetVTime();
                                AddMprSelectorTuple(mprsel_tuple);

                                // Schedules mpr selector tuple deletion
                                m_events.Track(Simulator::Schedule(DELAY(mprsel_tuple.expirationTime),
                                                                   &RoutingProtocol::MprSelTupleTimerExpire,
                                                                   this, mprsel_tuple.mainAddr));
                            }
                            else
                            {
                                existing_mprsel_tuple->expirationTime = now + msg.GetVTime();
                            }
                        }
                    }
                }
            }
            NS_LOG_DEBUG("Computed MPR selector set for node " << m_mainAddress << ": "
                                                               << m_state.PrintMprSelectorSet());
        }

#if 0
///
/// \brief Drops a given packet because it couldn't be delivered to the corresponding
/// destination by the MAC layer. This may cause a neighbor loss, and appropriate
/// actions are then taken.
///
/// \param p the packet which couldn't be delivered by the MAC layer.
///
void
OLSR::mac_failed (Ptr<Packet> p)
{
  double now              = Simulator::Now ();
  struct hdr_ip* ih       = HDR_IP (p);
  struct hdr_cmn* ch      = HDR_CMN (p);

  debug ("%f: Node %d MAC Layer detects a breakage on link to %d\n",
         now,
         OLSR::node_id (ra_addr ()),
         OLSR::node_id (ch->next_hop ()));

  if ((uint32_t)ih->daddr () == IP_BROADCAST)
    {
      drop (p, DROP_RTR_MAC_CALLBACK);
      return;
    }

  OLSR_link_tuple* link_tuple = state_.find_link_tuple (ch->next_hop ());
  if (link_tuple != NULL)
    {
      link_tuple->lost_time () = now + OLSR_NEIGHB_HOLD_TIME;
      link_tuple->time ()      = now + OLSR_NEIGHB_HOLD_TIME;
      nb_loss (link_tuple);
    }
  drop (p, DROP_RTR_MAC_CALLBACK);
}
#endif

        void
        RoutingProtocol::NeighborLoss(const LinkTuple &tuple)
        {
            NS_LOG_DEBUG(Simulator::Now().As(Time::S) << ": OLSR Node " << m_mainAddress << " LinkTuple "
                                                      << tuple.neighborIfaceAddr << " -> neighbor loss.");
            LinkTupleUpdated(tuple, OLSR_WILL_DEFAULT);
            m_state.EraseTwoHopNeighborTuples(GetMainAddress(tuple.neighborIfaceAddr));
            m_state.EraseMprSelectorTuples(GetMainAddress(tuple.neighborIfaceAddr));

            MprComputation();
            RoutingTableComputation();
        }

        void
        RoutingProtocol::AddDuplicateTuple(const DuplicateTuple &tuple)
        {
            /*debug("%f: Node %d adds dup tuple: addr = %d seq_num = %d\n",
                Simulator::Now (),
                OLSR::node_id(ra_addr()),
                OLSR::node_id(tuple->addr()),
                tuple->seq_num());*/
            m_state.InsertDuplicateTuple(tuple);
        }

        void
        RoutingProtocol::RemoveDuplicateTuple(const DuplicateTuple &tuple)
        {
            /*debug("%f: Node %d removes dup tuple: addr = %d seq_num = %d\n",
          Simulator::Now (),
          OLSR::node_id(ra_addr()),
          OLSR::node_id(tuple->addr()),
          tuple->seq_num());*/
            m_state.EraseDuplicateTuple(tuple);
        }

        NeighborTuple *RoutingProtocol::LinkTupleAdded(const LinkTuple &tuple, uint8_t willingness)
        {
            // Creates associated neighbor tuple
            NeighborTuple nb_tuple;
            nb_tuple.neighborMainAddr = GetMainAddress(tuple.neighborIfaceAddr);
            nb_tuple.willingness = willingness;

            if (tuple.symTime >= Simulator::Now())
            {
                nb_tuple.status = NeighborTuple::STATUS_SYM;
            }
            else
            {
                nb_tuple.status = NeighborTuple::STATUS_NOT_SYM;
            }
            return AddNeighborTuple(nb_tuple);
        }

        void
        RoutingProtocol::RemoveLinkTuple(const LinkTuple &tuple)
        {
            NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                         << ": OLSR Node " << m_mainAddress << " LinkTuple " << tuple << " REMOVED.");

            m_state.EraseNeighborTuple(GetMainAddress(tuple.neighborIfaceAddr));
            m_state.EraseLinkTuple(tuple);
        }

        void RoutingProtocol::LinkTupleUpdated(const LinkTuple &tuple, uint8_t willingness)
        {
            // Each time a link tuple changes, the associated neighbor tuple must be recomputed

            NS_LOG_DEBUG(Simulator::Now().As(Time::S)
                         << ": OLSR Node " << m_mainAddress << " LinkTuple " << tuple << " UPDATED.");

            NeighborTuple *nb_tuple = m_state.FindNeighborTuple(GetMainAddress(tuple.neighborIfaceAddr));

            if (nb_tuple == NULL)
            {
                nb_tuple = LinkTupleAdded(tuple, willingness);
            }

            if (nb_tuple != NULL)
            {
                int statusBefore = nb_tuple->status;

                bool hasSymmetricLink = false;

                const LinkSet &linkSet = m_state.GetLinks();
                for (LinkSet::const_iterator it = linkSet.begin(); it != linkSet.end(); it++)
                {
                    const LinkTuple &link_tuple = *it;
                    if (GetMainAddress(link_tuple.neighborIfaceAddr) == nb_tuple->neighborMainAddr &&
                        link_tuple.symTime >= Simulator::Now())
                    {
                        hasSymmetricLink = true;
                        break;
                    }
                }

                if (hasSymmetricLink)
                {
                    nb_tuple->status = NeighborTuple::STATUS_SYM;
                    NS_LOG_DEBUG(*nb_tuple << "->status = STATUS_SYM; changed:"
                                           << int(statusBefore != nb_tuple->status));
                }
                else
                {
                    nb_tuple->status = NeighborTuple::STATUS_NOT_SYM;
                    NS_LOG_DEBUG(*nb_tuple << "->status = STATUS_NOT_SYM; changed:"
                                           << int(statusBefore != nb_tuple->status));
                }
            }
            else
            {
                NS_LOG_WARN("ERROR! Wanted to update a NeighborTuple but none was found!");
            }
        }

        NeighborTuple *RoutingProtocol::AddNeighborTuple(const NeighborTuple &tuple)
        {
            //   debug("%f: Node %d adds neighbor tuple: nb_addr = %d status = %s\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->neighborMainAddr),
            //         ((tuple->status() == OLSR_STATUS_SYM) ? "sym" : "not_sym"));

            NeighborTuple *neighborTuple = m_state.InsertOrUpdateNeighborTuple(tuple);
            IncrementAnsn();
            return neighborTuple;
        }

        void
        RoutingProtocol::RemoveNeighborTuple(const NeighborTuple &tuple)
        {
            //   debug("%f: Node %d removes neighbor tuple: nb_addr = %d status = %s\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->neighborMainAddr),
            //         ((tuple->status() == OLSR_STATUS_SYM) ? "sym" : "not_sym"));

            m_state.EraseNeighborTuple(tuple);
            IncrementAnsn();
        }

        void
        RoutingProtocol::AddTwoHopNeighborTuple(const TwoHopNeighborTuple &tuple)
        {
            //   debug("%f: Node %d adds 2-hop neighbor tuple: nb_addr = %d nb2hop_addr = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->neighborMainAddr),
            //         OLSR::node_id(tuple->twoHopNeighborAddr));

            m_state.InsertTwoHopNeighborTuple(tuple);
        }

        void
        RoutingProtocol::RemoveTwoHopNeighborTuple(const TwoHopNeighborTuple &tuple)
        {
            //   debug("%f: Node %d removes 2-hop neighbor tuple: nb_addr = %d nb2hop_addr = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->neighborMainAddr),
            //         OLSR::node_id(tuple->twoHopNeighborAddr));

            m_state.EraseTwoHopNeighborTuple(tuple);
        }

        void
        RoutingProtocol::IncrementAnsn(void)
        {
            m_ansn = (m_ansn + 1) % (OLSR_MAX_SEQ_NUM + 1);
        }

        void
        RoutingProtocol::AddMprSelectorTuple(const MprSelectorTuple &tuple)
        {
            //   debug("%f: Node %d adds MPR selector tuple: nb_addr = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->main_addr()));

            m_state.InsertMprSelectorTuple(tuple);
            IncrementAnsn();
        }

        void
        RoutingProtocol::RemoveMprSelectorTuple(const MprSelectorTuple &tuple)
        {
            //   debug("%f: Node %d removes MPR selector tuple: nb_addr = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->main_addr()));

            m_state.EraseMprSelectorTuple(tuple);
            IncrementAnsn();
        }

        void
        RoutingProtocol::AddTopologyTuple(const TopologyTuple &tuple)
        {
            //   debug("%f: Node %d adds topology tuple: dest_addr = %d last_addr = %d seq = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->dest_addr()),
            //         OLSR::node_id(tuple->last_addr()),
            //         tuple->seq());

            m_state.InsertTopologyTuple(tuple);
        }

        void
        RoutingProtocol::RemoveTopologyTuple(const TopologyTuple &tuple)
        {
            //   debug("%f: Node %d removes topology tuple: dest_addr = %d last_addr = %d seq = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->dest_addr()),
            //         OLSR::node_id(tuple->last_addr()),
            //         tuple->seq());

            m_state.EraseTopologyTuple(tuple);
        }

        void
        RoutingProtocol::AddIfaceAssocTuple(const IfaceAssocTuple &tuple)
        {
            //   debug("%f: Node %d adds iface association tuple: main_addr = %d iface_addr = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->main_addr()),
            //         OLSR::node_id(tuple->iface_addr()));

            m_state.InsertIfaceAssocTuple(tuple);
        }

        void
        RoutingProtocol::RemoveIfaceAssocTuple(const IfaceAssocTuple &tuple)
        {
            //   debug("%f: Node %d removes iface association tuple: main_addr = %d iface_addr = %d\n",
            //         Simulator::Now (),
            //         OLSR::node_id(ra_addr()),
            //         OLSR::node_id(tuple->main_addr()),
            //         OLSR::node_id(tuple->iface_addr()));

            m_state.EraseIfaceAssocTuple(tuple);
        }

        void
        RoutingProtocol::AddAssociationTuple(const AssociationTuple &tuple)
        {
            m_state.InsertAssociationTuple(tuple);
        }

        void
        RoutingProtocol::RemoveAssociationTuple(const AssociationTuple &tuple)
        {
            m_state.EraseAssociationTuple(tuple);
        }

        uint16_t
        RoutingProtocol::GetPacketSequenceNumber()
        {
            m_packetSequenceNumber = (m_packetSequenceNumber + 1) % (OLSR_MAX_SEQ_NUM + 1);
            return m_packetSequenceNumber;
        }

        uint16_t
        RoutingProtocol::GetMessageSequenceNumber()
        {
            m_messageSequenceNumber = (m_messageSequenceNumber + 1) % (OLSR_MAX_SEQ_NUM + 1);
            return m_messageSequenceNumber;
        }

        void
        RoutingProtocol::HelloTimerExpire(void)
        {
            SendHello();
            m_helloTimer.Schedule(m_helloInterval);
        }

        void
        RoutingProtocol::TcTimerExpire(void)
        {
            if (m_state.GetMprSelectors().size() > 0)
            {
                SendTc();
            }
            else
            {
                NS_LOG_DEBUG("Not sending any TC, no one selected me as MPR.");
            }
            m_tcTimer.Schedule(m_tcInterval);
        }

        void
        RoutingProtocol::MidTimerExpire(void)
        {
            SendMid();
            m_midTimer.Schedule(m_midInterval);
        }

        void
        RoutingProtocol::HnaTimerExpire(void)
        {
            if (m_state.GetAssociations().size() > 0)
            {
                SendHna();
            }
            else
            {
                NS_LOG_DEBUG("Not sending any HNA, no associations to advertise.");
            }
            m_hnaTimer.Schedule(m_hnaInterval);
        }

        void
        RoutingProtocol::DupTupleTimerExpire(Ipv4Address address, uint16_t sequenceNumber)
        {
            DuplicateTuple *tuple = m_state.FindDuplicateTuple(address, sequenceNumber);
            Time now = Simulator::Now();
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->expirationTime < now)
            {
                RemoveDuplicateTuple(*tuple);
            }
            else
            {
                m_events.Track(Simulator::Schedule(DELAY(tuple->expirationTime),
                                                   &RoutingProtocol::DupTupleTimerExpire, this, address,
                                                   sequenceNumber));
            }
        }

        void
        RoutingProtocol::LinkTupleTimerExpire(Ipv4Address neighborIfaceAddr)
        {
            Time now = Simulator::Now();

            // the tuple parameter may be a stale copy; get a newer version from m_state
            LinkTuple *tuple = m_state.FindLinkTuple(neighborIfaceAddr);
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->time < now)
            {
                // 记录与邻居断开联系
                newLoss.push_back(std::pair<Time, Ipv4Address>(now, neighborIfaceAddr));

                RemoveLinkTuple(*tuple);
            }
            else if (tuple->symTime < now)
            {
                if (m_linkTupleTimerFirstTime)
                {
                    m_linkTupleTimerFirstTime = false;
                }
                else
                {
                    //

                    NeighborLoss(*tuple);
                }

                m_events.Track(Simulator::Schedule(
                    DELAY(tuple->time), &RoutingProtocol::LinkTupleTimerExpire, this, neighborIfaceAddr));
            }
            else
            {
                Time minTime = std::min(tuple->time, tuple->symTime);
                m_events.Track(Simulator::Schedule(DELAY(minTime),
                                                   &RoutingProtocol::LinkTupleTimerExpire, this,
                                                   neighborIfaceAddr));
            }
        }

        void RoutingProtocol::LinkQosTupleTimerExpire(Ipv4Address localIfaceAddr, Ipv4Address neighborIfaceAddr)
        {
            LinkQosTuple *linkQosTupleForw = m_state.FindLinkQosTuple(localIfaceAddr, neighborIfaceAddr);
            LinkQosTuple *linkQosTupleRev = m_state.FindLinkQosTuple(neighborIfaceAddr, localIfaceAddr);
            if (linkQosTupleForw == NULL && linkQosTupleRev == NULL)
            {
                return;
            }
            Time now = Simulator::Now();
            bool needTrack = false;
            Time minTime = now + 6 * OLSR_NEIGHB_HOLD_TIME;
            if (linkQosTupleForw != NULL)
            {

                if (linkQosTupleForw->time < now)
                {
                    m_state.EraseLinkQosTuple(*linkQosTupleForw);
                }
                else
                {
                    needTrack = true;
                    minTime = std::min(minTime, linkQosTupleForw->time);
                }
            }
            if (linkQosTupleRev != NULL)
            {
                if (linkQosTupleRev->time < now)
                {
                    m_state.EraseLinkQosTuple(*linkQosTupleRev);
                }
                else
                {
                    needTrack = true;
                    minTime = std::min(minTime, linkQosTupleRev->time);
                }
            }
            if (needTrack)
            {
                m_events.Track(Simulator::Schedule(DELAY(minTime),
                                                   &RoutingProtocol::LinkQosTupleTimerExpire, this,
                                                   localIfaceAddr, neighborIfaceAddr));
            }
        }

        void
        RoutingProtocol::Nb2hopTupleTimerExpire(Ipv4Address neighborMainAddr,
                                                Ipv4Address twoHopNeighborAddr)
        {
            TwoHopNeighborTuple *tuple;
            tuple = m_state.FindTwoHopNeighborTuple(neighborMainAddr, twoHopNeighborAddr);
            Time now = Simulator::Now();
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->expirationTime < now)
            {
                RemoveTwoHopNeighborTuple(*tuple);
            }
            else
            {
                m_events.Track(Simulator::Schedule(DELAY(tuple->expirationTime),
                                                   &RoutingProtocol::Nb2hopTupleTimerExpire, this,
                                                   neighborMainAddr, twoHopNeighborAddr));
            }
        }

        void
        RoutingProtocol::MprSelTupleTimerExpire(Ipv4Address mainAddr)
        {
            MprSelectorTuple *tuple = m_state.FindMprSelectorTuple(mainAddr);
            Time now = Simulator::Now();
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->expirationTime < now)
            {
                RemoveMprSelectorTuple(*tuple);
            }
            else
            {
                m_events.Track(Simulator::Schedule(
                    DELAY(tuple->expirationTime), &RoutingProtocol::MprSelTupleTimerExpire, this, mainAddr));
            }
        }

        void
        RoutingProtocol::TopologyTupleTimerExpire(Ipv4Address destAddr, Ipv4Address lastAddr)
        {
            TopologyTuple *tuple = m_state.FindTopologyTuple(destAddr, lastAddr);
            Time now = Simulator::Now();
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->expirationTime < now)
            {
                RemoveTopologyTuple(*tuple);
            }
            else
            {
                m_events.Track(Simulator::Schedule(DELAY(tuple->expirationTime),
                                                   &RoutingProtocol::TopologyTupleTimerExpire, this,
                                                   tuple->destAddr, tuple->lastAddr));
            }
        }

        void
        RoutingProtocol::IfaceAssocTupleTimerExpire(Ipv4Address ifaceAddr)
        {
            IfaceAssocTuple *tuple = m_state.FindIfaceAssocTuple(ifaceAddr);
            Time now = Simulator::Now();
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->time < now)
            {
                RemoveIfaceAssocTuple(*tuple);
            }
            else
            {
                m_events.Track(Simulator::Schedule(
                    DELAY(tuple->time), &RoutingProtocol::IfaceAssocTupleTimerExpire, this, ifaceAddr));
            }
        }

        void
        RoutingProtocol::AssociationTupleTimerExpire(Ipv4Address gatewayAddr, Ipv4Address networkAddr,
                                                     Ipv4Mask netmask)
        {
            AssociationTuple *tuple = m_state.FindAssociationTuple(gatewayAddr, networkAddr, netmask);
            Time now = Simulator::Now();
            if (tuple == NULL)
            {
                return;
            }
            if (tuple->expirationTime < now)
            {
                RemoveAssociationTuple(*tuple);
            }
            else
            {
                m_events.Track(Simulator::Schedule(DELAY(tuple->expirationTime),
                                                   &RoutingProtocol::AssociationTupleTimerExpire, this,
                                                   gatewayAddr, networkAddr, netmask));
            }
        }

        void
        RoutingProtocol::Clear(void)
        {
            NS_LOG_FUNCTION_NOARGS();
            m_table.clear();
        }

        void
        RoutingProtocol::RemoveEntry(Ipv4Address const &dest)
        {
            m_table.erase(dest);
        }

        bool
        RoutingProtocol::Lookup(Ipv4Address const &dest, RoutingTableEntry &outEntry) const
        {
            // Get the iterator at "dest" position
            std::map<Ipv4Address, RoutingTableEntry>::const_iterator it = m_table.find(dest);
            // If there is no route to "dest", return NULL
            if (it == m_table.end())
            {
                return false;
            }
            outEntry = it->second;
            return true;
        }

        bool
        RoutingProtocol::FindSendEntry(RoutingTableEntry const &entry, RoutingTableEntry &outEntry) const
        {
            outEntry = entry;
            while (outEntry.destAddr != outEntry.nextAddr)
            {
                if (not Lookup(outEntry.nextAddr, outEntry))
                {
                    return false;
                }
            }
            return true;
        }

        Ptr<Ipv4Route>
        RoutingProtocol::RouteOutput(Ptr<Packet> p, const Ipv4Header &header, Ptr<NetDevice> oif,
                                     Socket::SocketErrno &sockerr)
        {
            NS_LOG_FUNCTION(this << " " << m_ipv4->GetObject<Node>()->GetId() << " "
                                 << header.GetDestination() << " " << oif);
            Ptr<Ipv4Route> rtentry;
            RoutingTableEntry entry1, entry2;
            bool found = false;

            if (Lookup(header.GetDestination(), entry1) != 0)
            {
                bool foundSendEntry = FindSendEntry(entry1, entry2);
                if (!foundSendEntry)
                {
                    NS_FATAL_ERROR("FindSendEntry failure");
                }
                uint32_t interfaceIdx = entry2.interface;
                if (oif && m_ipv4->GetInterfaceForDevice(oif) != static_cast<int>(interfaceIdx))
                {
                    // We do not attempt to perform a constrained routing search
                    // if the caller specifies the oif; we just enforce that
                    // that the found route matches the requested outbound interface
                    NS_LOG_DEBUG("Olsr node " << m_mainAddress
                                              << ": RouteOutput for dest=" << header.GetDestination()
                                              << " Route interface " << interfaceIdx
                                              << " does not match requested output interface "
                                              << m_ipv4->GetInterfaceForDevice(oif));
                    sockerr = Socket::ERROR_NOROUTETOHOST;
                    return rtentry;
                }
                rtentry = Create<Ipv4Route>();
                rtentry->SetDestination(header.GetDestination());
                // the source address is the interface address that matches
                // the destination address (when multiple are present on the
                // outgoing interface, one is selected via scoping rules)
                NS_ASSERT(m_ipv4);
                uint32_t numOifAddresses = m_ipv4->GetNAddresses(interfaceIdx);
                NS_ASSERT(numOifAddresses > 0);
                Ipv4InterfaceAddress ifAddr;
                if (numOifAddresses == 1)
                {
                    ifAddr = m_ipv4->GetAddress(interfaceIdx, 0);
                }
                else
                {
                    /// \todo Implement IP aliasing and OLSR
                    NS_FATAL_ERROR("XXX Not implemented yet:  IP aliasing and OLSR");
                }
                rtentry->SetSource(ifAddr.GetLocal());
                rtentry->SetGateway(entry2.nextAddr);
                rtentry->SetOutputDevice(m_ipv4->GetNetDevice(interfaceIdx));
                sockerr = Socket::ERROR_NOTERROR;
                NS_LOG_DEBUG("Olsr node " << m_mainAddress << ": RouteOutput for dest="
                                          << header.GetDestination() << " --> nextHop=" << entry2.nextAddr
                                          << " interface=" << entry2.interface);
                NS_LOG_DEBUG("Found route to " << rtentry->GetDestination() << " via nh "
                                               << rtentry->GetGateway() << " with source addr "
                                               << rtentry->GetSource() << " and output dev "
                                               << rtentry->GetOutputDevice());
                found = true;
            }
            else
            {
                rtentry = m_hnaRoutingTable->RouteOutput(p, header, oif, sockerr);

                if (rtentry)
                {
                    found = true;
                    NS_LOG_DEBUG("Found route to " << rtentry->GetDestination() << " via nh "
                                                   << rtentry->GetGateway() << " with source addr "
                                                   << rtentry->GetSource() << " and output dev "
                                                   << rtentry->GetOutputDevice());
                }
            }

            if (!found)
            {
                NS_LOG_DEBUG("Olsr node " << m_mainAddress << ": RouteOutput for dest="
                                          << header.GetDestination() << " No route to host");
                sockerr = Socket::ERROR_NOROUTETOHOST;
            }
            return rtentry;
        }

        bool
        RoutingProtocol::RouteInput(Ptr<const Packet> p, const Ipv4Header &header,
                                    Ptr<const NetDevice> idev, UnicastForwardCallback ucb,
                                    MulticastForwardCallback mcb, LocalDeliverCallback lcb,
                                    ErrorCallback ecb)
        {
            NS_LOG_FUNCTION(this << " " << m_ipv4->GetObject<Node>()->GetId() << " "
                                 << header.GetDestination());

            Ipv4Address dst = header.GetDestination();
            Ipv4Address origin = header.GetSource();

            // Consume self-originated packets
            if (IsMyOwnAddress(origin) == true)
            {
                return true;
            }

            // Local delivery
            NS_ASSERT(m_ipv4->GetInterfaceForDevice(idev) >= 0);
            uint32_t iif = m_ipv4->GetInterfaceForDevice(idev);
            if (m_ipv4->IsDestinationAddress(dst, iif))
            {
                if (!lcb.IsNull())
                {
                    NS_LOG_LOGIC("Local delivery to " << dst);
                    lcb(p, header, iif);
                    return true;
                }
                else
                {
                    // The local delivery callback is null.  This may be a multicast
                    // or broadcast packet, so return false so that another
                    // multicast routing protocol can handle it.  It should be possible
                    // to extend this to explicitly check whether it is a unicast
                    // packet, and invoke the error callback if so
                    NS_LOG_LOGIC("Null local delivery callback");
                    return false;
                }
            }

            NS_LOG_LOGIC("Forward packet");
            // Forwarding
            Ptr<Ipv4Route> rtentry;
            RoutingTableEntry entry1, entry2;
            if (Lookup(header.GetDestination(), entry1))
            {
                bool foundSendEntry = FindSendEntry(entry1, entry2);
                if (!foundSendEntry)
                {
                    NS_FATAL_ERROR("FindSendEntry failure");
                }
                rtentry = Create<Ipv4Route>();
                rtentry->SetDestination(header.GetDestination());
                uint32_t interfaceIdx = entry2.interface;
                // the source address is the interface address that matches
                // the destination address (when multiple are present on the
                // outgoing interface, one is selected via scoping rules)
                NS_ASSERT(m_ipv4);
                uint32_t numOifAddresses = m_ipv4->GetNAddresses(interfaceIdx);
                NS_ASSERT(numOifAddresses > 0);
                Ipv4InterfaceAddress ifAddr;
                if (numOifAddresses == 1)
                {
                    ifAddr = m_ipv4->GetAddress(interfaceIdx, 0);
                }
                else
                {
                    /// \todo Implement IP aliasing and OLSR
                    NS_FATAL_ERROR("XXX Not implemented yet:  IP aliasing and OLSR");
                }
                rtentry->SetSource(ifAddr.GetLocal());
                rtentry->SetGateway(entry2.nextAddr);
                rtentry->SetOutputDevice(m_ipv4->GetNetDevice(interfaceIdx));

                NS_LOG_DEBUG("Olsr node " << m_mainAddress << ": RouteInput for dest="
                                          << header.GetDestination() << " --> nextHop=" << entry2.nextAddr
                                          << " interface=" << entry2.interface);

                ucb(rtentry, p, header);
                return true;
            }
            else
            {
                NS_LOG_LOGIC("No dynamic route, check network routes");
                if (m_hnaRoutingTable->RouteInput(p, header, idev, ucb, mcb, lcb, ecb))
                {
                    return true;
                }
                else
                {

#ifdef NS3_LOG_ENABLE
                    NS_LOG_DEBUG("Olsr node " << m_mainAddress
                                              << ": RouteInput for dest=" << header.GetDestination()
                                              << " --> NOT FOUND; ** Dumping routing table...");

                    for (std::map<Ipv4Address, RoutingTableEntry>::const_iterator iter = m_table.begin();
                         iter != m_table.end(); iter++)
                    {
                        NS_LOG_DEBUG("dest=" << iter->first << " --> next=" << iter->second.nextAddr
                                             << " via interface " << iter->second.interface);
                    }

                    NS_LOG_DEBUG("** Routing table dump end.");
#endif // NS3_LOG_ENABLE

                    return false;
                }
            }
        }
        void
        RoutingProtocol::NotifyInterfaceUp(uint32_t i)
        {
        }
        void
        RoutingProtocol::NotifyInterfaceDown(uint32_t i)
        {
        }
        void
        RoutingProtocol::NotifyAddAddress(uint32_t interface, Ipv4InterfaceAddress address)
        {
        }
        void
        RoutingProtocol::NotifyRemoveAddress(uint32_t interface, Ipv4InterfaceAddress address)
        {
        }

        void
        RoutingProtocol::AddEntry(Ipv4Address const &dest, Ipv4Address const &next, uint32_t interface,
                                  uint32_t distance, uint32_t etxDistance)
        {
            NS_LOG_FUNCTION(this << dest << next << interface << distance << m_mainAddress);

            NS_ASSERT(distance > 0);

            // Creates a new rt entry with specified values
            RoutingTableEntry &entry = m_table[dest];

            entry.destAddr = dest;
            entry.nextAddr = next;
            entry.interface = interface;
            entry.distance = distance;
            entry.etxDistance = etxDistance;
        }

        void
        RoutingProtocol::AddEntry(Ipv4Address const &dest, Ipv4Address const &next,
                                  Ipv4Address const &interfaceAddress, uint32_t distance, uint32_t etxDistance)
        {
            NS_LOG_FUNCTION(this << dest << next << interfaceAddress << distance << m_mainAddress);

            NS_ASSERT(distance > 0);
            NS_ASSERT(m_ipv4);

            RoutingTableEntry entry;
            for (uint32_t i = 0; i < m_ipv4->GetNInterfaces(); i++)
            {
                for (uint32_t j = 0; j < m_ipv4->GetNAddresses(i); j++)
                {
                    if (m_ipv4->GetAddress(i, j).GetLocal() == interfaceAddress)
                    {
                        AddEntry(dest, next, i, distance, etxDistance);
                        return;
                    }
                }
            }
            NS_ASSERT(false); // should not be reached
            AddEntry(dest, next, 0, distance, etxDistance);
        }

        std::vector<RoutingTableEntry>
        RoutingProtocol::GetRoutingTableEntries(void) const
        {
            std::vector<RoutingTableEntry> retval;
            for (std::map<Ipv4Address, RoutingTableEntry>::const_iterator iter = m_table.begin();
                 iter != m_table.end(); iter++)
            {
                retval.push_back(iter->second);
            }
            return retval;
        }

        MprSet
        RoutingProtocol::GetMprSet(void) const
        {
            return m_state.GetMprSet();
        }

        const MprSelectorSet &
        RoutingProtocol::GetMprSelectors(void) const
        {
            return m_state.GetMprSelectors();
        }

        const NeighborSet &
        RoutingProtocol::GetNeighbors(void) const
        {
            return m_state.GetNeighbors();
        }

        const TwoHopNeighborSet &
        RoutingProtocol::GetTwoHopNeighbors(void) const
        {
            return m_state.GetTwoHopNeighbors();
        }

        const TopologySet &
        RoutingProtocol::GetTopologySet(void) const
        {
            return m_state.GetTopologySet();
        }

        const OlsrState &
        RoutingProtocol::GetOlsrState(void) const
        {
            return m_state;
        }

        int64_t
        RoutingProtocol::AssignStreams(int64_t stream)
        {
            NS_LOG_FUNCTION(this << stream);
            m_uniformRandomVariable->SetStream(stream);
            return 1;
        }

        bool
        RoutingProtocol::IsMyOwnAddress(const Ipv4Address &a) const
        {
            std::map<Ptr<Socket>, Ipv4InterfaceAddress>::const_iterator j;
            for (j = m_sendSockets.begin(); j != m_sendSockets.end(); ++j)
            {
                Ipv4InterfaceAddress iface = j->second;
                if (a == iface.GetLocal())
                {
                    return true;
                }
            }
            return false;
        }

        void
        RoutingProtocol::Dump(void)
        {
#ifdef NS3_LOG_ENABLE
            Time now = Simulator::Now();
            NS_LOG_DEBUG("Dumping for node with main address " << m_mainAddress);
            NS_LOG_DEBUG(" Neighbor set");
            for (NeighborSet::const_iterator iter = m_state.GetNeighbors().begin();
                 iter != m_state.GetNeighbors().end(); iter++)
            {
                NS_LOG_DEBUG("  " << *iter);
            }
            NS_LOG_DEBUG(" Two-hop neighbor set");
            for (TwoHopNeighborSet::const_iterator iter = m_state.GetTwoHopNeighbors().begin();
                 iter != m_state.GetTwoHopNeighbors().end(); iter++)
            {
                if (now < iter->expirationTime)
                {
                    NS_LOG_DEBUG("  " << *iter);
                }
            }
            NS_LOG_DEBUG(" Routing table");
            for (std::map<Ipv4Address, RoutingTableEntry>::const_iterator iter = m_table.begin();
                 iter != m_table.end(); iter++)
            {
                NS_LOG_DEBUG("  dest=" << iter->first << " --> next=" << iter->second.nextAddr
                                       << " via interface " << iter->second.interface);
            }
            NS_LOG_DEBUG("");
#endif // NS3_LOG_ENABLE
        }

        Ptr<const Ipv4StaticRouting>
        RoutingProtocol::GetRoutingTableAssociation(void) const
        {
            return m_hnaRoutingTable;
        }

    } // namespace olsr
} // namespace ns3
