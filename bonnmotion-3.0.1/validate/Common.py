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

from Config import Config

import hashlib, os, datetime, getpass
from subprocess import Popen, PIPE

## InputDirOrFile enumeration
class InputDirOrFile:
    FILE = 0
    DIRECTORY = 1

## Task enumeration
class Task:
    DETERMINEMODEL = 0
    VALIDATEMODEL = 1
    DETERMINEAPP = 2
    VALIDATEAPP = 3

## OutputPolicy enumeration
class OutputPolicy:
    NONE = 0
    ONLYLOGFILE = 1
    ONLYCONSOLE = 2
    LOGFILEANDCONSOLE = 3
    
## HashPolicy enumeration
class HashPolicy:
    MD5 = 0
    SHA1 = 1
    BOTH = 2
 
#######################################
#Wrapper Class for hashes
class Hashes(object):
    def calcHashes(self, dict, data):
        if Config().readConfigEntry('hashpolicy') == HashPolicy.BOTH:
            dict['md5'] = self.md5(data)
            dict['sha1'] = self.sha1(data)
        elif Config().readConfigEntry('hashpolicy') == HashPolicy.MD5:
            dict['md5'] = self.md5(data)
        elif Config().readConfigEntry('hashpolicy') == HashPolicy.SHA1:
            dict['sha1'] = self.sha1(data)
    
    def md5(self, text): 
        return hashlib.md5(text).hexdigest()

    def sha1(self, text): 
        return hashlib.sha1(text).hexdigest()        
                
##
#input: 
#        filename: string with filename
#output: value of the parameter 'model' 
def readModelnameFromParamsFile(filename):
    f = open(filename)
    for line in f:
        if "=" in line:
            x, y = (s.strip() for s in line.split("="))
            if x == "model": return y
            
def runBonnmotionModel(path, i, modelname): 
    outputfilename = os.path.join(path, Config().readConfigEntry('tempoutputname') + str(i))
    inputfilename = os.path.join(path, Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i)))
    bmbinarypath = os.path.join(Config().readConfigEntry('bonnmotionpath'), 'bin/bm')  
    cmd = [bmbinarypath, '-f', outputfilename, '-I', inputfilename, modelname]
    if Config().readConfigEntry('bonnmotionstdout') is OutputPolicy.NONE:
        if Config().readConfigEntry('bonnmotionstderr') is OutputPolicy.ONLYCONSOLE:
            process = Popen(cmd, stdout=PIPE)
        elif Config().readConfigEntry('bonnmotionstderr') is OutputPolicy.NONE:        
            process = Popen(cmd, stdout=PIPE, stderr=PIPE)
        else:
            raise Exception("not possible right now: bonnmotionstderr=" + `Config().readConfigEntry('bonnmotionstderr')`)
    elif Config().readConfigEntry('bonnmotionstdout') is OutputPolicy.ONLYCONSOLE:
        if Config().readConfigEntry('bonnmotionstderr') is OutputPolicy.ONLYCONSOLE:
            process = Popen(cmd)
        elif Config().readConfigEntry('bonnmotionstderr') is OutputPolicy.NONE:
            process = Popen(cmd, stderr=PIPE)
        else:
            raise Exception("not possible right now: bonnmotionstderr=" + `Config().readConfigEntry('bonnmotionstderr')`)
    else:
        raise Exception("not possible right now: bonnmotionstdout=" + `Config().readConfigEntry('bonnmotionstdout')`)
    
    #run BM                   
    process.communicate()

    if process.returncode != 0:
        raise Exception("bonnmotion did not run successfully with params file: " + inputfilename)
    
def runBonnmotionApp(path, i, appname, parameters):
    bmbinarypath = os.path.join(Config().readConfigEntry('bonnmotionpath'), 'bin/bm') 
    outputfilename = os.path.join(path, Config().readConfigEntry('tempoutputname') + str(i))
    if len(parameters) > 0:
        #build arguments
        if appname == 'GPXImport':
            cmd = [bmbinarypath, appname]
        else:
            cmd = [bmbinarypath, appname, '-f', outputfilename]
        for x in parameters.strip().split(' '): cmd.append(x)

        #run BM-App with BM-Output
        if Config().readConfigEntry('bonnmotionstdout') is OutputPolicy.NONE:
            process = Popen(cmd, stdout=PIPE)#, stderr=PIPE)
        elif Config().readConfigEntry('bonnmotionstdout') is OutputPolicy.ONLYCONSOLE:
            process = Popen(cmd)

        #run BM
        process.communicate()

        if process.returncode != 0:
            raise Exception("bonnmotion did not run successfully")  
    else: 
        cmd = [bmbinarypath, appname, '-f', outputfilename]

        #run BM-App with BM-Output
        if Config().readConfigEntry('bonnmotionstdout') is OutputPolicy.NONE:
            process = Popen(cmd, stdout=PIPE, stderr=PIPE)
        elif Config().readConfigEntry('bonnmotionstdout') is OutputPolicy.ONLYCONSOLE:
            process = Popen(cmd)

        #run BM                   
        process.communicate()

        if process.returncode != 0:
            raise Exception("bonnmotion did not run successfully")

def log(text):
    if Config().readConfigEntry('showscriptoutput') == OutputPolicy.LOGFILEANDCONSOLE:
        print text
        f = open(Config().readConfigEntry('logfilename'),'a')
        str = '[' + datetime.datetime.now().strftime("%Y-%m-%d %H:%M") + '][' + getpass.getuser() + '] ' + text + '\n'
        f.write(str)
        f.close()    
    elif Config().readConfigEntry('showscriptoutput') == OutputPolicy.ONLYLOGFILE:
        f = open(Config().readConfigEntry('logfilename'),'a')
        str = '[' + datetime.datetime.now().strftime("%Y-%m-%d %H:%M") + '][' + getpass.getuser() + '] ' + text + '\n'
        f.write(str)
        f.close()    
    elif Config().readConfigEntry('showscriptoutput') == OutputPolicy.ONLYCONSOLE: 
        print text     
