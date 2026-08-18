@echo off
setlocal

rem ---------------------------------------------------------------------------
rem  streem-setup - start backend (:8765) and frontend (:5173) together.
rem
rem    start.bat          dev mode: two windows, hot reload on both sides
rem    start.bat prod     build the fat JAR and run just that on :8765
rem
rem  Double-clicking this file works too. Close the spawned windows to stop.
rem ---------------------------------------------------------------------------

cd /d "%~dp0"

if /i "%~1"=="prod" goto prod

rem ---------- dev ----------

if not exist "frontend\node_modules" (
  echo [streem-setup] installing frontend dependencies ^(first run^)...
  pushd frontend
  call npm install || goto fail
  popd
)

echo [streem-setup] starting backend on http://localhost:8765 ...
start "streem-setup backend" /d "%~dp0backend" cmd /k gradlew.bat bootRun

echo [streem-setup] starting frontend on http://localhost:5173 ...
start "streem-setup frontend" /d "%~dp0frontend" cmd /k npm run dev

echo [streem-setup] waiting for the UI to come up...
call :waitfor 5173 60
start "" "http://localhost:5173"

echo.
echo [streem-setup] running. Close the two spawned windows to stop.
goto :eof

rem ---------- prod ----------

:prod
if not exist "frontend\node_modules" (
  echo [streem-setup] installing frontend dependencies ^(first run^)...
  pushd frontend
  call npm install || goto fail
  popd
)

echo [streem-setup] building UI into the backend's static resources...
pushd frontend
call npm run build || goto fail
popd

echo [streem-setup] building fat JAR...
pushd backend
call gradlew.bat bootJar || goto fail
popd

echo [streem-setup] starting on http://localhost:8765 ...
start "" "http://localhost:8765"
java -jar "backend\build\libs\streem-setup.jar"
goto :eof

rem ---------- helpers ----------

rem :waitfor <port> <max-seconds> - poll until something is listening
:waitfor
set "_port=%~1"
set /a _left=%~2
:waitloop
netstat -an | findstr ":%_port%" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :eof
if %_left% leq 0 (
  echo [streem-setup] port %_port% still not up - open http://localhost:%_port% manually once it is.
  goto :eof
)
rem ping, not timeout: timeout aborts when stdin is redirected
ping -n 2 127.0.0.1 >nul
set /a _left-=1
goto waitloop

:fail
echo.
echo [streem-setup] startup failed - see the error above.
exit /b 1
