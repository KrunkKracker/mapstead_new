# Beginner-First UX Redesign Phase 2.2h5R9B Walkthrough

This phase completes the basemap resilience work by addressing stale state recovery and ensuring metadata integrity for backup-only scenarios.

### 1. Resilient Stale Recovery
The `onMapReady` logic is now transactional. When the map view is ready, it precisely checks if the pending request matches the current "authoritative" generation. If it's stale, it re-syncs to the current user preference without unnecessary increments.

### 2. Backup Metadata Truth
Loading attempts for backup-only basemaps (e.g., when API keys are missing) now carry the correct `BACKUP` role and `BACKUP` reason from inception, ensuring clear logs and accurate attribution logic.

### 3. Verification & Evidence
Full behavioral coverage is provided by new regression tests and a build-config consistency check, ensuring that the "no-key" state is correctly reported and handled.

# Beginner-First UX Redesign Phase 2 Walkthrough — Task-Oriented Add Something

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
