# Mapstead

**Map Your Property. Track Your World.**

Mapstead is a local-first, private property management tool designed for homeowners, small-scale farmers, and rural property owners. It allows you to create high-precision maps of your land, document infrastructure and operational records (wells, septic, electrical), and track maintenance schedules without relying on third-party cloud services.

> [!WARNING]
> This is unreleased development software (v0.03 / versionCode 3).
> Phase 3A overall is in progress with physical-device acceptance pending.
> Beginner-First UX 2.0 Slice 1 (Property Home) is source-implemented with physical-device acceptance pending.

## Beginner-First UX 2.0 Status
- Authoritative [Master Plan](BEGINNER_FIRST_UX_MASTER_PLAN.md) APPROVED.
- Terminology and IA decisions APPROVED.
- Prototype review COMPLETE (Work on isolated prototypes stopped).
- Production Slice 1 (Property Home) SOURCE-IMPLEMENTED.
- Physical-device acceptance PENDING.
- Property Inventory remains blocked.

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
- **Unified Concepts**: Replaces technical jargon with the **Property Item** concept.
- **Property Home**: Task-oriented landing page with clear "Needs Attention" and "Recently Added" summaries.
- **Simplified Setup**: Name, Location, and Review steps with atomic Map creation.
- **Add Something**: Replaces technical geometry-first choices with real-world object categories.

## Technical Foundation
- **Modern Android**: 100% Kotlin, Jetpack Compose, and Navigation 3.
- **Spatialite Data**: Powered by MapLibre and Room (Spatialite-ready schema).
- **Dependency Injection**: Hilt for robust component lifecycle management.
- **Measurement Precision**: Support for both Imperial and Metric systems with GIS-grade coordinate handling.

---
© 2026 JuMaSt Appworks LLC. All Rights Reserved.
