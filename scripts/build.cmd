@echo off
rem Builds every module, runs the tests and assembles the agent jar.
rem Waits for a key before closing, so a double-clicked window can be read; set ALLOY_NO_PAUSE to skip.
setlocal
cd /d "%~dp0.."
call :main
set "RESULT=%errorlevel%"
if not defined ALLOY_NO_PAUSE pause
exit /b %RESULT%

:main
if not exist workspace\repository (
  echo No workspace yet: running scripts\setup-workspace.cmd
  call :setupWorkspace
  if errorlevel 1 exit /b 1
)
call mvnw.cmd package
if errorlevel 1 exit /b 1
echo.
echo Agent jar: alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar
exit /b 0

rem Called from here, the other script must not stop for a key in the middle of the build.
:setupWorkspace
setlocal
set "ALLOY_NO_PAUSE=1"
call scripts\setup-workspace.cmd
exit /b %errorlevel%
