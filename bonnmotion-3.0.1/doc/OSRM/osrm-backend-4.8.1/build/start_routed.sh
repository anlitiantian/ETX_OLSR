#!/bin/bash

if [ $# -ne 3 ]; then
    echo "usage: $0 <OSRM file> <port> <threads>"
    exit 1
fi

osrmfile=$1
port=$2
threads=$3

./osrm-routed $osrmfile -i 0.0.0.0 -p $port -t $threads
