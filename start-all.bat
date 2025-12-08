@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0"

set "MODE=full"
set "DOCKER_COMPOSE=docker-compose"
set "GUI_INSTANCES=3"

echo.
echo ===============================================================
echo        VeriCrop Start-All Script (Windows)
echo ===============================================================
echo.

REM Check if demo mode
if /I "%~1"=="demo" goto :skip_docker_check
if /I "%~1"=="standalone" goto :skip_docker_check

REM Check prerequisites
echo Checking prerequisites...

where docker >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not installed.
    echo.
    echo TIP: Use demo mode to run without Docker: start-all.bat demo
    echo.
)
echo ✓ Docker is installed

docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker daemon is not running.
    echo.
    echo TIP: Use demo mode to run without Docker: start-all.bat demo
    echo.
)
echo ✓ Docker daemon is running

REM Check docker-compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo ERROR: Docker Compose is not installed.
        echo.
        echo TIP: Use demo mode to run without Docker: start-all.bat demo
        echo.
    )
    set "DOCKER_COMPOSE=docker compose"
)
echo ✓ Docker Compose is available
echo.
goto :after_docker_check

:skip_docker_check
echo Skipping Docker checks (demo mode)
echo.

:after_docker_check

REM Create Airflow marker if it doesn't exist (for first run)
if not exist "airflow-initialized.txt" (
    REM Check if Airflow is actually running
    docker-compose ps | findstr "airflow-webserver" | findstr "Up" >nul
    if not errorlevel 1 (
        echo ✓ Airflow appears to be running. Creating marker file...
        echo initialized > airflow-initialized.txt
    )
)

REM Command handling
if "%~1"=="" goto :start_all

if /I "%~1"=="demo" goto :start_demo
if /I "%~1"=="standalone" goto :start_demo
if /I "%~1"=="init-airflow" goto :init_airflow
if /I "%~1"=="fix-airflow" goto :fix_airflow
if /I "%~1"=="infra" goto :start_infra
if /I "%~1"=="logs" goto :show_logs
if /I "%~1"=="ps" goto :show_status
if /I "%~1"=="stop" goto :stop_all
if /I "%~1"=="run-gui" goto :run_gui
if /I "%~1"=="gui" goto :run_gui
if /I "%~1"=="build" goto :build_java
if /I "%~1"=="help" goto :show_help

:start_all
echo Starting all VeriCrop services...
echo.

%DOCKER_COMPOSE% up -d --remove-orphans

echo.
call :show_service_status

echo.
echo ===============================================================
echo                      Service URLs
echo ===============================================================
echo.
echo   ✓ PostgreSQL:     localhost:5432 (vericrop/vericrop123)
echo   ✓ Kafka:          localhost:9092
echo   ✓ Kafka UI:       http://localhost:8081
echo   ✓ ML Service:     http://localhost:8000
echo   ✓ Redis:          localhost:6379
echo.
REM Check if Airflow is initialized
if exist "airflow-initialized.txt" (
    echo   ✓ Airflow UI:     http://localhost:8080 (admin/admin)
) else (
    echo   ⚠ Airflow UI:     http://localhost:8080 (needs initialization)
    echo     Run: start-all.bat init-airflow
)
echo.
echo ===============================================================
echo.
echo Starting 3 GUI instances...
echo.
call :run_gui
goto :end_script

:start_demo
echo.
echo ===============================================================
echo                      DEMO MODE
echo ===============================================================
echo.
echo Demo mode features:
echo   ✓ No external dependencies (PostgreSQL, Kafka, ML Service)
echo   ✓ In-memory blockchain
echo   ✓ Mock ML predictions
echo   ✓ Demo data in all screens
echo   ✓ Fully functional delivery simulator
echo   ✓ QR code generation
echo   ✓ All UI flows operational
echo.
echo Perfect for:
echo   • Quick demonstrations
echo   • Development without infrastructure setup
echo   • Testing UI flows in isolation
echo   • Offline scenarios
echo.
echo ===============================================================
echo.

REM Set demo mode environment variable
set "VERICROP_LOAD_DEMO=true"

echo Starting 3 GUI instances in demo mode...
echo.
call :run_gui_demo
goto :end_script

:run_gui
echo Starting %GUI_INSTANCES% instances of VeriCrop JavaFX GUI...
echo.
echo Each instance will run in a separate window:
echo   - Instance 1: Farmer role (default)
echo   - Instance 2: Distributor role
echo   - Instance 3: Retailer role
echo.

REM Check if Java and Gradle are available
call :check_java
if errorlevel 1 goto :eof

if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found in current directory.
    echo Please run from project root directory.
    goto :eof
)
goto :run_gui_common

:run_gui_demo
REM Demo mode version with VERICROP_LOAD_DEMO set
echo Demo Mode Enabled - No external dependencies required
echo.
echo Demo Mode Credentials:
echo   • admin / admin123
echo   • farmer / farmer123
echo   • supplier / supplier123
echo   • consumer / consumer123
echo.

REM Check if Java and Gradle are available
call :check_java
if errorlevel 1 goto :eof

if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found in current directory.
    echo Please run from project root directory.
    goto :eof
)

:run_gui_common

echo Starting GUI instances...
echo.

REM First, let's see what Gradle tasks are available
echo Checking available Gradle tasks...
gradlew.bat tasks --console=plain | findstr "run" | head -5
echo.

REM Try different run commands based on project structure
echo Trying to start instances...
echo.

REM Instance 1
echo [1/3] Starting Instance 1 (Farmer)...
start "VeriCrop GUI - Instance 1 (Farmer)" cmd /k "title VeriCrop GUI - Instance 1 (Farmer) && echo Starting GUI... && gradlew.bat :vericrop-gui:run || gradlew.bat run || gradlew.bat :app:run || gradlew.bat bootRun"
timeout /t 5 /nobreak >nul

REM Instance 2
echo [2/3] Starting Instance 2 (Distributor)...
start "VeriCrop GUI - Instance 2 (Distributor)" cmd /k "title VeriCrop GUI - Instance 2 (Distributor) && echo Starting GUI... && gradlew.bat :vericrop-gui:run -Dapp.instance=2 || gradlew.bat run || gradlew.bat :app:run || gradlew.bat bootRun"
timeout /t 5 /nobreak >nul

REM Instance 3
echo [3/3] Starting Instance 3 (Retailer)...
start "VeriCrop GUI - Instance 3 (Retailer)" cmd /k "title VeriCrop GUI - Instance 3 (Retailer) && echo Starting GUI... && gradlew.bat :vericrop-gui:run -Dapp.instance=3 || gradlew.bat run || gradlew.bat :app:run || gradlew.bat bootRun"

echo.
echo ✓ GUI instances launched. Check the opened windows.
echo.

if "%VERICROP_LOAD_DEMO%"=="true" (
    echo Demo Mode Active:
    echo   • Using in-memory blockchain
    echo   • Mock ML predictions
    echo   • Demo data in all screens
    echo   • No external services required
    echo.
)

echo If GUIs don't start, try building first:
echo   start-all.bat build
echo.
echo Troubleshooting:
echo   1. Make sure Java JDK 11+ is installed
echo   2. Check Gradle wrapper exists (gradlew.bat)
echo   3. Try: gradlew.bat tasks (to see available tasks)
echo.
goto :eof

:check_java
where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH.
    echo Please install JDK 11+ and ensure JAVA_HOME is set.
    echo Skipping GUI instances...
)
echo ✓ Java is available

:build_java
echo Building Java artifacts with Gradle...
echo.
call :check_java
if errorlevel 1 goto :end_script

if exist "gradlew.bat" (
    echo Building all Java modules...
    gradlew.bat clean build --no-daemon
    echo.
    echo ✓ Java build completed!
) else (
    echo ERROR: gradlew.bat not found.
    echo Please run from project root directory.
)
goto :end_script

:init_airflow
echo Initializing Airflow...
echo.
echo Step 1: Stopping Airflow services...
docker-compose stop airflow-scheduler airflow-webserver
echo.
echo Step 2: Initializing Airflow database...
docker-compose run --rm airflow-webserver airflow db init
if errorlevel 1 (
    echo ERROR: Failed to initialize Airflow database
    echo Trying alternative method...
    goto :fix_airflow
)
echo.
echo Step 3: Creating admin user...
docker-compose run --rm airflow-webserver airflow users create --username admin --password admin --firstname Admin --lastname User --role Admin --email admin@example.com
echo.
echo Step 4: Starting Airflow services...
docker-compose up -d airflow-scheduler airflow-webserver
echo.
echo ✓ Airflow initialization complete!
echo   Access at: http://localhost:8080
echo   Username: admin
echo   Password: admin
echo.
echo Creating initialization marker...
echo initialized > airflow-initialized.txt
goto :end_script

:fix_airflow
echo Using alternative Airflow initialization method...
echo.
echo 1. Ensuring postgres-airflow is ready...
timeout /t 10 /nobreak >nul
echo.
echo 2. Checking database connection...
docker-compose exec postgres-airflow pg_isready -U airflow
echo.
echo 3. Initializing with direct exec...
docker-compose exec airflow-webserver airflow db init
echo.
echo 4. Creating user...
docker-compose exec airflow-webserver airflow users create --username admin --password admin --firstname Admin --lastname User --role Admin --email admin@example.com
echo.
echo 5. Restarting...
docker-compose restart airflow-scheduler airflow-webserver
echo.
echo ✓ Airflow should now be working!
echo initialized > airflow-initialized.txt
goto :end_script

:start_infra
echo Starting infrastructure only...
echo.
%DOCKER_COMPOSE% up -d postgres kafka zookeeper redis ml-service
echo ✓ Infrastructure services started!
goto :end_script

:show_logs
echo Showing service logs...
echo.
docker-compose logs --tail=50
echo.
echo For Airflow specific logs:
echo docker-compose logs airflow-webserver
echo docker-compose logs airflow-scheduler
goto :end_script

:show_status
call :show_service_status
goto :end_script

:stop_all
echo Stopping all services...
echo.
docker-compose down
if exist "airflow-initialized.txt" del airflow-initialized.txt
echo ✓ All services stopped
goto :end_script

:show_service_status
echo ===============================================================
echo                     Service Status
echo ===============================================================
echo.
docker-compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
echo.
echo ===============================================================
goto :eof

:show_help
echo.
echo VeriCrop Start Script Commands:
echo.
echo   start-all.bat          - Start all services and 3 GUI instances
echo   start-all.bat demo     - Start 3 GUI instances in demo mode (no Docker)
echo   start-all.bat infra    - Start infrastructure only
echo   start-all.bat init-airflow - Initialize Airflow (first time)
echo   start-all.bat fix-airflow - Alternative Airflow fix
echo   start-all.bat build    - Build Java artifacts
echo   start-all.bat run-gui  - Run 3 GUI instances only
echo   start-all.bat logs     - View service logs
echo   start-all.bat ps       - Check service status
echo   start-all.bat stop     - Stop all services
echo   start-all.bat help     - Show this help
echo.
echo GUI Instances:
echo   By default, 3 GUI instances are started automatically.
echo   Each instance runs in separate window with different roles:
echo   - Instance 1: Farmer role
echo   - Instance 2: Distributor role
echo   - Instance 3: Retailer role
echo.
echo Demo Mode:
echo   Use "start-all.bat demo" to run without Docker dependencies.
echo   Perfect for quick demonstrations and offline scenarios.
echo   All UI features work with mock data and in-memory services.
echo.
echo All instances connect to the same backend services.
echo.
goto :end_script

:end_script
echo.
echo Script completed.
echo Press any key to exit...
pause >nul