/* -*- Mode:C++; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/*
 * Copyright (c) 2009 CTTC
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
 * Authors: Nicola Baldo <nbaldo@cttc.es>
 *          Sébastien Deronne <sebastien.deronne@gmail.com>
 */

#include "ns3/log.h"
#include "ns3/simulator.h"
#include "ns3/test.h"
#include "ns3/yans-wifi-phy.h"
#include "ns3/he-ru.h"
#include "ns3/wifi-psdu.h"
#include "ns3/packet.h"
#include <numeric>

using namespace ns3;

NS_LOG_COMPONENT_DEFINE ("InterferenceHelperTxDurationTest");

/**
 * \ingroup wifi-test
 * \ingroup tests
 *
 * \brief Tx Duration Test
 */
class TxDurationTest : public TestCase
{
public:
  TxDurationTest ();
  virtual ~TxDurationTest ();
  virtual void DoRun (void);


private:
  /**
   * Check if the payload tx duration returned by InterferenceHelper
   * corresponds to a known value of the pay
   *
   * @param size size of payload in octets (includes everything after the PHY header)
   * @param payloadMode the WifiMode used for the transmission
   * @param channelWidth the channel width used for the transmission (in MHz)
   * @param guardInterval the guard interval duration used for the transmission (in nanoseconds)
   * @param preamble the WifiPreamble used for the transmission
   * @param knownDuration the known duration value of the transmission
   *
   * @return true if values correspond, false otherwise
   */
  bool CheckPayloadDuration (uint32_t size, WifiMode payloadMode, uint16_t channelWidth, uint16_t guardInterval, WifiPreamble preamble, Time knownDuration);

  /**
   * Check if the overall tx duration returned by InterferenceHelper
   * corresponds to a known value of the pay
   *
   * @param size size of payload in octets (includes everything after the PHY header)
   * @param payloadMode the WifiMode used for the transmission
   * @param channelWidth the channel width used for the transmission (in MHz)
   * @param guardInterval the guard interval duration used for the transmission (in nanoseconds)
   * @param preamble the WifiPreamble used for the transmission
   * @param knownDuration the known duration value of the transmission
   *
   * @return true if values correspond, false otherwise
   */
  bool CheckTxDuration (uint32_t size, WifiMode payloadMode, uint16_t channelWidth, uint16_t guardInterval, WifiPreamble preamble, Time knownDuration);

  /**
   * Check if the overall Tx duration returned by WifiPhy for a HE MU PPDU
   * corresponds to a known value
   *
   * @param sizes the list of PSDU sizes for each station in octets
   * @param userInfos the list of HE MU specific user transmission parameters
   * @param channelWidth the channel width used for the transmission (in MHz)
   * @param guardInterval the guard interval duration used for the transmission (in nanoseconds)
   * @param knownDuration the known duration value of the transmission
   *
   * @return true if values correspond, false otherwise
   */
  static bool CheckHeMuTxDuration (std::list<uint32_t> sizes, std::list<HeMuUserInfo> userInfos,
                                   uint16_t channelWidth, uint16_t guardInterval,
                                   Time knownDuration);

  /**
   * Calculate the overall Tx duration returned by WifiPhy for list of sizes.
   * A map of WifiPsdu indexed by STA-ID is built using the provided lists
   * and handed over to the corresponding SU/MU WifiPhy Tx duration computing
   * method.
   * Note that provided lists should be of same size.
   *
   * @param sizes the list of PSDU sizes for each station in octets
   * @param staIds the list of STA-IDs of each station
   * @param txVector the TXVECTOR used for the transmission of the PPDU
   * @param band the selected wifi PHY band
   *
   * @return the overall Tx duration for the list of sizes (SU or MU PPDU)
   */
  static Time CalculateTxDurationUsingList (std::list<uint32_t> sizes, std::list<uint16_t> staIds,
                                            WifiTxVector txVector, WifiPhyBand band);
};

TxDurationTest::TxDurationTest ()
  : TestCase ("Wifi TX Duration")
{
}

TxDurationTest::~TxDurationTest ()
{
}

bool
TxDurationTest::CheckPayloadDuration (uint32_t size, WifiMode payloadMode, uint16_t channelWidth, uint16_t guardInterval, WifiPreamble preamble, Time knownDuration)
{
  WifiTxVector txVector;
  txVector.SetMode (payloadMode);
  txVector.SetPreambleType (preamble);
  txVector.SetChannelWidth (channelWidth);
  txVector.SetGuardInterval (guardInterval);
  txVector.SetNss (1);
  txVector.SetStbc (0);
  txVector.SetNess (0);
  WifiPhyBand band = WIFI_PHY_BAND_2_4GHZ;
  Ptr<YansWifiPhy> phy = CreateObject<YansWifiPhy> ();
  if (payloadMode.GetModulationClass () == WIFI_MOD_CLASS_OFDM
      || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HT
      || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_VHT
      || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HE)
    {
      band = WIFI_PHY_BAND_5GHZ;
    }
  Time calculatedDuration = phy->GetPayloadDuration (size, txVector, band);
  if (calculatedDuration != knownDuration)
    {
      std::cerr << "size=" << size
                << " mode=" << payloadMode
                << " channelWidth=" << channelWidth
                << " guardInterval=" << guardInterval
                << " datarate=" << payloadMode.GetDataRate (channelWidth, guardInterval, 1)
                << " known=" << knownDuration
                << " calculated=" << calculatedDuration
                << std::endl;
      return false;
    }
  if (payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HT || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HE)
    {
      //Durations vary depending on frequency; test also 2.4 GHz (bug 1971)
      band = WIFI_PHY_BAND_2_4GHZ;
      calculatedDuration = phy->GetPayloadDuration (size, txVector, band);
      knownDuration += MicroSeconds (6);
      if (calculatedDuration != knownDuration)
        {
          std::cerr << "size=" << size
                    << " mode=" << payloadMode
                    << " channelWidth=" << channelWidth
                    << " guardInterval=" << guardInterval
                    << " datarate=" << payloadMode.GetDataRate (channelWidth, guardInterval, 1)
                    << " known=" << knownDuration
                    << " calculated=" << calculatedDuration
                    << std::endl;
          return false;
        }
    }
  return true;
}

bool
TxDurationTest::CheckTxDuration (uint32_t size, WifiMode payloadMode, uint16_t channelWidth, uint16_t guardInterval, WifiPreamble preamble, Time knownDuration)
{
  WifiTxVector txVector;
  txVector.SetMode (payloadMode);
  txVector.SetPreambleType (preamble);
  txVector.SetChannelWidth (channelWidth);
  txVector.SetGuardInterval (guardInterval);
  txVector.SetNss (1);
  txVector.SetStbc (0);
  txVector.SetNess (0);
  WifiPhyBand band = WIFI_PHY_BAND_2_4GHZ;
  Ptr<YansWifiPhy> phy = CreateObject<YansWifiPhy> ();
  if (payloadMode.GetModulationClass () == WIFI_MOD_CLASS_OFDM
      || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HT
      || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_VHT
      || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HE)
    {
      band = WIFI_PHY_BAND_5GHZ;
    }
  Time calculatedDuration = phy->CalculateTxDuration (size, txVector, band);
  Time calculatedDurationUsingList = CalculateTxDurationUsingList (std::list<uint32_t> {size}, std::list<uint16_t> {SU_STA_ID},
                                                                   txVector, band);
  if (calculatedDuration != knownDuration || calculatedDuration != calculatedDurationUsingList)
    {
      std::cerr << "size=" << size
                << " mode=" << payloadMode
                << " channelWidth=" << +channelWidth
                << " guardInterval=" << guardInterval
                << " datarate=" << payloadMode.GetDataRate (channelWidth, guardInterval, 1)
                << " preamble=" << preamble
                << " known=" << knownDuration
                << " calculated=" << calculatedDuration
                << " calculatedUsingList=" << calculatedDurationUsingList
                << std::endl;
      return false;
    }
  if (payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HT || payloadMode.GetModulationClass () == WIFI_MOD_CLASS_HE)
    {
      //Durations vary depending on frequency; test also 2.4 GHz (bug 1971)
      band = WIFI_PHY_BAND_2_4GHZ;
      calculatedDuration = phy->CalculateTxDuration (size, txVector, band);
      calculatedDurationUsingList = CalculateTxDurationUsingList (std::list<uint32_t> {size}, std::list<uint16_t> {SU_STA_ID},
                                                                  txVector, band);
      knownDuration += MicroSeconds (6);
      if (calculatedDuration != knownDuration || calculatedDuration != calculatedDurationUsingList)
        {
          std::cerr << "size=" << size
                    << " mode=" << payloadMode
                    << " channelWidth=" << channelWidth
                    << " guardInterval=" << guardInterval
                    << " datarate=" << payloadMode.GetDataRate (channelWidth, guardInterval, 1)
                    << " preamble=" << preamble
                    << " known=" << knownDuration
                    << " calculated=" << calculatedDuration
                    << " calculatedUsingList=" << calculatedDurationUsingList
                    << std::endl;
          return false;
        }
    }
  return true;
}

bool
TxDurationTest::CheckHeMuTxDuration (std::list<uint32_t> sizes, std::list<HeMuUserInfo> userInfos,
                                     uint16_t channelWidth, uint16_t guardInterval,
                                     Time knownDuration)
{
  NS_ASSERT (sizes.size () == userInfos.size () && sizes.size () > 1);
  NS_ABORT_MSG_IF (channelWidth < std::accumulate (std::begin (userInfos), std::end (userInfos), 0,
                                                   [](const uint16_t prevBw, const HeMuUserInfo &info)
                                                   { return prevBw + HeRu::GetBandwidth (info.ru.ruType); }),
                   "Cannot accommodate all the RUs in the provided band"); //MU-MIMO (for which allocations use the same RU) is not supported
  WifiTxVector txVector;
  txVector.SetPreambleType (WIFI_PREAMBLE_HE_MU);
  txVector.SetChannelWidth (channelWidth);
  txVector.SetGuardInterval (guardInterval);
  txVector.SetStbc (0);
  txVector.SetNess (0);
  std::list<uint16_t> staIds;
  uint16_t staId = 1;
  for (const auto & userInfo : userInfos)
    {
      txVector.SetHeMuUserInfo (staId, userInfo);
      staIds.push_back (staId++);
    }
  Ptr<YansWifiPhy> phy = CreateObject<YansWifiPhy> ();
  std::list<WifiPhyBand> testedBands {WIFI_PHY_BAND_5GHZ, WIFI_PHY_BAND_2_4GHZ}; //Durations vary depending on frequency; test also 2.4 GHz (bug 1971)
  for (auto & testedBand : testedBands)
    {
      if (testedBand == WIFI_PHY_BAND_2_4GHZ)
        {
          knownDuration += MicroSeconds (6);
        }
      Time calculatedDuration = NanoSeconds (0);
      uint32_t longuestSize = 0;
      auto iterStaId = staIds.begin ();
      for (auto & size : sizes)
        {
          Time ppduDurationForSta = phy->CalculateTxDuration (size, txVector, testedBand, *iterStaId);
          if (ppduDurationForSta > calculatedDuration)
            {
              calculatedDuration = ppduDurationForSta;
              staId = *iterStaId;
              longuestSize = size;
            }
          ++iterStaId;
        }
      Time calculatedDurationUsingList = CalculateTxDurationUsingList (sizes, staIds, txVector, testedBand);
      if (calculatedDuration != knownDuration || calculatedDuration != calculatedDurationUsingList)
        {
          std::cerr << "size=" << longuestSize
                    << " band=" << testedBand
                    << " staId=" << staId
                    << " nss=" << +txVector.GetNss (staId)
                    << " mode=" << txVector.GetMode (staId)
                    << " channelWidth=" << channelWidth
                    << " guardInterval=" << guardInterval
                    << " datarate=" << txVector.GetMode (staId).GetDataRate (channelWidth, guardInterval, txVector.GetNss (staId))
                    << " known=" << knownDuration
                    << " calculated=" << calculatedDuration
                    << " calculatedUsingList=" << calculatedDurationUsingList
                    << std::endl;
          return false;
        }
    }
  return true;
}

Time
TxDurationTest::CalculateTxDurationUsingList (std::list<uint32_t> sizes, std::list<uint16_t> staIds,
                                              WifiTxVector txVector, WifiPhyBand band)
{
  NS_ASSERT (sizes.size () == staIds.size ());
  WifiConstPsduMap psduMap;
  auto itStaId = staIds.begin ();
  WifiMacHeader hdr;
  hdr.SetType (WIFI_MAC_CTL_ACK); //so that size may not be empty while being as short as possible
  for (auto & size : sizes)
    {
      // MAC header and FCS are to deduce from size
      psduMap[*itStaId++] = Create<WifiPsdu> (Create<Packet> (size - hdr.GetSerializedSize () - 4), hdr);
    }
  return WifiPhy::CalculateTxDuration (psduMap, txVector, band);
}

void
TxDurationTest::DoRun (void)
{
  bool retval = true;

  //IEEE Std 802.11-2007 Table 18-2 "Example of LENGTH calculations for CCK"
  retval = retval
    && CheckPayloadDuration (1023, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (744))
    && CheckPayloadDuration (1024, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (745))
    && CheckPayloadDuration (1025, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (746))
    && CheckPayloadDuration (1026, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (747));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11b CCK duration failed");

  //Similar, but we add PHY preamble and header durations
  //and we test different rates.
  //The payload durations for modes other than 11mbb have been
  //calculated by hand according to  IEEE Std 802.11-2007 18.2.3.5
  retval = retval
    && CheckTxDuration (1023, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (744 + 96))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (745 + 96))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (746 + 96))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (747 + 96))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (744 + 192))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (745 + 192))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (746 + 192))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (747 + 192))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (1488 + 96))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (1490 + 96))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (1491 + 96))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (1493 + 96))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (1488 + 192))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (1490 + 192))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (1491 + 192))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate5_5Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (1493 + 192))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (4092 + 96))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (4096 + 96))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (4100 + 96))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (4104 + 96))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (4092 + 192))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (4096 + 192))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (4100 + 192))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate2Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (4104 + 192))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (8184 + 192))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (8192 + 192))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (8200 + 192))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_SHORT, MicroSeconds (8208 + 192))
    && CheckTxDuration (1023, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (8184 + 192))
    && CheckTxDuration (1024, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (8192 + 192))
    && CheckTxDuration (1025, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (8200 + 192))
    && CheckTxDuration (1026, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (8208 + 192));

  //values from http://mailman.isi.edu/pipermail/ns-developers/2009-July/006226.html
  retval = retval && CheckTxDuration (14, WifiPhy::GetDsssRate1Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (304));

  //values from http://www.oreillynet.com/pub/a/wireless/2003/08/08/wireless_throughput.html
  retval = retval
    && CheckTxDuration (1536, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (1310))
    && CheckTxDuration (76, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (248))
    && CheckTxDuration (14, WifiPhy::GetDsssRate11Mbps (), 22, 800, WIFI_PREAMBLE_LONG, MicroSeconds (203));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11b duration failed");

  //802.11a durations
  //values from http://www.oreillynet.com/pub/a/wireless/2003/08/08/wireless_throughput.html
  retval = retval
    && CheckTxDuration (1536, WifiPhy::GetOfdmRate54Mbps (), 20, 800, WIFI_PREAMBLE_LONG, MicroSeconds (248))
    && CheckTxDuration (76, WifiPhy::GetOfdmRate54Mbps (), 20, 800, WIFI_PREAMBLE_LONG, MicroSeconds (32))
    && CheckTxDuration (14, WifiPhy::GetOfdmRate54Mbps (), 20, 800, WIFI_PREAMBLE_LONG, MicroSeconds (24));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11a duration failed");

  //802.11g durations are same as 802.11a durations but with 6 us signal extension
  retval = retval
    && CheckTxDuration (1536, WifiPhy::GetErpOfdmRate54Mbps (), 20, 800, WIFI_PREAMBLE_LONG, MicroSeconds (254))
    && CheckTxDuration (76, WifiPhy::GetErpOfdmRate54Mbps (), 20, 800, WIFI_PREAMBLE_LONG, MicroSeconds (38))
    && CheckTxDuration (14, WifiPhy::GetErpOfdmRate54Mbps (), 20, 800, WIFI_PREAMBLE_LONG, MicroSeconds (30));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11g duration failed");

  //802.11n durations
  retval = retval
    && CheckTxDuration (1536, WifiPhy::GetHtMcs7 (), 20, 800, WIFI_PREAMBLE_HT_MF, MicroSeconds (228))
    && CheckTxDuration (76, WifiPhy::GetHtMcs7 (), 20, 800, WIFI_PREAMBLE_HT_MF, MicroSeconds (48))
    && CheckTxDuration (14, WifiPhy::GetHtMcs7 (), 20, 800, WIFI_PREAMBLE_HT_MF, MicroSeconds (40))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs7 (), 20, 800, WIFI_PREAMBLE_HT_GF, MicroSeconds (220))
    && CheckTxDuration (76, WifiPhy::GetHtMcs7 (), 20, 800, WIFI_PREAMBLE_HT_GF, MicroSeconds (40))
    && CheckTxDuration (14, WifiPhy::GetHtMcs7 (), 20, 800, WIFI_PREAMBLE_HT_GF, MicroSeconds (32))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs0 (), 20, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (1742400))
    && CheckTxDuration (76, WifiPhy::GetHtMcs0 (), 20, 400, WIFI_PREAMBLE_HT_MF, MicroSeconds (126))
    && CheckTxDuration (14, WifiPhy::GetHtMcs0 (), 20, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (57600))
    && CheckTxDuration (1536,WifiPhy::GetHtMcs0 (), 20, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (1734400))
    && CheckTxDuration (76, WifiPhy::GetHtMcs0 (), 20, 400, WIFI_PREAMBLE_HT_GF, MicroSeconds (118))
    && CheckTxDuration (14, WifiPhy::GetHtMcs0 (), 20, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (49600))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs6 (), 20, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (226800))
    && CheckTxDuration (76, WifiPhy::GetHtMcs6 (), 20, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (46800))
    && CheckTxDuration (14, WifiPhy::GetHtMcs6 (), 20, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (39600))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs6 (), 20, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (218800))
    && CheckTxDuration (76, WifiPhy::GetHtMcs6 (), 20, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (38800))
    && CheckTxDuration (14, WifiPhy::GetHtMcs6 (), 20, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (31600))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs7 (), 40, 800, WIFI_PREAMBLE_HT_MF, MicroSeconds (128))
    && CheckTxDuration (76, WifiPhy::GetHtMcs7 (), 40, 800, WIFI_PREAMBLE_HT_MF, MicroSeconds (44))
    && CheckTxDuration (14, WifiPhy::GetHtMcs7 (), 40, 800, WIFI_PREAMBLE_HT_MF, MicroSeconds (40))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs7 (), 40, 800, WIFI_PREAMBLE_HT_GF, MicroSeconds (120))
    && CheckTxDuration (76, WifiPhy::GetHtMcs7 (), 40, 800, WIFI_PREAMBLE_HT_GF, MicroSeconds (36))
    && CheckTxDuration (14, WifiPhy::GetHtMcs7 (), 40, 800, WIFI_PREAMBLE_HT_GF, MicroSeconds (32))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs7 (), 40, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (118800))
    && CheckTxDuration (76, WifiPhy::GetHtMcs7 (), 40, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (43200))
    && CheckTxDuration (14, WifiPhy::GetHtMcs7 (), 40, 400, WIFI_PREAMBLE_HT_MF, NanoSeconds (39600))
    && CheckTxDuration (1536, WifiPhy::GetHtMcs7 (), 40, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (110800))
    && CheckTxDuration (76, WifiPhy::GetHtMcs7 (), 40, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (35200))
    && CheckTxDuration (14, WifiPhy::GetHtMcs7 (), 40, 400, WIFI_PREAMBLE_HT_GF, NanoSeconds (31600));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11n duration failed");

  //802.11ac durations
  retval = retval
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs8 (), 20, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (196))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs8 (), 20, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (48))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs8 (), 20, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs8 (), 20, 400, WIFI_PREAMBLE_VHT_SU, MicroSeconds (180))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs8 (), 20, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (46800))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs8 (), 20, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs9 (), 40, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (108))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs9 (), 40, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs9 (), 40, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs9 (), 40, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (100800))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs9 (), 40, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs9 (), 40, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs0 (), 80, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (460))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs0 (), 80, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (60))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs0 (), 80, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (44))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs0 (), 80, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (417600))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs0 (), 80, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (57600))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs0 (), 80, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (43200))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs9 (), 80, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (68))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs9 (), 80, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs9 (), 80, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs9 (), 80, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (64800))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs9 (), 80, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs9 (), 80, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs8 (), 160, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (56))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs8 (), 160, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs8 (), 160, 800, WIFI_PREAMBLE_VHT_SU, MicroSeconds (40))
    && CheckTxDuration (1536, WifiPhy::GetVhtMcs8 (), 160, 400, WIFI_PREAMBLE_VHT_SU, MicroSeconds (54))
    && CheckTxDuration (76, WifiPhy::GetVhtMcs8 (), 160, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600))
    && CheckTxDuration (14, WifiPhy::GetVhtMcs8 (), 160, 400, WIFI_PREAMBLE_VHT_SU, NanoSeconds (39600));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11ac duration failed");

  //802.11ax SU durations
  retval = retval
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 20, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (1485600))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 20, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (125600))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 20, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (71200))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 40, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (764800))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 40, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (84800))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 40, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 80, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (397600))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 80, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (71200))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 80, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 160, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (220800))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 160, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 160, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 20, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (1570400))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 20, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (130400))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 20, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (72800))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 40, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (807200))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 40, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (87200))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 40, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 80, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (418400))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 80, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (72800))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 80, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 160, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (231200))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 160, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 160, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 20, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (1740))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 20, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (140))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 20, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (76))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 40, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (892))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 40, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (92))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 40, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 80, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (460))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 80, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (76))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 80, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs0 (), 160, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (252))
    && CheckTxDuration (76, WifiPhy::GetHeMcs0 (), 160, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (14, WifiPhy::GetHeMcs0 (), 160, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 20, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (139200))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 20, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 20, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 40, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (98400))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 40, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 40, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 80, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (71200))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 80, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 80, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 160, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 160, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 160, 800, WIFI_PREAMBLE_HE_SU, NanoSeconds (57600))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 20, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (144800))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 20, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 20, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 40, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (101600))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 40, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 40, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 80, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (72800))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 80, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 80, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 160, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 160, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 160, 1600, WIFI_PREAMBLE_HE_SU, NanoSeconds (58400))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 20, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (156))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 20, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 20, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 40, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (108))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 40, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 40, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 80, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (76))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 80, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 80, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (1536, WifiPhy::GetHeMcs11 (), 160, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (76, WifiPhy::GetHeMcs11 (), 160, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60))
    && CheckTxDuration (14, WifiPhy::GetHeMcs11 (), 160, 3200, WIFI_PREAMBLE_HE_SU, MicroSeconds (60));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11ax SU duration failed");

  //802.11ax MU durations
  retval = retval
    && CheckHeMuTxDuration (std::list<uint32_t> {1536,
                                                 1536},
                            std::list<HeMuUserInfo> { {{true, HeRu::RU_242_TONE, 1}, WifiPhy::GetHeMcs0 (), 1},
                                                      {{true, HeRu::RU_242_TONE, 2}, WifiPhy::GetHeMcs0 (), 1} },
                            40, 800, NanoSeconds (1489600)) //equivalent to HE_SU for 20 MHz with extra HE-SIG-B (i.e. 4 us)
  && CheckHeMuTxDuration (std::list<uint32_t> {1536,
                                               1536},
                          std::list<HeMuUserInfo> { {{true, HeRu::RU_242_TONE, 1}, WifiPhy::GetHeMcs1 (), 1},
                                                    {{true, HeRu::RU_242_TONE, 2}, WifiPhy::GetHeMcs0 (), 1} },
                          40, 800, NanoSeconds (1489600)) //shouldn't change if first PSDU is shorter
  && CheckHeMuTxDuration (std::list<uint32_t> {1536,
                                               76},
                          std::list<HeMuUserInfo> { {{true, HeRu::RU_242_TONE, 1}, WifiPhy::GetHeMcs0 (), 1},
                                                    {{true, HeRu::RU_242_TONE, 2}, WifiPhy::GetHeMcs0 (), 1} },
                          40, 800, NanoSeconds (1489600));

  NS_TEST_EXPECT_MSG_EQ (retval, true, "an 802.11ax MU duration failed");

  Simulator::Destroy ();
}

/**
 * \ingroup wifi-test
 * \ingroup tests
 *
 * \brief Tx Duration Test Suite
 */
class TxDurationTestSuite : public TestSuite
{
public:
  TxDurationTestSuite ();
};

TxDurationTestSuite::TxDurationTestSuite ()
  : TestSuite ("wifi-devices-tx-duration", UNIT)
{
  AddTestCase (new TxDurationTest, TestCase::QUICK);
}

static TxDurationTestSuite g_txDurationTestSuite; ///< the test suite
