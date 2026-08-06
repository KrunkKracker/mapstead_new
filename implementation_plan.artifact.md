# Implementation Plan — Beginner-First UX 2.0: Property Home Reliability and Acceptance

Correct the remaining runtime, accessibility, and documentation defects for Property Home (Slice 1) to establish a trustworthy production baseline.

## User Review Required

> [!IMPORTANT]
> **Error Handling Refactor**: We are moving error handling inside the property collection flow. This ensures that a single failure doesn't kill the dashboard stream and allows the "Retry" action to work reliably in-context.
> **Property Isolation**: We are ensuring that switching properties immediately clears the previous state and ignores any late emissions, preventing stale data leakage.
> **Adaptive UI**: The primary dashboard actions (Find / Emergency) will now adaptively stack vertically on narrow screens or high font scales to maintain readability.

## Proposed Changes

- Production implementation of Home reliability and accessibility.
- No database changes.
- Version: **0.03 (3)** preserved.
- JVM Tests: Target 695+ successful executions.
- Lint: Target Zero Errors.

### Dashboard Reliability

#### [MODIFY] [HomeViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeViewModel.kt)
- Refactor `uiState` to use `flatMapLatest` on a combined `propertyId` and `retryTrigger` flow.
- Emit `Loading` immediately on each property/retry cycle.
- Move `catch` inside the collection block to allow for recoverable error states.
- Extract maintenance classification helpers (`isTaskActive`, `isOverdueOrDueToday`, `isUpcoming`) that accept a `LocalDate` to ensure deterministic testing.
- Standardize on `nextDueDate` as the source of truth for due states.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreen.kt)
- Implement adaptive layout for the Find Something and Emergency Guide buttons using `LocalConfiguration` and stacking vertically on narrow screens.
- Implement localized date formatting in `formatDueDateLocalized`.
- Add `NeedsAttentionEmptyState` to provide reassuring feedback when no tasks are due.
- Replace hard-coded strings with resources from `strings.xml`.
- Ensure 48dp touch targets and logical TalkBack traversal.

### Localization

#### [MODIFY] [strings.xml](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/res/values/strings.xml)
- Add missing Home and Maintenance labels (`home_title`, `home_switch_property`, `home_no_tasks_attention`, `maint_overdue`, etc.).

### Quality and Evidence

#### [MODIFY] [qa-results/](file:///C:/Users/Justi/StudioProjects/mapstead_new/qa-results/)
- Synchronize all summaries with the actual results of the v0.03 (3) build.
- Truthfully report the executed JVM test count from Gradle XML.

#### [MODIFY] [ROADMAP.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/ROADMAP.md) / [README.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/README.md)
- Update status to reflect that Slice 1 is source-implemented with physical-device acceptance pending.

## Verification Plan

### Automated Tests
- **HomeViewModelTest**:
    - Initial state is Loading.
    - Property switch isolation (verified by state sequence and ID checks).
    - Late emission rejection.
    - Recoverable Error -> Retry flow.
    - Deterministic maintenance classification.
- **HomeScreenTest**:
    - Property name visibility.
    - Primary action presence.
    - Needs Attention empty state message.
    - Narrow layout stacking behavior.

### Quality Gates
- `gradlew.bat --stop`
- `gradlew.bat :app:compileDebugKotlin --no-parallel`
- `gradlew.bat :app:testDebugUnitTest --no-parallel`
- `gradlew.bat :app:assembleDebug`
- `gradlew.bat :app:lintDebug --no-parallel`

### Manual Verification
- **Physical Device**:
    - Verify property switching never flashes old content.
    - Verify Retry recovers from failure.
    - Verify 2.0 font scaling usability.
    - Verify TalkBack semantics on the property selector.
