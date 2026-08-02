# Beginner-First UX Redesign Phase 2.2h5R9D — Transactional Pending Consumption, Test Truth, Documentation, and Evidence Finalization

**Status**: COMPLETED
**Date**: 2026-08-02

## 1. Transactional Pending Consumption
- Verified that `PendingBasemapRequest` is consumed only after a successful attempt issuance in `onMapReady`.
- Ensured intent preservation across render session gaps and recreation.

## 2. Test Truth Finalization
- Verified all 640 tests pass for both Normal and No-Key builds.
- Synchronized `BuildConfigConsistencyTest` for Phase 2.2h5R9D.

## 3. Evidence and Documentation
- Updated README, ROADMAP, CHANGELOG, and QA_WORKFLOW to Phase 2.2h5R9D / v0.02 (2).
- Staged APKs, manifests, and transcripts for final review.

# Beginner-First UX Redesign Phase 2.2h5R9C — Preference Confirmation, Stale-Branch Testability, and Evidence Closure

Correct the remaining source-level defects to ensure the basemap implementation is functional, buildable, and verified with accurate identity and attribution.

## Status
- **Phase 2.2h4**: IN PROGRESS
- **Phase 2.2h3**: INCOMPLETE / CORRECTIVE WORK ACTIVE
- **Phase 2.2h2**: INCOMPLETE
- **Phase 2.2h1**: INCOMPLETE
- **Phase 2.2h**: INCOMPLETE
- **Phase 3A**: NOT STARTED
- **Stage 4**: IN PROGRESS
- **Beginner-First UX Foundation**: IN PROGRESS
- **Property Inventory**: BLOCKED

## User Review Required

> [!IMPORTANT]
> **Basemap Initialization Fix**: I am removing the duplicate first-load initiation path in `MapScreen` to ensure the map always starts with a valid render session registered in the ViewModel.
> **Official Logo Asset**: I will physically add the `maptiler_logo.svg` to the repository to ensure the project builds correctly without missing resources.
> **Stale Style Reconciliation**: I am implementing native repair for stale `setStyle` completions to prevent the map from displaying an incorrect provider after a rejected callback.

> [!CAUTION]
> Phase 3A (Unified Item Details) is NOT started. Current status claims in other documents are being corrected.

## Proposed Changes

### 1. Official Logo Asset Addition
Ensure the project is buildable by providing the physical logo asset referenced by the UI.

#### [NEW] [maptiler_logo.svg](file:///C:/Users/Justi/StudioProjects/Mapstead/app/src/main/res/raw/maptiler_logo.svg)
- Download and save the official MapTiler SVG asset.
- Verify its presence and calculate SHA-256 for verification.

---

### 2. Initialization & Recreation Hardening
Eliminate races and ensure a consistent basemap load lifecycle across rotations.

#### [MODIFY] [MapScreen.kt](file:///C:/Users/Justi/StudioProjects/Mapstead/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapScreen.kt)
- Tie `renderSessionId` to the `MapView` instance using `remember(mapView)`.
- Call `viewModel.onMapReady(renderSessionId)` exactly once inside `getMapAsync`.
- Remove the `LaunchedEffect(prefs, mapLibreMap)` call to `requestBasemap`.

#### [MODIFY] [MapViewModel.kt](file:///C:/Users/Justi/StudioProjects/Mapstead/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapViewModel.kt)
- Refactor `onMapReady` to handle all statuses (IDLE, LOADING_PRIMARY, LOADING_BACKUP, LOADED, FAILED) by creating a new session-bound attempt.
- Defer concrete loading in `requestBasemap` if no render session is active.

---

### 3. Unified Validation & Single Timeout
Consolidate attempt verification and timeout ownership.

#### [MODIFY] [MapViewModel.kt](file:///C:/Users/Justi/StudioProjects/Mapstead/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapViewModel.kt)
- Create `validateAttempt()` function for success/failure/timeout callbacks.
- Remove all internal ViewModel timeout jobs; let `BasemapStyleLoader` own the single 15s timer.
- Ensure `handleBasemapLoadSuccess` requires a matching `LOADING` status.

---

### 4. Native Stale Style Reconciliation
Repair the map if a rejected style request natively completes.

#### [MODIFY] [MapViewModel.kt](file:///C:/Users/Justi/StudioProjects/Mapstead/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapViewModel.kt)
- Implement `handleStaleStyleApplied` to re-assert the latest accepted source with a bounded repair attempt.

---

### 5. Camera & Persistence Correction
Ensure programmatic style loads do not create unintended user interaction records.

#### [MODIFY] [MapScreen.kt](file:///C:/Users/Justi/StudioProjects/Mapstead/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapScreen.kt)
- Fix the `isProgrammaticMovement` guard in the camera idle listener.
- Ensure user pans during load win over automated restorations.

---

### 6. Verification & Documentation
Synchronize the source-truth and finalize the QA milestone.

#### [MODIFY] [ROADMAP.md](file:///C:/Users/Justi/StudioProjects/Mapstead/ROADMAP.md)
#### [MODIFY] [README.md](file:///C:/Users/Justi/StudioProjects/Mapstead/README.md)
#### [MODIFY] [CHANGELOG.md](file:///C:/Users/Justi/StudioProjects/Mapstead/CHANGELOG.md)
#### [MODIFY] [QA_WORKFLOW.md](file:///C:/Users/Justi/StudioProjects/Mapstead/QA_WORKFLOW.md)
- Recount verified tests and claims.
- Correct Phase 2.2h/2.2h1 completion status.

## Verification Plan

### Automated Tests
- **Initialization Order**: Verify attempt creation when MapView ready comes before/after preferences.
- **Recreation Recovery**: Prove `LOADING_PRIMARY` on old session becomes a new attempt on new session.
- **Validation Strictness**: Prove success for wrong role/source is rejected.

### Manual Verification
- **Build Pass**: Confirm project compiles and assembles with bundled SVG.
- **Reentry Check**: Leave map while loading -> Reopen -> Confirm style returns.
- **Logo Check**: Verify logo appears on device (Main, Picker, Preview).
