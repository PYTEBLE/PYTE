@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1
if errorlevel 1 (
  echo Bitte zuerst Node.js 22 oder neuer von https://nodejs.org/ installieren.
  pause
  exit /b 1
)
node start.mjs
if errorlevel 1 pause
