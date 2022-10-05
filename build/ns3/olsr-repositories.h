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

#ifndef OLSR_REPOSITORIES_H
#define OLSR_REPOSITORIES_H

#include <set>
#include <vector>
#include <map>

#include "ns3/ipv4-address.h"
#include "ns3/nstime.h"

namespace ns3
{
    namespace olsr
    {

        /// \ingroup olsr
        /// An Interface Association Tuple.
        struct IfaceAssocTuple
        {
            /// Interface address of a node.
            Ipv4Address ifaceAddr;
            /// Main address of the node.
            Ipv4Address mainAddr;
            /// Time at which this tuple expires and must be removed.
            Time time;
        };

        static inline bool
        operator==(const IfaceAssocTuple &a, const IfaceAssocTuple &b)
        {
            return (a.ifaceAddr == b.ifaceAddr && a.mainAddr == b.mainAddr);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const IfaceAssocTuple &tuple)
        {
            os << "IfaceAssocTuple(ifaceAddr=" << tuple.ifaceAddr
               << ", mainAddr=" << tuple.mainAddr
               << ", time=" << tuple.time << ")";
            return os;
        }

        /// \ingroup olsr
        /// A Link Tuple.
        struct LinkTuple
        {
            /// Interface address of the local node.
            Ipv4Address localIfaceAddr;
            /// Interface address of the neighbor node.
            Ipv4Address neighborIfaceAddr;
            /// The link is considered bidirectional until this time.
            Time symTime;
            /// The link is considered unidirectional until this time.
            Time asymTime;
            /// Time at which this tuple expires and must be removed.
            Time time;
        };

        static inline bool
        operator==(const LinkTuple &a, const LinkTuple &b)
        {
            return (a.localIfaceAddr == b.localIfaceAddr && a.neighborIfaceAddr == b.neighborIfaceAddr);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const LinkTuple &tuple)
        {
            os << "LinkTuple(localIfaceAddr=" << tuple.localIfaceAddr
               << ", neighborIfaceAddr=" << tuple.neighborIfaceAddr
               << ", symTime=" << tuple.symTime
               << ", asymTime=" << tuple.asymTime
               << ", expTime=" << tuple.time
               << ")";
            return os;
        }

        /// \ingroup olsr
        /// A LinkQos Tuple.
        // struct LinkQosTuple
        // {
        //     /// Interface address of the local node.
        //     Ipv4Address localIfaceAddr;
        //     /// Interface address of the neighbor node.
        //     Ipv4Address neighborIfaceAddr;
        //     /// Time at which this tuple expires and must be removed.
        //     Time time;

        //     // 本地节点发送到邻居的hello包个数
        //     uint32_t sendHelloSum;
        //     // 本地节点接收到邻居的helloAck包个数
        //     uint32_t receiveAckSum;
        //     /// ETX of this link local->neighbor，后期计算
        //     uint32_t Etx;

        //     // 记录平均邻居变化率
        //     uint16_t ancr;

        //     // 用来统计链路维持时间LHT
        //     // 相对位置
        //     int32_t pos_x;
        //     int32_t pos_y;
        //     int16_t pos_z;
        //     // 相对速度
        //     int16_t vel_x;
        //     int16_t vel_y;
        //     int16_t vel_z;
        //     std::double_t LHT;

        //     // 用来计算链路稳定度LSD
        //     std::vector<uint32_t> distance;
        //     std::double_t LSD;
        // };

        /// \ingroup olsr
        /// A LinkQos Tuple.不考虑过期时间，用于长期观察更新
        struct LinkQosTuple
        {
            // 存邻居的主地址
            Ipv4Address neighborMainAddr;

            // 本地节点发送到邻居的hello包的时间，存ms值
            std::vector<uint32_t> sendHelloTime;
            // 本地节点接收到邻居的helloAck包时间，存ms值
            std::vector<uint32_t> receiveHelloAckTime;
            // 正向链路质量，计算为成功发送一个数据包需要发送的次数
            uint32_t EtxForw;
            // 反向链路质量，计算为邻居发送一个数据包需要发送的次数（只能从邻居的hello包中获取）
            uint32_t EtxRev;

            // 记录平均邻居变化率
            uint16_t ancr;

            // 用来统计链路维持时间LHT
            // 相对位置
            int32_t pos_x;
            int32_t pos_y;
            int16_t pos_z;
            // 相对速度
            int16_t vel_x;
            int16_t vel_y;
            int16_t vel_z;
            std::double_t LHT;

            // 用来计算链路稳定度LSD
            std::vector<uint32_t> distance;
            std::double_t LSD;

            // 后续可以添加衡量拥塞程度的指标
            // TODO
        };

        static inline bool
        operator==(const LinkQosTuple &a, const LinkQosTuple &b)
        {
            return (a.neighborMainAddr == b.neighborMainAddr && a.ancr == b.ancr && a.EtxForw == b.EtxForw && a.EtxRev == b.EtxRev
                    && a.LHT == b.LHT && a.LSD == b.LSD);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const LinkQosTuple &tuple)
        {
            os << "LinkQosTuple(neighborMainAddr=" << tuple.neighborMainAddr
               << ", EtxForw=" << tuple.EtxForw
               << ", EtxRev=" << tuple.EtxRev
               << ", 平均邻居变化率=" << tuple.ancr
               << ", 链路维持时间=" << tuple.LHT
               << ", 链路稳定度=" << tuple.LSD
               << ")";
            return os;
        }

        /// \ingroup olsr
        /// A Neighbor Tuple.
        struct NeighborTuple
        {
            /// Main address of a neighbor node.
            Ipv4Address neighborMainAddr;
            /// Status of the link (Symmetric or not Symmetric).
            enum Status
            {
                STATUS_NOT_SYM = 0, // "not symmetric"
                STATUS_SYM = 1,     // "symmetric"
            } status;               //!< Status of the link.
            /// A value between 0 and 7 specifying the node's willingness to carry traffic on behalf of other nodes.
            uint8_t willingness;
        };

        static inline bool
        operator==(const NeighborTuple &a, const NeighborTuple &b)
        {
            return (a.neighborMainAddr == b.neighborMainAddr && a.status == b.status && a.willingness == b.willingness);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const NeighborTuple &tuple)
        {
            os << "NeighborTuple(neighborMainAddr=" << tuple.neighborMainAddr
               << ", status=" << (tuple.status == NeighborTuple::STATUS_SYM ? "SYM" : "NOT_SYM")
               << ", willingness=" << (int)tuple.willingness << ")";
            return os;
        }

        /// \ingroup olsr
        /// A 2-hop Tuple.
        struct TwoHopNeighborTuple
        {
            /// Main address of a neighbor.
            Ipv4Address neighborMainAddr;
            /// Main address of a 2-hop neighbor with a symmetric link to nb_main_addr.
            Ipv4Address twoHopNeighborAddr;
            /// Time at which this tuple expires and must be removed.
            Time expirationTime; // previously called 'time_'
        };

        static inline std::ostream &
        operator<<(std::ostream &os, const TwoHopNeighborTuple &tuple)
        {
            os << "TwoHopNeighborTuple(neighborMainAddr=" << tuple.neighborMainAddr
               << ", twoHopNeighborAddr=" << tuple.twoHopNeighborAddr
               << ", expirationTime=" << tuple.expirationTime
               << ")";
            return os;
        }

        static inline bool
        operator==(const TwoHopNeighborTuple &a, const TwoHopNeighborTuple &b)
        {
            return (a.neighborMainAddr == b.neighborMainAddr && a.twoHopNeighborAddr == b.twoHopNeighborAddr);
        }

        /// \ingroup olsr
        /// An MPR-Selector Tuple.
        struct MprSelectorTuple
        {
            /// Main address of a node which have selected this node as a MPR.
            Ipv4Address mainAddr;
            /// Time at which this tuple expires and must be removed.
            Time expirationTime; // previously called 'time_'
        };

        static inline bool
        operator==(const MprSelectorTuple &a, const MprSelectorTuple &b)
        {
            return (a.mainAddr == b.mainAddr);
        }

        // The type "list of interface addresses"
        // typedef std::vector<nsaddr_t> addr_list_t;

        /// \ingroup olsr
        /// A Duplicate Tuple
        struct DuplicateTuple
        {
            /// Originator address of the message.
            Ipv4Address address;
            /// Message sequence number.
            uint16_t sequenceNumber;
            /// Indicates whether the message has been retransmitted or not.
            bool retransmitted;
            /// List of interfaces which the message has been received on.
            std::vector<Ipv4Address> ifaceList;
            /// Time at which this tuple expires and must be removed.
            Time expirationTime;
        };

        static inline bool
        operator==(const DuplicateTuple &a, const DuplicateTuple &b)
        {
            return (a.address == b.address && a.sequenceNumber == b.sequenceNumber);
        }

        /// \ingroup olsr
        /// A Topology Tuple
        struct TopologyTuple
        {
            /// Main address of the destination.                                      存mpr selector
            Ipv4Address destAddr;
            /// Main address of a node which is a neighbor of the destination.        存mpr
            Ipv4Address lastAddr;
            /// Sequence number.
            uint16_t sequenceNumber;
            /// Time at which this tuple expires and must be removed.
            Time expirationTime;
            //  ETX data of the link destAddr->lastAddr
            uint32_t Etx;
        };

        static inline bool
        operator==(const TopologyTuple &a, const TopologyTuple &b)
        {
            return (a.destAddr == b.destAddr && a.lastAddr == b.lastAddr && a.sequenceNumber == b.sequenceNumber);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const TopologyTuple &tuple)
        {
            os << "TopologyTuple(destAddr=" << tuple.destAddr
               << ", lastAddr=" << tuple.lastAddr
               << ", sequenceNumber=" << (int)tuple.sequenceNumber
               << ", expirationTime=" << tuple.expirationTime
               << ", Etx=" << tuple.Etx
               << ")";
            return os;
        }

        /// \ingroup olsr
        /// Association
        struct Association
        {
            Ipv4Address networkAddr; //!< IPv4 Network address.
            Ipv4Mask netmask;        //!< IPv4 Network mask.
        };

        static inline bool
        operator==(const Association &a, const Association &b)
        {
            return (a.networkAddr == b.networkAddr && a.netmask == b.netmask);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const Association &tuple)
        {
            os << "Association(networkAddr=" << tuple.networkAddr
               << ", netmask=" << tuple.netmask
               << ")";
            return os;
        }

        /// \ingroup olsr
        /// An Association Tuple
        struct AssociationTuple
        {
            /// Main address of the gateway.
            Ipv4Address gatewayAddr;
            /// Network Address of network reachable through gatewayAddr
            Ipv4Address networkAddr;
            /// Netmask of network reachable through gatewayAddr
            Ipv4Mask netmask;
            /// Time at which this tuple expires and must be removed
            Time expirationTime;
        };

        static inline bool
        operator==(const AssociationTuple &a, const AssociationTuple &b)
        {
            return (a.gatewayAddr == b.gatewayAddr && a.networkAddr == b.networkAddr && a.netmask == b.netmask);
        }

        static inline std::ostream &
        operator<<(std::ostream &os, const AssociationTuple &tuple)
        {
            os << "AssociationTuple(gatewayAddr=" << tuple.gatewayAddr
               << ", networkAddr=" << tuple.networkAddr
               << ", netmask=" << tuple.netmask
               << ", expirationTime=" << tuple.expirationTime
               << ")";
            return os;
        }

        typedef std::set<Ipv4Address> MprSet;                       //!< MPR Set type.
        typedef std::vector<MprSelectorTuple> MprSelectorSet;       //!< MPR Selector Set type.         存主地址
        typedef std::vector<LinkTuple> LinkSet;                     //!< Link Set type.                 存接口地址
        typedef std::vector<NeighborTuple> NeighborSet;             //!< Neighbor Set type.             存主地址
        typedef std::vector<TwoHopNeighborTuple> TwoHopNeighborSet; //!< 2-hop Neighbor Set type.       存主地址
        typedef std::vector<TopologyTuple> TopologySet;             //!< Topology Set type.             存主地址
        typedef std::vector<DuplicateTuple> DuplicateSet;           //!< Duplicate Set type.
        typedef std::vector<IfaceAssocTuple> IfaceAssocSet;         //!< Interface Association Set type. 存接口地址和主地址（两者不同）
        typedef std::vector<AssociationTuple> AssociationSet;       //!< Association Set type.
        typedef std::vector<Association> Associations;              //!< Association Set type.

        typedef std::map<Ipv4Address, LinkQosTuple> LinkQosSet;     //链路质量集合                        存邻居主地址

    }
} // namespace ns3, olsr

#endif /* OLSR_REPOSITORIES_H */
