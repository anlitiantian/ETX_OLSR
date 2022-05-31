cbf-routing-profiles
====================

Experimental routing profiles for OSRM, together with some scripts to compile and
run a site with multiple routing profiles.

Note that the bicycle and routing profiles misuse speed as a weight to give
preference to more quiet routes. Therefore, times for the routes cannot be
trusted. On routing.osm.ch, times are computed from the distance instead,
assuming that walkers and bikers maintain a fairly constant speed.

Installation
------------

* get OSRM (http://project-osrm.org) and compile.
  Currently version 0.4.5 is needed.

* get OSRM website (https://github.com/DennisSchiefer/Project-OSRM-Web)

* get OSM data, for example the planet or an extract from Geofabrik

* create configuration file for the compilation environment, see `profiles.conf.example`

* add additional profiles to OSRM website, see `WebContent/OSRM.config.js`

* adapt look and feel of OSRM website to your liking

* compile OSRM profiles:

    ./compile_profiles.sh -c your_profiles.conf

* start servers:

    ./start-servers.sh your_profiles.conf

* when updating, first recompile the profiles, then reinitialise the server:

    ./compile_profiles.sh your_profiles.conf &&
    ./start-servers.sh -c your_profiles.conf

License
-------

All scripts are hereby released to the public domain. Feel free to do whatever
you want with them.
