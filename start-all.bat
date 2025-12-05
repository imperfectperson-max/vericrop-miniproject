@echo off
REM ============================================================================
REM VeriCrop Start-All Script (Windows)
REM
REM This script helps you start all VeriCrop services with a single command.
REM It supports multiple modes: full stack, infrastructure only, build, and run.
REM
REM Usage:
REM   start-all.bat [mode] [options]
REM
REM Modes:
REM   full           - Start all services (default): Kafka, PostgreSQL, ML Service, Airflow
REM   infra          - Start infrastructure only: PostgreSQL, Kafka, Zookeeper
REM   kafka          - Start Kafka stack only (using docker-compose-kafka.yml)
REM   simulation     - Start simulation environment (using docker-compose-simulation.yml)
REM   prod           - Start production environment (using docker-compose.prod.yml)
REM   build          - Build Java artifacts with Gradle
REM   docker-build   - Build Docker images (vericrop-gui, ml-service)
REM   run            - Run the JavaFX GUI application
REM   all-build      - Build everything (Java + Docker images)
REM
REM Options:
REM   -b, --build      Rebuild Docker images before starting
REM   -h, --help       Show this help message
REM   --no-cache       Build Docker images without cache
REM ============================================================================

setlocal EnableDelayedExpansion

REM Script directory
cd /d "%~dp0"

REM Default values
set "MODE=full"
set "BUILD_FLAG="
set "NO_CACHE="

REM Parse first argument as mode
if not "%~1"=="" (
    set "MODE=%~1"
    shift
)

REM Parse options
:parse_args
if "%~1"=="" goto end_parse
if /I "%~1"=="-b" set "BUILD_FLAG=--build" & shift & goto parse_args
if /I "%~1"=="--build" set "BUILD_FLAG=--build" & shift & goto parse_args
if /I "%~1"=="--no-cache" set "NO_CACHE=--no-cache" & shift & goto parse_args
if /I "%~1"=="-h" goto show_help
if /I "%~1"=="--help" goto show_help
echo Unknown option: %~1
exit /b 1
:end_parse

REM Print banner
call :print_banner

REM Check prerequisites
call :check_prerequisites
if errorlevel 1 exit /b 1

REM Execute based on mode
if /I "%MODE%"=="full" goto start_full
if /I "%MODE%"=="infra" goto start_infra
if /I "%MODE%"=="infrastructure" goto start_infra
if /I "%MODE%"=="kafka" goto start_kafka
if /I "%MODE%"=="simulation" goto start_simulation
if /I "%MODE%"=="sim" goto start_simulation
if /I "%MODE%"=="prod" goto start_prod
if /I "%MODE%"=="production" goto start_prod
if /I "%MODE%"=="build" goto build_java
if /I "%MODE%"=="docker-build" goto build_docker
if /I "%MODE%"=="docker" goto build_docker
if /I "%MODE%"=="all-build" goto build_all
if /I "%MODE%"=="build-all" goto build_all
if /I "%MODE%"=="run" goto run_gui
if /I "%MODE%"=="help" goto show_help
if /I "%MODE%"=="-h" goto show_help
if /I "%MODE%"=="--help" goto show_help

echo Unknown mode: %MODE%
echo.
echo Available modes:
echo   full           - Start all services (default)
echo   infra          - Start infrastructure only
echo   kafka          - Start Kafka stack
echo   simulation     - Start simulation environment
echo   prod           - Start production environment
echo   build          - Build Java artifacts
echo   docker-build   - Build Docker images
echo   all-build      - Build everything
echo   run            - Run the JavaFX GUI
echo.
exit /b 1

REM ============================================================================
REM Functions
REM ============================================================================

:print_banner
echo.
echo ===============================================================
echo.
echo           [92m  VeriCrop Start-All Script (Windows)  [0m
echo.
echo    AI-Powered Agricultural Supply Chain Management
echo    with Quality Control and Blockchain Transparency
echo.
echo ===============================================================
echo.
goto :eof

:check_prerequisites
echo [94mChecking prerequisites...[0m

REM Check Docker
where docker >nul 2>&1
if errorlevel 1 (
    echo [91mX Docker is not installed. Please install Docker Desktop.[0m
    exit /b 1
)
echo [92m✓ Docker is installed[0m

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo [91mX Docker daemon is not running. Please start Docker Desktop.[0m
    exit /b 1
)
echo [92m✓ Docker daemon is running[0m

REM Check Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo [91mX Docker Compose is not installed.[0m
        exit /b 1
    )
)
echo [92m✓ Docker Compose is installed[0m
echo.
goto :eof

:check_java
where java >nul 2>&1
if errorlevel 1 (
    echo [91mX Java is not installed. Please install JDK 17 or later.[0m
    exit /b 1
)
echo [92m✓ Java is installed[0m
goto :eof

:start_full
echo [94mStarting full VeriCrop stack...[0m
echo.
docker-compose up -d %BUILD_FLAG%
echo.
echo [92m✓ Full stack started successfully![0m
call :print_service_urls
goto :eof

:start_infra
echo [94mStarting infrastructure services (PostgreSQL, Kafka, Zookeeper)...[0m
echo.
docker-compose up -d postgres kafka zookeeper ml-service
echo.
echo [92m✓ Infrastructure services started![0m
echo.
echo Services running:
echo   * PostgreSQL:  localhost:5432
echo   * Kafka:       localhost:9092
echo   * Zookeeper:   localhost:2181
echo   * ML Service:  http://localhost:8000
goto :eof

:start_kafka
echo [94mStarting Kafka stack (docker-compose-kafka.yml)...[0m
echo.
docker-compose -f docker-compose-kafka.yml up -d %BUILD_FLAG%
echo.
echo [92m✓ Kafka stack started![0m
echo.
echo Services running:
echo   * Kafka:       localhost:9092
echo   * Zookeeper:   localhost:2181
echo   * Kafka UI:    http://localhost:8090
goto :eof

:start_simulation
echo [94mStarting simulation environment (docker-compose-simulation.yml)...[0m
echo.
docker-compose -f docker-compose-simulation.yml up -d %BUILD_FLAG%
echo.
echo [92m✓ Simulation environment started![0m
echo.
echo Services running:
echo   * Kafka:         localhost:9092
echo   * Kafka UI:      http://localhost:8090
echo   * PostgreSQL:    localhost:5432
echo   * ML Service:    http://localhost:8000
echo   * Airflow:       http://localhost:8080 (admin/admin)
goto :eof

:start_prod
echo [94mStarting production environment (docker-compose.prod.yml)...[0m
echo.
if not exist ".env" (
    echo [93m⚠ Warning: .env file not found. Please create from .env.production.example[0m
    if exist ".env.production.example" (
        copy .env.production.example .env
        echo [93m  .env file created. Please review and update with production credentials.[0m
    )
)
docker-compose -f docker-compose.prod.yml up -d %BUILD_FLAG%
echo.
echo [92m✓ Production environment started![0m
call :print_service_urls
goto :eof

:build_java
echo [94mBuilding Java artifacts with Gradle...[0m
echo.
call :check_java
if errorlevel 1 exit /b 1

if exist "gradlew.bat" (
    call gradlew.bat clean build --no-daemon
) else (
    echo [91mX Gradle wrapper not found. Please run from project root.[0m
    exit /b 1
)
echo.
echo [92m✓ Java build completed successfully![0m
goto :eof

:build_docker
echo [94mBuilding Docker images...[0m
echo.

echo [93mBuilding vericrop-gui Docker image...[0m
docker build %NO_CACHE% -t vericrop-gui:latest -f vericrop-gui/Dockerfile .
echo [92m✓ vericrop-gui image built[0m

echo [93mBuilding ml-service Docker image...[0m
docker build %NO_CACHE% -t vericrop-ml-service:latest -f docker/ml-service/Dockerfile docker/ml-service
echo [92m✓ vericrop-ml-service image built[0m

echo.
echo [92m✓ All Docker images built successfully![0m
echo.
echo Available images:
docker images | findstr /I "vericrop-gui vericrop-ml-service"
goto :eof

:build_all
echo [94mBuilding everything (Java + Docker)...[0m
echo.
call :build_java
if errorlevel 1 exit /b 1
echo.
call :build_docker
if errorlevel 1 exit /b 1
echo.
echo [92m✓ All builds completed successfully![0m
goto :eof

:run_gui
echo [94mRunning VeriCrop JavaFX GUI...[0m
echo.
call :check_java
if errorlevel 1 exit /b 1

if exist "gradlew.bat" (
    call gradlew.bat :vericrop-gui:run
) else (
    echo [91mX Gradle wrapper not found. Please run from project root.[0m
    exit /b 1
)
goto :eof

:print_service_urls
echo.
echo ===============================================================
echo                      Service URLs
echo ===============================================================
echo.
echo   * ML Service:     http://localhost:8000
echo   * ML Health:      http://localhost:8000/health
echo   * Kafka UI:       http://localhost:8081
echo   * Airflow UI:     http://localhost:8080 (admin/admin)
echo   * PostgreSQL:     localhost:5432 (vericrop/vericrop123)
echo   * Kafka:          localhost:9092
echo.
echo ===============================================================
echo.
echo To run the JavaFX GUI:  gradlew.bat :vericrop-gui:run
echo To stop all services:   stop-all.bat
echo.
goto :eof

:show_help
echo.
echo VeriCrop Start-All Script (Windows)
echo.
echo This script helps you start all VeriCrop services with a single command.
echo.
echo Usage:
echo   start-all.bat [mode] [options]
echo.
echo Modes:
echo   full           - Start all services (default)
echo   infra          - Start infrastructure only (PostgreSQL, Kafka, Zookeeper)
echo   kafka          - Start Kafka stack only
echo   simulation     - Start simulation environment
echo   prod           - Start production environment
echo   build          - Build Java artifacts with Gradle
echo   docker-build   - Build Docker images
echo   run            - Run the JavaFX GUI application
echo   all-build      - Build everything (Java + Docker images)
echo.
echo Options:
echo   -b, --build      Rebuild Docker images before starting
echo   -h, --help       Show this help message
echo   --no-cache       Build Docker images without cache
echo.
goto :eof

endlocal
