# Beginner-First UX 2.0 — Slice 1 Physical-Acceptance Corrections

Correct the concrete defects and usability problems found during physical-device acceptance of Slice 1.

## User Review Required

> [!IMPORTANT]
> **No Automatic Onboarding**: We are removing the automatic Getting Started popup after property creation. Customers will land directly on the Home screen.
> **Keyboard Improvements**: The property-name field will now automatically capitalize words and the keyboard will dismiss reliably when finished.
> **Address Search in Editing**: Existing properties can now be updated using the same debounced address search and map-preview logic used during initial setup.
> **Map Camera Integrity**: We are implementing a deterministic camera priority (Saved Camera > Property Location > Feature Bounds) to prevent the map from defaulting to a whole-world view.

## Proposed Changes

- Documentation-only planning pass (completed).
- Production implementation of physical-acceptance corrections.
- No database changes.
- Version: **0.03 (3)** preserved.

### Onboarding & Home

#### [MODIFY] [NavigationGraph.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/navigation/NavigationGraph.kt)
- Ensure property creation returns to Home without triggering an intermediate Getting Started screen or popup.

#### [MODIFY] [HomeViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeViewModel.kt)
- Refactor property observation to be fully reactive using `propertyRepository.getAllProperties()`.
- Ensure `Ready` state only emits if the `selectedPropertyId` matches the payload.
- Update `showChecklist` logic to prevent automatic popping on first launch after creation.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/dashboard/HomeScreen.kt)
- Derive top-app-bar title directly from the `properties` list and `selectedPropertyId`.
- Suppress content rendering and actions during property ID mismatches (display `Loading`).
- Add "Add Another Property" and "Manage Properties" prominently to the property selector.
- Implement inline empty-property guidance instead of a modal popup.

### Property Setup & Editing

#### [MODIFY] [AddPropertyScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/properties/AddPropertyScreen.kt)
- Update name field to use `KeyboardCapitalization.Words`.
- Implement `KeyboardActions` for `ImeAction.Done` and `ImeAction.Next` to manage focus and keyboard visibility.
- Clear stale focus when advancing setup steps.

#### [MODIFY] [EditPropertyViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/properties/EditPropertyViewModel.kt)
- Implement debounced address search using `AddressLocationResolver`.
- Ensure address selection updates coordinates and indicates resolution status.

#### [MODIFY] [EditPropertyScreen.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/properties/EditPropertyScreen.kt)
- Update UI to show address suggestions and resolve coordinates, matching the creation flow.
- Improve keyboard and focus handling for name and address fields.

### Map & Camera

#### [MODIFY] [MapViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapViewModel.kt) / [MapCameraResolver.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/mapping/MapCameraResolver.kt)
- Implement deterministic camera focus priority.
- Add world-view rejection: Ignore saved cameras with broad zooms if a property location or feature bounds exist.
- Ensure camera state is strictly property- and plan-scoped.

## Verification Plan

### Automated Tests
- **JVM Unit Tests**:
    - `HomeViewModelTest`: Reactive property updates, mismatch protection, isolated collection.
    - `AddPropertyViewModelTest`: Keyboard configuration, GPS coordinate integrity.
    - `MapCameraResolverTest`: Priority logic, world-view rejection, property-scoped isolation.
- **Instrumented Tests**:
    - `HomeScreenTest`: Authoritative title, mismatch loading, selector semantics.
    - `AddPropertyIntegrityTest`: Name field capitalization and keyboard actions.

### Quality Gates
- `gradlew :app:testDebugUnitTest` -> 700+ Passed.
- `gradlew :app:assembleDebug` -> SUCCESS.
- `gradlew :app:lintDebug` -> SUCCESS (0 Errors).

### Physical-Device Retest
- Verify no automatic Getting Started popup after creation.
- Verify natural word capitalization in property name.
- Verify keyboard dismisses on Done/Next.
- Verify address search works when editing existing property.
- Verify map doesn't show whole world on return.
