@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\gradle.ps1" runClient
exit /b %errorlevel%
