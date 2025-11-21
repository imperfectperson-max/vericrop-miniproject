#!/bin/bash
###############################################################################
# VeriCrop GUI Production Build Script
# Builds an optimized production JAR with all security and performance checks
###############################################################################

set -e  # Exit on error
set -u  # Exit on undefined variable

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}VeriCrop GUI Production Build${NC}"
echo -e "${GREEN}=============================================${NC}"

# 1. Clean previous builds
echo -e "\n${YELLOW}[1/7] Cleaning previous builds...${NC}"
./gradlew clean

# 2. Run code quality checks
echo -e "\n${YELLOW}[2/7] Running code quality checks (Checkstyle)...${NC}"
./gradlew checkstyleMain checkstyleTest || {
    echo -e "${RED}Code quality checks failed! Fix issues before proceeding.${NC}"
    exit 1
}

# 3. Run security analysis
echo -e "\n${YELLOW}[3/7] Running security analysis (SpotBugs)...${NC}"
./gradlew spotbugsMain || {
    echo -e "${YELLOW}Warning: SpotBugs found potential issues. Review reports in build/reports/spotbugs/${NC}"
}

# 4. Run all tests
echo -e "\n${YELLOW}[4/7] Running unit tests...${NC}"
./gradlew test || {
    echo -e "${RED}Tests failed! Fix failing tests before deploying to production.${NC}"
    exit 1
}

# 5. Build optimized JAR
echo -e "\n${YELLOW}[5/7] Building production JAR...${NC}"
./gradlew :vericrop-gui:bootJar -Pprofile=production

# 6. Generate SHA256 checksum
echo -e "\n${YELLOW}[6/7] Generating checksum...${NC}"
JAR_FILE=$(find vericrop-gui/build/libs -name "vericrop-gui-*.jar" | head -n 1)
if [ -f "$JAR_FILE" ]; then
    sha256sum "$JAR_FILE" > "${JAR_FILE}.sha256"
    echo -e "${GREEN}Checksum: $(cat ${JAR_FILE}.sha256)${NC}"
else
    echo -e "${RED}JAR file not found!${NC}"
    exit 1
fi

# 7. Print build summary
echo -e "\n${GREEN}=============================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}=============================================${NC}"
echo -e "JAR Location: ${GREEN}$JAR_FILE${NC}"
echo -e "JAR Size: ${GREEN}$(du -h $JAR_FILE | cut -f1)${NC}"
echo -e "\n${YELLOW}Next steps:${NC}"
echo -e "  1. Review code quality reports: build/reports/checkstyle/"
echo -e "  2. Review security reports: build/reports/spotbugs/"
echo -e "  3. Review test reports: build/reports/tests/"
echo -e "  4. Build Docker image: docker build -t vericrop-gui:latest -f vericrop-gui/Dockerfile ."
echo -e "  5. Deploy to production"

exit 0
