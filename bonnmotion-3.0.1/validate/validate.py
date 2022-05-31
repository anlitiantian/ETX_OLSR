#!/usr/bin/python

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

#
# TODO:
#    compare only hashes that are actually saved in the DB (if only MD5 OR sha1 value exists)
#    throw exceptions
#

from BonnmotionParamsGenerator import BonnmotionParamsGenerator
from DataAccess import DataAccess
from AppValidationDispatcher import AppValidationDispatcher
from BonnmotionDispatcher import BonnmotionDispatcher
from AppDeterminationDispatcher import AppDeterminationDispatcher
from ModelDeterminationDispatcher import ModelDeterminationDispatcher
from ModelValidationDispatcher import ModelValidationDispatcher
from Config import Config 
from Common import Task, OutputPolicy, log, InputDirOrFile

import os, sys, getopt
                    
def parseScriptArguments(args): 
    result = {}
    result['delete'] = False
    
    try:
        opts, args = getopt.getopt(args, 'hdmMaA', ['help', 'determine-model', 'delete', 'validate-model', 'determine-app', 'validate-app'])
    except getopt.GetoptError, err:
        printHelp()
        print str(err) # will print something like "option -xy not recognized"
        sys.exit(1)

    if opts == []:
        printHelp()
        sys.exit(1)
    
    for o, a in opts:
        if o in ('-h', '--help'):
            printHelp()
            sys.exit(1)
        elif o in ('-m', '--determine-model'):
            if ('-d', '') in opts or ('--delete', '') in opts or ('-d') in args or ('--delete') in args:
                result['task'] = Task.DETERMINEMODEL
                result['delete'] = True
            else:
                result['task'] = Task.DETERMINEMODEL
        elif o in ('-M', '--validate-model'):
            result['task'] = Task.VALIDATEMODEL
        elif o in ('-a', '--determine-app'):
            if ('-d', '') in opts or ('--delete', '') in opts or ('-d') in args or ('--delete') in args:
                result['task'] = Task.DETERMINEAPP
                result['delete'] = True
            else:
                result['task'] = Task.DETERMINEAPP
        elif o in ('-A', '--validate-app'):
            result['task'] = Task.VALIDATEAPP
            
    if result['task'] in (Task.DETERMINEAPP, Task.DETERMINEMODEL):        
        if os.path.isfile(args[0]):
            result['inputDirOrFile'] = InputDirOrFile.FILE
        elif os.path.isdir(args[0]):
            result['inputDirOrFile'] = InputDirOrFile.DIRECTORY
        else:
            print 'input file or directory not found or not valid'
            print 'you provided: ' + args[0]
            sys.exit(1)
    
    if args == []:
        if ('-m', '') in opts or ('--determine-model', '') in opts:
            print 'No modeltestFilename with model parameters provided'
            sys.exit(1)    
                    
        if ('-a', '') in opts or ('--determine-app', '') in opts:
            print 'No apptestFilename with app parameters provided'
            sys.exit(1)
            
        if ('-M', '') in opts or ('--validate-model', '') in opts:
            print 'No model to validate provided'
            print 'validating all saved scenarios'
                        
        if ('-A', '') in opts or ('--validate-app', '') in opts:
            print 'No app to validate provided'
            print 'validating all saved scenarios'
    else:
        ## this is a modeltest file, a modeltest path, a model name or an app name     
        result['arg'] = args[0]
    return result

def printHelp(): 
    print """A validate script for Bonnmotion. Usage:
    
    -m, --determine-model [-d, --delete] <filename/folder>
    write hash values into database
        [-d, --delete] : (optional) delete saved hash values of this model from the database
        <filename/folder> : filename of modeltest file, or folder with one or more modeltest files
    
    -M, --validate-model [modelname]
    validate BonnMotion with hash values saved in database
        [modelname] : (optional) name of the model to validate
                      if no model provided all saved models will be validated 
    
    -a, --determine-app [-d, --delete] <filename/folder>
    write hash values into database
        [-d, --delete] : (optional) delete saved hash values of this app from the database
        <filename/folder> : filename of apptest file, or folder with one or more apptest files
    
    -A, --validate-app [appname]
    validate BonnMotion with hash values saved in database
        [appname] : (optional) name of the app to validate
                    if no app provided all saved apps will be validated
"""
   
def listOfModelTestFiles(isDirOrFile):   
    result = []
    if isDirOrFile is InputDirOrFile.FILE:    
        result.append(Config().argsconfig['arg'])
    else:
        for file in os.listdir(Config().argsconfig['arg']):
            if file.endswith('.modeltest'):
                result.append(os.path.join(Config().argsconfig['arg'], file))
    return result

def listOfAppTestFiles(isDirOrFile):   
    result = []
    if isDirOrFile is InputDirOrFile.FILE:    
        result.append(Config().argsconfig['arg'])
    else:
        for file in os.listdir(Config().argsconfig['arg']):
            if file.endswith('.apptest'):
                result.append(os.path.join(Config().argsconfig['arg'], file))
    return result
    
def main():
    config = Config()  
    config.setArgsConfig(parseScriptArguments(sys.argv[1:]))
    
    if config.argsconfig['task'] is Task.DETERMINEMODEL:
        for filename in listOfModelTestFiles(Config().argsconfig['inputDirOrFile']):
            generator = BonnmotionParamsGenerator()
            generator.setModeltestFilename(filename)
            generator.createBonnmotionParamsFiles()
            if config.argsconfig['delete'] == True:
                DataAccess().cleartable(generator.modelname)
            log("starting model-determination of " + generator.modelname)
            md = ModelDeterminationDispatcher(generator.noOfFilesCreated)
            log("done. " + str(generator.noOfFilesCreated) + " hashes saved.")
            md.cleanup()           
    elif config.argsconfig['task'] is Task.VALIDATEMODEL:
        md5 = []
        sha1 = []
        result = []
        
        if config.argsconfig.has_key('arg'):
            DataAccess().get(config.argsconfig['arg'], result, md5, sha1)                  #get parameters and hashes from database
            log("starting model-validation of " + config.argsconfig['arg'])
        else:
            DataAccess().get4(result, md5, sha1)                  
                    
        n = 0
        for x in result:
            f = open(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), config.readConfigEntry('tempoutputparamsfile').replace('INDEX', str(n))), 'w')
            f.write(x['bmparamsfile']) 
            f.close()
            n += 1

        mv = ModelValidationDispatcher(n,md5,sha1)
        log("done. " + str(n) + " hashes checked.")     
    elif config.argsconfig['task'] is Task.DETERMINEAPP:
        for filename in listOfAppTestFiles(config.argsconfig['inputDirOrFile']):
            generator = BonnmotionParamsGenerator()
            generator.setApptestFilename(filename)
            params = generator.parseApptestFile()

            if config.argsconfig['delete'] == True:
                DataAccess().cleartable(params['app']) ##APP-DATATABLE
                
            if params['inputDirOrFile'] is InputDirOrFile.FILE:
                generator2 = BonnmotionParamsGenerator()
                generator2.setModeltestFilename(params['paramsfile'])
                generator2.createBonnmotionParamsFiles()  
                log("starting app determination: " + filename + ", modeltest: " + params['paramsfile'])    
                bmd = BonnmotionDispatcher(generator2.noOfFilesCreated, config.readConfigEntry('bonnmotionvalidatepath'))
                add = AppDeterminationDispatcher(generator2.noOfFilesCreated, params)
                log("done")
                add.cleanup()
            elif params['inputDirOrFile'] is InputDirOrFile.DIRECTORY:
                for file in os.listdir(params['paramsfile']):
                    if file.endswith('.modeltest'):
                        generator2 = BonnmotionParamsGenerator()
                        generator2.setModeltestFilename(os.path.join(params['paramsfile'], file))
                        generator2.createBonnmotionParamsFiles()
                        log("starting app determination: " + filename + ", modeltest: " + file)
                        bmd = BonnmotionDispatcher(generator2.noOfFilesCreated, config.readConfigEntry('bonnmotionvalidatepath'))
                        add = AppDeterminationDispatcher(generator2.noOfFilesCreated, params)
                        add.cleanup()        
                        log("done")        
    elif config.argsconfig['task'] is Task.VALIDATEAPP:      
        result = []
        if config.argsconfig.has_key('arg'):
            DataAccess().get2(config.argsconfig['arg'], result)                       #get parameters and hashes from database
            log("starting app-validation of " + config.argsconfig['arg'])
        else:
            DataAccess().get3(result)
            
        n = 0
        for x in result:
            f = open(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), config.readConfigEntry('tempoutputparamsfile').replace('INDEX', str(n))), 'w')
            f.write(x['bmparamsfile'])
            f.close()
            n += 1
        bmd = BonnmotionDispatcher(n, config.readConfigEntry('bonnmotionvalidatepath'))
        AppValidationDispatcher(n, result).cleanup()
        log("done. " + str(n) + " hashes checked.")
    
if __name__ == "__main__":
    error = False
    try:
        main()
    except Exception as ex:
        error = True
        print >> sys.stderr, ex
        
    if error:
        sys.exit(1)
    else:
        sys.exit(0)
