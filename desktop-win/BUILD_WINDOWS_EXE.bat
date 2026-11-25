@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   JARVIS 3D Gesture Control - Windows 11 .EXE Builder
echo ========================================================

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js is not installed. Please install Node.js LTS from https://nodejs.org/
  pause
  exit /b 1
)

echo [1/2] Installing Electron and Electron Builder...
call npm install
if errorlevel 1 (
  echo [ERROR] npm install failed.
  pause
  exit /b 1
)

echo [2/2] Building Windows 11 x64 .exe executables...
call npm run dist
if errorlevel 1 (
  echo [ERROR] Build failed.
  pause
  exit /b 1
)

echo.
echo [SUCCESS] Built in "%~dp0dist\":
echo   - JARVIS-3D-GestureControl-Win11-x64.exe (Portable)
echo   - JARVIS-3D-GestureControl-Setup-Win11-x64.exe (Installer)
explorer "%~dp0dist"
pause
