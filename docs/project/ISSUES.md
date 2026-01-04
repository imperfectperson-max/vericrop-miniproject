# GitHub Issues to Create

The following issues should be created in the repository to track the MVP development progress.

## Issue 1: MVP: Scaffold docker/ml-service (FastAPI + Docker)

**Title:** MVP: Scaffold docker/ml-service (FastAPI + Docker)

**Description:**
Set up the machine learning service infrastructure for the VeriCrop project.

**Tasks:**
- [x] Create FastAPI application with `/predict` endpoint
- [x] Add dummy prediction logic for MVP demo
- [x] Create requirements.txt with FastAPI, Pillow, uvicorn
- [x] Create Dockerfile for docker/ml-service
- [ ] Add model weights or integrate with actual ML model
- [ ] Test endpoint with sample Fruits-360 images
- [ ] Add unit tests for the predict endpoint

**Labels:** MVP, enhancement, ml-service

---

## Issue 2: MVP: Scaffold Java modules (vericrop-core, vericrop-gui)

**Title:** MVP: Scaffold Java modules (vericrop-core, vericrop-gui)

**Description:**
Create the Java module structure for the VeriCrop core logic and GUI application.

**Tasks:**
- [x] Create vericrop-core module with build.gradle
- [x] Create vericrop-gui module with build.gradle and JavaFX dependencies
- [x] Add vericrop-core and vericrop-gui to settings.gradle
- [x] Create minimal JavaFX Hello World in vericrop-gui
- [x] Set up module dependencies (vericrop-gui depends on vericrop-core)
- [ ] Enhance GUI with actual VeriCrop functionality
- [ ] Add integration with blockchain module

**Labels:** MVP, enhancement, java, gui

---

## Issue 3: MVP: Create blockchain core (Block, Blockchain)

**Title:** MVP: Create blockchain core (Block, Blockchain)

**Description:**
Implement basic blockchain functionality for VeriCrop's supply chain tracking.

**Tasks:**
- [x] Create Block.java class with SHA-256 hashing
- [x] Create Blockchain.java class with validation methods
- [x] Implement genesis block creation
- [x] Add methods for adding blocks and validating chain integrity
- [x] Create unit tests (BlockchainTest.java)
- [ ] Add support for supply chain transactions
- [ ] Integrate with ML service results
- [ ] Add persistence layer for blockchain data

**Labels:** MVP, enhancement, blockchain, core

---

## Issue 4: MVP: CI for GHCR build and push

**Title:** MVP: CI for GHCR build and push

**Description:**
Set up continuous integration workflow to build and push the ML service Docker image to GitHub Container Registry.

**Tasks:**
- [x] Create .github/workflows/ci-ghcr.yml workflow file
- [x] Configure workflow to trigger on push to main for docker/ml-service/** changes
- [x] Set up Docker Buildx for multi-platform builds
- [x] Configure GHCR authentication with GITHUB_TOKEN
- [x] Add image tagging (sha-<commit>, main)
- [x] Add documentation for GHCR_TOKEN alternative
- [ ] Test workflow execution on push to main
- [ ] Verify images are published to GHCR
- [ ] Set up image vulnerability scanning

**Labels:** MVP, enhancement, ci-cd, infrastructure

---

## Issue 5: MVP: Docker Compose integration

**Title:** MVP: Docker Compose integration

**Description:**
Integrate all services in docker-compose.yml for local development and testing.

**Tasks:**
- [x] Add ml-service to docker-compose.yml
- [x] Configure port mapping for ml-service (8000:8000)
- [x] Set up volume mounting for model weights
- [ ] Test docker-compose up with all services
- [x] Add health checks for ml-service
- [ ] Document service dependencies
- [ ] Add environment variable configuration
- [ ] Create docker-compose.dev.yml for development

**Labels:** MVP, enhancement, docker, infrastructure

---

## Issue 6: MVP: Dataset loader for Fruits-360

**Title:** MVP: Dataset loader for Fruits-360

**Description:**
Implement dataset loader for Fruits-360 dataset to train and validate the ML model.

**Tasks:**
- [ ] Download Fruits-360 dataset (small/demo version)
- [ ] Create data loader script in docker/ml-service
- [ ] Implement data preprocessing and augmentation
- [ ] Split dataset into train/val/test sets
- [ ] Document dataset structure and usage
- [ ] Add dataset to .gitignore
- [ ] Create sample images for testing
- [ ] Integrate with ML model (if using PyTorch, see legacy implementation)

**Labels:** MVP, enhancement, ml-service, dataset

---

## How to Create These Issues

To create these issues in the GitHub repository, run the following commands or use the GitHub web interface:

```bash
# Issue 1
gh issue create --title "MVP: Scaffold docker/ml-service (FastAPI + Docker)" \
  --body "$(sed -n '/## Issue 1/,/^---$/p' ISSUES.md | tail -n +3 | head -n -1)" \
  --label "MVP,enhancement,ml-service"

# Issue 2
gh issue create --title "MVP: Scaffold Java modules (vericrop-core, vericrop-gui)" \
  --body "$(sed -n '/## Issue 2/,/^---$/p' ISSUES.md | tail -n +3 | head -n -1)" \
  --label "MVP,enhancement,java,gui"

# Issue 3
gh issue create --title "MVP: Create blockchain core (Block, Blockchain)" \
  --body "$(sed -n '/## Issue 3/,/^---$/p' ISSUES.md | tail -n +3 | head -n -1)" \
  --label "MVP,enhancement,blockchain,core"

# Issue 4
gh issue create --title "MVP: CI for GHCR build and push" \
  --body "$(sed -n '/## Issue 4/,/^---$/p' ISSUES.md | tail -n +3 | head -n -1)" \
  --label "MVP,enhancement,ci-cd,infrastructure"

# Issue 5
gh issue create --title "MVP: Docker Compose integration" \
  --body "$(sed -n '/## Issue 5/,/^---$/p' ISSUES.md | tail -n +3 | head -n -1)" \
  --label "MVP,enhancement,docker,infrastructure"

# Issue 6
gh issue create --title "MVP: Dataset loader for Fruits-360" \
  --body "$(sed -n '/## Issue 6/,/^---$/p' ISSUES.md | tail -n +3 | head -n -1)" \
  --label "MVP,enhancement,ml-service,dataset"
```

Or create them manually via the GitHub web interface at:
https://github.com/imperfectperson-max/vericrop-miniproject/issues/new
