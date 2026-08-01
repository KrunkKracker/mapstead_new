# UX Redesign Resources: Presets and Policies

## Preset Policy & Configuration Table

| Preset ID | Category | Name | Layer | Policy | Suggestion |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **GENERATOR** | Location | Generator | Utilities | AUTOMATIC | Maintenance, Emergency |
| **ELEC_PANEL** | Location | Electrical Panel | Utilities | AUTOMATIC | Connections |
| **WELL_PUMP** | Location | Well Pump | Utilities | AUTOMATIC | Maintenance, Documents |
| **FIRE_EXT** | Location | Fire Extinguisher | Safety | AUTOMATIC | Reminders |
| **HVAC** | Location | HVAC Unit | Utilities | AUTOMATIC | Maintenance |
| **UTILITY_METER**| Location | Utility Meter | Utilities | AUTOMATIC | Account Info |
| **WELL** | Location | Well | Utilities | OPTIONAL | "Keep records?" |
| **WATER_VALVE** | Location | Water Valve | Utilities | OPTIONAL | "Keep records?" |
| **SEPTIC_ACCESS**| Location | Septic Access | Utilities | OPTIONAL | Maintenance |
| **HOUSE / SHED** | Area | Building | Structures | OPTIONAL | "Keep records?" |
| **POOL / POND** | Area | Water Feature | Landscape | OPTIONAL | Maintenance |
| **GATE** | Location | Gate | Structures | OPTIONAL | Maintenance |
| **BOUNDARY** | Area | Property Boundary | Boundary | MAP_ONLY | Visual only |
| **FENCE** | Route | Fence | Structures | MAP_ONLY | Visual only |
| **DRIVEWAY** | Route/Area| Driveway | Structures | MAP_ONLY | Visual only |
| **TRAIL / PATH** | Route | Trail | Landscape | MAP_ONLY | Visual only |
| **UTILITY_LINE** | Route | Pipe / Wire | Utilities | OPTIONAL | "Keep records?" |

---

## Onboarding Specification (First Launch)

### Screen 1: Welcome
- **Visual**: Large Property Icon.
- **Title**: Welcome to Mapstead.
- **Body**: Map your property, track equipment maintenance, and store important documents all in one local, private place.

### Screen 2: Mark Your World
- **Visual**: Simplified Map Animation.
- **Title**: Mark What Matters.
- **Body**: Map your wells, septic tanks, property lines, and anything else that exists on your place.

### Screen 3: Stay Prepared
- **Visual**: Checklist Icon.
- **Title**: Maintenance & Emergencies.
- **Body**: Set service reminders and store emergency shut-off instructions for generators, panels, and pumps.

### Screen 4: Local & Private
- **Visual**: Shield/Lock Icon.
- **Title**: Your Data, Your Control.
- **Body**: Your property information is stored primarily on this device. Remember to create periodic backups to stay safe.

---

## Wireframe Detail Specifications

### W1: Property Setup (Phase 1 COMPLETE)
- **Primary Buttons**: [Address Search] | [Current Location] | [Choose on Map].
- **Secondary**: "Add Location Later" (Text link).
- **Behavior**: [Choose on Map] shows full screen map with a center-crosshair and "Confirm Property Location" button.

### W2: Task-Oriented Add Menu (Phase 2 COMPLETE)
- **Primary Action**: Add Something.
- **Categories**: 
    1. **Mark a Location**: Objects at one spot.
    2. **Draw a Route**: Fences, lines, paths.
    3. **Outline an Area**: Buildings, boundaries, ponds.
- **Visuals**: Adaptive grid of cards with icons and descriptions.

### W3: Unified Item Details
- **Dynamic Header**: Sets background color based on category (e.g., Green for Landscape, Blue for Utilities, Red for Safety).
- **Status Chip**: Simple "Active" (Green dot) or "Needs Service" (Orange dot).
- **Maintenance Card**: Mini-list of last 3 records + "Add Record" button.
- **Connections Section**: Plain language list:
    - "Power comes from: [Main Panel]"
    - "Supplies: [House]"
- **Advanced Section (Collapsed)**: 
    - Coordinates: 45.1234, -81.5678
    - Accuracy: ± 9.4 ft (Phone GPS)
    - Map Layer: Utilities

---

## Error & Interruption Handling

| Event | System Response | Customer Feedback |
| :--- | :--- | :--- |
| **No GPS Permission** | Fallback to Address/Map. | "GPS is disabled. You can still pick a location manually." |
| **Address Fail** | Show manual coords fields. | "We couldn't find that address. Try picking it on the map." |
| **Atomic Save Fail** | Rollback transaction. | "Something went wrong saving. Please try again." |
| **Process Death** | Save wizard state in Bundle. | Reopen to exact step and field focus. |
| **Discard Changes** | Intercept back/cancel. | "Discard unsaved property details?" [Keep Editing] [Discard] |
