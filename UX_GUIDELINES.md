# Mapstead Beginner-First UX Guidelines

**JuMaSt Appworks LLC — Authoritative Standards for Stage 4 and Beyond**

## Core Philosophy: The Beginner-First Foundation
Mapstead is designed for property owners who may not be GIS experts or heavy mobile users. The interface must be predictable, forgiving, and helpful without being intrusive.

### Real-World Mental Model
*   **Task-Oriented Entry**: Customers should select the real-world object they want to document (e.g., "Well", "Fence") rather than technical geometry or record types.
*   **Add Something**: Use a single, clear "Add Something" entry point for all mapping and documentation tasks.
*   **Operational Record Policies**:
    *   **AUTOMATIC**: Critical infrastructure (Panels, Meters) automatically creates linked operational records without asking.
    *   **OPTIONAL**: Most items offer a benefit-based choice: "Keep records for this item."
    *   **MAP_ONLY**: Purely spatial features (Boundaries, Gardens) create only the Map feature.

## Language & Communication
*   **Plain Language**: Avoid technical jargon (e.g., use "Outline Area" instead of "Polygon").
*   **Contextual Guidance**: Provide small, relevant tips (e.g., "Tap several points along the route") rather than large, blocking manuals.
*   **No Raw Exceptions**: Technical error text or stack traces must NEVER be displayed to the customer. All errors must map to localized, helpful string resources.
*   **GPS Safety Notices**: Always reinforce that measurements and positions are approximate and intended for reference only.

## Layout & Accessibility
*   **One Clear Primary Action**: Every screen should have exactly one high-emphasis primary button (e.g., "Save", "Create", "Continue").
*   **48dp Touch Targets**: All interactive elements (buttons, icons, checkboxes) must meet the minimum 48x48dp touch target size.
*   **Font Scaling & Responsive Layouts**: Interfaces must remain usable and layout-stable at font scales up to 2.0. Use scrollable containers for all forms.
*   **Labeled Controls**: Never use an icon alone without a text label or a descriptive `contentDescription`.
*   **No Hidden Critical Gestures**: Core functionality must not rely solely on long-press, double-tap, or complex swipes. Provide visible buttons for critical actions.

## Form Design & Keyboard Behavior
*   **Keyboard Policy**:
    *   **Intermediate Fields**: Use `ImeAction.Next` to advance focus.
    *   **Final Single-Line Field**: Use `ImeAction.Done` to perform safe validation and dismiss the keyboard.
    *   **Search Fields**: Use `ImeAction.Search`.
    *   **Multiline Fields**: Use `ImeAction.Default`. Pressing Enter must insert a newline, NOT submit the form or move focus.
*   **Focus Management**: Upon save/validation failure, automatically focus and scroll the first invalid field into view.
*   **Bring-into-View**: Ensure focused fields and sticky bottom actions remain visible and reachable while the IME (keyboard) is open.
*   **Adaptive Forms**: Related short fields (like Latitude/Longitude) must stack vertically on compact screens to maintain legibility.

## Permissions & System Interaction
*   **In-Context Requests**: Request permissions only at the moment they are needed for a specific customer action. NEVER request permissions on app startup.
*   **The Denial Model**:
    *   **Not Requested**: No warning displayed.
    *   **Retryable Denial**: Explain why the feature is unavailable and offer a "Retry" button.
    *   **Permanent Denial**: Explain that the feature must be enabled in Android Settings and offer an explicit "Open Settings" button.
*   **Graceful Degradation**: If a permission (like GPS) is denied, ensure manual alternatives (like address search or manual coordinate entry) remain fully functional.

## Action Safeguards
*   **Destructive-Action Confirmation**: Any action that deletes data or discards significant edits must require an explicit customer confirmation dialog.
*   **Safe Cancellation**: Back navigation and "Cancel" buttons must prompt for confirmation if unsaved changes exist (Dirty Check).
*   **Loading States**: Display clear progress indicators (CircularProgressIndicator or LinearProgressIndicator) during asynchronous operations.
*   **Duplicate-Submission Protection**: Disable primary actions and fields while a save operation is in progress to prevent duplicate records or inconsistent states.
*   **Coroutine Cancellation**: Ensure UI-triggered work is tied to `viewModelScope` or `Lifecycle` to prevent leaks or background work on stale data.
*   **Local-First Data**: All property information, maps, and attachments are stored primarily on your device. Customers are responsible for creating periodic backups to protect against device loss or damage.
*   **Data Availability**: Offline access is a priority. Once a map area is viewed, its basic content should remain available without an active internet connection where supported by the map provider.

## Creation Photo Lifecycle
*   **Staged Deletion**: Temporary camera captures must be deleted immediately if the user removes the staged photo, cancels the creation workflow, or chooses to "Continue Without Photo."
*   **Success Consumption**: Upon successful save, clear the staged URI and token. Do NOT delete the imported attachment.
*   **Failure Recovery**: If an entity saves successfully but its photo attachment fails, the staged photo must be preserved to allow the user to "Retry Photo" without losing their work.
*   **Post-Save Warnings**: Clearly distinguish between entity-save failure and photo-attachment failure. Offer meaningful options (Retry vs. Continue Without) for the latter.
*   **In-Place Staging**: New items should stage photos within the creation review sheet rather than navigating to a separate editor, maintaining a focused setup flow.
*   **Restoration Persistence**: Authority over external-launch state (Camera, Picker) must reside in the ViewModel (`SavedStateHandle`) to ensure that returned results are applied to the correct entity regardless of device rotation or configuration changes.
*   **Result Validation**: Do not trust activity result booleans alone for file-writing operations (e.g. `TakePicture`). Validate the presence and integrity of the expected file before accepting or rejecting the result.
*   **Actionable Failures**: Location and photo failures must be granular and actionable. Offer "Retry", "Open Settings", or "Continue Without" rather than generic error messages.
*   **Preview Reliability**: Always provide visible recovery options (Retry Preview) when thumbnail or media decoding fails, ensuring users can verify their captures without retaking them.
*   **Request Atomicity**: Location and permission requests must be independently tracked. System launcher state must be cleared immediately upon return to ensure Subsequent requests are not blocked by stale flags.

## Navigation & Outcomes
*   **Authoritative Navigation**: ViewModels should emit high-level outcome states. Screens are the sole owners of navigation calls, triggered by observing these outcomes.
*   **Map Reuse**: Always reuse an existing map context (property + plan) from the backstack instead of adding duplicate map screens. Remove intervening detail screens when returning to the map.
*   **Detail Singularity**: Never allow two identical item detail screens to be stacked consecutively.
*   **Selection Preservation**: Navigation to linked documentation records must never clear the active map feature selection. Returning from details should restore the map state exactly.

## Unified Item Details
*   **Read-Only First**: Existing items (Infrastructure and Map Features) must open into a clean, read-only detail screen/sheet. Editing is a secondary action triggered by an explicit "Edit" button.
*   **Information Hierarchy**: Prioritize item name, category, and status/type in a prominent header. High-priority items (like Emergency Instructions) must be elevated to the top.
*   **Beginner-Friendly Geometry**: Use "Marked Location", "Drawn Route", and "Outlined Area" instead of technical spatial terms.
*   **Progressive Disclosure**: Group detailed information into titled sections. Omit empty or unsupported fields to reduce cognitive load.
*   **Relationship Visibility**: Clearly present links between spatial Map Features and documentation Records. Use beginner-facing labels like "Map Locations" or "Documentation Record."
*   **Consistent Action Placement**: Use the Top App Bar overflow for destructive actions (Delete) and keep primary actions (Edit, Show on Map) easily reachable.
*   **Responsive Details**: Constrain detail content width on tablets and large screens to maintain a readable line length, while keeping full width on phones.

## Mapping & GIS Standards
*   **Coordinate Ordering**: Internally and in data exchange, always use `(longitude, latitude)`. In the UI, display as `(Latitude, Longitude)` where appropriate for human reading, clearly labeled.
*   **Point Movement**: Support both dragging and tapping for point relocation. Provide an original-location ghost to maintain context until the move is confirmed.
*   **Editing Emphasis**: Features currently being edited must remain visibly emphasized (e.g., magenta highlight) even if their property detail sheet is closed.
*   **Normalization**: Map bearing must be normalized to `[0, 360)` before being persisted to the database.
*   **Precision**: Coordinates should be displayed with 5-6 decimal places to provide sufficient accuracy without visual clutter.
*   **Camera Reliability**: Programmatic focus changes and resolution fallback (e.g. Return to Property) must never navigate to the whole-world view. Saved cameras at (0,0,0) must be automatically repaired to property or feature bounds.
*   **Process Restoration**: Use `SavedStateHandle` in ViewModels to preserve user input and navigation state across process death and configuration changes.
