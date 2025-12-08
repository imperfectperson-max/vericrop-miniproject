@echo off
REM ============================================================================
REM VeriCrop Stop-All Script (Windows)
REM
REM This script stops all VeriCrop Docker services.
REM
REM Usage:
REM   stop-all.bat [options]
REM
REM Options:
REM   -v, --volumes    Remove volumes (deletes all data)
REM   -a, --all        Stop services from all compose files
REM   -h, --help       Show this help message
REM ============================================================================

setlocal EnableDelayedExpansion

REM Script directory
cd /d "%~dp0"

REM Default values
set "REMOVE_VOLUMES="
set "STOP_ALL=false"
set "DOCKER_COMPOSE=docker-compose"

REM Check Docker Compose and set DOCKER_COMPOSE variable
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if not errorlevel 1 (
        set "DOCKER_COMPOSE=docker compose"
    )
) else (
    set "DOCKER_COMPOSE=docker-compose"
)

REM Parse arguments
:parse_args
if "%~1"=="" goto end_parse
if /I "%~1"=="-v" set "REMOVE_VOLUMES=-v" & shift & goto parse_args
if /I "%~1"=="--volumes" set "REMOVE_VOLUMES=-v" & shift & goto parse_args
if /I "%~1"=="-a" set "STOP_ALL=true" & shift & goto parse_args
if /I "%~1"=="--all" set "STOP_ALL=true" & shift & goto parse_args
if /I "%~1"=="-h" goto show_help
if /I "%~1"=="--help" goto show_help
echo Unknown option: %~1
exit /b 1
:end_parse

REM Print banner
echo.
echo ===============================================================
echo        VeriCrop Stop-All Script (Windows)
echo ===============================================================
echo.

if defined REMOVE_VOLUMES (
    echo ⚠ Warning: Volumes will be removed. All data will be deleted!
    echo.
)

echo Stopping VeriCrop services...
echo.

if "%STOP_ALL%"=="true" (
    echo Stopping services from all docker-compose files...
    
    REM Main docker-compose
    if exist "docker-compose.yml" (
        echo   -^> Stopping main services...
        %DOCKER_COMPOSE% down %REMOVE_VOLUMES% 2>nul
    )
    
    REM Kafka compose
    if exist "docker-compose-kafka.yml" (
        echo   -^> Stopping Kafka services...
        %DOCKER_COMPOSE% -f docker-compose-kafka.yml down %REMOVE_VOLUMES% 2>nul
    )
    
    REM Simulation compose
    if exist "docker-compose-simulation.yml" (
        echo   -^> Stopping simulation services...
        %DOCKER_COMPOSE% -f docker-compose-simulation.yml down %REMOVE_VOLUMES% 2>nul
    )
    
    REM Production compose
    if exist "docker-compose.prod.yml" (
        echo   -^> Stopping production services...
        %DOCKER_COMPOSE% -f docker-compose.prod.yml down %REMOVE_VOLUMES% 2>nul
    )
) else (
    %DOCKER_COMPOSE% down %REMOVE_VOLUMES%
)

echo.
echo ✓ All VeriCrop services stopped.

if defined REMOVE_VOLUMES (
    echo   Volumes have been removed.
)

echo.
echo To start services again, run: start-all.bat
echo.

goto :eof

:show_help
echo.
echo VeriCrop Stop-All Script (Windows)
echo.
echo This script stops all VeriCrop Docker services.
echo.
echo Usage:
echo   stop-all.bat [options]
echo.
echo Options:
echo   -v, --volumes    Remove volumes (deletes all data)
echo   -a, --all        Stop services from all compose files
echo   -h, --help       Show this help message
echo.
goto :eof

endlocal
