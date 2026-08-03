# Deferred Technical Debt and Refactoring

## Basemap and Workflow Reactive Composition

**Status**: Deferred (Phase 2.2h5R9F)

The following reactive compositions are deferred for future hardening:

1. **MapViewModel location-batch reactive composition**:
   `_locationBatchFlow` directly reads `_showLocationDetails.value` and
   `_hasRequestedLocOnceFlow.value` rather than combining them as flows.

2. **AddPropertyViewModel existing-loaded reactive composition**:
   `_identityBatch` directly reads `savedStateHandle[KEY_EXISTING_LOADED]`
   instead of observing a StateFlow.

## Phase 3A — Unified Item Details

**Status**: NOT STARTED.

Implementing a singular, high-integrity detail surface for all mapped infrastructure items.

## Google Drive Destructive Restore Testing

**Status**: Paused.

Implementation is preserved but customer access is disabled. Robust testing on a variety of Android versions and storage configurations is required before this feature is re-exposed to customers.
