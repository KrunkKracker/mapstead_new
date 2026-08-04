# Mapstead Beginner-First UX 2.0 Master Plan

This document is the authoritative plan for redesigning Mapstead to support customers with very limited smartphone and technical experience.

## Purpose

Mapstead must be usable by customers who:
- Rarely use complex smartphone applications.
- Do not understand GIS terminology.
- Do not understand Mapstead’s internal records.
- Need clear, forgiving, and predictable workflows.
- May need critical property information quickly.

This redesign is larger than visual polish. It includes:
- Information architecture
- Navigation
- Terminology
- Workflow structure
- Error recovery
- Accessibility
- Progressive disclosure
- Emergency readiness

## Core Customer Mental Model

> Mapstead helps me remember what is on my property, where it is, what needs attention, and what someone needs during an emergency.

The primary customer-facing concept is the **Property Item**.

A **Property Item** is a unified concept that internally may include:
- A map feature
- A documentation record
- Photos and files
- Tasks and maintenance history
- Emergency instructions
- Related items

The customer experiences these as one cohesive item. They should not need to understand technical concepts such as:
- Map Features vs. Infrastructure Records
- Geometry types (Point, Line, Polygon)
- Attachment ownership
- Repository structure
- Database relationships

## Redesign Method

Mapstead will not be redesigned merely screen-by-screen or technical system-by-technical system. Instead, the approach is:

**Architecture-first planning followed by customer-journey vertical slices.**

A **vertical slice** is a complete customer goal crossing all necessary screens and technical systems.
*Example:* Home → Find Main Water Shutoff → View Details → Show on Map → Add Photo → Return to Details.

A screen is not considered complete until its entry path, exit path, back behavior, empty state, loading state, error behavior, completion state, and accessibility behavior are fully understandable for a beginner.

## Working Information Architecture (Initial Hypothesis)

The initial navigation hypothesis consists of:

- **Home**: Clearly show current property, property switching, "Add Something," "Find Something," "Needs Attention" (upcoming tasks), Emergency Guide entry, and recently viewed items.
- **Map**: Persistent property map, search, "Add Something," Recenter on Property, My Location, and secondary controls for layers and basemaps.
- **Property Items**: Searchable list of everything documented on the property using beginner-friendly categories, with clear map-location, task, and Emergency Guide status.
- **Tasks**: Filtered views for Overdue, Due Soon, Upcoming, and Completed tasks, with clear association back to Property Items.

> [!NOTE]
> Specific navigation and architecture recommendations are being evaluated in the [Terminology and Information Architecture Decision Pack](BEGINNER_FIRST_UX_TERMINOLOGY_IA_DECISION_PACK.md).

### Emergency Guide
The **Emergency Guide** is a prominent destination reachable from Home and relevant Property Item details. It is not necessarily a bottom-navigation tab.

## Customer-Facing Terminology

The primary rule is: **Use the actual item name whenever possible** (e.g., Well, Pool Pump, Fence, Pond, Main Water Shutoff). When a generic category-level term is required, use **Property Item**.

| Technical Term | Beginner-First Term |
| :--- | :--- |
| Infrastructure Item | Property Item |
| Map Feature | Property Item (or actual name) |
| Attachment | Photo or File |
| Add Attachment | Add Photo or File |
| Maintenance Record | Task or Maintenance Entry |
| Relationships | Related Items |
| Map Locations | Where It Is |
| Open Record | View Details |
| Return to Property | Recenter on Property (for map camera action) |

*Specific terminology recommendations are detailed in the [Decision Pack](BEGINNER_FIRST_UX_TERMINOLOGY_IA_DECISION_PACK.md).*

## Priority Customer Journeys

1.  **First launch and property setup**
2.  **Open Mapstead and understand what to do**
3.  **Find an existing Property Item**
4.  **View and edit a Property Item**
5.  **Find an item on the map**
6.  **Add something to the property**
7.  **Add a photo or document**
8.  **Create and complete a task**
9.  **Use the Emergency Guide**
10. **Recover from an error or interruption**
11. **Back up or transfer property information**

Each journey specification must define: Entry points, Main goal, Required screens, Primary action, Back behavior, Empty state, Loading state, Error recovery, Completion result, Large-font behavior, and TalkBack behavior.

## Reference Journey: Add Something

**Scenario:** The customer is standing beside a pool pump in the backyard and wants to document it.

**Path:**
Home → Add Something → Pool Equipment → I’m Standing Next to It → Confirm or adjust location → Take Photo → Review → Save → Pool Pump details.

**The Review step includes:**
- Suggested item name
- Optional note
- Location summary
- Photo
- Edit actions
- Save Item

**Location Choices:**
1.  **I’m Standing Next to It**: Uses phone GPS as a suggested position; allows confirmation/adjustment.
2.  **Place It on the Map**: Allows manual location selection.
3.  **Add the Location Later**: Allows saving the item without a location blocking the workflow.

> [!IMPORTANT]
> A photo must not independently define the authoritative map location. Reasons include missing/stripped metadata, stale location, or the customer standing away from the object. Mapstead may capture a location candidate at photo time, but the customer must be able to confirm or adjust it.

Customers must **never** be asked to choose between Point, Line, Polygon, Map Feature, or Infrastructure Record during a normal preset workflow.

## Vertical-Slice Sequence

1.  **SLICE 1: App Shell and Property Home** - Navigation, property switching, primary entries.
2.  **SLICE 2: Find and Understand a Property Item** - Property items list, search, unified details, return behavior.
3.  **SLICE 3: Add Something** - Real-world presets, location/photo optionality, explicit Review screen.
4.  **SLICE 4: Emergency Guide** - Critical item elevation, instructions, contacts, offline access.
5.  **SLICE 5: Photos and Files** - Unified acquisition (Take/Choose), ownership management.
6.  **SLICE 6: Tasks** - Overdue, Due Soon, Upcoming, and Completed views; association with items; history.
7.  **SLICE 7: Advanced Features** - Related Items, Reports, Backup and Restore, Property Handoff, Data Transfer, Advanced map controls, technical metadata, and settings.

## Prototype Phase

Production implementation must be preceded by static Compose previews or wireframes using fake data.
The first prototype set must include: Property Home, Find Property Item, Unified Property Item Details, Map with Recenter, Add Something, Emergency Guide, and Tasks.
Prototypes must demonstrate complete journeys. No production code/navigation will be modified during this phase.

## Migration Strategy

1.  Preserve current data models and repositories.
2.  Introduce a unified presentation model for Property Items.
3.  Build the new app shell separately from the current production shell.
4.  Connect one vertical slice at a time.
5.  Keep existing flows available internally until the replacement is accepted.
6.  Do not switch normal launch navigation until the core experience is coherent.
7.  Remove legacy screens only after replacements pass physical-device acceptance.

*This is not a big-bang database rewrite.*

## Emergency Guide Definition

> Emergency Guide helps someone quickly locate critical controls, hazards, contacts, and property instructions. It does not contact emergency services.

**First-use explanation:** "The Emergency Guide keeps important property information in one place. It does not contact emergency services. For immediate danger, call 911."

**Candidate Content:** Water shutoff, Electrical disconnect, Gas/Propane shutoff, Generator, Fire extinguishers, Hazards, Address/Coordinates, Contacts.

**Item-level fields:** Include in Emergency Guide, Emergency Instructions, Location description, Photo, Safety warning, Show on Map.

Emergency Guide information must remain available offline.

## Beginner Acceptance Standards

A vertical slice is not complete until it meets these requirements:
- One obvious primary action.
- Plain language (no technical jargon).
- Home reachable predictably.
- Back never loses customer context.
- No dependency on hidden gestures.
- Minimum 48dp touch targets.
- Usability at Android font scale 2.0.
- TalkBack compatibility.
- Clear empty/loading/error states.
- Safe cancellation.
- No whole-world map resets.
- Actionable errors.
- No duplicate destinations.
- No requirement to understand internal records.

### Practical Beginner Checks
- A new customer can immediately identify the current property.
- A new customer can find the main water shutoff without instruction.
- A new customer can add a photo without understanding attachments.
- A new customer can explain what Emergency Guide does.
- A new customer can return Home without repeatedly pressing Back.
- A customer never has to choose a database, record, or geometry concept during a normal preset workflow.

> [!NOTE]
> Documentation completion is not customer acceptance. Physical-device journey testing remains required.

## Scope Protection

- No major new features until the core is cohesive.
- Do not remove working repositories to simplify presentation.
- Do not expose advanced controls on primary screens.
- Use progressive disclosure instead of separate "Simple/Advanced" modes.
- Do not treat documentation completion as customer acceptance.
- Require physical-device journey testing before a slice is accepted.
- Property Inventory remains **BLOCKED** until the foundation is coherent.

## Document Roles

- **BEGINNER_FIRST_UX_MASTER_PLAN.md**: Authoritative redesign strategy, sequence, migration plan, acceptance gates, progress tracker, and open decisions.
- **UX_GUIDELINES.md**: Permanent design, accessibility, interaction, and usability standards.
- **UX_REDESIGN_RESOURCES.md**: Supporting presets, examples, wireframes, and prototype resources.
- **ROADMAP.md**: Concise source of current progress and links to the authoritative master plan.

## Open Decisions (Requires Visual Prototype Review)

Marked: OPEN — REQUIRES VISUAL PROTOTYPE REVIEW

- Final bottom-navigation destinations.
- Exact Home content priority.
- Property switching interaction.
- Search location.
- Final customer term for Property Item.
- Emergency Guide placement.
- Whether Tasks remains a primary tab.
- How advanced map controls are exposed.
- How legacy navigation remains internally available during migration.
- What constitutes minimum viable cutover.

## Progress Tracker

- [x] Master-plan document created
- [x] Master plan approved
- [ ] Terminology approved
- [ ] Information architecture approved
- [ ] Prototype journeys created
- [ ] Prototype review complete
- [ ] Slice 1 complete
- [ ] Slice 2 complete
- [ ] Slice 3 complete
- [ ] Slice 4 complete
- [ ] Slice 5 complete
- [ ] Slice 6 complete
- [ ] Slice 7 complete
- [ ] Legacy navigation retired
- [ ] Final beginner physical-device acceptance complete

## Current Boundary

Planning and prototype work may proceed while final Phase 3A physical acceptance remains separate. Production navigation replacement must not begin until all of the following are true:
- The stable pre-redesign baseline is recorded.
- The master plan is approved.
- Terminology and information architecture decisions are approved.
- The first complete prototype journeys are visually reviewed.
