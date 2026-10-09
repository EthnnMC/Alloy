@echo off
rem Installs the agent into %USERPROFILE%\.alloy and prints the JVM argument for Lunar Client.
rem Waits for a key before closing, so a double-clicked window can be read; set ALLOY_NO_PAUSE to skip.
setlocal
cd /d "%~dp0.."
call :main
set "RESULT=%errorlevel%"
if not defined ALLOY_NO_PAUSE pause
exit /b %RESULT%

:main
set "ALLOY_HOME=%USERPROFILE%\.alloy"
rem A space in the path would cut the JVM argument in two: install at the root of the drive instead.
if not "%USERPROFILE%"=="%USERPROFILE: =%" set "ALLOY_HOME=%SystemDrive%\.alloy"
if not exist alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar (
  echo The agent jar is missing: run scripts\build.cmd first.
  exit /b 1
)
if not exist "%ALLOY_HOME%\agents" mkdir "%ALLOY_HOME%\agents"
if not exist "%ALLOY_HOME%\mods" mkdir "%ALLOY_HOME%\mods"
rem Keep the agent that was installed: rename it back to return to it.
if exist "%ALLOY_HOME%\agents\Alloy-Agent.jar" copy /y "%ALLOY_HOME%\agents\Alloy-Agent.jar" "%ALLOY_HOME%\agents\Alloy-Agent.previous.jar" >nul
copy /y alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar "%ALLOY_HOME%\agents\Alloy-Agent.jar" >nul
if errorlevel 1 (
  echo The agent could not be replaced: close the game first.
  exit /b 1
)
echo Agent installed: %ALLOY_HOME%\agents\Alloy-Agent.jar
echo Put Forge 1.8.9 mods in: %ALLOY_HOME%\mods
echo.
echo Lunar Client launcher, profile 1.8, advanced settings, JVM Arguments:
echo   -javaagent:%ALLOY_HOME%\agents\Alloy-Agent.jar
exit /b 0
