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
from Common import Task, log

import sqlite3, thread
from string import Template

## Encapsulate Database access
#
# @TODO thread lock required??
# better names for get, get2, get3, get4?
#
class DataAccess(object):
    connection = None
    cursor = None

    def __init__(self,):
        if Config().argsconfig['task'] in (Task.DETERMINEAPP, Task.VALIDATEAPP):
            self.connection = sqlite3.connect(Config().readConfigEntry('dbfilename_apps'))
        elif Config().argsconfig['task'] in (Task.DETERMINEMODEL, Task.VALIDATEMODEL):
            self.connection = sqlite3.connect(Config().readConfigEntry('dbfilename_models'))
        self.cursor = self.connection.cursor()
        
    def __del__(self):
        self.cursor.close()
        
    def cleartable(self, identifier):
        lock = thread.allocate_lock()
        lock.acquire()
        temp = Template('DROP TABLE IF EXISTS $tablename;')
        self.cursor.execute(temp.substitute(tablename=identifier))
        self.connection.commit()
        log("delete table: " + identifier)
        lock.release()

    #######################################
    #input: 
    #    parameters: Dictionary from which the keys will be used to create a suitable database-table
    #                (parameters['identifier'] value is used as table name. For models its the name of the model
    #                for apps the appname)
    def __createtable(self, parameters):
        lock = thread.allocate_lock()     
           
        identifier = parameters['identifier']
        del parameters['identifier']
        
        temp = Template("CREATE TABLE IF NOT EXISTS $tablename ($columns);");
        columns = " TEXT, ".join(parameters) + " TEXT"

        lock.acquire()
        self.cursor.execute(temp.substitute(tablename=identifier, columns=columns))
        self.connection.commit()
        lock.release()
        
    #######################################
    #input: 
    #    parameters: Dictionary of values which will be saved in database
    def save(self, parameters):
        identifier = parameters['identifier']
        
        lock = thread.allocate_lock()
        lock.acquire()
            
        self.__createtable(parameters)

        temp = Template("INSERT INTO $tablename ($columns) VALUES ($values);");
        
        values = ":" + ", :".join(parameters)
        columns = ", ".join(parameters)
        


        self.cursor.execute(temp.substitute(tablename=identifier, columns=columns, values=values), parameters);
        self.connection.commit()
        
        lock.release()
        
    def get(self, model, resultseqofdicts, md5, sha1):
        tmpkeys= []

        temp = "SELECT * FROM " + model
        self.cursor.execute(temp)

        for x in self.cursor.description:
            tmpkeys.append(x[0])
      
        for row in self.cursor:
            resultdict = {}
            i = 0
            tmp = {}
            for x in tmpkeys:
                if (x == "md5"):
                    md5.append(row[i])
                elif (x == "sha1"):
                    sha1.append(row[i])
                elif ((x <> "datetime") & (x <> "user")):
                    resultdict[x] = row[i]
                i+=1

            resultseqofdicts.append(resultdict)
        
        for x in resultseqofdicts:
            x["model"] = model
    
    def get2(self, model, result):
        tmpkeys= []

        temp = "SELECT * FROM " + model
        self.cursor.execute(temp)

        for x in self.cursor.description:
            tmpkeys.append(x[0])
      
        for row in self.cursor:
            resultdict = {}
            i = 0
            tmp = {}
            for x in tmpkeys:
                if ((x <> "datetime") & (x <> "user")):
                    resultdict[x] = row[i]
                i+=1

            result.append(resultdict)
        
        for x in result:
            x["model"] = model
            
    def get3(self, result):
        tablenames = []
        getalltables = 'SELECT name FROM sqlite_master WHERE type="table"'
        self.cursor.execute(getalltables)

        for row in self.cursor:
            tablenames.append(row[0])

        for tablename in tablenames:
            tmpkeys = []
            temp = "SELECT * FROM " + tablename
            self.cursor.execute(temp)
    
            for x in self.cursor.description:
                tmpkeys.append(x[0])

            for row in self.cursor:
                resultdict = {}
                i = 0
                tmp = {}
                for x in tmpkeys:
                    if ((x <> "datetime") & (x <> "user")):
                        resultdict[x] = row[i]
                    i+=1
                resultdict['model'] = tablename
                
                result.append(resultdict)
                
    def get4(self, resultseqofdicts, md5, sha1):
        tablenames = []
        getalltables = 'SELECT name FROM sqlite_master WHERE type="table"'
        self.cursor.execute(getalltables)

        for row in self.cursor:
            tablenames.append(row[0])

        for tablename in tablenames:
            tmpkeys= []
    
            temp = "SELECT * FROM " + tablename
            self.cursor.execute(temp)
    
            for x in self.cursor.description:
                tmpkeys.append(x[0])
          
            for row in self.cursor:
                resultdict = {}
                i = 0
                tmp = {}
                for x in tmpkeys:
                    if (x == "md5"):
                        md5.append(row[i])
                    elif (x == "sha1"):
                        sha1.append(row[i])
                    elif ((x <> "datetime") & (x <> "user")):
                        resultdict[x] = row[i]
                    i+=1
                resultdict["model"] = tablename
                
                resultseqofdicts.append(resultdict)