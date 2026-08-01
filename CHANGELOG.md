# Changelog

## [0.01] - Unreleased

### Added
- **Beginner-First UX Redesign Phase 2.2h5R3 — Loader Atomicity, Repair Epoch, Camera Idle, Secondary Repair, and Evidence Closure**:
    - **Atomic Loader Outcomes**: Implemented strict state controller in `BasemapStyleLoader` to eliminate Success/Timeout/Failure race conditions.
    - **Refined Repair Epoch**: Ensured only `REPAIR` attempts exhaust epochs, preserving future repair eligibility for normal successful loads.
    - **Camera Snapshot Lifecycle**: Automated cleanup of authoritative pre-load snapshots on supersession, terminal failure, and session disposal.
    - **Session-Scoped Camera Isolation**: Hardened `ProgrammaticCameraController` with render-session ownership for gesture cancellation and disposal.
    - **Secondary Repair Epochs**: Rebuilt `SecondaryBasemapController` with explicit `IN_FLIGHT` and `EXHAUSTED` states to prevent repair loops.
    - **Secondary Attribution Truth**: Strictly separated `requestedSourceId` and `acceptedSourceId` to drive UI attribution only upon success.
    - **Baseline Restoration**: Restored 590+ JVM unit tests and added comprehensive coverage for loader races and epoch transitions.

- **Beginner-First UX Redesign Phase 2.2h5R2 — Camera Identity, Style Restoration, Repair, Test, and Documentation Closure**:
    - **Session-Matched Camera Guard**: Implemented movement fingerprinting (Target, Zoom, Bearing, Tilt) to ensure programmatic idles only consume matching sessions.
    - **Pre-Load Camera Snapshots**: Authoritative restoration using actual captured position instead of post-load MapLibre state.
    - **Repair Epoch Lifecycle**: Implemented IN_FLIGHT and EXHAUSTED states to prevent recursive repair loops and bounded registry storage.
    - **Typed Terminal Tracking**: Hardened failure reporting with explicit TIMEOUT, PROVIDER_FAILURE, SUPERSEDED, and DISPOSED reasons.
    - **Strict Callback Validation**: Authoritative multi-field identity check for all Main and Secondary map callbacks.
    - **Secondary Attribution Truth**: Separated requested and accepted sources to drive attribution and logo visibility only upon successful style application.
    - **Baseline Restoration**: Restored baseline JVM unit tests and added comprehensive behavioral coverage for camera and repair epochs.

- **Beginner-First UX Redesign Phase 2.2h5R1 — Final Basemap Runtime and QA Closure**:
    - **Session-Aware Interaction Guard**: Implemented a stable "latest-session-wins" `ProgrammaticCameraController` that correctly cancels suppression on customer gestures and matches idle events to specific movements.
    - **Reactive Style Restoration**: Replaced non-reactive state-flow waits with a typed `AcceptedBasemapStyleEvent` mechanism, ensuring camera restoration runs exactly once and only when identity truth is preserved.
    - **Hardened Terminal Closure**: Implemented a separate terminal-attempt registry to permanently close timed-out or failed attempts, preventing late native successes from reviving failed states.
    - **Authoritative Main-Map Validation**: Enforced multi-field verification (generation, ID, session, source, provider, role, status) for all MapLibre callbacks.
    - **Main Style Repair**: Trigger exactly one bounded authoritative-source repair when a stale native style application is detected on the active render session.
    - **Resilient Secondary Repair**: Refactored the shared secondary loader to assign distinct IDs for primary and backup attempts and perform replacement loads for stale styles.
    - **Picker & Preview Reliability**: Preserved coordinates and camera target during fallbacks in the Property Picker and ensured non-spinning terminal failure states for the Preview Map.
