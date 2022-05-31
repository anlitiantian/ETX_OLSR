# Random Waypoint movement test script

# creates UDP CBR traffic
proc create-udp-traffic {id src dst start stop tti} {
	global ns_

	set udp_($id) [new Agent/UDP]
	$ns_ attach-agent $src $udp_($id)
	set null_($id) [new Agent/Null]
	$ns_ attach-agent $dst $null_($id)
	set cbr_($id) [new Application/Traffic/CBR]
	$cbr_($id) set interval_ $tti
	$cbr_($id) set random_ 1
	$cbr_($id) set maxpkts_ [expr ($stop - $start) / $tti]
	$cbr_($id) attach-agent $udp_($id)
	$ns_ connect $udp_($id) $null_($id)
	$ns_ at $start "$cbr_($id) start"
	$ns_ at $stop "$cbr_($id) stop"
}

# procedure to call after the simulation
proc finish {} {
	global ns_ tracefd_

	$ns_ flush-trace
	close $tracefd_
	exit 0
}

# ======================================================================
# Options
# ======================================================================
set opt(chan)           Channel/WirelessChannel    ;# channel type
set opt(prop)           Propagation/TwoRayGround   ;# radio-propagation model
set opt(netif)          Phy/WirelessPhy            ;# network interface type
set opt(mac)            Mac/802_11                 ;# MAC type
set opt(adhocRouting)   AODV
set opt(ifq)            Queue/DropTail/PriQueue    ;# interface queue type
set opt(ll)             LL                         ;# link layer type
set opt(ant)            Antenna/OmniAntenna        ;# antenna model
set opt(ifqlen)         50                         ;# max packet in ifq
set opt(nn)             10                         ;# number of mobile nodes
set opt(x)              520                        ;# x-dimension of the topography
set opt(y)              520                        ;# y-dimension of the topography
set opt(tr)             temp.rands
set opt(stop)           600.0                      ;# simulation time
set opt(seed)           1
set opt(movements)      movement/rwp_10.ns_movements

# =====================================================================
# Other default settings
# =====================================================================
LL set mindelay_		50us
LL set delay_			25us
LL set bandwidth_		0	;# not used

Agent/Null set sport_		0
Agent/Null set dport_		0

Agent/CBR set sport_		0
Agent/CBR set dport_		0

Queue/DropTail/PriQueue set Prefer_Routing_Protocols    1

# Unity gain, omni-directional antennas
# Set up the antennas to be centered in the node and 1.5 meters above it
Antenna/OmniAntenna set X_ 0
Antenna/OmniAntenna set Y_ 0
Antenna/OmniAntenna set Z_ 1.5
Antenna/OmniAntenna set Gt_ 1.0
Antenna/OmniAntenna set Gr_ 1.0

# Initialize the SharedMedia interface with parameters to make
# it work like the 914MHz Lucent WaveLAN DSSS radio interface
Phy/WirelessPhy set CPThresh_ 10.0
Phy/WirelessPhy set CSThresh_ 1.559e-11
Phy/WirelessPhy set RXThresh_ 2.81838e-09  ;# Set threshold for 150m   (3.65262e-10 = 250m)
Phy/WirelessPhy set Rb_ 2*1e6
Phy/WirelessPhy set Pt_ 0.2818
Phy/WirelessPhy set freq_ 914e+6
Phy/WirelessPhy set L_ 1.0


# ======================================================================
# Initialization
# ======================================================================
set ns_ [new Simulator]

$ns_ use-newtrace

puts "Seeding RNG with $opt(seed)"
ns-random $opt(seed)

set tracefd_ [open $opt(tr) w]
$ns_ trace-all $tracefd_

set chan_ [new $opt(chan)]
set topo_ [new Topography]
$topo_ load_flatgrid $opt(x) $opt(y)

create-god $opt(nn)

$ns_ node-config -adhocRouting $opt(adhocRouting) \
				-macType $opt(mac) \
				-llType $opt(ll) \
				-ifqType $opt(ifq) \
				-ifqLen $opt(ifqlen) \
				-antType $opt(ant) \
				-propType $opt(prop) \
				-phyType $opt(netif) \
				-topoInstance $topo_ \
				-agentTrace ON \
				-routerTrace ON \
				-macTrace ON \
				-movementTrace ON \
				-channel $chan_

for {set i 0} {$i < $opt(nn)} {incr i} {
	set node_($i) [$ns_ node]
	$node_($i) random-motion 0  ;# disable random motion
}

puts "Loading movement file $opt(movements)..."
source $opt(movements)

create-udp-traffic 1 $node_(0) $node_(9) 1.0 [expr $opt(stop) - 1] 1.0

for {set i 0} {$i < $opt(nn)} {incr i} {
	$ns_ at $opt(stop) "$node_($i) reset";
}

$ns_ at $opt(stop) "finish"
$ns_ at $opt(stop) "puts \"NS EXITING...\" ; $ns_ halt"

puts "Starting Simulation..."
$ns_ run
