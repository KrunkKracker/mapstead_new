# Deferred Technical Debt and Refactoring

## MapViewModel State Type-Safety Regression (RESOLVED 2026-07-29)

**Status**: Resolved. The `MapViewModel.uiState` composition now uses typed intermediate batch models and named parameters in all `combine` calls, ensuring compile-time type safety and maintainability.

## AddPropertyViewModel State Aggregation (RESOLVED 2026-07-29)

**Status**: Resolved. The `AddPropertyViewModel.uiState` aggregation has been refactored to use typed batch combinations, removing all positional casting.

## Basemap Runtime Closure and Camera Identity (RESOLVED 2026-08-01)

**Status**: Resolved. Implemented session-matched camera controller (fingerprinting), snapshot-based style restoration, repair epoch epochs, and strict callback validation.

## Basemap Runtime Closure and Interaction Guard (RESOLVED 2026-07-31)

**Status**: Resolved. Implemented session-aware programmatic camera controller, reactive style restoration (AcceptedBasemapStyleEvent), terminal attempt closure, and strict main/secondary map validation.

## Official MapTiler Logo Attribution (RESOLVED 2026-07-31)

**Status**: Resolved. The official MapTiler SVG logo is now physically bundled as a tracked raw resource (`maptiler_logo.svg`) and verified to build correctly.

## Google Drive Destructive Restore Testing

**Status**: Paused.

Implementation is preserved but customer access is disabled. Robust testing on a variety of Android versions and storage configurations is required before this feature is re-exposed to customers.
