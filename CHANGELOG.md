# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **Product Journey and Final Quality Score updates when running separate controller instances** ([#Issue](link))
  
  Fixed an issue where the Consumer portal's Product Journey panel would not receive updates
  and the Final Quality Score would not be computed when running the three controller simulations
  (LogisticsController, ProducerController, ConsumerController) as separate instances.

  **Root Causes:**
  - The `handleSimulationControlEvent()` method in ConsumerController required `simulationStartTimeMs > 0`
    to compute final quality. When ConsumerController started after the simulation began (or missed
    the START event), this condition failed and quality computation was skipped.
  - The journey display initialization set status to "Waiting" without a mechanism to recover state
    from persisted data or catch up with an active simulation.
  - Status transitions used implicit state checks rather than explicit status values.

  **Changes:**
  - Added fallback timestamp calculation in `handleSimulationControlEvent()`: When receiving a STOP
    event without a valid `simulationStartTimeMs`, the handler now uses the event timestamp minus
    an estimated simulation duration (2 minutes) to compute quality decay.
  - Changed initial status from "Waiting" to "Awaiting Shipment" for clearer user feedback.
  - Enhanced logging throughout the simulation control event handling for better diagnostics.
  - Improved START event handling to directly set journey steps instead of calling
    `initializeJourneyDisplay()` which would reset status to the awaiting state.

  **Files Changed:**
  - `vericrop-gui/src/main/java/org/vericrop/gui/ConsumerController.java`

  **New Tests:**
  - `ConsumerControllerJourneyTest.java` - Tests for simulation control event handling, timestamp
    fallback calculation, and journey update behaviors.
