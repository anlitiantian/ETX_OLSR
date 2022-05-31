# -*- coding: utf-8 -*-
################################################################################
## A validation script for                                                    ##
## BonnMotion - a mobility scenario generation and analysis tool              ##
## Copyright (C) 2002-2012 University of Bonn                                 ##
## Copyright (C) 2012-2016 University of Osnabrueck                           ##
##                                                                            ##
## This program is free software; you can redistribute it and/or modify       ##
## it under the terms of the GNU General Public License as published by       ##
## the Free Software Foundation; either version 2 of the License, or          ##
## (at your option) any later version.                                        ##
##                                                                            ##
## This program is distributed in the hope that it will be useful,            ##
## but WITHOUT ANY WARRANTY; without even the implied warranty of             ##
## MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              ##
## GNU General Public License for more details.                               ##
##                                                                            ##
## You should have received a copy of the GNU General Public License          ##
## along with this program; if not, write to the Free Software                ##
## Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA  ##
################################################################################

import ConfigParser

CONFIGFILENAME = 'validate.cfg'
#
# This class reads a config file and ...
# singleton..
#    @TODO error checking... (ex. are dbfiles valid...)
#    is BMPATH valid???
class Config(object):
    def __new__(type, *args):
        if not '_the_instance' in type.__dict__:
            type._the_instance = object.__new__(type)
        return type._the_instance
    
    def __init__(self):
        if not '_ready' in dir(self):
            self._ready = True
            self._configparser = ConfigParser.ConfigParser()
            self._configparser.read(CONFIGFILENAME)
            
    def setArgsConfig(self, argsconfig):
        self.argsconfig = argsconfig
    
    def readConfigEntry(self, entryname):
        if self.argsconfig is not None:
            if self.argsconfig.has_key(entryname):
                return argsconfig[entryname]
            
        try:
            return int(self._configparser.get('validate', entryname))
        except ValueError:
            return self._configparser.get('validate', entryname)