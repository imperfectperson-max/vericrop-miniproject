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
echo.
echo           [91m  VeriCrop Stop-All Script (Windows)  [0m
echo.
echo ===============================================================
echo.

if defined REMOVE_VOLUMES (
    echo [93m⚠ Warning: Volumes will be removed. All data will be deleted![0m
    echo.
)

echo [94mStopping VeriCrop services...[0m
echo.

if "%STOP_ALL%"=="true" (
    echo [93mStopping services from all docker-compose files...[0m
    
    REM Main docker-compose
    if exist "docker-compose.yml" (
        echo   -^> Stopping main services...
        docker-compose down %REMOVE_VOLUMES% 2>nul
    )
    
    REM Kafka compose
    if exist "docker-compose-kafka.yml" (
        echo   -^> Stopping Kafka services...
        docker-compose -f docker-compose-kafka.yml down %REMOVE_VOLUMES% 2>nul
    )
    
    REM Simulation compose
    if exist "docker-compose-simulation.yml" (
        echo   -^> Stopping simulation services...
        docker-compose -f docker-compose-simulation.yml down %REMOVE_VOLUMES% 2>nul
    )
    
    REM Production compose
    if exist "docker-compose.prod.yml" (
        echo   -^> Stopping production services...
        docker-compose -f docker-compose.prod.yml down %REMOVE_VOLUMES% 2>nul
    )
) else (
    docker-compose down %REMOVE_VOLUMES%
)

echo.
echo [92m✓ All VeriCrop services stopped.[0m

if defined REMOVE_VOLUMES (
    echo [93m  Volumes have been removed.[0m
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
