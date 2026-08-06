# Walkthrough - Slice 1 Physical-Acceptance Corrections

This pass resolves critical defects and usability issues identified during physical-device testing of the Slice 1 "Property Home" milestone.

## Key Improvements

### 1. Seamless Onboarding
- **Direct to Home**: Removed the automatic Getting Started popup. Customers now land directly on the Home screen after property creation.
- **Inline Orientation**: Replaced the blocking modal with a compact inline "Add Your First Item" card for empty properties, guiding beginners without interruption.

### 2. Reliable Property Selection & Identity
- **Authoritative Title**: Fixed a race condition where the top app bar could show a previous property's name while switching. The title now derives directly from the authoritative navigation state.
- **Mismatch Protection**: Guaranteed that stale content from a previous property is never displayed. The screen now forces a `Loading` state if the ViewModel data doesn't match the selected property ID.

### 3. Smart Data Entry (Name & Address)
- **Natural Capitalization**: Added `KeyboardCapitalization.Words` to the property name field.
- **Keyboard Hygiene**: Corrected focus and keyboard dismissal behavior. The keyboard now hides reliably when the customer finishes an input or selects an address.
- **Reactive Address Editing**: Re-enabled debounced address suggestions for existing properties, ensuring coordinates are resolved and verified on a map preview before saving.

### 4. Deterministic Map Focus
- **Camera Priority**: Implemented a strict priority (Restoration > Saved Plan > Property Location > Feature Bounds) for initial map focus.
- **World-View Rejection**: The map now rejects "whole-world" saved views if a valid property anchor exists, preventing the jarring reset to a global scale.

## Verification Results

### Automated Tests
- **693 JVM Unit Tests Passed**: Reconciled with XML results (693 executions).
- **Instrumented Test Compilation**: SUCCESS. Verified 74 test methods in the `androidTest` source set.
- **Zero Lint Errors**: Passed with high-integrity baseline.

### Physical-Device Status
> [!NOTE]
> **Status: RETEST PENDING**. All reported Slice 1 findings have been corrected and verified via automation. A final physical-device retest is required to close the Slice 1 gate.

---
**Commit Message**: `fix(ux): close property setup and map acceptance gaps`
