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
from DataAccess import DataAccess
from Common import Hashes, runBonnmotionApp

import threading, os, getpass, datetime

class AppDeterminationDispatcher(object):
    def __init__(self, n, params):
        dict = {}
        self.threads = []                            #sequence of threads
        noofthreads = Config().readConfigEntry('noofthreads')
        
        for i in range(noofthreads):
            dict[i] = []
        
        for i in range(n):
            y = i % noofthreads
            dict[y].append(i)
            
        for i in range(noofthreads):
            thread = self.AppDeterminationThread(dict[i], params)      #create thread
            self.threads.append(thread)
            thread.start()
        
        for t in self.threads:
            t.join()                            #wait for threads to finish
            
    #######################################
    #deletes all unneeded files created
    def cleanup(self):
        for t in self.threads:
            for i in t.Seq:
                try:
                    os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i))))
                    os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))))
                    
                    for case in t.Cases:
                        for x in case['extensions']: 
                            os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputname') + str(i)) + '.' + x)
                except OSError: pass
                 
    class AppDeterminationThread(threading.Thread):   
        def __init__(self, seq, params):
            threading.Thread.__init__(self)
            self.App = params['app']
            self.Seq = seq                          #numbers of outputfiles to run with BM
            self.Cases = params['cases']
        def run(self):
            for i in self.Seq:
                for case in self.Cases:
                    paramsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i)))
                    movementsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i)))
                    outputfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputname') + str(i))    

                    if 'appparams' in case:
                        runBonnmotionApp(Config().readConfigEntry('bonnmotionvalidatepath'), i, self.App, case['appparams'])
                    else:
                        runBonnmotionApp(Config().readConfigEntry('bonnmotionvalidatepath'), i, self.App, '')
                    ordering = []
                    content = ''
                    for ext in case['extensions']:
                        ordering.append(ext)
                        #open file
                        if ext != 'NULL':
                            f = open(outputfilename + '.' + ext)
                            content = content + f.read()
                            f.close()    

                    #read parameters
                    f2 = open(paramsfilename)
                    params = f2.read()
                    f2.close()

                    p = {}
                    if 'appparams' in case:
                        p['appparameters'] = case['appparams']
                    else:
                        p['appparameters'] = ''
                    p['identifier'] = self.App
                    p['bmparamsfile'] = params
                    Hashes().calcHashes(p, content)
                    p['user'] = getpass.getuser()
                    p['datetime'] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")

                    tmp = ''
                    for y in ordering:
                        tmp = tmp + y + ','
                    p['ordering'] = tmp[0:-1]

                    #save in DB
                    DataAccess().save(p)  
