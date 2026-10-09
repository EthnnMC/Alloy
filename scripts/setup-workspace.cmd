@echo off
rem Generates the renamed Minecraft and Forge jars that alloy-forge compiles against.
rem Run once after cloning, and again after a Lunar Client update.
rem Waits for a key before closing, so a double-clicked window can be read; set ALLOY_NO_PAUSE to skip.
setlocal
cd /d "%~dp0.."
call :main %*
set "RESULT=%errorlevel%"
if not defined ALLOY_NO_PAUSE pause
exit /b %RESULT%

:main
call mvnw.cmd -q -pl alloy-devtools -am package -DskipTests
if errorlevel 1 exit /b 1
java -jar alloy-devtools\target\alloy-devtools.jar setup-workspace %*
exit /b %errorlevel%
