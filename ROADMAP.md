# Mapstead Roadmap

**JuMaSt Appworks LLC**

## Phase 1 — Property Mapping Foundation (v0.03)
- [x] Room Schema with UUID and Audit fields.
- [x] Navigation 3 state-driven architecture with Adaptive support.
- [x] Local property profile management.
- [x] Map plan transactions and default layers.
- [x] Map Editor with point features and layer control.
- [x] GPS Location support (My Location & Use Phone Location).
- [x] Adaptive layouts for Phone, Tablet, and Foldables.
- [x] Material 3 Typography and Inset handling.

## Phase 2 — Google Drive Backup and Cloud Sync
- [ ] Phase 2A — Paused: implementation preserved, customer access disabled pending destructive restore testing.
- [x] .mapsteadbackup (v1) archive validation with SHA-256 and relationship checks.
- [x] Atomic restore with safety backup and attachment rollback.
- [x] Modern identity authorization with GIS result parsing.
- [ ] Automated/Continuous cloud sync (NOT STARTED).

## Phase 3 — Collaborative and Reporting Tools
- [x] Local property documentation reports (PDF) (Implemented in Phase 5D1).
- [/] Phase 3A — Unified Item Details: Implementing a singular, high-integrity detail surface for all mapped infrastructure items. (IN PROGRESS / PHYSICAL-DEVICE ACCEPTANCE PENDING)
    - [x] Phase 3A1 — Unified Infrastructure Details Foundation: COMPLETE
    - [x] Phase 3A2 — Unified Map Feature Details: COMPLETE
    - [/] Phase 3A3 — Detail Navigation & Consistency: IMPLEMENTED / PHYSICAL-DEVICE ACCEPTANCE PENDING
- [ ] Multi-device sync and property sharing (NOT STARTED).
- [ ] QR-code based access sharing for contractors (NOT STARTED).
- [ ] Automated property health reports (Advanced analysis/PDF) (NOT STARTED).

## Phase 4 — Advanced Mapping and Linear Assets
- [x] Phase 4A — COMPLETE: Basemap Engine and State Machine (Serialized).
- [x] Phase 4B — COMPLETE: polyline vertex editing.
- [x] Phase 4C1 — COMPLETE: polygon drawing, validation, area/perimeter calculation.
- [x] Phase 4C2 — COMPLETE: saved-polygon vertex editing and validation.

## Phase 5 — Product Experience and Property Operations
- [x] Phase 5A — COMPLETE: Map-Centered Product Experience and Search
- [x] Phase 5B — COMPLETE: Maintenance Records and Reminder Workflows
- [x] Phase 5C1 — COMPLETE: Attachments and Local File Management
- [x] Phase 5C2 — COMPLETE: Infrastructure Relationships
- [x] Phase 5C3 — COMPLETE: Map Feature Photos and Attachments
- [x] Phase 5D — COMPLETE: Reports and Property Handoff
- [/] Beginner-First UX Foundation — IN PROGRESS
- [x] Stage 3 — Map Presentation and Guided Mapping (COMPLETE)
- [/] Stage 4 — Guidance, Help, and Final Beginner Review (IN PROGRESS)
- [/] Beginner-First UX 2.0 — [Master Plan](BEGINNER_FIRST_UX_MASTER_PLAN.md) (IN PROGRESS)
- [ ] Phase 5E — Property Inventory (BLOCKED)

---

## Beginner-First UX 2.0
Status:
- Master planning: IN PROGRESS
- Master-plan document: CREATED / EXTERNAL APPROVAL PENDING
- Terminology approval: NOT STARTED
- Information architecture approval: NOT STARTED
- Prototype creation: NOT STARTED
- Production redesign: NOT STARTED
- Property Inventory: BLOCKED

Authoritative Document: [BEGINNER_FIRST_UX_MASTER_PLAN.md](BEGINNER_FIRST_UX_MASTER_PLAN.md)

---

## Future Backlog (Advanced GIS)
- Multipolygon support (islands and holes).
- Interior ring editing.
- Snapping and topology tools.
- Advanced geometric measurements.
- PDF and high-res image overlays.
- Enhanced Satellite and Hybrid Basemaps.
