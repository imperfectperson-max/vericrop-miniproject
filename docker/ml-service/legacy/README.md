# Legacy ML Service Files

This directory contains the original PyTorch-based implementation from the top-level `ml_service/` directory.

## Files

- `app.py.pytorch` - Original FastAPI app with PyTorch model loading and inference
- `requirements.txt.pytorch` - Original requirements including torch and torchvision
- `Dockerfile.pytorch` - Original Dockerfile with PyTorch dependencies

## Differences from Canonical Version

The canonical version in `docker/ml-service/app.py` is a simpler implementation that:
- Returns dummy scores (no actual ML model)
- Uses simpler dependencies (no PyTorch)
- Has test coverage in `tests/`

The legacy PyTorch version includes:
- Actual PyTorch model loading from `weights/model.pt`
- Image preprocessing with torchvision transforms
- Real inference when model weights are present
- Falls back to dummy scores when weights are missing

## TODO

Reviewers should decide whether to:
1. Keep the simpler canonical version for the MVP demo
2. Merge PyTorch model loading capabilities into the canonical version
3. Use the PyTorch version as canonical instead

The current docker-compose.yml and CI/CD are configured to use the canonical (simpler) version.
