# Untitled — VeriCrop Mini Demo

This repository contains a small end-to-end demo for a VeriCrop-style workflow:
- A Dockerized FastAPI "ml-service" that exposes a /predict endpoint (stub or model).
- A JavaFX desktop "Producer" app (untitled-gui) that uploads images to the ML service and records a simple in-memory blockchain (vericrop-core).

The app is intentionally small and educational — it demonstrates image → ML → ledger flow. This README explains how to run the system, common troubleshooting, and recommended next steps.

---

## Repository layout

- `docker/ml-service/` — FastAPI ML service, Dockerfile and requirements.
- `vericrop-core/` — Java core module containing Block, Blockchain, SupplyChainTx and unit tests.
- `untitled-gui/` — JavaFX GUI module (Producer app).
- `docker-compose.yml` — orchestrates the ml-service container.

---

## Prerequisites

- Git
- Docker & Docker Compose (v2+)
- JDK (17+) — the project builds with current Gradle; Java 17 or newer is fine. (You have Java 23 and that works.)
- Gradle wrapper is included; use `./gradlew` on Windows `.\gradlew` (or run from IntelliJ).
- Internet access for Gradle to download dependencies.

Notes about JavaFX:
- The Gradle JavaFX plugin in `untitled-gui/build.gradle` is configured to pull JavaFX for the version declared. If you run from IntelliJ instead of Gradle you may need to set VM options (instructions below). Using Gradle (`:untitled-gui:run`) avoids manual VM configuration.

---

## Quick start (recommended)

1. Start the ml-service via Docker Compose (from repository root):

   Windows PowerShell:
   ```powershell
   cd C:\Users\bonga\IdeaProjects\untitled
   docker compose up -d ml-service
   ```

2. Verify ML service health:
   ```powershell
   curl http://localhost:8000/health
   ```

   Expected: HTTP 200 with JSON `{"status":"ok","time":...}`

3. Run the GUI via Gradle (this ensures JavaFX is configured by Gradle):
   ```powershell
   .\gradlew :untitled-gui:run
   ```

4. In the GUI:
   - Click "Choose Image" and select an image file.
   - Click "Upload & Create Shipment". The GUI will POST the image to `http://localhost:8000/predict`, show the ML score, and add a block to the in-memory blockchain list.

---

## Run from IntelliJ (if you prefer)

If you use IntelliJ to run the Application, set an Application run configuration:

- Main class: `org.untitled.gui.Main`
- Use classpath of module: `untitled-gui`
- VM options (only if you do NOT run via Gradle and you installed a local JavaFX SDK):
  ```
  --module-path "C:\javafx-sdk-25.0.1\lib" --add-modules javafx.controls,javafx.fxml
  ```
  Important: the flags must go in VM options (double hyphen `--module-path`), not in the Main class field. If you see errors like "Could not find or load main class C:\javafx-sdk-..." the module-path flags were placed in the wrong field.

Preferred: use Gradle run to avoid VM flags.

---

## Building & tests

- Build the whole project:
  ```powershell
  .\gradlew build
  ```

- Run unit tests for core:
  ```powershell
  .\gradlew :vericrop-core:test
  ```

---

## Docker ml-service notes

- If you had to add extra Python dependencies (e.g. `python-multipart`) add them to:
  ```
  docker/ml-service/requirements.txt
  ```
  Then rebuild the image:
  ```powershell
  docker compose build --no-cache ml-service
  docker compose up -d ml-service
  ```

- Tail logs:
  ```powershell
  docker compose logs --follow ml-service
  ```

- Example ml-service activity you may see:
  - `POST /predict 200 OK` — successful prediction
  - `GET /predict 405` — GET not implemented (harmless)
  - `GET /favicon.ico 404` — harmless

---

## Common issues & fixes

- "Could not find or load main class C:\javafx-sdk-...":
  - Cause: module-path/--add-modules flags placed into Main class or Program arguments.
  - Fix: move `--module-path "C:\javafx-sdk-XX\lib" --add-modules javafx.controls,javafx.fxml` into VM options (exactly with `--module-path`, double hyphen).

- "Could not create the Java Virtual Machine":
  - Cause: malformed/unsupported VM options, too-large `-Xmx`, or 32-bit JDK with 64-bit libraries.
  - Fix: check VM options for typos, remove unsupported flags, lower `-Xmx`, ensure 64-bit JDK.

- FXML LoadExceptions (examples encountered):
  - "Unable to coerce 16 to class javafx.geometry.Insets":
    - Cause: using a numeric literal where an `Insets` object was expected.
    - Fix: use explicit `<padding><Insets top="16" .../></padding>` in FXML.
  - "The entity name must immediately follow the '&' in the entity reference.":
    - Cause: using `&` unescaped in XML (e.g. `Upload & Create Shipment`).
    - Fix: escape ampersand as `&amp;` in FXML.

- Type mismatch when creating a block:
  - Error: passing `List<String>` to `addBlock` which expects `List<SupplyChainTx>`.
  - Fix: create `SupplyChainTx` instances and pass `List.of(tx)` or pass an empty typed list `List.<SupplyChainTx>of()`.

---

## Persistence (optional)

The current blockchain is in-memory (demo). If you want the chain to survive restarts you can:

- Add simple JSON save/load methods to `vericrop-core.Blockchain` (serialize `getChain()` with Jackson).
- Add "Save" / "Load" buttons to the GUI to call those methods.
I can prepare and add these if you want — it’s a small change.

---

## Development tips

- Use a feature branch for GUI work:
  ```powershell
  git checkout -b feat/gui-producer
  git add .
  git commit -m "feat: add GUI producer and core blockchain"
  git push -u origin feat/gui-producer
  ```

- To debug networking:
  - Confirm ml-service reachable from host: `curl -F "file=@C:\path\to\img.jpg" http://localhost:8000/predict`
  - Tail container logs: `docker compose logs --follow ml-service`

- Avoid committing:
  - Large binaries (models, dataset)
  - IDE workspace files (`.idea/`), local configs — add to `.gitignore`

---

## Next steps you may want

- Option A — Add blockchain persistence (JSON save/load + UI buttons).
- Option B — Replace stubbed ML with a trained model:
  - I can prepare a Colab notebook for transfer-learning (e.g., Fruits/Quality classifier), export to TorchScript or ONNX, and add instructions to include it in `docker/ml-service`.
- Option C — Improve GUI: add validation/tamper demo, QR export, better progress/notifications.
- Option D — CI: automated tests, Docker image build in GitHub Actions.

Tell me which of the above you'd like me to prepare next and I’ll produce the files/instructions.

---

## Contact / support

If something fails, please paste:
- the full console/stack trace,
- output of `java -version`,
- `docker compose logs --follow ml-service` snippet,
- `./gradlew :untitled-gui:run --stacktrace` output (if running via Gradle).

That info lets me diagnose and provide precise fixes quickly.

---
