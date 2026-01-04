# VeriCrop Project Plan

This file outlines the weekly plan you provided, adapted for the repository layout.

Week 1 — Git, Java, Gradle, and environment (8–10 hours)
- Initialize Gradle multi-module project (vericrop-core, vericrop-gui).
- Run JavaFX skeleton app in IntelliJ.

Week 2 — Python & ML environment + dataset (8–12 hours)
- Setup Python 3.11 environment and PyTorch/TensorFlow.
- Download Fruits-360 or PlantVillage dataset.
- Small transfer-learning run (MobileNet/ResNet) and produce inference script/notebook.

Week 3 — ML model → containerized inference (8–12 hours)
- Wrap model in FastAPI exposing POST /predict (image -> JSON).
- Dockerfile for ML service; local test using curl/Postman.

Week 4 — Basic blockchain core in Java (8–12 hours)
- Implement Block, Transaction, Blockchain classes with hashing + validation.
- Unit tests to validate chain integrity.

Week 5 — Integrate Java GUI with ML service (8–12 hours)
- Add image chooser in Producer screen, call ML service, display result.

Week 6 — Create blockchain transactions from Java app (8–12 hours)
- On "Create Shipment", generate dataHash from ML report and add new block.

Week 7 — Logistics view and simulated transfers (8–12 hours)
- Logistics UI, confirm receipt/delivery, persistence (JSON/H2).

Week 8 — Consumer QR + viewer + polishing (8–12 hours)
- QR generation for shipments, consumer viewer to show block timeline.

Week 9 — Sensor / IoT simulation & data hashing (8–12 hours)
- Simulate sensors and add dataHash in blocks.

Week 10 — Testing, integration, and Docker Compose (8–12 hours)
- Compose stack for ML service + DB, integration tests.

Week 11 — UI polish, demo scripts, writeup (8–12 hours)
- Improve visuals, demo steps, slides.

Week 12 — Buffer + final submission
- Fix bugs, finalize README, record demo.

6-week compressed plan
- Combine several weeks into parallel workstreams (Java dev vs ML dev) — suggested 20–30 hrs/week total (detailed schedule omitted here; follow original weekly tasks but overlap Weeks 2–5).

Deliverables
- Gradle multi-module skeleton
- Dockerized ML service (local)
- Training notebook + improved model in `python/train`
- JavaFX GUI able to call `/predict` and create blocks
- docker-compose to run services locally
