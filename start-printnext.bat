@echo off
title printNext Server Runner
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\run-printnext-production.ps1" -Port 8080
pause
