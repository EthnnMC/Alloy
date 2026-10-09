@echo off
rem Generates the renamed Minecraft and Forge jars that alloy-forge compiles against.
rem Run once after cloning, and again after a Lunar Client update.
setlocal
cd /d "%~dp0.."
call mvnw.cmd -q -pl alloy-devtools -am package -DskipTests
if errorlevel 1 exit /b 1
java -jar alloy-devtools\target\alloy-devtools.jar setup-workspace %*
