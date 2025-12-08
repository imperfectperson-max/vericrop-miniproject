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

REM Check prerequisites
echo Checking prerequisites...

where docker >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not installed.
    pause
    exit /b 1
)
echo ✓ Docker is installed

docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker daemon is not running.
    pause
    exit /b 1
)
echo ✓ Docker daemon is running

REM Check docker-compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    docker compose version >nul 2>&1
    if errorlevel 1 (
        echo ERROR: Docker Compose is not installed.
        pause
        exit /b 1
    )
    set "DOCKER_COMPOSE=docker compose"
)
echo ✓ Docker Compose is available
echo.

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
if errorlevel 1 (
    echo.
    echo 💡 TIP: You can still access VeriCrop services via:
    echo   • Kafka UI:       http://localhost:8081
    echo   • Airflow UI:     http://localhost:8080
    echo   • ML Service:     http://localhost:8000
    echo   • PostgreSQL:     localhost:5432
    echo.
    goto :eof
)

if not exist "gradlew.bat" (
    echo.
    echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    echo ⚠ WARNING: gradlew.bat not found
    echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    echo.
    echo The Gradle wrapper is missing. This could mean:
    echo   1. You're not in the project root directory
    echo   2. The project was not cloned correctly
    echo   3. The gradlew.bat file was accidentally deleted
    echo.
    echo 📂 Current directory: %CD%
    echo.
    echo 🔧 To fix:
    echo   1. Navigate to the project root directory
    echo   2. Or re-clone the repository
    echo   3. Or regenerate Gradle wrapper: gradle wrapper
    echo.
    echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    echo.
    goto :eof
)

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
    echo.
    echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    echo ⚠ WARNING: Java is not installed or not in PATH
    echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    echo.
    echo The GUI instances require Java JDK 11 or later ^(JDK 17 recommended^).
    echo.
    echo 📥 To install Java:
    echo   1. Download from: https://adoptium.net/
    echo   2. Install the JDK ^(not just JRE^)
    echo   3. Set JAVA_HOME environment variable
    echo   4. Add %%JAVA_HOME%%\bin to your PATH
    echo.
    echo 🔍 Quick install commands:
    echo   • Windows ^(winget^): winget install EclipseAdoptium.Temurin.17.JDK
    echo   • Windows ^(chocolatey^): choco install temurin17
    echo.
    echo 🐳 Alternative: Use Docker to run GUI instances
    echo   See README.md for Docker-based setup instructions
    echo.
    echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    echo.
    echo Skipping GUI instances... ^(infrastructure services will still start^)
    echo.
    exit /b 1
)
echo ✓ Java is available
exit /b 0

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
echo All instances connect to the same backend services.
echo.
goto :end_script

:end_script
echo.
echo Script completed.
pause