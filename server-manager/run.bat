@echo off
setlocal
rem Minecraft server manager - Windows launcher
rem First run: creates .venv, installs Python packages, (builds the UI only if frontend\dist is missing)
cd /d "%~dp0"

rem ---- Python 3.11+ ----
set "PY="
where py >nul 2>nul && (py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)" >nul 2>nul && set "PY=py -3")
if not defined PY (
  where python >nul 2>nul && (python -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)" >nul 2>nul && set "PY=python")
)
if not defined PY (
  echo [ERROR] Python 3.11 or newer is required. Install it from https://www.python.org/downloads/ ^(check "Add to PATH"^).
  pause
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo Creating virtual environment...
  %PY% -m venv .venv || goto :fail
)
".venv\Scripts\python.exe" -m pip install -q --disable-pip-version-check -r backend\requirements.txt || goto :fail

rem ---- UI: the release zip already contains frontend\dist, so npm is NOT needed ----
if exist "frontend\dist\index.html" goto :ui_ok
echo frontend\dist is missing - building the UI (needs Node.js 20.19+ or 22.12+)...
where node >nul 2>nul || (
  echo [ERROR] Node.js is not installed. Either install Node.js LTS from https://nodejs.org/
  echo         or use the release zip, which already includes frontend\dist.
  pause
  exit /b 1
)
pushd frontend
if exist package-lock.json (call npm ci --no-audit --no-fund) else (call npm install --no-audit --no-fund)
if errorlevel 1 (popd & goto :fail)
call npm run build
if errorlevel 1 (popd & goto :fail)
popd
:ui_ok

cd backend
if not exist manager.toml (
  copy manager.example.toml manager.toml >nul
  echo Created backend\manager.toml - set the server folder, start command and RCON password, then run again.
  pause
  exit /b 0
)
if "%~1"=="" (
  "..\.venv\Scripts\python.exe" -m mcmanager run
) else (
  "..\.venv\Scripts\python.exe" -m mcmanager %*
)
exit /b %errorlevel%

:fail
echo.
echo [ERROR] Setup failed. See the messages above.
pause
exit /b 1
