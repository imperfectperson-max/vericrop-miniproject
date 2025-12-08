#!/bin/bash

#
# VeriCrop Stop-All Script (Unix/Linux/Mac)
#
# This script stops all VeriCrop Docker services.
#
# Usage:
#   ./stop-all.sh [options]
#
# Options:
#   -v, --volumes    Remove volumes (⚠️ deletes all data)
#   -a, --all        Stop services from all compose files
#   -h, --help       Show this help message
#

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Show help immediately if requested
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "VeriCrop Stop-All Script (Unix/Linux/Mac)"
    echo ""
    echo "This script stops all VeriCrop Docker services."
    echo ""
    echo "Usage:"
    echo "  ./stop-all.sh [options]"
    echo ""
    echo "Options:"
    echo "  -v, --volumes    Remove volumes (deletes all data)"
    echo "  -a, --all        Stop services from all compose files"
    echo "  -h, --help       Show this help message"
    echo ""
fi

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Default options
REMOVE_VOLUMES=""
STOP_ALL=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--volumes)
            REMOVE_VOLUMES="-v"
            shift
            ;;
        -a|--all)
            STOP_ALL=true
            shift
            ;;
        -h|--help)
            exec "$0" --help
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            ;;
    esac
done

# Detect docker-compose command
if ! command -v docker-compose &> /dev/null; then
    if docker compose version &> /dev/null; then
        DOCKER_COMPOSE="docker compose"
    else
        echo -e "${RED}❌ Docker Compose is not installed.${NC}"
    fi
else
    DOCKER_COMPOSE="docker-compose"
fi

# Print banner
echo ""
echo -e "${CYAN}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                                                           ║${NC}"
echo -e "${CYAN}║${RED}           🛑 VeriCrop Stop-All Script 🛑                 ${CYAN}║${NC}"
echo -e "${CYAN}║                                                           ║${NC}"
echo -e "${CYAN}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ -n "$REMOVE_VOLUMES" ]; then
    echo -e "${YELLOW}⚠️  Warning: Volumes will be removed. All data will be deleted!${NC}"
    echo ""
fi

# Stop services
echo -e "${BLUE}Stopping VeriCrop services...${NC}"
echo ""

if $STOP_ALL; then
    # Stop services from all compose files
    echo -e "${YELLOW}Stopping services from all docker-compose files...${NC}"
    
    # Main docker-compose
    if [ -f "docker-compose.yml" ]; then
        echo "  → Stopping main services..."
        $DOCKER_COMPOSE down $REMOVE_VOLUMES 2>/dev/null || true
    fi
    
    # Kafka compose
    if [ -f "docker-compose-kafka.yml" ]; then
        echo "  → Stopping Kafka services..."
        $DOCKER_COMPOSE -f docker-compose-kafka.yml down $REMOVE_VOLUMES 2>/dev/null || true
    fi
    
    # Simulation compose
    if [ -f "docker-compose-simulation.yml" ]; then
        echo "  → Stopping simulation services..."
        $DOCKER_COMPOSE -f docker-compose-simulation.yml down $REMOVE_VOLUMES 2>/dev/null || true
    fi
    
    # Production compose
    if [ -f "docker-compose.prod.yml" ]; then
        echo "  → Stopping production services..."
        $DOCKER_COMPOSE -f docker-compose.prod.yml down $REMOVE_VOLUMES 2>/dev/null || true
    fi
else
    # Stop only main docker-compose
    $DOCKER_COMPOSE down $REMOVE_VOLUMES
fi

echo ""
echo -e "${GREEN}✓ All VeriCrop services stopped.${NC}"

if [ -n "$REMOVE_VOLUMES" ]; then
    echo -e "${YELLOW}  Volumes have been removed.${NC}"
fi

echo ""
echo -e "To start services again, run: ${CYAN}./start-all.sh${NC}"
echo ""
