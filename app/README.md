# Mapstead Android

Mapstead is a local-first property mapping and emergency information tool.

## Current Status: Beginner-First UX (Stage 3)

We are currently in **Stage 3: Map Presentation and Guided Mapping**.

### Stage 2 Completion (Keyboard-Aware Forms)
- **Hardened Validation**: core forms now focus and scroll to the first invalid field upon submission.
- **Snapshot Dirty Detection**: accurately prompts to discard changes only when data has truly changed.
- **Unit-Aware Accuracy**: map feature accuracy supports Feet and Meters with automatic conversion.
- **Form Visibility**: every editable field remains visible above the software keyboard on all devices.
- **Functional Restoration**: restored all Equipment, Support, and Relationship fields to Infrastructure items.

### Active: Stage 3 (Map HUD & Guided Workflows)
- **Compact GPS HUD**: replacing the large location card with a streamlined accuracy chip.
- **Guided Add to Map**: category-based presets (Buildings, Utilities, Boundaries) for non-technical users.
- **Boundary Safety**: one-time legally-defensive acknowledgment for property boundaries.
- **Starter Layers**: optional pre-configured layers for new maps.
- **Map Control Help**: clear plain-language explanations for all map interactions.

### Key Components
- **MapScreen**: full-height MapLibre implementation with coordinated overlays and adaptive layouts.
- **MapViewModel**: centralized state management for features, layers, location, and basemaps.
- **BasemapProvider**: resolution logic for production and fallback map styles.

## QA Workflow
1. `./gradlew testDebugUnitTest` - Run all 351+ JVM tests.
2. `./gradlew assembleDebugAndroidTest` - Verify instrumented test compilation (54+ tests).
3. `./gradlew lintDebug` - Check for code quality issues.
4. Manual verification on device/emulator for MapLibre rendering and workflow animations.

## License
Proprietary. All rights reserved.
