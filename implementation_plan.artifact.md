# Beginner-First UX 2.0 — Property Home Reliability, Accessibility, and Closure

Close Property Home (Slice 1) defects related to state isolation, error retry, and adaptive layout.

## Proposed Changes

- Production implementation of reliability fixes.
- No database changes.
- Version: **0.03 (3)** preserved.

### Dashboard

#### [MODIFY] [HomeViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeViewModel.kt)
- Refactored `uiState` to use `combine(_propertyId, _retryTrigger)` with `flatMapLatest`.
- Ensured `emit(HomeUiState.Loading)` happens immediately on each property/retry cycle to clear stale state.
- Implemented `catch` block inside `flatMapLatest` for in-context Error/Retry support.
- Extracted deterministic maintenance classification helpers (`isTaskActive`, `isOverdueOrDueToday`, `isUpcoming`) using `LocalDate` parameters.
- Standardized on `nextDueDate` as the authority for due states.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreen.kt)
- Implemented adaptive layout for `PrimaryActionsSection` using `LocalConfiguration` and `LocalDensity` fontScale.
- Stack Find Something and Emergency Guide vertically on compact/high-font screens; use Row otherwise.
- Localized all customer-facing text using `strings.xml`.
- Implemented `NeedsAttentionEmptyState` for reassuring feedback when no tasks are due.
- Added `ErrorScreen` with Retry support.
- Improved accessibility with explicit roles and selected semantics.

### Localization

#### [MODIFY] [strings.xml](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/res/values/strings.xml)
- Added missing Home and Maintenance labels (`home_title`, `home_switch_property`, `state_selected`, `maint_overdue`, etc.).

### Tests

#### [MODIFY] [HomeViewModelTest.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/test/java/com/jumastappworks/mapstead/ui/dashboard/HomeViewModelTest.kt)
- Expanded coverage for:
    - Initial Loading state.
    - Property switch isolation (verified no stale ID leakage).
    - Late emission rejection (verified correct item counts after interleaved emissions).
    - Error and Retry flow (Error -> Fix -> Retry -> Ready).
    - Deterministic date classification logic.
    - Map-only content neutral guidance logic.

#### [NEW] [HomeScreenTest.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/androidTest/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreenTest.kt)
- Added focused Compose UI tests for:
    - Property name visibility.
    - Primary action presence.
    - Needs Attention empty state message.

## Verification Plan

### Automated Tests
- **JVM Unit Tests**: `gradlew :app:testDebugUnitTest` (696 passed).
- **Instrumented Tests**: `gradlew :app:assembleDebugAndroidTest`.

### Quality Gates
- `gradlew :app:compileDebugKotlin` -> SUCCESS.
- `gradlew :app:assembleDebug` -> SUCCESS.
- `gradlew :app:lintDebug` -> SUCCESS (Zero errors).

---
## Final Evidence Status

- **JVM tests in source**: ~696 (Verified by Gradle XML).
- **Executed count**: 696.
- **Instrumented source methods**: ~3.
- **Physical-device acceptance**: PENDING review on hardware.
