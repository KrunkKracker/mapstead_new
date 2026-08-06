# Mapstead Beginner-First UX 2.0 — Terminology and Information Architecture Decision Pack

This document contains the approved customer-facing terminology and navigation structures for the Mapstead Beginner-First UX 2.0 redesign. These decisions drive the production implementation of the new app shell and Property Home.

## Status: APPROVED

## Terminology Strategy

The primary rule for customer-facing text is to **prefer specific item names** (e.g., "Well", "Pool Pump", "Main Water Shutoff") over generic category terms. When a generic term is required, **Property Item** is the standard.

### Core Vocabulary Matrix

| Technical Concept | Approved Customer Term | Context / Usage |
| :--- | :--- | :--- |
| Infrastructure Item | **Property Item** | Used in list titles and generic instructions. |
| Map Feature | **Property Item** (or Name) | Used when referring to a visual item on the map. |
| Attachment | **Photo or File** | Standard plural for all media and documents. |
| Add Attachment | **Add Photo or File** | Action label for acquiring new media. |
| Maintenance Record | **Task or Entry** | "Task" for scheduled items, "Entry" for history. |
| Relationship | **Related Items** | Used in detail sections to show connected items. |
| Map Location | **Where It Is** | Header for coordinate or map-link information. |
| Open Record | **View Details** | Standard action to enter a detailed view. |

## Information Architecture

### Navigation Pillar: The Four-Tab Hypothesis
Mapstead will transition to a standard bottom-navigation shell with four primary destinations:

1.  **Home**: The property landing page. Switch properties, see critical alerts, and quick actions.
2.  **Map**: The persistent property map. Primary surface for visual organization.
3.  **Items**: A searchable list of all Property Items. Replaces the "Infrastructure" list.
4.  **Tasks**: The maintenance and reminder hub. Replaces the "Maintenance" screen.

### Emergency Guide Placement
The **Emergency Guide** is a high-priority destination prominently accessible from:
- A dedicated card or button on the **Home** screen.
- The **Map** screen (via the Emergency Mode toggle).
- Relevant **Property Item Details** (e.g., a "Show in Emergency Guide" indicator).

## Progressive Disclosure Rules

To prevent overwhelming beginner customers, advanced features are tucked behind secondary interactions:
- **Map Layers & Basemaps**: Accessed via a "Map Settings" or "Layers" FAB/Icon on the Map screen.
- **Technical Metadata**: GPS coordinates and UUIDs are hidden under a "Technical Details" expander in Item Details.
- **System Relationships**: Complex "Feeds/Controls" logic is secondary to the primary item description and photos.

## Prototyping Focus

Visual prototypes must validate the following journeys using this vocabulary:
1.  **"Find the Shutoff"**: Home -> Emergency Guide -> Show on Map.
2.  **"Document the Pump"**: Add Something -> Category -> Review -> Save.
3.  **"Check Status"**: Home -> Needs Attention -> View Details.
