# Walkthrough — Beginner-First UX 2.0 — Selection Integrity & Evidence Closure

This pass resolves the remaining race conditions during property switching and establishes a clean, reconciled baseline for test evidence.

## Key Improvements

### 1. Authoritative Title Integrity
- **Logic**: The Home screen top app bar now derives the property name directly from the `properties` list using `selectedPropertyId` as the key.
- **Race Protection**: It no longer relies on the `HomeUiState.Ready` payload for the title. This ensures that the title updates immediately upon selection, even if the ViewModel's state is still catching up.

### 2. Selection Mismatch Protection
- **Deterministic Loading**: Implemented a state guard that forces the `Loading` state if `uiState.property.id` does not match the authoritative `selectedPropertyId`.
- **Isolation**: This prevents "stale" tasks, items, or address details from a previous property from flickering on the screen during a switch.
- **Safety**: Property-specific actions and navigation callbacks are suppressed during the mismatch window.

### 3. Reconciled Test Evidence
- **Reconciliation**: Successfully reconciled the source `@Test` method count with the XML execution count.
- **Test Baseline**:
    - **690** tests in `src/test/java`.
    - **10** tests in `src/testDebug/java` (Prototype validation).
    - **700** Total executions verified across 104 XML result files.
- **Evidence**: Created `evidence-staging/property-home-title-closure/` containing detailed class counts and result XMLs for external audit.

### 4. Strategy & Terminology Alignment
- **Terminology**: Standardized on **Property Item** as the singular generic concept. Technical terms like "Map Feature" are strictly internal.
- **Decision Status**: Marked initial navigation and progressive disclosure decisions as **APPROVED** and source-implemented.

## Verification Results

### Automated Tests
- **JVM Tests**: 700 Passed (100% consistency).
- **Instrumented Tests**: Compiled and assembled (72 methods in source).
- **Lint**: Zero errors.

### Physical-Device Status
> [!NOTE]
> **Status: PENDING**. Slice 1 is fully implemented and verified via simulation and automation. Physical-device acceptance is required to close the Slice 1 gate.

---
**Commit Message**: `fix(home): close title race and test evidence`
