@echo off

REM *********************************************
REM Please configure for your enviroment

set JAVAPATH=e:\Programme\java\jdk\1.4.0_01
set BONNMOTION=H:\Eigene-Daten\Uni\AG_RN\bonnmotion\bonnmotion

REM *********************************************

echo BonnMotion - mobility scenario generation and analysis tool
echo Copyright (C) 2002-2012 University of Bonn
echo Copyright (C) 2012-2016 University of Osnabrueck 
echo.
echo This program is free software; you can redistribute it and/or modify
echo it under the terms of the GNU General Public License as published by
echo the Free Software Foundation; either version 2 of the License, or
echo (at your option) any later version."
echo.
echo This program is distributed in the hope that it will be useful,
echo but WITHOUT ANY WARRANTY; without even the implied warranty of
echo MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
echo GNU General Public License for more details.
echo.
echo You should have received a copy of the GNU General Public License
echo along with this program; if not, write to the Free Software
echo Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
echo.
echo.

set PACKAGE=edu.bonn.cs.iv.bonnmotion
set JAVA=%JAVAPATH%\bin\java.exe
set JAVAC=%JAVAPATH%\bin\javac.exe
set JAVADOC=%JAVAPATH%\bin\javadoc.exe

setlocal enableDelayedExpansion
set LIBRARYPATH=
for /r %%i in (lib\*.jar) DO set LIBRARYPATH=!LIBRARYPATH!%%i;

if not exist %JAVA% (
	echo Don't forget to configure this script.
	echo Could not find java.exe : %JAVA%
	goto Ende
)
if not exist %JAVAC% (
	echo Don't forget to configure this script.
	echo Could not find javac.exe : %JAVAC%
	goto Ende
)
if not exist %JAVADOC% (
	echo Don't forget to configure this script.
	echo Could not find javadoc.exe : %JAVACDOC%
	goto Ende
)
if not exist %BONNMOTION% (
	echo Don't forget to configure this script.
	echo Could not find BonnMotion root directory: %BONNMOTION% 
	goto Ende
)

echo Installing ...

REM bm
set FILE=bm.bat
echo %FILE%
if exist bin\%FILE% del bin\%FILE%
echo @echo off >> bin\%FILE%
echo cd %BONNMOTION% >> bin\%FILE%
echo %JAVA% -cp %BONNMOTION%\classes;%LIBRARYPATH% %PACKAGE%.run.BM %%* >> bin\%FILE%
echo cd bin >> bin\%FILE%

REM compile
set FILE=compile.bat
echo %FILE%
if exist bin\%FILE% del bin\%FILE%
echo @echo off >> bin\%FILE%
echo cd %BONNMOTION% >> bin\%FILE%
echo if not exist classes mkdir classes >> bin\%FILE%
echo echo on >> bin\%FILE%
echo for /r %%%%i in (*.java) do %JAVAC% -d classes -sourcepath src -classpath classes;%LIBRARYPATH% %%%%i >> bin\%FILE%

REM makedoc
set FILE=makedoc.bat
echo %FILE%
if exist bin\%FILE% del bin\%FILE%
echo @echo off >> bin\%FILE%
echo cd %BONNMOTION% >> bin\%FILE%
echo if not exist doc mkdir doc >> bin\%FILE%
echo %JAVADOC% -quiet -d doc -use -windowtitle "BonnMotion" -sourcepath %BONNMOTION%\src edu.bonn.cs.iv.bonnmotion edu.bonn.cs.iv.bonnmotion.apps edu.bonn.cs.iv.bonnmotion.run edu.bonn.cs.iv.bonnmotion.models >> bin\%FILE%

echo done.
echo Starting compilation ...
call bin\compile.bat
@echo off
echo compilation done ...
echo.
echo \bin\bm.bat -h
call bin\bm.bat

:Ende
