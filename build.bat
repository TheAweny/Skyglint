@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\gradle.ps1" build
exit /b %errorlevel%
