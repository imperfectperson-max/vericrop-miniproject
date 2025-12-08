#!/bin/bash

#
# VeriCrop Start-All Script (Unix/Linux/Mac)
#
# This script helps you start all VeriCrop services with a single command.
# It supports multiple modes: full stack, infrastructure only, build, and run.
#
# Usage:
#   ./start-all.sh [mode] [options]
#
# Modes:
#   full           - Start all services and 3 GUI instances (default)
#   infra          - Start infrastructure only: PostgreSQL, Kafka, Zookeeper
#   kafka          - Start Kafka stack only (using docker-compose-kafka.yml)
#   simulation     - Start simulation environment (using docker-compose-simulation.yml)
#   prod           - Start production environment (using docker-compose.prod.yml)
#   build          - Build Java artifacts with Gradle
#   docker-build   - Build Docker images (vericrop-gui, ml-service)
#   run            - Run 3 GUI instances only
#   all-build      - Build everything (Java + Docker images)
#   init-airflow   - Initialize Airflow (first time setup)
#   fix-airflow    - Fix Airflow using alternative method
#   logs           - Show service logs
#   ps             - Show service status
#   stop           - Stop all services
#
# Options:
#   -d, --detach     Run services in detached mode (background)
#   -b, --build      Rebuild Docker images before starting
#   -h, --help       Show this help message
#   --no-cache       Build Docker images without cache
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Show help immediately if requested
if [[ "$1" == "-h" || "$1" == "--help" || "$1" == "help" ]]; then
    echo "VeriCrop Start-All Script (Unix/Linux/Mac)"
    echo ""
    echo "This script helps you start all VeriCrop services with a single command."
    echo ""
    echo "Usage:"
    echo "  ./start-all.sh [mode] [options]"
    echo ""
    echo "Modes:"
    echo "  full           - Start all services and 3 GUI instances (default)"
    echo "  infra          - Start infrastructure only: PostgreSQL, Kafka, Zookeeper"
    echo "  kafka          - Start Kafka stack only (using docker-compose-kafka.yml)"
    echo "  simulation     - Start simulation environment (using docker-compose-simulation.yml)"
    echo "  prod           - Start production environment (using docker-compose.prod.yml)"
    echo "  build          - Build Java artifacts with Gradle"
    echo "  docker-build   - Build Docker images (vericrop-gui, ml-service)"
    echo "  run            - Run 3 GUI instances only"
    echo "  all-build      - Build everything (Java + Docker images)"
    echo "  init-airflow   - Initialize Airflow (first time setup)"
    echo "  fix-airflow    - Fix Airflow using alternative method"
    echo "  logs           - Show service logs"
    echo "  ps             - Show service status"
    echo "  stop           - Stop all services"
    echo ""
    echo "Options:"
    echo "  -d, --detach     Run services in detached mode (background)"
    echo "  -b, --build      Rebuild Docker images before starting"
    echo "  -h, --help       Show this help message"
    echo "  --no-cache       Build Docker images without cache"
    echo ""
    exit 0
fi

# Default options
MODE="${1:-full}"
DETACH="-d"
BUILD_FLAG=""
NO_CACHE=""

# Parse arguments
shift || true
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--detach)
            DETACH="-d"
            shift
            ;;
        -b|--build)
            BUILD_FLAG="--build"
            shift
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            shift
            ;;
        -h|--help)
            exec "$0" help
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Print banner
print_banner() {
    echo ""
    echo -e "${CYAN}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                                                           ║${NC}"
    echo -e "${CYAN}║${GREEN}           🌱 VeriCrop Start-All Script 🌱                ${CYAN}║${NC}"
    echo -e "${CYAN}║                                                           ║${NC}"
    echo -e "${CYAN}║${NC}   AI-Powered Agricultural Supply Chain Management        ${CYAN}║${NC}"
    echo -e "${CYAN}║${NC}   with Quality Control and Blockchain Transparency       ${CYAN}║${NC}"
    echo -e "${CYAN}║                                                           ║${NC}"
    echo -e "${CYAN}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# Check prerequisites
check_prerequisites() {
    echo -e "${BLUE}Checking prerequisites...${NC}"
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}❌ Docker is not installed. Please install Docker first.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Docker is installed${NC}"
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        if ! docker compose version &> /dev/null; then
            echo -e "${RED}❌ Docker Compose is not installed. Please install Docker Compose.${NC}"
            exit 1
        fi
        DOCKER_COMPOSE="docker compose"
    else
        DOCKER_COMPOSE="docker-compose"
    fi
    echo -e "${GREEN}✓ Docker Compose is installed${NC}"
    
    # Check if Docker is running
    if ! docker info &> /dev/null; then
        echo -e "${RED}❌ Docker daemon is not running. Please start Docker.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Docker daemon is running${NC}"
    
    echo ""
}

# Check Java for Gradle commands
check_java() {
    if ! command -v java &> /dev/null; then
        echo ""
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${YELLOW}⚠ WARNING: Java is not installed or not in PATH${NC}"
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo ""
        echo "The GUI instances require Java JDK 11 or later (JDK 17 recommended)."
        echo ""
        echo -e "${GREEN}📥 To install Java:${NC}"
        echo ""
        echo -e "${BLUE}  macOS (Homebrew):${NC}"
        echo "    brew install openjdk@17"
        echo "    sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk"
        echo ""
        echo -e "${BLUE}  Ubuntu/Debian:${NC}"
        echo "    sudo apt update"
        echo "    sudo apt install openjdk-17-jdk"
        echo ""
        echo -e "${BLUE}  Fedora/RHEL/CentOS:${NC}"
        echo "    sudo dnf install java-17-openjdk-devel"
        echo ""
        echo -e "${BLUE}  Arch Linux:${NC}"
        echo "    sudo pacman -S jdk-openjdk"
        echo ""
        echo -e "${BLUE}  Or download from:${NC} https://adoptium.net/"
        echo ""
        echo -e "${GREEN}🐳 Alternative: Use Docker to run GUI instances${NC}"
        echo "  See README.md for Docker-based setup instructions"
        echo ""
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo ""
        echo "Skipping GUI instances... (infrastructure services will still start)"
        echo ""
        return 1
    fi
    
    # Parse Java version - handles both old (1.8.0) and new (11.0.1, 17) formats
    JAVA_VERSION_OUTPUT=$(java -version 2>&1 | head -n 1)
    if [[ "$JAVA_VERSION_OUTPUT" =~ \"([0-9]+)\.([0-9]+) ]]; then
        JAVA_MAJOR="${BASH_REMATCH[1]}"
        # For old format "1.8.0", the major version is the second number
        if [[ "$JAVA_MAJOR" == "1" ]]; then
            JAVA_MAJOR="${BASH_REMATCH[2]}"
        fi
    elif [[ "$JAVA_VERSION_OUTPUT" =~ \"([0-9]+) ]]; then
        JAVA_MAJOR="${BASH_REMATCH[1]}"
    else
        JAVA_MAJOR="unknown"
    fi
    
    if [[ "$JAVA_MAJOR" != "unknown" && "$JAVA_MAJOR" -lt 11 ]]; then
        echo -e "${YELLOW}⚠ Java version $JAVA_MAJOR detected. This version is unsupported. JDK 11+ is required (JDK 17 recommended).${NC}"
    else
        echo -e "${GREEN}✓ Java $JAVA_MAJOR detected${NC}"
    fi
    return 0
}

# Start full stack
start_full() {
    echo -e "${BLUE}Starting full VeriCrop stack...${NC}"
    echo ""
    
    $DOCKER_COMPOSE up $DETACH $BUILD_FLAG
    
    echo ""
    echo -e "${GREEN}✓ Full stack started successfully!${NC}"
    print_service_urls
    
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Starting 3 GUI instances..."
    echo ""
    run_gui
}

# Start infrastructure only
start_infra() {
    echo -e "${BLUE}Starting infrastructure services (PostgreSQL, Kafka, Zookeeper)...${NC}"
    echo ""
    
    $DOCKER_COMPOSE up $DETACH postgres kafka zookeeper ml-service
    
    echo ""
    echo -e "${GREEN}✓ Infrastructure services started!${NC}"
    echo ""
    echo -e "Services running:"
    echo -e "  • PostgreSQL:  ${CYAN}localhost:5432${NC}"
    echo -e "  • Kafka:       ${CYAN}localhost:9092${NC}"
    echo -e "  • Zookeeper:   ${CYAN}localhost:2181${NC}"
    echo -e "  • ML Service:  ${CYAN}http://localhost:8000${NC}"
}

# Start Kafka stack
start_kafka() {
    echo -e "${BLUE}Starting Kafka stack (docker-compose-kafka.yml)...${NC}"
    echo ""
    
    $DOCKER_COMPOSE -f docker-compose-kafka.yml up $DETACH $BUILD_FLAG
    
    echo ""
    echo -e "${GREEN}✓ Kafka stack started!${NC}"
    echo ""
    echo -e "Services running:"
    echo -e "  • Kafka:       ${CYAN}localhost:9092${NC}"
    echo -e "  • Zookeeper:   ${CYAN}localhost:2181${NC}"
    echo -e "  • Kafka UI:    ${CYAN}http://localhost:8090${NC}"
}

# Start simulation environment
start_simulation() {
    echo -e "${BLUE}Starting simulation environment (docker-compose-simulation.yml)...${NC}"
    echo ""
    
    $DOCKER_COMPOSE -f docker-compose-simulation.yml up $DETACH $BUILD_FLAG
    
    echo ""
    echo -e "${GREEN}✓ Simulation environment started!${NC}"
    echo ""
    echo -e "Services running:"
    echo -e "  • Kafka:         ${CYAN}localhost:9092${NC}"
    echo -e "  • Kafka UI:      ${CYAN}http://localhost:8090${NC}"
    echo -e "  • PostgreSQL:    ${CYAN}localhost:5432${NC}"
    echo -e "  • ML Service:    ${CYAN}http://localhost:8000${NC}"
    echo -e "  • Airflow:       ${CYAN}http://localhost:8080${NC} (admin/admin)"
}

# Start production environment
start_prod() {
    echo -e "${BLUE}Starting production environment (docker-compose.prod.yml)...${NC}"
    echo ""
    
    if [ ! -f ".env" ]; then
        echo -e "${YELLOW}⚠ Warning: .env file not found. Copying from .env.production.example${NC}"
        if [ -f ".env.production.example" ]; then
            cp .env.production.example .env
            echo -e "${YELLOW}  Please review and update .env with production credentials.${NC}"
        fi
    fi
    
    $DOCKER_COMPOSE -f docker-compose.prod.yml up $DETACH $BUILD_FLAG
    
    echo ""
    echo -e "${GREEN}✓ Production environment started!${NC}"
    print_service_urls
}

# Build Java artifacts
build_java() {
    echo -e "${BLUE}Building Java artifacts with Gradle...${NC}"
    echo ""
    
    if ! check_java; then
        exit 1
    fi
    
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        ./gradlew clean build --no-daemon
    else
        echo -e "${RED}❌ Gradle wrapper not found. Please run from project root.${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${GREEN}✓ Java build completed successfully!${NC}"
}

# Build Docker images
build_docker() {
    echo -e "${BLUE}Building Docker images...${NC}"
    echo ""
    
    # Build vericrop-gui
    echo -e "${YELLOW}Building vericrop-gui Docker image...${NC}"
    docker build $NO_CACHE -t vericrop-gui:latest -f vericrop-gui/Dockerfile .
    echo -e "${GREEN}✓ vericrop-gui image built${NC}"
    
    # Build ml-service
    echo -e "${YELLOW}Building ml-service Docker image...${NC}"
    docker build $NO_CACHE -t vericrop-ml-service:latest -f docker/ml-service/Dockerfile docker/ml-service
    echo -e "${GREEN}✓ vericrop-ml-service image built${NC}"
    
    echo ""
    echo -e "${GREEN}✓ All Docker images built successfully!${NC}"
    echo ""
    echo "Available images:"
    docker images | grep -E "vericrop-gui|vericrop-ml-service" || echo "No images found"
}

# Build everything
build_all() {
    echo -e "${BLUE}Building everything (Java + Docker)...${NC}"
    echo ""
    
    build_java
    echo ""
    build_docker
    
    echo ""
    echo -e "${GREEN}✓ All builds completed successfully!${NC}"
}

# Initialize Airflow
init_airflow() {
    echo -e "${BLUE}Initializing Airflow...${NC}"
    echo ""
    
    echo "Step 1: Stopping Airflow services..."
    $DOCKER_COMPOSE stop airflow-scheduler airflow-webserver
    echo ""
    
    echo "Step 2: Initializing Airflow database..."
    if ! $DOCKER_COMPOSE run --rm airflow-webserver airflow db init; then
        echo -e "${RED}ERROR: Failed to initialize Airflow database${NC}"
        echo "Trying alternative method..."
        fix_airflow
        return
    fi
    echo ""
    
    echo "Step 3: Creating admin user..."
    $DOCKER_COMPOSE run --rm airflow-webserver airflow users create --username admin --password admin --firstname Admin --lastname User --role Admin --email admin@example.com
    echo ""
    
    echo "Step 4: Starting Airflow services..."
    $DOCKER_COMPOSE up -d airflow-scheduler airflow-webserver
    echo ""
    
    echo -e "${GREEN}✓ Airflow initialization complete!${NC}"
    echo -e "  Access at: ${CYAN}http://localhost:8080${NC}"
    echo -e "  Username: ${YELLOW}admin${NC}"
    echo -e "  Password: ${YELLOW}admin${NC}"
    echo ""
    
    echo "Creating initialization marker..."
    echo "initialized" > airflow-initialized.txt
}

# Fix Airflow using alternative method
fix_airflow() {
    echo -e "${BLUE}Using alternative Airflow initialization method...${NC}"
    echo ""
    
    echo "1. Ensuring postgres-airflow is ready..."
    sleep 10  # Wait for PostgreSQL to be fully ready
    echo ""
    
    echo "2. Checking database connection..."
    $DOCKER_COMPOSE exec postgres-airflow pg_isready -U airflow
    echo ""
    
    echo "3. Initializing with direct exec..."
    $DOCKER_COMPOSE exec airflow-webserver airflow db init
    echo ""
    
    echo "4. Creating user..."
    $DOCKER_COMPOSE exec airflow-webserver airflow users create --username admin --password admin --firstname Admin --lastname User --role Admin --email admin@example.com
    echo ""
    
    echo "5. Restarting..."
    $DOCKER_COMPOSE restart airflow-scheduler airflow-webserver
    echo ""
    
    echo -e "${GREEN}✓ Airflow should now be working!${NC}"
    echo "initialized" > airflow-initialized.txt
}

# Show service logs
show_logs() {
    echo -e "${BLUE}Showing service logs...${NC}"
    echo ""
    
    # Show last 50 lines of logs (matches start-all.bat behavior)
    $DOCKER_COMPOSE logs --tail=50
    
    echo ""
    echo "For Airflow specific logs:"
    echo "  $DOCKER_COMPOSE logs airflow-webserver"
    echo "  $DOCKER_COMPOSE logs airflow-scheduler"
}

# Show service status
show_status() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}                     Service Status                         ${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    
    $DOCKER_COMPOSE ps
    
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
}

# Stop all services
stop_all() {
    echo -e "${BLUE}Stopping all services...${NC}"
    echo ""
    
    $DOCKER_COMPOSE down
    
    if [ -f "airflow-initialized.txt" ]; then
        rm airflow-initialized.txt
    fi
    
    echo ""
    echo -e "${GREEN}✓ All services stopped${NC}"
}

# Run GUI instances
run_gui() {
    local GUI_INSTANCES=3
    local INSTANCE_START_DELAY=5
    local GRADLE_RUN_CMD="./gradlew :vericrop-gui:run || ./gradlew run || ./gradlew :app:run || ./gradlew bootRun"
    
    echo -e "${BLUE}Starting $GUI_INSTANCES instances of VeriCrop JavaFX GUI...${NC}"
    echo ""
    echo "Each instance will run in a separate window:"
    echo "  - Instance 1: Farmer role (default)"
    echo "  - Instance 2: Distributor role"
    echo "  - Instance 3: Retailer role"
    echo ""
    
    if ! check_java; then
        echo ""
        echo -e "${GREEN}💡 TIP: You can still access VeriCrop services via:${NC}"
        echo -e "  • Kafka UI:       ${CYAN}http://localhost:8081${NC}"
        echo -e "  • Airflow UI:     ${CYAN}http://localhost:8080${NC}"
        echo -e "  • ML Service:     ${CYAN}http://localhost:8000${NC}"
        echo -e "  • PostgreSQL:     ${CYAN}localhost:5432${NC}"
        echo ""
        return 1
    fi
    
    if [ ! -f "./gradlew" ]; then
        echo ""
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${YELLOW}⚠ WARNING: gradlew not found${NC}"
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo ""
        echo "The Gradle wrapper is missing. This could mean:"
        echo "  1. You're not in the project root directory"
        echo "  2. The project was not cloned correctly"
        echo "  3. The gradlew file was accidentally deleted"
        echo ""
        echo -e "${BLUE}📂 Current directory:${NC} $(pwd)"
        echo ""
        echo -e "${GREEN}🔧 To fix:${NC}"
        echo "  1. Navigate to the project root directory"
        echo "  2. Or re-clone the repository"
        echo "  3. Or regenerate Gradle wrapper: gradle wrapper"
        echo ""
        echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo ""
        return 1
    fi
    
    chmod +x ./gradlew
    
    echo "Starting GUI instances..."
    echo ""
    
    # Instance 1
    echo "[1/3] Starting Instance 1 (Farmer)..."
    if command -v gnome-terminal &> /dev/null; then
        gnome-terminal -- bash -c "$GRADLE_RUN_CMD; exec bash" &
    elif command -v xterm &> /dev/null; then
        xterm -title "VeriCrop GUI - Instance 1 (Farmer)" -e bash -c "$GRADLE_RUN_CMD; exec bash" &
    elif command -v konsole &> /dev/null; then
        konsole --title "VeriCrop GUI - Instance 1 (Farmer)" -e bash -c "$GRADLE_RUN_CMD; exec bash" &
    else
        # Fallback: run in background and redirect to log files
        echo "No terminal emulator found, starting in background mode..."
        nohup ./gradlew :vericrop-gui:run > /tmp/vericrop-gui-instance1.log 2>&1 &
    fi
    sleep $INSTANCE_START_DELAY
    
    # Instance 2
    echo "[2/3] Starting Instance 2 (Distributor)..."
    local GRADLE_RUN_CMD_INSTANCE2="./gradlew :vericrop-gui:run -Dapp.instance=2 || ./gradlew run -Dapp.instance=2 || ./gradlew :app:run -Dapp.instance=2 || ./gradlew bootRun -Dapp.instance=2"
    if command -v gnome-terminal &> /dev/null; then
        gnome-terminal -- bash -c "$GRADLE_RUN_CMD_INSTANCE2; exec bash" &
    elif command -v xterm &> /dev/null; then
        xterm -title "VeriCrop GUI - Instance 2 (Distributor)" -e bash -c "$GRADLE_RUN_CMD_INSTANCE2; exec bash" &
    elif command -v konsole &> /dev/null; then
        konsole --title "VeriCrop GUI - Instance 2 (Distributor)" -e bash -c "$GRADLE_RUN_CMD_INSTANCE2; exec bash" &
    else
        nohup ./gradlew :vericrop-gui:run -Dapp.instance=2 > /tmp/vericrop-gui-instance2.log 2>&1 &
    fi
    sleep $INSTANCE_START_DELAY
    
    # Instance 3
    echo "[3/3] Starting Instance 3 (Retailer)..."
    local GRADLE_RUN_CMD_INSTANCE3="./gradlew :vericrop-gui:run -Dapp.instance=3 || ./gradlew run -Dapp.instance=3 || ./gradlew :app:run -Dapp.instance=3 || ./gradlew bootRun -Dapp.instance=3"
    if command -v gnome-terminal &> /dev/null; then
        gnome-terminal -- bash -c "$GRADLE_RUN_CMD_INSTANCE3; exec bash" &
    elif command -v xterm &> /dev/null; then
        xterm -title "VeriCrop GUI - Instance 3 (Retailer)" -e bash -c "$GRADLE_RUN_CMD_INSTANCE3; exec bash" &
    elif command -v konsole &> /dev/null; then
        konsole --title "VeriCrop GUI - Instance 3 (Retailer)" -e bash -c "$GRADLE_RUN_CMD_INSTANCE3; exec bash" &
    else
        nohup ./gradlew :vericrop-gui:run -Dapp.instance=3 > /tmp/vericrop-gui-instance3.log 2>&1 &
    fi
    
    echo ""
    echo -e "${GREEN}✓ GUI instances launched.${NC}"
    echo ""
    echo "If GUIs don't start, try building first:"
    echo "  ./start-all.sh build"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Make sure Java JDK 11+ is installed"
    echo "  2. Check Gradle wrapper exists (./gradlew)"
    echo "  3. Try: ./gradlew tasks (to see available tasks)"
    echo ""
}

# Print service URLs
print_service_urls() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}                     Service URLs                           ${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  • ML Service:     ${GREEN}http://localhost:8000${NC}"
    echo -e "  • ML Health:      ${GREEN}http://localhost:8000/health${NC}"
    echo -e "  • Kafka UI:       ${GREEN}http://localhost:8081${NC}"
    echo -e "  • Airflow UI:     ${GREEN}http://localhost:8080${NC} (admin/admin)"
    echo -e "  • PostgreSQL:     ${GREEN}localhost:5432${NC} (vericrop/vericrop123)"
    echo -e "  • Kafka:          ${GREEN}localhost:9092${NC}"
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "To stop all services:   ${YELLOW}./stop-all.sh${NC}"
    echo ""
}

# Main execution
main() {
    print_banner
    check_prerequisites
    
    case $MODE in
        full)
            start_full
            ;;
        infra|infrastructure)
            start_infra
            ;;
        kafka)
            start_kafka
            ;;
        simulation|sim)
            start_simulation
            ;;
        prod|production)
            start_prod
            ;;
        build)
            build_java
            ;;
        docker-build|docker)
            build_docker
            ;;
        all-build|build-all)
            build_all
            ;;
        run|run-gui|gui)
            run_gui
            ;;
        init-airflow)
            init_airflow
            ;;
        fix-airflow)
            fix_airflow
            ;;
        logs)
            show_logs
            ;;
        ps|status)
            show_status
            ;;
        stop)
            stop_all
            ;;
        help|-h|--help)
            echo "Run './start-all.sh --help' for usage information"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown mode: $MODE${NC}"
            echo ""
            echo "Available modes:"
            echo "  full           - Start all services and 3 GUI instances (default)"
            echo "  infra          - Start infrastructure only"
            echo "  kafka          - Start Kafka stack"
            echo "  simulation     - Start simulation environment"
            echo "  prod           - Start production environment"
            echo "  build          - Build Java artifacts"
            echo "  docker-build   - Build Docker images"
            echo "  all-build      - Build everything"
            echo "  run            - Run 3 GUI instances only"
            echo "  init-airflow   - Initialize Airflow"
            echo "  fix-airflow    - Fix Airflow with alternative method"
            echo "  logs           - Show service logs"
            echo "  ps             - Show service status"
            echo "  stop           - Stop all services"
            echo ""
            exit 1
            ;;
    esac
}

main "$@"
