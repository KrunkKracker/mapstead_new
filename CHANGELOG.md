# Changelog

## [0.02] - Unreleased

### Added
- **Beginner-First UX Redesign Phase 2.2h5R7 — Pending Request Authority, Recreation Coverage, Documentation, and Evidence Closure**:
    - **Authoritative Pending Requests**: Refactored `MapViewModel` to use a typed `PendingBasemapRequest` that captures the complete semantic intent (Source, Role, Reason, Generation) when a MapView is not present.
    - **Selection Supersession**: New basemap selections now immediately clear old live styles and reset the fallback policy, preventing stale backup configurations from bleeding into new requests.
    - **Recreation Regression Coverage**: Added comprehensive JVM tests for "Accepted-Backup to New-Selection" flows, verifying that superseded styles are correctly disposed of during deferred transitions.
    - **Preference Protection**: Hardened the preference collection logic to respect active pending requests, preventing redundant load triggers when the repository confirms a change already in flight.
    - **Evidence Closure**: Finalized v0.02 Phase 2.2h5R7 documentation and updated QA results for the latest build.

- **Beginner-First UX Redesign Phase 2.2h5R6 — Recreation Lifecycle, Secondary Disposal Truth, and Evidence Closure**:
    - **Typed Deferred Requests**: Implemented `PendingBasemapRequest` in `MapViewModel` to preserve semantic intent across render session gaps, ensuring the correct style loads immediately on `onMapReady`.
    - **Resilient Fallback Reset**: Deferred requests now reset the fallback policy, ensuring a previous backup failure does not block the next explicit selection's backup eligibility.
    - **Secondary Disposal Truth**: Refactored `SecondaryBasemapController.dispose()` to correctly record `DISPOSED` terminal reasons while preserving earlier `TIMEOUT` or `FAILURE` outcomes.
    - **Recreation Lifecycle Hardening**: Formalized the sequence in `onMapReady` to process pending requests first, followed by standard recreation logic for established sessions.
    - **Evidence Closure**: Synchronized all project documentation and QA results for the v0.02 Phase 2.2h5R6 milestone.

- **Beginner-First UX Redesign Phase 2.2h5R5 — Recreation Lifecycle, Secondary Disposal, Evidence, and Version Closure**:
    - **Recreation State Preservation**: Hardened `MapViewModel` to preserve authoritative status and source truth during MapView disposal/recreation, eliminating "IDLE" resets during rotation.
    - **Explicit Secondary Disposal**: Implemented a formal `dispose()` lifecycle in `SecondaryBasemapController` to permanently close old sessions and block stale repair triggers.
    - **Session-Keyed UI Bridge**: Updated `ResilientBasemapLoader` to isolate attempts by render session, preventing cross-talk between old callbacks and new native maps.
    - **Restricted Secondary Repair**: Formalized repair eligibility rules to ensure only live sessions with Authoritative identity can trigger bounded style reassertions.
    - **Version Consolidation**: Bumped project to v0.02 and consolidated all Phase 2 documentation and QA evidence.

- **Beginner-First UX Redesign Phase 2.2h5R4 — Secondary Repair Transition, Render-Session Retirement, Validation, and Evidence Closure**:
    - **Secondary Repair Transition**: Implemented full state transitions (loading status, requested source) for repairs on secondary surfaces.
    - **Render-Session Retirement**: Main map render sessions are now properly retired on disposal, marking in-flight attempts as DISPOSED.
    - **Deferred Basemap Requests**: Requests made while no MapView is active are deferred until the next render session is ready.
    - **Strict Main Validation**: Added explicit `requestedSourceId` validation to all loading paths in `MapViewModel`.
    - **Production Camera Policy**: Formalized the production idle policy using a specialized helper to drive persistence decisions.
    - **Race Condition Hardening**: Added exhaustive test coverage for loader race conditions and secondary repair lifecycles.

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
