# Beginner-First UX 2.0 — Production Shell Build Closure and Property Home Foundation

Repair the incomplete production navigation shell and establish the real-data Property Home foundation.

## Proposed Changes

- Documentation-only planning pass (completed).
- Production implementation of Home destination.
- No database changes.
- No version changes.
- Version: **0.03 (3)** preserved.

### Dashboard

#### [NEW] [HomeViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeViewModel.kt)
- Define `HomeUiState` to hold property data, items, and tasks.
- Implement `HomeViewModel` to observe `PropertyRepository`, `InfrastructureRepository`, and `MaintenanceRepository`.
- Implement property-scoped data loading for "Needs Attention" (overdue/today tasks), "Upcoming" (next 3 tasks), and "Recently Added" (last 5 items).

#### [NEW] [HomeScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreen.kt)
- Implement `HomeScreen` composable with the following sections:
    - **Top Bar**: Property selector with switching action and Settings entry.
    - **Primary Actions**: "Add Something", "Find Something", "Emergency Guide" cards.
    - **Needs Attention**: List of overdue or today's tasks with a clear empty state.
    - **Recently Added**: List of latest property items.
    - **Secondary Actions**: "Edit Property" and "Help Center" buttons.
- Handle state transitions (Loading, Selected, Empty).

### Navigation

#### [MODIFY] [NavigationGraph.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/navigation/NavigationGraph.kt)
- Ensure `HomeScreen` and `HomeViewModel` references are correctly wired. (Current code already references them but files are missing).
- Verify bottom navigation destinations: Home, Map, Items, Tasks.

## Verification Plan

### Automated Tests
- **HomeViewModelTest**:
    - Verify property ID sets correct scoped data.
    - Verify switching properties clears old data and loads new data.
    - Verify "Needs Attention" logic (Overdue + Today).
    - Verify "Upcoming" logic (Future tasks, sorted, limited to 3).
    - Verify "Recently Added" logic (Limited to 5, sorted by creation date).
    - Verify empty states when no items or tasks exist.

### Manual Verification
- App startup reaches Home.
- Property switching updates Home content immediately.
- "Add Something" launches the guided mapping workflow.
- "Find Something" navigates to Items tab.
- "Tasks" navigates to Tasks tab.
- "Emergency Guide" launches Emergency screen.
- Clicking an item in "Recently Added" opens its details.
- Settings button opens Settings.

### Quality Gates
- `gradlew.bat :app:compileDebugKotlin --no-parallel`
- `gradlew.bat :app:testDebugUnitTest --no-parallel`
- `gradlew.bat :app:assembleDebug`
- `gradlew.bat :app:lintDebug --no-parallel`
