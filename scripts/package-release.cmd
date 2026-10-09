@echo off
rem Assembles what a user needs (agent jar, installer, notices) next to the project, in
rem "..\Alloy release", and zips it for a GitHub release. Run scripts\build.cmd first.
rem Waits for a key before closing, so a double-clicked window can be read; set ALLOY_NO_PAUSE to skip.
setlocal
cd /d "%~dp0.."
call :main
set "RESULT=%errorlevel%"
if not defined ALLOY_NO_PAUSE pause
exit /b %RESULT%

:main
set "VERSION=0.1.0"
set "AGENT=alloy-dist\target\alloy-agent-0.1.0-SNAPSHOT.jar"
set "OUT=..\Alloy release"
set "STAGE=%OUT%\Alloy-%VERSION%"
set "ZIP=%OUT%\Alloy-%VERSION%.zip"
if not exist "%AGENT%" (
  echo The agent jar is missing: run scripts\build.cmd first.
  exit /b 1
)
rem Start from an empty folder so nothing of an older release is left behind.
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%\licenses"
copy /y "%AGENT%" "%STAGE%\Alloy-Agent.jar" >nul
copy /y release\install.cmd "%STAGE%" >nul
copy /y release\README.txt "%STAGE%" >nul
copy /y release\THIRD-PARTY.txt "%STAGE%" >nul
copy /y LICENSE "%STAGE%\LICENSE.txt" >nul
copy /y release\licenses\*.txt "%STAGE%\licenses" >nul
if exist "%ZIP%" del "%ZIP%"
rem Windows' own tar writes standard zip paths; PowerShell's Compress-Archive does not.
"%SystemRoot%\System32\tar.exe" -a -c -f "%ZIP%" -C "%STAGE%" Alloy-Agent.jar install.cmd README.txt THIRD-PARTY.txt LICENSE.txt licenses
if errorlevel 1 exit /b 1
echo Release folder: %STAGE%
echo Release zip:    %ZIP%
exit /b 0
