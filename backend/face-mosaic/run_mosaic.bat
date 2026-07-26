@echo off
setlocal
if "%~1"=="" (
  echo Usage: drag a video onto this file, or run:
  echo   run_mosaic.bat "D:\path\input.mp4"
  pause
  exit /b 2
)

set "SCRIPT_DIR=%~dp0"
"%SCRIPT_DIR%.venv\Scripts\python.exe" "%SCRIPT_DIR%face_mosaic.py" --source "%~1"
if errorlevel 1 pause
