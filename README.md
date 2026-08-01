# Mapstead

**Map Your Property. Track Your World.**

Mapstead is a local-first, private property management tool designed for homeowners, small-scale farmers, and rural property owners. It allows you to create high-precision maps of your land, document infrastructure and operational records (wells, septic, electrical), and track maintenance schedules without relying on third-party cloud services.

> [!WARNING]
> This is unreleased development software (v0.02). It is currently in a basemap runtime hardening pass (Phase 2.2h5R5).

- **Phase 2.2h5R5 (Recreation Lifecycle and Version Closure)**: IN PROGRESS.
- **Phase 2.2h5R4 (Secondary Repair and Session Retirement)**: COMPLETE.
- **Phase 2.2h5R3 (Loader Atomicity and Repair Epoch)**: COMPLETE.
- **Phase 2.2h5R2 (Camera and Repair Closure)**: COMPLETE.
- **Phase 2.2h5R1 (Basemap Runtime Closure)**: COMPLETE.
- **Phase 2.2h (Basemap Implementation)**: IMPLEMENTED / INSTALLED ACCEPTANCE PENDING.
- **Phase 3A (Unified Item Details)**: NOT STARTED.

## Core Features
- **MapTiler Integration**: High-quality Streets, Topo, and Satellite Hybrid imagery with resilient OpenFreeMap fallback.
- **Guided Onboarding**: 3-step Property Setup Wizard for rapid initialization.
- **Task-Oriented Mapping**: "Add Something" workflow based on real-world tasks (Mark a Location, Draw a Route, Outline an Area).
- **Intelligent Tracking**: AUTOMATIC, OPTIONAL, and MAP_ONLY operational record policies.
- **Infrastructure & Operational Records**: Document generators, pumps, and utility panels with maintenance history and emergency instructions.
- **Emergency Readiness**: One-tap access to emergency shut-off locations and instructions.
- **Local-First Architecture**: All property data, maps, and photos stay on your device. Google Drive customer access is currently paused and hidden.
- **Adaptive UI**: Optimized for Phones, Tablets, and Foldable devices.

## Beginner-First UX Foundation
The redesign prioritizes task-oriented entry points and plain language:
- **Simplified Setup**: Name, Location, and Review steps with atomic Map creation.
- **Add Something**: Replaces technical geometry-first choices with real-world object categories.
- **Terminology Cleanup**: Hides technical jargon like "Map Feature" and "System Item" from guided workflows.

## Technical Foundation
- **Modern Android**: 100% Kotlin, Jetpack Compose, and Navigation 3.
- **Spatialite Data**: Powered by MapLibre and Room (Spatialite-ready schema).
- **Dependency Injection**: Hilt for robust component lifecycle management.
- **Measurement Precision**: Support for both Imperial and Metric systems with GIS-grade coordinate handling.

---
© 2026 JuMaSt Appworks LLC. All Rights Reserved.
