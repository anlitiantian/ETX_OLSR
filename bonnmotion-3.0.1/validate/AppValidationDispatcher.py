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
from Common import log, runBonnmotionApp, Hashes

import threading, os

class AppValidationDispatcher(object):
    def __init__(self, n, rows):
        dict = []
        self.threads = []                            #sequence of threads
        noofthreads = Config().readConfigEntry('noofthreads')
        
        for i in range(noofthreads):
            dict.append([])
        
        for i in range(n):
            y = i % noofthreads
            dict[y].append(self.ThreadParameter(i, rows[i]["model"], rows[i]["appparameters"], rows[i]["ordering"], rows[i]["md5"], rows[i]["sha1"])) #append ThreadParameter instance
                
        for i in range(noofthreads):
            thread = self.AppValidationThread(dict[i])                      #create thread
            self.threads.append(thread)
            thread.start()
        
        for t in self.threads:
            t.join()                                                        #wait for threads to finish
        
        n = 0
        for t in self.threads:
            if len(t.ReturnValue) > 0:                                      #thread object reported errors
                for x in t.ReturnValue:
                    log("\nerror. different hashes with these parameters: \n" + x)
                    n += 1
                
        if n > 0: 
            log(str(n) + " errors.")
            raise Exception("different hashes")
        else: log("success. hashes are identical.")
        
    #######################################
    #deletes all unneeded files created  
    def cleanup(self):
        for t in self.threads:
            for i in t.Seq:
                os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i.no))))
                os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i.no))))
                
                for x in i.ordering.split(','): 
                    if x != 'NULL':
                        os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputname') + str(i.no)) + '.' + x)          
    
    #######################################
    #Value class. To encapsulate the parameters an AppValidationThread needs.
    class ThreadParameter:
        def __init__(self, no, identifier, appparameters, ordering, md5, sha1):
            self.no = no
            self.identifier = identifier
            self.appparameters = appparameters
            self.ordering = ordering
            self.md5 = md5
            self.sha1 = sha1
    
    class AppValidationThread(threading.Thread):
        def __init__(self, seq):
            threading.Thread.__init__(self)
            self.Seq = seq                          # Sequence of ThreadParameter-instances
            self.ReturnValue = []
        def run(self):
            j = 0
            for i in self.Seq:
                paramsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i.no)))
                movementsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i.no)))
                outputfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputname') + str(i.no))

                if len(i.appparameters) > 0:
                    runBonnmotionApp(Config().readConfigEntry('bonnmotionvalidatepath'), i.no, i.identifier, i.appparameters)
                else:
                    runBonnmotionApp(Config().readConfigEntry('bonnmotionvalidatepath'), i.no, i.identifier, '')            
                
                content = ''
                
                spliting = i.ordering.split(',')
                if len(spliting) > 1:                       #the app creates more than 1 file
                    for ext in spliting: 
                        if i.ordering != 'NULL':
                            f = open(outputfilename + '.' + ext)
                            content = content + f.read()        #concatenate content of all files created
                            f.close()
                else:                                       #the app creates only one file
                    if i.ordering != 'NULL':
                        f = open(outputfilename + '.' + i.ordering)
                        content = f.read()
                        f.close()
    
                if (i.md5 <> Hashes().md5(content) or i.sha1 <> Hashes().sha1(content)):
                    f2 = open(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i.no))), 'r')
                    if i.appparameters != '': 
                        self.ReturnValue.append('parameters of bonnmotion:\n' + f2.read() + '\nappname: ' + i.identifier + '\nparameters of the app:\n' + i.appparameters)
                    else: 
                        self.ReturnValue.append('parameters of bonnmotion:\n' + f2.read()+'\nappname: ' + i.identifier + '\nparameters of the app: none\n')
                    f2.close()       
                j += 1 
