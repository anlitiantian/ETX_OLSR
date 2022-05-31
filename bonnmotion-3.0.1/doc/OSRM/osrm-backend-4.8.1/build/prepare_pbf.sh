#!/bin/bash

if [ $# -ne 1 ]; then
    echo "usage: $0 <OSM PBF file basename>"
    exit 1
fi

osmfile=$1
profiledir="./cbf-routing-profiles-master"

LUA_PATH="$profiledir/lib/?.lua" ./osrm-extract -p $profiledir/foot-city.lua ${osmfile}.osm.pbf
LUA_PATH="$profiledir/lib/?.lua" ./osrm-prepare -p $profiledir/foot-city.lua ${osmfile}.osrm
