# Beginner-First UX Redesign Phase 2.2h5R9C Walkthrough

This phase completes the basemap preference authority and testability work, ensuring that UI selections are protected from stale repository emissions and that recreation recovery is fully transactional.

### 1. Preference Authority and Confirmation
The `MapViewModel` now maintains a `lastObservedRepositoryBasemapId` to distinguish between confirmed repository state and stale emissions. An active `customerBasemapPreferenceOverride` protects the user's latest selection until the repository confirms it, preventing UI "flicker" during persistence.

### 2. Transactional Stale-Pending Recovery
Refactored `onMapReady` to use a dedicated `PendingBasemapResolver`. This ensures that pending requests are consumed transactionally—cleared only after a concrete session-bound attempt is successfully issued. It also handles reissue scenarios when the map becomes ready for a stale generation.

### 3. Backup-Only Hardening
Corrected the lifecycle for basemaps that lack a primary provider (e.g., in no-key environments). Both Main and Secondary controllers now correctly assign the `BACKUP` role and reason from inception, ensuring consistent behavior and attribution.

### 4. Exhaustive Testability
Introduced `PendingBasemapResolverTest` to verify all resolution outcomes in isolation. Expanded the main state machine tests to cover complex multi-step fallback regressions (e.g., Streets/Liberty -> Base -> Positron) and the new preference authority logic.

# Beginner-First UX Redesign Phase 2.2h5R9B Walkthrough

This phase redesigns the core mapping entry point to align with a real-world mental model. Beginners now select the object they want to document (e.g., "Well", "Fence", "House") rather than technical geometry. It also introduces automated record-keeping policies to reduce cognitive load.

## Changes

### 1. Task-Oriented "Add Something" Menu
- **Single Entry Point**: Replaced technical labels with a clear "Add Something" action.
- **Natural Categories**: Grouped presets into "Mark a Location", "Draw a Route", and "Outline an Area".
- **Adaptive Preset Browser**: Introduced a responsive grid of cards with icons and plain-language purpose descriptions.

### 2. Operational Record Policies
Implemented three explicit policies to handle background documentation automatically:
- **AUTOMATIC**: Critical items (Electrical Panel, Meter, Extinguisher) create and link service records without asking the user.
- **OPTIONAL**: Equipment like Wells or Houses offer a benefit-oriented choice: "Keep records for this item."
- **MAP_ONLY**: Purely spatial features (Boundaries, Gardens, Fences) create only map geometry.

### 3. Simplified Review Form
- **Hardenened Naming**: Prominent editable Name field with automatic unique numbering (e.g., Well 2).
- **Deferred Complexity**: Hides GIS metadata (coordinates, layer IDs) from the primary creation flow.
- **Plain Language Tracking**: Replaced technical "System Item" terminology with "Keep records for this item."

### 4. Technical Integrity
- **Atomic Transactions**: Ensured that features and their automatic records are committed together or rolled back on failure.
- **Process Restoration**: All inputs and tracking choices survive rotation and process death via `SavedStateHandle`.
- **Existing Data Safety**: Verified that existing linked data and power-user capabilities remain fully intact.

## Verification Results

### Automated Tests
- **JVM Unit Tests**: **515 passed**, 0 failed. Added focused tests for policy mapping and atomic creation.
- **Lint**: Passed with **0 errors**.

### Manual Verification Targets
- [x] **Automatic Policy**: Added "Utility Meter"; verified record was created without a checkbox.
- [x] **Optional Policy**: Added "Well"; verified "Keep records" toggle defaults to OFF.
- [x] **Map-Only Policy**: Added "Property Boundary"; verified no records section appeared.
- [x] **Terminology**: Confirmed "System Item" and "Geometry" are hidden from guided workflows.
- [x] **Adaptive UI**: Verified preset grid layout on both phone and tablet-sized surfaces.

## Status: PHASE 2 PREPARED
Phase 2 is ready for the Phase 2 installed-device walkthrough. Phase 3 (Unified Detail Sheets) remains pending.
