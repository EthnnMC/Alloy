@echo off
rem Installs the agent into %USERPROFILE%\.alloy and prints the JVM argument for Lunar Client.
setlocal
cd /d "%~dp0.."
set "ALLOY_HOME=%USERPROFILE%\.alloy"
if not exist alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar (
  echo The agent jar is missing: run scripts\build.cmd first.
  exit /b 1
)
if not exist "%ALLOY_HOME%\agents" mkdir "%ALLOY_HOME%\agents"
if not exist "%ALLOY_HOME%\mods" mkdir "%ALLOY_HOME%\mods"
copy /y alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar "%ALLOY_HOME%\agents\Alloy-Agent.jar" >nul
echo Agent installed: %ALLOY_HOME%\agents\Alloy-Agent.jar
echo Put Forge 1.8.9 mods in: %ALLOY_HOME%\mods
echo.
echo Lunar Client launcher, profile 1.8, advanced settings, JVM Arguments:
echo   -javaagent:%ALLOY_HOME%\agents\Alloy-Agent.jar
