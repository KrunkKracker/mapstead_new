# Walkthrough — Beginner-First UX 2.0 — Property Home Correctness

The Home screen has been finalized to ensure reliable, truthful, and strictly property-scoped behavior using real production data.

## Key Improvements

### 1. Robust Home Architecture
- **Type-Safe State**: Replaced loose state fields with a `HomeUiState` sealed interface (`Loading`, `Ready`, `NotFound`, `Error`).
- **Strict Isolation**: Used `flatMapLatest` and immediate `Loading` emission to ensure switching properties never leaks stale data from the previous selection.
- **Retry Mechanism**: Added a dedicated retry trigger to safely restart repository collection upon failure.

### 2. Maintenance Logic Parity
- **Due Date Authority**: Now uses `nextDueDate` (consistent with the Tasks destination) instead of `serviceDate` for status calculations.
- **Needs Attention**: Highlights tasks that are Overdue or Due Today.
- **Upcoming Section**: Introduced a new "Upcoming" section for future tasks, limited to the next three items with localized date formatting (e.g., "Due tomorrow").
- **Exclusion Rules**: Correctly filters out deleted, completed, and cancelled records (case-insensitively).

### 3. Truthful Data Representation
- **Map-Only Awareness**: The "Nothing added yet" empty state now queries `MapRepository` to check for map-only features. If features exist but infrastructure items don't, a neutral guidance message is shown instead of claiming the property is empty.
- **Recently Added**: Sorted by `createdAt` descending and limited to the five most recent items.
- **Presentation Models**: Decoupled the UI from Room entities using `HomeTaskSummary` and `HomePropertyItemSummary`.

### 4. Beginner-First UX & Accessibility
- **Prominent Primary Action**: Made "Add Something" the strongest visual element (full-width button).
- **Responsive Layout**: Replaced cramped horizontal cards with an adaptive arrangement that supports font scaling up to 2.0 without clipping.
- **Accessible Switcher**: Enhanced the Property Selector with semantic state descriptions and proper touch targets.

## Quality Gates & Verification

### Automated Tests
- **JVM Tests**: 695 Passed (100%).
- **New Coverage**: `HomeViewModelTest` provides deterministic verification of property switching, maintenance sorting, and truthful empty states.

### Build Results
- **Kotlin Compilation**: PASS
- **APK Assembly**: v0.03 Debug successfully produced.
- **Lint**: Passed with 0 errors.

---
**Commit Message**: `fix(home): complete property-scoped beginner home`
