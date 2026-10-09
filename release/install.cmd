@echo off
rem Installs Alloy from this folder and shows the JVM argument for the Lunar Client launcher.
setlocal
cd /d "%~dp0"
set "ALLOY_HOME=%USERPROFILE%\.alloy"
rem A space in the path would cut the JVM argument in two: install at the root of the drive instead.
if not "%USERPROFILE%"=="%USERPROFILE: =%" set "ALLOY_HOME=%SystemDrive%\.alloy"
if not exist Alloy-Agent.jar (
  echo Alloy-Agent.jar is missing: extract the whole zip first.
  pause
  exit /b 1
)
if not exist "%ALLOY_HOME%\agents" mkdir "%ALLOY_HOME%\agents"
if not exist "%ALLOY_HOME%\mods" mkdir "%ALLOY_HOME%\mods"
rem Keep the agent that was installed: rename it back to return to it.
if exist "%ALLOY_HOME%\agents\Alloy-Agent.jar" copy /y "%ALLOY_HOME%\agents\Alloy-Agent.jar" "%ALLOY_HOME%\agents\Alloy-Agent.previous.jar" >nul
copy /y Alloy-Agent.jar "%ALLOY_HOME%\agents\Alloy-Agent.jar" >nul
if errorlevel 1 (
  echo Alloy could not be installed: close the game first, then run this file again.
  pause
  exit /b 1
)
echo Alloy is installed in %ALLOY_HOME%
echo.
echo 1. Put your Forge 1.8.9 mods in:
echo      %ALLOY_HOME%\mods
echo 2. Lunar Client launcher, Profile Settings, advanced mode, JVM Arguments: paste this line
echo      -javaagent:%ALLOY_HOME%\agents\Alloy-Agent.jar
echo 3. Launch 1.8.9. The log is %ALLOY_HOME%\logs\latest.log
echo.
pause
