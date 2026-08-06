# Beginner-First UX 2.0 — Property Home Title Integrity and Evidence Closure

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
- Replaced one-shot property lookup with a reactive flow from `propertyRepository.getAllProperties()`.
- Home now updates automatically when property details (name, type, address) are edited.
- Preserved `flatMapLatest` isolation and immediate `Loading` emission on ID change.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreen.kt)
- Implemented **Selection-to-State Mismatch Protection**: If `selectedPropertyId` (authoritative navigation state) does not match `s.property.id`, the screen forces a `Loading` state.
- Derived the top-app-bar title directly from the `properties` list and `selectedPropertyId` to eliminate naming races.

### Evidence & Documentation

#### [MODIFY] [qa-results/unit-test-summary.txt](file:///C:/Users/Justi/StudioProjects/mapstead_new/qa-results/unit-test-summary.txt)
- Truthful report of **700 executed tests** (verified by Gradle XML).
- Source @Test count: **700** (690 in `test`, 10 in `testDebug`).
- Consistency: 100%.

#### [MODIFY] [ROADMAP.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/ROADMAP.md) / [README.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/README.md)
- Status updated: **Slice 1 Property Home SOURCE-IMPLEMENTED / PHYSICAL-DEVICE ACCEPTANCE PENDING**.
- Redesign Status: **IN PROGRESS**.

#### [MODIFY] [BEGINNER_FIRST_UX_TERMINOLOGY_IA_DECISION_PACK.md](file:///C:/Users/Justi/StudioProjects/mapstead_new/BEGINNER_FIRST_UX_TERMINOLOGY_IA_DECISION_PACK.md)
- Standardized terminology: "Map Item" -> "Property Item".
- Removed "external approval pending" status.

## Verification Plan

### Automated Tests
- **HomeViewModelTest**:
    - `same-ID property update refreshes Home reactively`: Proves name changes appear without re-selecting.
    - `missing selected property emits NotFound`.
    - `property switching clears prior state and rejects late emissions`.
- **HomeScreenTest**:
    - `selected_property_name_is_displayed`.
    - `mismatched_ready_id_displays_loading`.
    - `authoritative_title_shown_during_mismatch`.

### Quality Gates
- `gradlew :app:compileDebugKotlin` -> SUCCESS.
- `gradlew :app:testDebugUnitTest` -> 700 Passed.
- `gradlew :app:assembleDebug` -> SUCCESS.
- `gradlew :app:lintDebug` -> SUCCESS (0 Errors).

### Manual Verification
- **Physical Device**: Verify no stale data flicker during fast property switching. (PENDING)
