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
from Common import InputDirOrFile

import os
import string
from copy import copy

DELIMITERSETS = ';'
DELIMITERSTEPS = ':'

class BonnmotionParamsGenerator(object):
    def setApptestFilename(self, apptestFilename):
        self.apptestFilename = apptestFilename

    def setModeltestFilename(self, modeltestFilename):
        self.modeltestFilename = modeltestFilename
    
    def createBonnmotionParamsFiles(self):
        testcases = self._readInputFile()
        self._createParamFilesforBM(testcases)

    ##
    #input: string (e.g. "1:9:2")
    #output: array of steps. (e.g. [1, 3, 5, 7, 9])
    def _calcVariableSteps(self, variablestr):
        tmp = variablestr.split(DELIMITERSTEPS)
        for x in tmp: x.strip()
        begin = float(tmp[0]) if '.' in tmp[0] else int(tmp[0])
        end = float(tmp[1]) if '.' in tmp[1] else int(tmp[1])
        step = float(tmp[2]) if '.' in tmp[2] else int(tmp[2])

        S = []
        y = begin
        while y <= end:
            S.append(y)
            y += step

        return S
    
    ##
    #input: string (e.g. "1;2;3")
    #output: array of the set. (e.g. [1, 2, 3])   
    def _calcVariableSets(self, variablestr):
        tmp = variablestr.split(DELIMITERSETS)
        for x in tmp: x.strip()
        
        return tmp
    
    ##
    #input: 
    #        modeltestFilename: string with modeltestFilename
    #        constantparams: empty dictionary to be filled with constant parameters
    #        variableparams: empty dictionary to be filled with variable parameters
    #
    def _readInputFile(self):
        inputfiledict = {}
        testcase = 0 ##default testcase
        testcasearray = []

        f = open(self.modeltestFilename)

        for line in f:
            if "[testcase]" in line.lower():
                testcasearray.append(inputfiledict.copy())
                inputfiledict.clear()
            elif "=" in line:
                key, inputfiledict[key] = (s.strip() for s in line.split("="))             
        
        testcasearray.append(inputfiledict.copy())        
        f.close()

        self.modelname = testcasearray[0]['model']
         
        result = []     
        constantparams = {}
        variableparams = {}          
        
        for y in testcasearray:
            for x in testcasearray[testcase]:  
                if "{" in testcasearray[testcase][x]:
                    if DELIMITERSTEPS in testcasearray[testcase][x]:                     #parameter is variable
                        variableparams[x] = self._calcVariableSteps(testcasearray[testcase][x][1:-1])
                    elif DELIMITERSETS in testcasearray[testcase][x]:                   #parameter is a set
                        variableparams[x] = self._calcVariableSets(testcasearray[testcase][x][1:-1])
                    elif '$' in testcasearray[testcase][x]:
                        constantparams[x] = os.path.join(os.path.dirname(self.modeltestFilename), testcasearray[testcase][x][3:])
                    else:
                        variableparams[x] = [testcasearray[testcase][x][1:-1]]
                else:                                               #parameter is constant
                    constantparams[x] = testcasearray[testcase][x] 
            
            result.append((constantparams.copy(), variableparams.copy()))
            testcase += 1 
            constantparams = result[0][0].copy()  ##default case
            variableparams = result[0][1].copy()  ##default case        
        return result
                
    ## creates all parameter combinations and safes them to .params files
    #input: 
    #        constantparams: dictionary of constant parameters
    #        variableparams: dictionary of variable parameters
    #output: 
    #        n: number of outputfiles created
    def _createParamFilesforBM(self, testcases):
        n = 0
        
        for testcase in testcases:
            (constantparams, variableparams) = testcase
            
            for x in variableparams: 
                constantparams[x] = str(variableparams[x][0])
            
            if (len(variableparams) == 0):                      # if there are no variable params there is only one constant
                self._writeParametersToFile(constantparams, os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', '0')))
                self.noOfFilesCreated = 1  
                return        
            
        # tool to create the cross product of all possible values
        import itertools
        keys =  variableparams.keys()
        values =  variableparams.values()
        # go through every combination and add it to the constant parameters and create a param file
        for element in itertools.product(*values):
            output = constantparams.copy()
            cnt = 0;
            for key in keys:
                output[key] = str(element[cnt])
                cnt = cnt + 1
            self._writeParametersToFile(output, os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(n))))     
            n += 1
        self.noOfFilesCreated = n
    
    ##
    #input: 
    #        params: dictionary of parameters
    #        modeltestFilename: string with modeltestFilename 
    def _writeParametersToFile(self, params, filename):
        f = open(filename, 'w')
        for x in params:
            f.write(x + '=' + params[x] + '\n')
        f.close()
 
    ##
    #    result: array of dictionaries of parameters (representing testcases)
    def parseApptestFile(self):
        testcase = 0
        result = {}       
        result['cases'] = []
        result['cases'].append({})
        f = open(self.apptestFilename, 'r')
        
        for line in f:
            if '=' in line:
                key, value = (s.strip() for s in line.split("="))
                if key == 'app':
                    result['app'] = value
                elif key == 'paramsfile':
                    result['paramsfile'] = value
                    if os.path.isfile(result['paramsfile']):
                        result['inputDirOrFile'] = InputDirOrFile.FILE
                    elif os.path.isdir(result['paramsfile']):
                        result['inputDirOrFile'] = InputDirOrFile.DIRECTORY
                elif key == 'extensions':
                    result['cases'][testcase]['extensions'] = self._calcVariableSets(value[1:-1])
                elif key == 'appparams':
                    if '$' in value:
                        value = string.replace(value, '$', os.path.join(os.getcwd(), os.path.dirname(self.apptestFilename)) + os.sep)
                    if '{&}' in value:
                        value = string.replace(value, '{&}', Config().readConfigEntry('bonnmotionvalidatepath'))
                    result['cases'][testcase][key] = value
                else:
                    raise Exception("invalid key provided in apptest file: " + line)
            elif "[testcase]" in line.lower():
                testcase += 1
                result['cases'].append({})
                if testcase > 1:
                    if 'extensions' not in result['cases'][testcase-1]:
                        result['cases'][testcase-1]['extensions'] = result['cases'][0]['extensions']

        if testcase > 0:
            if 'extensions' not in result['cases'][testcase]:
                result['cases'][testcase]['extensions'] = result['cases'][0]['extensions']
        f.close()

        return result
