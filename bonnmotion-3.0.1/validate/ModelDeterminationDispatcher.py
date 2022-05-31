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
from Common import readModelnameFromParamsFile, runBonnmotionModel, Hashes, log
  
import threading, os, gzip, getpass, datetime, sys
                    
class ModelDeterminationDispatcher(object):  
    def __init__(self,n):
        dict = {}
        self.threads = []                            #sequence of threads
        noofthreads = Config().readConfigEntry('noofthreads')
        
        for i in range(noofthreads):
            dict[i] = []

        for i in range(n):
            y = i % noofthreads
            dict[y].append(i)
            
        for i in range(noofthreads):
            thread = self.ModelDeterminationThread(dict[i])      #create thread
            self.threads.append(thread)
            thread.start()
        
        for t in self.threads:
            t.join()                            #wait for threads to finish   
    
    def cleanup(self):
        for t in self.threads:
            for i in t.Seq:
                os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i))))
                os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))))
                        
    class ModelDeterminationThread(threading.Thread):
        def __init__(self, seq):
            threading.Thread.__init__(self)
            self.Seq = seq                          #numbers of outputfiles to run with BM
        def run(self):
            for i in self.Seq:
                modelname = readModelnameFromParamsFile(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))))
                
                runBonnmotionModel(Config().readConfigEntry('bonnmotionvalidatepath'), i, modelname)
                
                paramsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i)))
                movementsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i)))
                try:
                    #open movements file
                    f = gzip.open(movementsfilename, 'rb')
                    movements_content = f.read()
                    f.close()  
                except IOError as ex:
                    print >> sys.stderr, ex  
                    print >> sys.stderr, "that means bonnmotion has not generated any output"
                    print >> sys.stderr, "parameter file:"
                    fd = open(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))))
                    print >> sys.stderr, fd.read()
                    fd.close()
                    sys.exit(1);
                
                p = {}
                p['identifier'] = modelname
                #read parameters
                f2 = open(paramsfilename)
                p['bmparamsfile'] = f2.read()
                f2.close()
                #create checksum
                Hashes().calcHashes(p, movements_content)
                p['user'] = getpass.getuser()
                p['datetime'] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
                
                #save in DB
                DataAccess().save(p) 
