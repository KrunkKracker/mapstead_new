# Changelog

## [0.01] - Unreleased

### Added
- **Beginner-First UX Redesign Phase 2.2h5R2 — Camera Identity, Style Restoration, Repair, Test, and Documentation Closure**:
    - **Session-Matched Camera Guard**: Implementing stable "latest-session-wins" programmatic camera control with exact movement identity and gesture cancellation.
    - **Snapshot-Based Style Restoration**: Replacing no-op restoration with authoritative pre-load camera snapshots and reactive event-driven restoration.
    - **Repair Epoch & Loop Prevention**: Implementing bounded authoritative repair logic to prevent recursive style reassertions.
    - **Typed Terminal Tracking**: Hardening failure reporting with TIMEOUT, FAILURE, SUPERSEDED, and DISPOSED states.
    - **Authoritative Secondary Validation**: Refactoring secondary loaders with strict identity truth and separated accepted source attribution.
    - **QA Truth Synchronization**: Correcting documentation statuses and derived test counts across all project artifacts.

- **Beginner-First UX Redesign Phase 2.2h5R2 — Camera Identity, Style Restoration, Repair, Test, and Documentation Closure**:
    - **Session-Matched Camera Guard**: Implemented movement fingerprinting (Target, Zoom, Bearing, Tilt) to ensure programmatic idles only consume matching sessions.
    - **Pre-Load Camera Snapshots**: Authoritative restoration using actual captured position instead of post-load MapLibre state.
    - **Repair Epoch Lifecycle**: Implemented IN_FLIGHT and EXHAUSTED states to prevent recursive repair loops and bounded registry storage.
    - **Typed Terminal Tracking**: Hardened failure reporting with explicit TIMEOUT, PROVIDER_FAILURE, SUPERSEDED, and DISPOSED reasons.
    - **Strict Callback Validation**: Authoritative multi-field identity check for all Main and Secondary map callbacks.
    - **Secondary Attribution Truth**: Separated requested and accepted sources to drive attribution and logo visibility only upon successful style application.
    - **Baseline Restoration**: Restored 33+ JVM unit tests and added comprehensive behavioral coverage for camera and repair epochs.

- **Beginner-First UX Redesign Phase 2.2h5R1 — Final Basemap Runtime and QA Closure**:
    - **Session-Aware Interaction Guard**: Implemented a stable "latest-session-wins" `ProgrammaticCameraController` that correctly cancels suppression on customer gestures and matches idle events to specific movements.
    - **Reactive Style Restoration**: Replaced non-reactive state-flow waits with a typed `AcceptedBasemapStyleEvent` mechanism, ensuring camera restoration runs exactly once and only when identity truth is preserved.
    - **Hardened Terminal Closure**: Implemented a separate terminal-attempt registry to permanently close timed-out or failed attempts, preventing late native successes from reviving failed states.
    - **Authoritative Main-Map Validation**: Enforced multi-field verification (generation, ID, session, source, provider, role, status) for all MapLibre callbacks.
    - **Main Style Repair**: Trigger exactly one bounded authoritative-source repair when a stale native style application is detected on the active render session.
    - **Resilient Secondary Repair**: Refactored the shared secondary loader to assign distinct IDs for primary and backup attempts and perform replacement loads for stale styles.
    - **Picker & Preview Reliability**: Preserved coordinates and camera target during fallbacks in the Property Picker and ensured non-spinning terminal failure states for the Preview Map.
    - **Attribution & Logo Preservation**: Verified bundled MapTiler SVG and refined attribution links with localized accessible semantics and accurate logo-visibility logic.

- **Beginner-First UX Redesign Phase 2.2h5R — Basemap Runtime Closure and Acceptance Evidence**:
    - **Readiness Architecture**: Enforced explicit wait for both initial UserPreferences emission and a real MapView render session before issuing the first concrete load attempt.
    - **Status-Specific Recreation**: Refactored `onMapReady` to handle MapView recreation based on exact BasemapLoadStatus, ensuring requested sources survive rotation.
    - **Authoritative Attempt Validation**: Implemented strict multi-field verification (generation, attempt ID, session, source, role) for all MapLibre style callbacks.
    - **Terminal Attempt Closure**: Introduced typed terminal tracking to permanently close timed-out or failed attempts, preventing stale style application from reviving failed states.
    - **Style Reconciliation**: Enhanced `MapScreen` to inspect rejection results and trigger exactly one bounded repair when a stale native style is applied to the active MapView.
    - **Unique Secondary Identity**: Refactored the shared ResilientBasemapLoader to assign distinct attempt IDs for primary and backup loads, ensuring identity truth for Picker and Preview surfaces.
    - **Stable Programmatic Camera**: Integrated a token-based `ProgrammaticCameraController` to suppress camera interaction events during style loading without recomposition dependencies.
    - **Immediate Selection Polish**: Ensured basemap selections update in-memory UI immediately before async DataStore persistence, maintaining switch functionality even if persistence fails.
    - **Attribution & Logo Preservation**: Verified bundled MapTiler SVG (SHA-256: c2fef...332) and refined attribution links with localized accessible semantics.

- **Beginner-First UX Redesign Phase 2.2h4 — Repository Build Closure and Acceptance Hardening**:
    - **Physical Asset Tracking**: Bundled the official MapTiler SVG logo as a tracked raw resource, ensuring repository buildability.
    - **Race Condition Elimination**: Unified first-load initialization through a single render-session registration path, removing duplicate preference-driven style requests.
    - **Comprehensive Recreation Support**: Refactored the load state machine to handle all MapView recreation scenarios, including rotation during loading or failure states.
    - **Strict Attempt Validation**: Implemented multi-field verification for all style callbacks, ensuring only the correct render session and role can trigger state changes.
    - **Automated Repair Mechanism**: Added native style reconciliation to re-assert the correct provider if a rejected stale MapLibre callback natively changes the map style.
    - **Hardened Programmatic Guards**: Corrected the camera persistence suppression to prevent automated style-load movements from reaching the database.
    - **Enhanced Secondary Resilience**: Updated the shared ResilientBasemapLoader with real identity validation and terminal-failure UI for Picker and Preview surfaces.

- **Beginner-First UX Redesign Phase 2.2h — MapTiler Primary Basemaps and Resilient Fallback**:
    - **MapTiler Integration**: Replaced legacy OpenFreeMap selection with five MapTiler v4 styles: Streets (default), Base, Topo, Satellite Hybrid, and Outdoor.
    - **Resilient Fallback Engine**: Implemented a provider-aware load state machine with a 15-second primary timeout that automatically triggers a matching OpenFreeMap backup on failure.
    - **State Machine Integrity**: Decoupled customer semantic preference from active concrete provider, ensuring temporary fallbacks never overwrite user choices.
    - **Robust State Preservation**: Guaranteed preservation of camera position, bearing, overlays, drafts, and active editing state during basemap switches and fallbacks.
    - **Secure API Configuration**: Implemented a safe local.properties workflow for API keys with centralized URL building and diagnostic redaction.
    - **Reactivity Correction**: Converted `mapRecoveryActive` and basemap status fields into real reactive StateFlow inputs for reliable UI state emission.
    - **Attribution Compliance**: Integrated required MapTiler and OpenStreetMap attribution links into the map interface.

- **Beginner-First UX Redesign Phase 2.2g — Source Truth and Typed State Stabilization**:
    - **Typed State Aggregation**: Refactored `MapViewModel`, `AddPropertyViewModel`, and `GettingStartedViewModel` to use typed batch models for UI state aggregation, eliminating error-prone positional casting.
    - **Architecture Cleanup**: Extracted map-related UI models and state definitions into a dedicated `MapUiModels.kt` file.
    - **Source Truth Sync**: Synchronized `README.md`, `ROADMAP.md`, and `QA_WORKFLOW.md` with the implementation status of Phase 2.2f and resolved technical debt.

- **Beginner-First UX Redesign Phase 2.2f — Property GPS Request-State Closure**:
    - **Independent Request Tracking**: Separated logical location intent from system launcher state, ensuring GPS requests remain functional after cancellations or timeouts.
    - **Authoritative State Cleanup**: Guaranteed clearing of pending location state across all terminal recovery paths, including settings launches and dialog dismissals.
    - **Unified Guided UI Hierarchy**: Streamlined the guided review form to show exactly one photo section and a clear field hierarchy for all new items.
    - **Hardened Production Logging**: Restricted diagnostic camera capture metadata to debug builds, enhancing user privacy in production releases.

- **Beginner-First UX Redesign Phase 2.2e — Property Permission Recovery Closure**:
    - **Authoritative Permission Flow**: Standardized the Property Setup location request to use a dedicated runtime permission launcher with wrapped Activity resolution.
    - **Functional Permission Recovery**: Fixed a defect where the "Retry" action was missing or non-functional after a location permission denial.
    - **Verified Camera Staging**: Hardened camera result handling to accept physically valid images even when the camera app returns a "canceled" boolean, resolving a common OEM failure mode.
    - **Reliable Thumbnail Preview**: Added visible error recovery and explicit URI parsing for creation-flow thumbnails, preventing blank preview states.
    - **Comprehensive Capture Cleanup**: Guaranteed deletion of all temporary camera captures across every discard, cancel, and "Continue Without" path.

- **Beginner-First UX Redesign Phase 2.2d — Property GPS Permission and Camera Preview Closure**:
    - **Fixed Guided Review UI**: Corrected branching in `FeatureDetailSheet` to show `CreationPhotoSection` for all new guided items.
    - **Resilient Activity Restoration**: Moved external-launch state (Purpose, FeatureID, In-flight captures) to the ViewModel to survive rotation while the Camera or Photo Picker is open.
    - **Hardened Error Recovery**: Wrapped photo imports in robust exception handling, ensuring properties and features are never lost due to photo failures.
    - **Atomic Focus Acknowledgement**: Unified camera focus clearing into a single matching operation that validates context tokens and request identity.
    - **Comprehensive Resource Cleanup**: Guaranteed deletion of temporary camera captures across all discard and continue-without paths, including `onCleared()` lifecycle cleanup.

- **Beginner-First UX Redesign Phase 2.2b — Creation Photo Wiring Closure**:
    - **Corrected Feature Photo UI**: Fixed branching logic to ensure all new guided features show the `CreationPhotoSection` instead of standard attachment history.
    - **Isolated Creation Flow**: Separated photo launchers so that guided creation stages photos in-place rather than navigating to the attachment editor prematurely.
    - **Authoritative Resource Cleanup**: Implemented definitive deletion of temporary camera captures for cancelled, discarded, or skipped photo workflows.
    - **Single-Event Navigation**: Migrated property setup to an authoritative outcome-based navigation model to prevent duplicate screen transitions.
    - **Atomic Focus Acknowledgement**: Unified camera focus lifecycle into a single matching operation that validates context tokens and entity IDs.
    - **Review Inset Polish**: Refined Property Review layout with comprehensive safe bottom insets for improved interactive reachability.

- **Beginner-First UX Redesign Phase 2.2a — Creation Lifecycle Closure**:
    - **Unified Property Review Photo UI**: Removed duplicate photo sections on the Property Review screen for a cleaner, authoritative presentation.
    - **Authoritative Photo Cleanup**: Implemented automatic deletion of temporary camera captures upon workflow cancellation, explicit removal, or ViewModel destruction, ensuring no orphaned files are left in storage.
    - **Creation Failure Recovery**: Introduced `PropertySetupOutcome` and `GuidedSaveOutcome` to handle scenarios where an entity is saved successfully but its photo attachment fails. Users can now retry the photo import without recreating the Property or Map Feature.
    - **Safe Drawing Layout**: Migrated the Property Review screen to use safe display insets and `statusBarsPadding`, improving edge-to-edge presentation on modern devices.
    - **Authoritative Keyboard Dismissal**: Standardized focus and keyboard hiding during every transition in the Property Setup wizard, preventing overlapping UI and improving input flow.
    - **Map Focus Acknowledgement**: Hardened the camera focus logic to only acknowledge initial focus after successful application, preventing the "world view" persistence issue and ensuring stable map initialization.
    - **Regression Coverage**: Added 20+ focused tests for creation lifecycle, layout insets, and camera focus reliability.

- **Beginner-First UX Redesign Phase 2.1b — Property Creation Crash Fix**:
    - **Global MapLibre Initialization**: Moved MapLibre configuration to `MapsteadApplication.onCreate()` to prevent initialization exceptions in the Property wizard.
    - **Idempotent MapView Lifecycle**: Hardened `LifecycleManagedMapView` with explicit state tracking to prevent duplicate lifecycle calls (onCreate, onDestroy) during composition and rotation.
    - **Non-Blocking Review Map**: Updated `PropertyLocationPreviewMap` with a style-load fallback and non-blocking architecture, ensuring property creation is not blocked by map failures.
    - **Synchronized Setup State**: Fixed a regression where `pendingLocationPurpose` was out of sync with `SavedStateHandle`, preventing permission rationale from appearing.
    - **Coverage Expansion**: Added 13 new unit tests for candidate confirmation and an instrumented integrity test for the complete located-property create path.
- **Beginner-First UX Redesign Phase 2.1a — Test Coverage Restoration and Logic Hardening**:
    - **Test Restoration**: Restored 33 JVM unit tests to reach full 527-test coverage across MapViewModel and Integrity suites.
    - **Synchronized Purpose**: Hardened `pendingLocationPurpose` to remain in sync between `MutableStateFlow` and `SavedStateHandle`.
    - **Intelligent Dirty State**: Refined `isActualEditorDirty` to only trigger for guided sessions with actual user work (geometry or field edits).
    - **MAP_ONLY Integrity**: Strictly enforced that no system item drafts or records are created for MAP_ONLY policies.
    - **Review Transition**: Automatically end `AddPoint` mode when the guided review form opens, preventing accidental redundant placements.
    - **Camera Repair**: Fixed a bug where `isPointMoveActive` was not being propagated to the UI state.

- **Beginner-First UX Redesign Phase 2.1 — Guided Creation Integrity**:
    - **Session Cancellation**: Implemented authoritative `cancelGuidedCreation` to strictly clear all temporary state, drafts, and dialogs.
    - **Idempotent Saves**: Hardened `saveFeatureWithOptionalItem` to use stable creation graph IDs, preventing duplicate records during retries.
    - **Camera Capture Repair**: Restored `createCameraCapture` as a proper suspend operation, fixing the "Take Photo" regression.
    - **Process Restoration**: Enabled full restoration of guided creation state (preset, phase, tracking toggle) from `SavedStateHandle`.
    - **Save Guard**: Implemented Mutex-based gate to prevent rapid double-save interactions.
    - **UI Polish**: Added icons to preset cards and combined TalkBack descriptions for improved accessibility.

- **Beginner-First UX Redesign Phase 2**:
    - **Add Something Workflow**: Replaced technical geometry-first choices with a single "Add Something" entry point and real-world task categories (Mark a Location, Draw a Route, Outline an Area).
    - **Operational Record Policies**: Implemented AUTOMATIC, OPTIONAL, and MAP_ONLY policies to simplify documentation decisions for beginners.
    - **Automatic Records**: Critical items like Electrical Panels and Utility Meters now automatically create and link service records atomically.
    - **Benefit-Based Choice**: Replaced "Create System Item" with plain-language tracking: "Keep records for this item."
    - **Adaptive Preset Browser**: Introduced a responsive grid/list browser for presets with icons and purpose descriptions.
    - **Guided Review Form**: Designed a focused review form that prioritizes Name and Notes while hiding technical GIS metadata.
    - **Terminology Cleanup**: Removed technical jargon like "Geometry", "Map Feature", and "System Item" from the primary customer-facing guided workflow.

- **Beginner-First UX Redesign Phase 1 Installed Test Correction**:
    - **Existing Property Guard**: Removed "Add Location Later" from the existing-property location flow and added defensive guards to prevent locationless updates for existing records.
    - **Safe Cancellation**: Added a "Cancel" action for existing-property location updates that returns to the dashboard without changes.
    - **Repository Hardening**: Enforced coordinate validation and property existence checks in the location update path.

- **Beginner-First UX Redesign Phase 1 Final Gate**:
    - **Robust Permission Recovery**: GPS permission requests are now recorded *before* launch, ensuring "Retry" and "Open Settings" guidance survives rotation.
    - **Reliable Existing Target Restoration**: Implemented an authoritative `existingPropertyLoaded` flag to trigger re-loading of metadata after process death.
    - **Immediate Map Confirmation**: Map picker now initializes with a confirmable target, allowing users to save location without manual map movement.
    - **Listener Cleanup**: Guaranteed removal of camera-idle listeners when the location picker leaves composition.
    - **Signed Decimal Coordinates**: Manual entry fields now support negative values and decimals, with range validation for GIS integrity.
    - **Public Tile Removal**: Eliminated all remaining references to public demonstration basemap URLs.

- **Beginner-First UX Redesign Phase 1.1b — Property Wizard Lifecycle and Verification Closure**:
    - **Genuine Map Lifecycle**: Refactored `LifecycleManagedMapView` to correctly forward all lifecycle events (pause, stop, destroy), preventing memory leaks and initialization flicker.
    - **Race-Safe Search**: Implemented monotonic generation tracking for address searches, ensuring delayed network results never overwrite more recent queries.
    - **Persistent Denial UX**: Moved GPS permission history into `SavedStateHandle` to preserve "Retry" and "Open Settings" guidance across wizard navigation.
    - **Metadata Integrity**: Hardened the repository to strictly preserve existing property fields (Acreage, Description, Notes) when updating coordinates via the beginner wizard.
    - **Measurement-Aware Accuracy**: GPS accuracy now displays in feet or meters based on the user's measurement system preference.
    - **Graph Rollback Proof**: Added repository tests proving that Property, Map, and Layer records are rolled back atomically if any step in the creation transaction fails.

- **Beginner-First UX Redesign Phase 1.1a**:
    - **Existing Property Support**: Separated New Property and Add Location modes in the setup wizard, ensuring existing property details are preserved when updating locations.
    - **Lifecycle-Safe Mapping**: Implemented `LifecycleManagedMapView` to handle MapLibre lifecycle events correctly, preventing memory leaks in the picker and review screens.
    - **Standardized Basemaps**: Removed all public demo tile references. All mapping surfaces now use the authoritative `BasemapProvider`.
    - **Typed State Model**: Eliminated positional array casting in `AddPropertyViewModel` by using typed state objects and SavedStateHandle for resilient process restoration.
    - **Measurement-Aware Accuracy**: Display candidate location accuracy in feet or meters based on the user's measurement system preference.
    - **Hardenened Atomic Saves**: Updated `PropertyRepository` with a dedicated, idempotent method for updating property locations and creating initial Maps atomically.
    - **Complete GPS Policy**: Restored standard retryable and permanent denial handling in the setup wizard.

- **Beginner-First UX Redesign Phase 1.1 — Property Wizard Integrity**:
    - **Robust State Management**: Migrated to a typed `PropertySetupState` using `SavedStateHandle` for full process-restoration of address queries, manual inputs, and camera state.
    - **Location Confirmation Lifecycle**: Introduced `PropertyLocationCandidate` to ensure that Address, GPS, and Map results require explicit user confirmation before being applied.
    - **Idempotent Creation**: Implemented a stable draft Property ID and hardened `insertPropertyWithDefaultMap` to prevent duplicate record creation during retries or rapid taps.
    - **Lifecycle-Safe Mapping**: Replaced unmanaged demo MapViews with a reusable, lifecycle-aware component that correctly handles Android lifecycle events.
    - **Neutral Property Type**: Ensured that skipping the optional property type uses a neutral "Property" sentinel instead of defaulting to "Home."
    - **Hardenened Transactions**: Added repository tests with forced in-transaction failures to verify atomic rollback of Property, Map, and Layer records.

- **Beginner-First UX Redesign Phase 1 — Simplified Property Setup**:
    - **Property Setup Wizard**: Replaced the dense creation form with a guided 3-step experience (Name, Location, Review).
    - **Atomic Map Creation**: Automatically create a "Property Map" centered on confirmed coordinates during initial setup.
    - **Locationless Support**: Allow creating valid Properties without location context, deferring Map creation until coordinates are provided.
    - **Wizard State Resilience**: Leveraged `SavedStateHandle` to preserve setup progress across rotation and process recreation.
    - **Choose on Map**: Introduced a beginner-friendly full-screen location picker with a fixed center-pin.
    - **Terminology Refinement**: Migrated "Plan" to "Map" in all customer-facing wizard and dashboard labels.

- **Stage 4 Internal Test Fix Pack B1.2 — Initial Camera Persistence Guard**:
    - **Camera Persistence Gate**: Implemented `CameraPersistenceState` to ignore MapLibre startup camera events until authoritative focus is applied.
    - **Default-World Detection**: Added logic to identify and skip "entire world" saved cameras when property or feature context is available.
    - **Automatic Plan Repair**: Corrected plans previously affected by the whole-world persistence bug by automatically saving a repaired camera focus upon reopening.
    - **Programmatic Focus Stability**: Ensured Return to Property and initial loading do not accidentally trigger world-view persistence.
    - **Installed-Device Results**: Recorded keyboard/form passes for Add Property, Create Map, Feature Details, and more.

- **Stage 4 Internal Alpha Candidate A2.4 — Geometry Editing Transitions and Link Baseline Integrity**:
    - **Feature Editor Session Architecture**: Implemented `FeatureLinkEditorSession` to track link state per-feature, ensuring link intent is strictly isolated and cleared across transitions.
    - **Restored Shape Editing**: Allowed transitioning from the Feature Detail sheet directly into line or polygon geometry editing for the same feature, resolving a regression where the sheet blocked interaction.
    - **Point-Move Details Closure**: Ensured the Feature Detail sheet closes when "Move Point" is tapped, preventing it from obscuring the map during relocation.
    - **Baseline-Aware Dirty State**: Refactored `isEditorDirty` to compare against an initial session baseline, preventing false "discard changes" warnings on unchanged linked features.
    - **Link Preservation**: Guaranteed that existing System Item links survive geometry-only edits and save operations.
    - **System Item Dialog Validation**: Added required-field validation for Name and Category in the creation dialog to prevent empty drafts.
    - **Quality Count Reconciliation**: Synchronized QA files with 473 JVM tests and 61 Instrumented methods.

- **Stage 4 Internal Alpha Candidate A2.3 — Persisted Link Initialization and Editor-State Isolation**:
    - **Authoritative Link Loading**: Implemented `initializeSystemItemLinkState()` to load persisted `infrastructureItemId` when opening existing features, ensuring links are visible and preserved.
    - **Isolated Editor Sessions**: Applied `clearSystemItemLinkEditorState()` to all context-changing paths (Save, Cancel, Dismiss, Switch) to prevent state leakage.
    - **Hardened Selection Invariants**: Synchronized link selection and manual drafts to prevent invalid states where a draft was active but unselected.
    - **UI Mutation Removal**: Removed redundant UI-side mutations from `FeatureDetailSheet`, relying solely on the ViewModel's state for link initialization.

- **Stage 4 Internal Alpha Candidate A2.2 — Explicit Link Selection Integrity**:
    - **Explicit Link Selection Model**: Replaced ambiguous linking booleans with a single `SystemItemLinkSelection` source of truth, ensuring mutually exclusive intent.
    - **No Linked Item Honor**: Fixed a defect where System Items were being suggested or created despite an explicit "No linked item" selection.
    - **Typed Draft Callback**: Migrated the Create System Item dialog to a typed `PendingSystemItemInput` model to ensure correct field mapping and prevent data corruption.
    - **Draft Cleanup on Deletion**: Guaranteed that pending System Item drafts are cleared upon successful feature deletion, preventing state leakage.
    - **Terminal Path Audit**: Re-verified that all link and draft state is reset during Property/Plan switching and editor dismissal.
    - **QA Count Reconciliation**: Updated all reporting files to reflect 446 JVM tests and 54 Instrumented methods.

- **Stage 4 Internal Alpha Candidate A2.1 — System Item Draft Integrity and QA Closure**:
    - **System Item Draft Lifecycle**: Refactored `MapViewModel` to handle new System Items as memory-only drafts until the final feature save, ensuring atomic persistence and preventing orphans.
    - **Draft Precedence Rules**: Enforced link-selection priority: Deliberate selection > Manual Draft > Suggested automatic creation.
    - **Terminal Path Cleanup**: Guaranteed draft clearing across feature cancellation, property/plan switching, and editor dismissal.
    - **Correct Field Mapping**: Fixed an issue where emergency instructions were being mapped to the `subtype` field in the repository.
    - **Hardenened Transactions**: Moved all ownership and context validation inside the Room transaction runner in `MapRepository`.
    - **QA & Documentation**: Regenerated build and lint summaries and updated the manual verification plan for final internal testing.

- **Stage 4 Alpha Candidate A2 — Data Integrity and Confirmation Corrections**:
    - **Transactional Example Seeding**: Moved example property creation into a database transaction with deterministic IDs for reliable idempotent installation and full graph removal.
    - **Address Result Confirmation**: Implemented a selection and confirmation workflow for address-to-coordinate lookups, preventing accidental overwrites.
    - **Atomic Feature/System Item Integrity**: Refactored the Create System Item dialog to treat new data as a draft, ensuring map features and linked items are committed together.
    - **Ownership Validation**: Added database-level property ownership checks to the atomic save path in `MapRepository`.
    - **Sanitized Error Handling**: Replaced remaining raw exception and UUID exposures in mapping and attachment paths with user-friendly strings.
    - **Terminology Standardization**: Completed the migration from "Infrastructure" to "Systems & Equipment" and "System Item" across the entire UI.
    - **Generic Label Localization**: Applied localized "Point", "Line", and "Area" labels for unguided feature creation.
    - **Icon Set Hardening**: Updated adaptive icon resources and removed density-specific WebP robots to ensure consistent branding.

- **Stage 4 Alpha Readiness Reconciliation**:
    - **Unique Feature Naming**: Implemented property-wide deterministic naming (e.g., "Well 2") via `FeatureNamingService`.
    - **System Item Policy**: Integrated operational asset policies to suggest linked System Items during feature creation.
    - **Atomic Feature/Item Transactions**: Enforced database-level atomicity for linked feature and equipment records.
    - **Sticky Feature Actions**: Improved IME reliability in the feature editor with sticky Save/Delete footers.
    - **Property-Specific Guidance**: Migrated onboarding checklists to property-scoped sets for multi-property management.
    - **Address Coordinate Tool**: Added address-to-coordinate resolution in property setup.
    - **Example Farm Seeder**: Introduced a comprehensive idempotent demonstration property for alpha users.
    - **Standardized Terminology**: Refined all customer-facing strings to use "Systems & Equipment" and "System Item".
    - **Production Icon Set**: Applied new adaptive launcher assets.
    - **Search Safety**: Ensured "Reveal" action preserves layer lock states.

- **Stage 4 Internal Test Fix Pack B1.1 — Point Move Gesture Wiring**:
    - **Synchronized Gesture Listening**: Updated the MapView touch listener to use `rememberUpdatedState` for point-move activity, ensuring that entering Move Point mode immediately enables handle hit-testing.
    - **Immediate Move Handle**: Rendered the point move handle immediately at the point's current location upon entering Move Point mode, enabling direct dragging without an initial map tap.
    - **Changed-Location Guard**: Implemented an authoritative `hasProposedMove` check using a 1e-8 geographic tolerance. The `Save Move` button is now enabled only when the coordinates have actually changed from their original persisted state.
    - **Pannable Map Restore**: Hardened `ShapeEditTouchHandler` to guarantee that map panning is restored across every exit path, including pointer cancellation, disposal, and editor dismissal.
    - **Correct Archive Order**: Refactored `PropertiesViewModel` to clear the selected property state only after a successful archive operation, preventing false state changes on failure.
    - **Accessibility Semantics**: Added meaningful TalkBack labels for the movable point handle and location markers, moving away from raw coordinate exposures.

- **Stage 4 Internal Test Fix Pack B1 — Edit Visibility and Map Interaction**:
    - **Active Edit Feature Emphasis**: Implemented a dedicated `activeEditFeatureId` in `MapUiState`. Features being edited now receive a distinct magenta highlight that remains visible even when the details sheet is closed and survives style reloads.
    - **Point Move by Drag or Tap**: Saved points can now be moved by both dragging a large handle or tapping a new location on the map. Added a subtle "ghost" marker at the original location and temporarily disable map panning during active drag.
    - **Keyboard & IME Reliability**: Conducted a project-wide audit and applied fixes for focus visibility, logical ImeActions (Next/Done/Search), and proper multiline behavior (Enter as newline) across all customer-editable forms.
    - **Property Archive Confirmation**: Added a mandatory confirmation dialog before archiving a property to prevent accidental hiding. Archiving the currently selected property now safely clears the selection state.
    - **Emergency Card Contrast**: Updated the property dashboard Emergency Items card to use the semantic `errorContainer` / `onErrorContainer` color pair for improved accessibility and importance communication.
    - **Map Recovery Hardening**: Implemented automated health checks for Mapstead map layers. The application now attempts to reinstall missing overlays without resetting the camera. Added a manual recovery dialog with "Return to Property" and "Retry Map" options.
    - **Location Status Chip Polish**: Refined the compact location chip to display status and accuracy on a single baseline with a pill-shaped layout and improved TalkBack semantics.

- **Stage 4 Internal Alpha Candidate A2.4a — Geometry Edit Exit-State Repair**:
    - **Corrected Geometry Exit State**: Centralized the transition out of persisted line and polygon editing via `finishPersistedGeometryEdit`. This ensures that all editing-specific StateFlows and editor targets are purged before attempting to re-select the feature.
    - **Feature Re-selection Restore**: Fixed a defect where saving or canceling a geometry edit would leave the UI in a "locked" state. Features now correctly reopen in their normal detail view after an edit is finished.
    - **Hardenened Re-selection**: Added safeguards to `selectPersistedFeature` to prevent duplicate or conflicting selection calls while an editor is transitioning.
    - **QA Coverage**: Added focused unit tests verifying the complete workflow of selecting, editing, and exiting (via save/cancel) for both lines and polygons.

- **Stage 4 Acceptance Build A1 — QA Reconciliation and Test Isolation**:
    - **Isolated Keyboard Tests**: Refactored `FormKeyboardTest` to ensure independent test cases that establish their own Property and Plan data prerequisites.
    - **Notification-Specific Denial UX**: Introduced dedicated localized strings for notification permission denial, moving away from reused location-permission wording.
    - **Settings-Launch Failure Feedback**: Added UI-level error handling for `openAppSettings` failures, ensuring users receive helpful feedback if system settings cannot be opened.
    - **Conservative Permission Classification**: Adjusted permission determination logic to avoid incorrectly labeling a denial as "Permanent" when a `ComponentActivity` is missing or the rationale state is unknown.
    - **QA Count Reconciliation**: Synchronized all QA reporting files with verified source method counts (452 JVM, 56 Instrumented).
    - **Relationship Resource Cleanup**: Moved hardcoded "No other items found to link" text into standardized string resources.
    - **Root Plan Consolidation**: Replaced the stale Stage 3 implementation plan with an authoritative Stage 4 Acceptance Build plan.
    - **Development Freeze**: Formally stopped new feature development and refactoring to enter the comprehensive User Acceptance Testing phase.

- **Stage 4 Acceptance Build — Final Pre-Test Corrections**:
    - **Multiline Field Semantics**: Restored standard newline behavior in `ReminderEditor`, `InfrastructureItemScreen`, `AttachmentEditorScreen`, and `RelationshipEditorScreen`.
    - **Reliable Activity Lookup**: Implemented `findComponentActivity()` extension to safely unwrap `ContextWrapper` chains.
    - **Complete Location Feedback**: Standardized the display of "Not Requested", "Retryable Denial", and "Permanent Denial" states.
    - **Create Plan IME Stability**: verified that manual coordinate fields remain visible and the "Apply" action remains reachable while the software keyboard is active.

### Fixed
- **Robolectric Package Name**: Corrected assertion failure in `PermissionUtilsTest` caused by package name mismatch in unit tests.
- **Save Blockers**: Fixed redundant validation triggering in coordinate fields using `ImeAction.Done`.
- **Capped Undo History**: Clarified 50-item undo history limit for lines and polygons.
