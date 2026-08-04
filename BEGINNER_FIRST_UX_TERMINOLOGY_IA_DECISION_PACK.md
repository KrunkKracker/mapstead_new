# Mapstead Beginner-First UX 2.0 — Terminology and Information Architecture Decision Pack

Status: CREATED / EXTERNAL APPROVAL PENDING

This document supports specific terminology and information-architecture decisions required before static visual prototype creation begins. It is a companion to the [Beginner-First UX 2.0 Master Plan](BEGINNER_FIRST_UX_MASTER_PLAN.md).

## 1. Purpose

The purpose of this decision pack is to finalize the vocabulary and structural hierarchy that will drive the visual redesign. By standardizing these elements now, we ensure that prototypes represent a cohesive customer experience rather than a collection of disconnected screens.

## 2. Decision Principles

-   **Prefer Actual Names**: Use the actual item name (e.g., "Well") over generic category terms whenever possible.
-   **Singular Generic Concept**: Use **Property Item** only when a generic term is necessary.
-   **Hide Technical Complexity**: Never expose Map Feature, Infrastructure Record, geometry types, repository ownership, or database relationships during normal customer workflows.
-   **Obvious Primary Action**: Keep one obvious primary action per screen.
-   **Predictable Home**: Make Home predictably reachable.
-   **Context Preservation**: Preserve customer context when navigating back.
-   **Progressive Disclosure**: Do not create separate "Simple/Advanced" modes; expose complexity only when needed.
-   **Emergency Readiness**: Keep emergency information quickly reachable and available offline.
-   **Avoid Duplication**: Avoid duplicate destinations with unclear differences.
-   **Mobile Primary**: Treat phone usability as primary while supporting tablets and foldables.
-   **Beginner Acceptance**: Require physical-device journey testing before a slice is accepted.

## 3. Recommended Customer Terminology

| Concept | Recommended Term | Alternative Considered | Rationale | Validation Question | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Generic documented object | **Property Item** | Map Item, Record | Unified term for spatial and data components. | Does "Item" imply it must be physical? | RECOMMENDED / PENDING |
| Bottom-navigation label | **Items** | Property, List | Compact for mobile; identifies content. | Is "Items" too vague without "Property"? | RECOMMENDED / PENDING |
| Full screen title | **Property Items** | Items List | Provides explicit context for the page. | Does the title clarify the "Items" tab? | RECOMMENDED / PENDING |
| Primary creation action | **Add Something** | Create, New | Low-friction, conversational entry. | Is "Something" too informal? | RECOMMENDED / PENDING |
| Search-oriented action | **Find Something** | Search, Locate | Focuses on the customer's goal of retrieval. | Does it imply search or browsing? | RECOMMENDED / PENDING |
| Maintenance hub | **Tasks** | Maintenance, Reminders | Identifies actionable work to be done. | Does "Tasks" exclude history? | RECOMMENDED / PENDING |
| Attachment records | **Photos & Files** | Attachments, Media | Plain language describing the content. | Is "Files" too technical? | RECOMMENDED / PENDING |
| Relationship records | **Related Items** | Connections, Links | Beginner-friendly term for associations. | Does it clearly imply hierarchy? | RECOMMENDED / PENDING |
| Location section | **Where It Is** | Geometry, Position | Explains the map's role for that item. | Does it work for routes/areas? | RECOMMENDED / PENDING |
| Map navigation action | **Show on Map** | Locate, View Map | Clear instruction for a camera transition. | Is the destination predictable? | RECOMMENDED / PENDING |
| Camera reset action | **Recenter on Property** | Home View, Reset | Explicitly describes the result. | Does "Property" imply whole-world? | RECOMMENDED / PENDING |
| Emergency destination | **Emergency Guide** | Quick Access, Safety | Clear purpose without causing alarm. | Is it prominent enough? | RECOMMENDED / PENDING |
| Existing-item opening | **View Details** | Open, Edit | Neutral entry into read-only summary. | Does "Details" imply technical data? | RECOMMENDED / PENDING |

## 4. Primary Navigation Options

### OPTION A — RECOMMENDED
**Bottom Navigation:**
1. **Home**
2. **Map**
3. **Items** (Property Items)
4. **Tasks**

**Emergency Guide** remains prominent on Home and relevant Property Item details rather than becoming a fifth tab.

- **Benefits**: Covers the four recurring customer goals; keeps map access persistent; avoids overcrowding the bottom bar.
- **Status**: RECOMMENDED / EXTERNAL APPROVAL PENDING

### OPTION B
**Bottom Navigation:**
1. **Home**
2. **Map**
3. **Items**
4. **More** (Tasks, Emergency Guide, Settings)

- **Tradeoff**: Simpler bottom bar, but recurring tasks become less discoverable and "More" becomes a dumping ground.
- **Status**: EVALUATED

### OPTION C
**Bottom Navigation:**
1. **Home**
2. **Items**
3. **Tasks**
4. **More** (Map, Settings)

- **Tradeoff**: Reduces emphasis on mapping, which conflicts with Mapstead's central spatial purpose.
- **Status**: EVALUATED

## 5. Recommended Information Architecture

```text
Mapstead
├── Current Property
│   ├── Home
│   │   ├── Add Something
│   │   ├── Find Something
│   │   ├── Emergency Guide
│   │   ├── Needs Attention (Upcoming Tasks)
│   │   └── Recently Viewed
│   ├── Map
│   │   ├── Search
│   │   ├── Add Something
│   │   ├── My Location
│   │   ├── Recenter on Property
│   │   └── Map Options (Layers, Basemaps, Measurements)
│   ├── Property Items
│   │   ├── Search
│   │   ├── Categories (Well, Septic, etc.)
│   │   ├── Needs Location (Unmapped items)
│   │   ├── Emergency Items
│   │   └── Property Item Details (Summary, Tasks, Photos, Location)
│   └── Tasks
│       ├── Overdue
│       ├── Due Soon
│       ├── Upcoming
│       ├── Completed (History)
│       └── Task Details
└── Property Switching and Management (Multi-property list, Add Property)
```

## 6. Property Home Content Priority

1.  **Current Property Identity**: Clearly identify which property is active.
2.  **Property Switcher**: Rapid access to other properties.
3.  **Add Something**: Primary entry for documentation.
4.  **Find Something**: Primary entry for retrieval.
5.  **Emergency Guide**: Elevated for safety access.
6.  **Needs Attention**: Summary of urgent tasks.
7.  **Recently Viewed**: Shortcuts to active work.
8.  **Secondary Management**: Settings, Backup, Reports.

*Note: This ordering is a prototype hypothesis for visual review.*

## 7. Property Switching

-   **Selector Placement**: Top of Home and other primary destinations.
-   **Interaction**: Tapping the property name opens a bottom sheet selector.
-   **Contents**: List of available properties, "Add Property," and "Manage Properties" (rename/delete).
-   **Safe Transitions**: Preserve the customer's current tab (e.g., Tasks) when switching properties, but clear item-specific context (e.g., details of a pump belonging to the previous property).
-   **Complexity Shield**: Never expose internal database IDs.

## 8. Search Placement

-   **Home**: "Find Something" button opens the global item search.
-   **Property Items**: Persistent search bar at the top of the list.
-   **Map**: Map-specific search bar for finding items *and* geographic context (addresses/places).
-   **Policy**: Clearly label what is being searched (e.g., "Search Items" vs "Search Map"). No single ambiguous global field.

## 9. Property Items Organization

-   **Primary View**: List of all items, searchable and filterable by category.
-   **Categories**: Grouped by real-world purpose (Water, Power, Structures, Boundaries).
-   **Status Badges**: Clear visual indicator for items marked in Emergency Guide or those needing a map location.
-   **Details**: Unified read-only summary for all item types.

## 10. Tasks Placement

**Recommended: Primary Bottom-Navigation Destination**

-   **Rationale**: Maintenance and reminders are a core value proposition. Work that is overdue or due soon must remain highly visible.
-   **Structure**: Link tasks back to their Property Item; move completed work to a separate history tab within the hub.
-   **Status**: RECOMMENDED / EXTERNAL APPROVAL PENDING

## 11. Emergency Guide Placement

**Recommended: High-Visibility Shortcut (Not a Tab)**

-   **Rationale**: Must be prominent on Home and in relevant item details (e.g., Main Shutoff) but does not require a persistent bottom-bar presence.
-   **Requirements**: Available offline; clear "Not 911" disclaimer; immediate-danger guidance visible.
-   **Status**: RECOMMENDED / EXTERNAL APPROVAL PENDING

## 12. Map and Advanced Controls

-   **Primary Controls**: Add Something, Search, My Location, Recenter on Property.
-   **Map Options**: A clearly labeled surface (icon + text) for:
    -   Layer visibility
    -   Basemap selection
    -   Measurement unit preferences
    -   Technical coordinates
-   **Philosophy**: Use progressive disclosure; do not call it "Advanced Mode."

## 13. Back and Return Behavior

-   **Tab State**: Bottom-navigation destinations should retain their state (scroll position, filters) where practical.
-   **Detail Return**: Back from a detail screen always returns to the originating context (Home, List, or Map).
-   **Spatial Return**: "Show on Map" focus should preserve a clear path back to the originating item details.
-   **Outcome Navigation**: Saving an item should open its details; canceling should return to the prior step without data loss.

## 14. Legacy Navigation During Migration

-   **Internal Preservation**: Existing production routes will remain available internally via a controlled route boundary or developer shortcut.
-   **Redesign Isolation**: New beginner-first components will be built in a separate UI package.
-   **Baseline Check**: Record a stable baseline of all legacy behaviors before replacing normal launch navigation.
-   **Retirement**: Legacy routes will be removed only after replacement journeys pass physical-device acceptance.

## 15. Minimum Viable Cutover

**OPTION 1 — RECOMMENDED: CORE OPERATIONAL CUTOVER**
Require accepted replacements for Slices 1 through 4, essential Slice 5 actions, and Slice 6. Advanced Slice 7 functions (Handoff, Reports) may use preserved legacy routes temporarily.

-   **Status**: RECOMMENDED / EXTERNAL APPROVAL PENDING

**OPTION 2 — FULL CUTOVER**
Require all seven slices before changing launch navigation. Delays access to the new experience.

## 16. Prototype Validation Questions

-   Can a new customer identify the current property immediately?
-   Is "Add Something" more obvious than the other Home actions?
-   Can a customer find an existing well or shutoff without using the Map?
-   Does "Items" clearly mean documented property contents?
-   Is "Tasks" important enough to remain a primary destination?
-   Can the customer locate "Emergency Guide" immediately?
-   Does "Show on Map" preserve a clear return path?
-   Are "Map Options" discoverable without cluttering the map?

## 17. Decisions Requiring External Approval

| Decision Point | Status |
| :--- | :--- |
| **Property Item** terminology | PENDING EXTERNAL APPROVAL |
| **Items** bottom-navigation label | PENDING EXTERNAL APPROVAL |
| **Home / Map / Items / Tasks** navigation | PENDING EXTERNAL APPROVAL |
| Home content priority | PENDING EXTERNAL APPROVAL |
| Property switcher interaction | PENDING EXTERNAL APPROVAL |
| Search placement strategy | PENDING EXTERNAL APPROVAL |
| **Tasks** as a primary tab | PENDING EXTERNAL APPROVAL |
| **Emergency Guide** placement | PENDING EXTERNAL APPROVAL |
| **Map Options** progressive disclosure | PENDING EXTERNAL APPROVAL |
| Legacy-route migration strategy | PENDING EXTERNAL APPROVAL |
| Minimum viable cutover (Option 1) | PENDING EXTERNAL APPROVAL |

## 18. Approval Record

*No approvals recorded in this decision pack version.*
