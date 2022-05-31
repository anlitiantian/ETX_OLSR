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
from Common import readModelnameFromParamsFile, runBonnmotionModel, Hashes, log

import threading, os, gzip
 
class ModelValidationDispatcher(object): 
    def __init__(self, n, md5, sha1):
        dict = {}
        md5dict = {}                            #md5 hashes to check against
        sha1dict = {}                           #sha1 hashes to check against
        self.threads = []                            #sequence of threads
        noofthreads = Config().readConfigEntry('noofthreads')
        
        for i in range(noofthreads):
            dict[i] = []
            md5dict[i] = []
            sha1dict[i] = []
        
        for i in range(n):
            y = i % noofthreads
            dict[y].append(i)
            md5dict[y].append(md5[i])
            sha1dict[y].append(sha1[i])
                
        for i in range(noofthreads):
            thread = self.ModelValidationThread(dict[i],md5dict[i], sha1dict[i])        #create thread
            self.threads.append(thread)
            thread.start()
        
        for t in self.threads:
            t.join()                                                        #wait for threads to finish
            
        self.cleanup()
             
        n = 0
        for t in self.threads:
            if len(t.ReturnValue) > 0:                                      #thread object reported errors
                for x in t.ReturnValue:
                    log("error. different hashes with these parameters:" + x)
                    n += 1
                
        if n > 0: 
            log(str(n) + " errors.")
            raise Exception("different hashes")
        else: log("success. hashes are identical.")        

    def cleanup(self):
        for t in self.threads:
            for i in t.Seq:
                os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i))))
                os.remove(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))))
                        
    class ModelValidationThread(threading.Thread):
        def __init__(self, seq, md5, sha1):
            threading.Thread.__init__(self)
            self.Seq = seq                          #numbers of outputfiles to run with BM
            self.Md5 = md5
            self.Sha1 = sha1
            self.ReturnValue = []
        def run(self):
            j = 0
            for i in self.Seq:
                modelname = readModelnameFromParamsFile(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))))
                
                runBonnmotionModel(Config().readConfigEntry('bonnmotionvalidatepath'), i, modelname)
                
                movementsfilename = os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputmovementsfile').replace('INDEX', str(i)))
                f = gzip.open(movementsfilename, 'rb')
                movements_content = f.read()
                f.close()    
                
                if (self.Md5[j] <> Hashes().md5(movements_content) or self.Sha1[j] <> Hashes().sha1(movements_content)):
                    f2 = open(os.path.join(Config().readConfigEntry('bonnmotionvalidatepath'), Config().readConfigEntry('tempoutputparamsfile').replace('INDEX', str(i))), 'r')
                    self.ReturnValue.append(f2.read())
                    f2.close()
                
                j += 1    
