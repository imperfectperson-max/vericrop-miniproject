#!/bin/bash
#
# VeriCrop Model Fetch Script
# Downloads the ONNX model from a trusted URL for production deployment
#
# Usage:
#   export MODEL_DOWNLOAD_URL="https://your-secure-storage.com/models/vericrop_quality_model.onnx"
#   ./scripts/fetch_model.sh
#
# Or pass URL as argument:
#   ./scripts/fetch_model.sh https://your-secure-storage.com/models/vericrop_quality_model.onnx
#

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
MODEL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker/ml-service/model"
MODEL_FILE="vericrop_quality_model.onnx"
MODEL_PATH="${MODEL_DIR}/${MODEL_FILE}"

echo -e "${GREEN}VeriCrop Model Fetch Script${NC}"
echo "=============================="

# Get download URL from argument or environment
if [ -n "$1" ]; then
    DOWNLOAD_URL="$1"
elif [ -n "$MODEL_DOWNLOAD_URL" ]; then
    DOWNLOAD_URL="$MODEL_DOWNLOAD_URL"
else
    echo -e "${RED}❌ Error: No download URL provided${NC}"
    echo ""
    echo "Please set MODEL_DOWNLOAD_URL environment variable or pass URL as argument:"
    echo "  export MODEL_DOWNLOAD_URL=\"https://your-storage.com/models/${MODEL_FILE}\""
    echo "  $0"
    echo ""
    echo "Or:"
    echo "  $0 https://your-storage.com/models/${MODEL_FILE}"
    echo ""
    echo "For local development, you can convert the existing PyTorch model:"
    echo "  python3 scripts/convert_model_to_onnx.py"
    # exit 1
fi

# Create model directory if it doesn't exist
mkdir -p "${MODEL_DIR}"

# Check if model already exists
if [ -f "${MODEL_PATH}" ]; then
    echo -e "${YELLOW}⚠️  Model file already exists at: ${MODEL_PATH}${NC}"
    read -p "Do you want to overwrite it? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborting."
        # exit 0
    fi
    echo "Backing up existing model..."
    mv "${MODEL_PATH}" "${MODEL_PATH}.backup.$(date +%Y%m%d_%H%M%S)"
fi

# Download the model
echo -e "Downloading model from: ${DOWNLOAD_URL}"
echo -e "Destination: ${MODEL_PATH}"

if command -v curl &> /dev/null; then
    echo "Using curl..."
    curl -L -f -o "${MODEL_PATH}" "${DOWNLOAD_URL}"
elif command -v wget &> /dev/null; then
    echo "Using wget..."
    wget -O "${MODEL_PATH}" "${DOWNLOAD_URL}"
else
    echo -e "${RED}❌ Error: Neither curl nor wget is available${NC}"
    echo "Please install curl or wget and try again."
    # exit 1
fi

# Verify download
if [ ! -f "${MODEL_PATH}" ]; then
    echo -e "${RED}❌ Error: Download failed - model file not found${NC}"
    # exit 1
fi

# Check file size (should be reasonably sized for a ResNet18 model)
MODEL_SIZE=$(stat -f%z "${MODEL_PATH}" 2>/dev/null || stat -c%s "${MODEL_PATH}" 2>/dev/null || echo "0")
if [ "${MODEL_SIZE}" -lt 10000 ]; then
    echo -e "${RED}❌ Error: Downloaded file is too small (${MODEL_SIZE} bytes)${NC}"
    echo "The download may have failed or returned an error page."
    rm -f "${MODEL_PATH}"
    # exit 1
fi

echo -e "${GREEN}✅ Model downloaded successfully${NC}"
echo "  File: ${MODEL_PATH}"
echo "  Size: $(numfmt --to=iec-i --suffix=B ${MODEL_SIZE} 2>/dev/null || echo "${MODEL_SIZE} bytes")"

# Verify it's a valid ONNX model (if Python and onnx are available)
if command -v python3 &> /dev/null; then
    echo ""
    echo "Verifying ONNX model..."
    python3 -c "
import sys
try:
    import onnx
    model = onnx.load('${MODEL_PATH}')
    onnx.checker.check_model(model)
    print('✅ ONNX model is valid')
except ImportError:
    print('⚠️  onnx package not installed - skipping validation')
    print('   Install with: pip install onnx')
except Exception as e:
    print(f'❌ Model validation failed: {e}')
    sys.exit(1)
" || {
        echo -e "${RED}❌ Model validation failed${NC}"
        rm -f "${MODEL_PATH}"
        # exit 1
    }
fi

echo ""
echo -e "${GREEN}✅ Model fetch complete!${NC}"
echo "The ML service is now ready for production deployment."
echo ""
echo "To test the model:"
echo "  docker-compose up ml-service"
echo "  curl http://localhost:8000/health"
