# VeriCrop

VeriCrop — supply-chain + ML demo (JavaFX GUI + ML inference service + simple blockchain core).

Status
- JavaFX skeleton: present in `vericrop-core` and `vericrop-gui` (runs in IntelliJ).
- ML service: present under `docker/ml-service` (contains a simple model).
- This repository skeleton organizes modules and gives a quickstart to run the GUI and ML service.

Prerequisites (local dev)
- IntelliJ IDEA Community Edition (for JavaFX modules)
- OpenJDK 17 (or Java 11+) — match your IntelliJ Gradle settings
- Python 3.11 (for ML notebooks / training)
- Docker & Docker Compose (for containerized ML service)
- Postman or Hoppscotch (to test REST endpoints)
- JavaFX Scene Builder (optional, for FXML editing)

Quickstart (local)
1. Clone:
   git clone <repo-url>
   cd vericrop

2. Java (IntelliJ):
   - Import the Gradle project (`settings.gradle` is provided).
   - Run `vericrop-gui` application configuration (main class: `com.vericrop.gui.MainApp`).
   - If using JavaFX libraries, set VM options to include JavaFX modules (or use OpenJFX dependencies configured via Gradle).

3. ML service (docker):
   - From repo root run:
     docker-compose up --build
   - ML endpoint: http://localhost:8000/predict

4. Test prediction:
   curl -X POST -F "file=@test.jpg" http://localhost:8000/predict

Repository layout (recommended)
- /vericrop-core        -- Java core library (blockchain, data models)
- /vericrop-gui         -- JavaFX GUI module
- /docker/ml-service    -- Dockerized ML inference service (FastAPI or Flask)
- /python/train         -- training notebooks and scripts (PyTorch/TensorFlow)
- /docs                 -- design notes, demo steps
- /scripts              -- local helper scripts (start services, run tests)

Project plan
See PROJECT_PLAN.md for the full week-by-week plan and a compressed 6-week option.

How to use this skeleton
- Developers working on Java can import Gradle modules and start building the blockchain classes in vericrop-core while UI devs wire up screens in vericrop-gui.
- ML developers can work in `python/train` to improve the model and rebuild a Docker image in `docker/ml-service`.

If you want I can:
- Create these files in a branch and open PR(s).
- Make the FastAPI / Dockerfile stub to match your existing docker/ml-service folder.
- Draft the initial GitHub issues (one per week task) as actual issues.
