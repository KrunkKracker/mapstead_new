# Beginner-First UX 2.0 — Remove UX Lab and Restore Clean Physical-Device Testing

Remove the obsolete debug UX Lab launcher and fake prototype runtime code to ensure debug builds reflect only the real Mapstead application.

## User Review Required

> [!IMPORTANT]
> **Data Removal**: This pass deletes the prototype runtime source code. Git history preserves the implementation if needed for reference, but it will no longer be compiled into the debug APK.
> **Single Launcher**: After this change, only one "Mapstead" icon will appear on the device launcher. The "UX Lab" icon will be removed.
> **GPS Verification**: Property Setup will exclusively use actual phone location coordinates. The simulated "St. Petersburg" address will no longer exist in the code.

## Proposed Changes

- Remove debug-only prototype activity and runtime code.
- Ensure a single launcher entry point in the merged manifest.
- Verify real GPS provider binding and behavior.
- Version: **0.03 (3)** preserved.

### Build and Manifest

#### [MODIFY] [app/src/debug/AndroidManifest.xml](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/debug/AndroidManifest.xml)
- Remove `PrototypeLabActivity` and its `<intent-filter>` containing `android.intent.action.MAIN` and `android.intent.category.LAUNCHER`.

### Source Deletion

#### [DELETE] [app/src/debug/java/com/jumastappworks/mapstead/ui/prototype/](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/debug/java/com/jumastappworks/mapstead/ui/prototype/)
- Remove all prototype Compose screens, navigation, and models.

#### [DELETE] [app/src/testDebug/java/com/jumastappworks/mapstead/ui/prototype/](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/testDebug/java/com/jumastappworks/mapstead/ui/prototype/)
- Remove obsolete prototype unit tests.

### Verification and Clean-up

#### [VERIFY] [LocationTracker.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/data/mapping/LocationTracker.kt)
- Confirmed use of `FusedLocationProviderClient`.
- Confirmed binding in `RepositoryModule.kt`.

#### [VERIFY] [AddPropertyViewModel.kt](file:///C:/Users/Justi/StudioProjects/mapstead_new/app/src/main/java/com/jumastappworks/mapstead/ui/properties/AddPropertyViewModel.kt)
- Confirmed `requestGpsLocation` uses `locationProvider.getCurrentLocation()` and sets the candidate with the real coordinates and accuracy.
- Confirmed it does not contain any hard-coded addresses.

---

## Verification Plan

### Automated Tests
- **JVM Unit Tests**: `gradlew :app:testDebugUnitTest` (700 tests in source, target consistency).
- **AddPropertyViewModelTest**: Verify GPS results use provided latitude/longitude/accuracy.

### Quality Gates
- `gradlew.bat --stop`
- `gradlew.bat :app:processDebugMainManifest` -> Verify only `MainActivity` is a launcher.
- `gradlew.bat :app:compileDebugKotlin` -> SUCCESS.
- `gradlew.bat :app:testDebugUnitTest` -> SUCCESS.
- `gradlew.bat :app:assembleDebug` -> SUCCESS.
- `gradlew.bat :app:lintDebug` -> SUCCESS (0 Errors).

### Manual Verification (User Action Required)
1. Uninstall existing Mapstead versions from the device (erases local data).
2. Install the new debug APK.
3. Confirm exactly one launcher icon: **Mapstead**.
4. Confirm "Use My Current Location" in Property Setup accurately reflects physical position.
