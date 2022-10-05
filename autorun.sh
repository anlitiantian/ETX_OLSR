#!/bin/bash

rm -f ./resultOfDiffVel.txt

minV=(10 15 20 25 30 35 40 45)

for i in ${minV[@]}; do
    ./waf --run "scratch/test-manet-routing-compare --minVelocity=${i} --maxDistance=300"
done