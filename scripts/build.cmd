@echo off
rem Builds every module, runs the tests and assembles the agent jar.
setlocal
cd /d "%~dp0.."
if not exist workspace\repository (
  echo No workspace yet: running scripts\setup-workspace.cmd
  call scripts\setup-workspace.cmd
  if errorlevel 1 exit /b 1
)
call mvnw.cmd package
if errorlevel 1 exit /b 1
echo.
echo Agent jar: alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar
