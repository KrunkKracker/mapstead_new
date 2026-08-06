# Walkthrough - Property Home Reliability & Accessibility (Phase 3A2.1)

Established the production-ready baseline for the Beginner-First Home screen, resolving critical reliability and accessibility defects.

## Key Changes

### 1. Reliable Property Switching
- **Isolation**: Refactored `HomeViewModel` to ensure that selecting a new property immediately emits `Loading` and cancels any active data collection from the previous property.
- **Late Emission Rejection**: Implemented logic that ignores late repository responses from previous property contexts, preventing flickering or data leakage between properties.

### 2. Recoverable Error Handling
- **Retry Mechanism**: Implemented a generation-based retry trigger. Customers can now tap "Retry" on an error screen to restart data collection without leaving the page.
- **Resilience**: Integrated `catch` handling directly into the property-scoped stream, preventing one failed request from completing the entire UI state flow.

### 3. Adaptive & Accessible UI
- **Responsive Layout**: The primary actions (Find Something, Emergency Guide) now adapt to screen width and font scale. On narrow devices or at 200% font scale, they stack vertically to maintain readability and 48dp touch targets.
- **Full-Width Add**: Kept "Add Something" as the most prominent, full-width primary action.
- **Localization**: Removed all hard-coded strings. All text is now managed via `strings.xml`.
- **Reassuring Empty States**: Added specific guidance for properties with no items or only map features.

### 4. Deterministic Maintenance Logic
- **nextDueDate Authority**: Standardized on `nextDueDate` for all due-state classifications (Overdue, Due Today, Upcoming).
- **Status Filtering**: Properly excludes "Completed" and "Cancelled" tasks (case-insensitive).
- **Pure Helpers**: Extracted pure classification functions that use explicit `LocalDate` parameters for deterministic unit testing.

## Verification Results

### Automated Tests
- **696 JVM Unit Tests Passed**: Added 7 new exhaustive tests to `HomeViewModelTest`.
- **Instrumented Tests Compiled**: Verified `HomeScreenTest` in the `androidTest` source set.
- **Zero Lint Errors**: Passed strict lint checks on all new code.

### Manual Verification Path (Physical Device)
1. Launch Mapstead with an existing property.
2. Verify "Needs Attention" correctly reflects overdue tasks.
3. Switch properties using the top-bar selector.
4. **Verify**: The UI immediately shows a progress indicator and then the correct new data (Isolation).
5. Increase system font scale to 2.0.
6. **Verify**: "Find Something" and "Emergency Guide" stack vertically and remain readable (Adaptive).
7. Tap "Add Something".
8. **Verify**: The real guided mapping workflow launches correctly (Navigation).
