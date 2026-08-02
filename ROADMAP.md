# Mapstead Roadmap

**JuMaSt Appworks LLC**

## Phase 1 — Property Mapping Foundation (v0.02)
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
- [ ] Phase 5E — Property Inventory (BLOCKED)

---

## Beginner-First UX Redesign Stages

### Stage 4 — Guidance, Help, and Final Beginner Review
Purpose: Provide in-context assistance and finalize the onboarding experience.

**ACTIVE PHASE**:
- **Phase 2.2h5R9C — Preference Confirmation, Stale-Branch Testability, and Evidence Closure**: Finalizing preference confirmation, transactional recovery, and evidence integrity. (IMPLEMENTED / EXTERNAL REVIEW PENDING)
- **Phase 2.2h5R9B — Stale Pending Recovery, Behavioral Coverage, Documentation, and Evidence Integrity Closure**: COMPLETE.

**COMPLETED WORK**:
- Preference Confirmation and Authority: Implemented `lastObservedRepositoryBasemapId` confirmation logic, stale emission filtering, and generation-validated selection authority.
- Stale-Pending Recovery and Transactional Reissue: Refactored `onMapReady` to use `PendingBasemapResolver`, ensuring transactional issue and reissue of authoritative preferences.
- Secondary Backup-Only: Hardened `SecondaryBasemapController` to correctly handle sources without primaries using the `BACKUP` role and reason.
- Preference Authority and Transactional Requests: Implemented `_customerBasemapPreferenceOverride`, generation-validated pending requests, and hardened disposal terminal reasons.
- Recreation Lifecycle and Version Closure: Hardened recreation state preservation in MapViewModel, implemented formal secondary disposal lifecycle, and enforced authoritative pending requests.
- Pending Request Authority: Ensured semantic intent survives render session gaps with typed pending requests and exhaustive regression tests.
- Loader Atomicity and Repair Epoch: Verified atomic outcomes, precise repair epochs, and session-scoped camera isolation.
- Camera and Repair Closure: Verified session-matched camera guards, authoritative snapshot restoration, repair epoch epochs, and strict callback validation.
- Final Basemap Runtime Closure: Verified session-aware interaction guards, reactive style restoration, terminal tracking, and strict callback validation.
- Basemap Implementation: Resilient MapTiler v4 integration with bundled branding, attempt-scoped isolation, and automated style repair.
- Alpha Readiness: Naming service, system item policies, property-specific guidance, address lookup, and creation photo lifecycle.
- Integrity: System Item draft lifecycle and terminal state cleanup.
- Reliability: Persistence guard, initial camera stability, and default-world repair.
- Wizard: Simplified Property setup with atomic Map creation.
- Tasks: Task-oriented "Add Something" and operational record policies.
- Location: GPS permission recovery and request-state independence.

**UPCOMING PHASE**:
- **Phase 3A — Unified Item Details**: Implementing a singular, high-integrity detail surface for all mapped infrastructure items. (NOT STARTED)

---

## Future Backlog (Advanced GIS)
- Multipolygon support (islands and holes).
- Interior ring editing.
- Snapping and topology tools.
- Advanced geometric measurements.
- PDF and high-res image overlays.
- Enhanced Satellite and Hybrid Basemaps.
