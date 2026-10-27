@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\gradle.ps1" build runSmokeClient -Psmoke -PworldSmoke
exit /b %errorlevel%
