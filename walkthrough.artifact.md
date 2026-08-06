# Walkthrough — Beginner-First UX 2.0 — UX Lab Removal & GPS Integrity

This pass restores a clean production-like installation experience by removing the obsolete debug UX Lab and ensuring GPS-driven property setup uses real device coordinates.

## Key Changes

### 1. UX Lab Launcher Removal
- **Manifest Cleanup**: Removed the `PrototypeLabActivity` from the debug manifest. USB-installed builds now show exactly one "Mapstead" icon.
- **Source Deletion**: Deleted the entire `ui.prototype` package from the `debug` source set and its associated tests from `testDebug`. Git history preserves these explorations if needed for reference.

### 2. Verified GPS Integrity
- **Real Location Binding**: Confirmed that `AddPropertyViewModel` and `LocationTracker` are correctly bound to the real Android Fused Location provider.
- **Removed Simulated Data**: Deleted the hard-coded St. Petersburg prototype address. GPS-selected locations now display as "Current Location" and reflect the phone's actual physical position.
- **Functional Reliability**: Verified that GPS success correctly populates latitude, longitude, and accuracy while maintaining the map preview for visual confirmation.

### 3. Clean-Install Preparation
- **Single Entry Point**: Confirmed the merged debug manifest contains only `MainActivity` as the `MAIN` and `LAUNCHER` activity.
- **No Simulation Residue**: Verified that strings such as "Mapstead UX Lab" and the simulated address are no longer present in executable debug code.

## Verification Results

### Automated Tests
- **690 JVM Unit Tests Passed**: All 690 production tests passed. (10 prototype-specific tests were removed as planned).
- **AddPropertyViewModel Coverage**: Verified GPS candidate logic using real-world coordinate mocks.

### Build Results
- **Kotlin Compilation**: PASS
- **Manifest Merge**: PASS (One launcher confirmed)
- **Lint**: Passed with 0 errors.

---
**Commit Message**: `chore(debug): remove UX Lab and fake location prototype`
