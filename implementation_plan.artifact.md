# Beginner-First UX 2.0 — Property Home Selection Integrity and Evidence Closure

Correct the final Property Home selection, reactive-property, test-evidence, and documentation defects.

## Goal Description
This pass ensures that the Property Home screen is robust against navigation races and data updates. It makes the selected property reactive (updating automatically on edit) and prevents mismatched state from being displayed during property transitions. It also finalizes test evidence with explained counts.

## Proposed Changes

- Production implementation of Home selection integrity.
- No database changes.
- Version: **0.03 (3)** preserved.
- Project Root: **C:\Users\Justi\StudioProjects\mapstead_new**

### Dashboard Reliability

#### [MODIFY] [HomeViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeViewModel.kt)
- Replaced one-shot `getPropertyById` with a reactive flow from `propertyRepository.getAllProperties()`.
- Home now updates automatically when property name, type, or address is edited.
- Preserved `flatMapLatest` isolation and immediate `Loading` emission on ID change.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreen.kt)
- Implemented **Selection-to-State Mismatch Protection**: If `selectedPropertyId` (authoritative navigation state) does not match `uiState.property.id`, the screen forces a `Loading` state.
- This prevents stale items/tasks from flickering or being actionable under the wrong property context.
- Ensured top-app-bar title corresponds to the intended property immediately.

### Evidence & Documentation

#### [MODIFY] [qa-results/unit-test-summary.txt](file:///C:/Users/Justi/StudioProjects/mapstead_new/qa-results/unit-test-summary.txt)
- Truthful report of **699 executed tests** (verified by Gradle XML).
- Source @Test count: **689**.
- Explanation of difference: Parameterized geometry and basemap tests, and Robolectric/Suite repetition in `ExampleUnitTest` and `MapBasemapStateMachineTest`.

#### [MODIFY] [ROADMAP.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/ROADMAP.md) / [README.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/README.md)
- Status updated: **Slice 1 Property Home SOURCE-IMPLEMENTED / PHYSICAL-DEVICE ACCEPTANCE PENDING**.
- Redesign Status: **IN PROGRESS**.

---

## Verification Plan

### Automated Tests
- **HomeViewModelTest**:
    - `same-ID property update refreshes Home reactively`: Proves name changes appear without re-selecting.
    - `missing selected property emits NotFound`.
    - `property switching clears prior state and rejects late emissions`.
- **HomeScreenTest**:
    - `selected_property_name_is_displayed`.
    - `primary_actions_are_visible`.

### Quality Gates
- `gradlew.bat :app:compileDebugKotlin` -> SUCCESS.
- `gradlew.bat :app:testDebugUnitTest` -> 699 Passed.
- `gradlew.bat :app:assembleDebug` -> SUCCESS.
- `gradlew.bat :app:lintDebug` -> SUCCESS (0 Errors).

### Manual Verification
- **Physical Device**: Verify no stale data flicker during fast property switching. (PENDING)
