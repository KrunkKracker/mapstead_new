# QA Workflow and Verification

**JuMaSt Appworks LLC**

## Automated Testing Strategy
Mapstead uses a three-tier testing strategy. Routine development prioritizes Tier 1 and 2.

### 1. JVM Unit Tests (Robolectric & Coroutines)
- **Scope**: ViewModel logic, Repository transactions, Cycle detection, Report/Handoff assembly, navigation mapping, and **Operational Record Policies**.
- **Execution**: `./gradlew testDebugUnitTest`

### Phase 2.2h5R9D Quality Gates
1. **Deterministic Unit Tests**: Run `./gradlew test` to verify `MapViewModelPhase2Test`, `MapBasemapStateMachineTest`, and `PendingBasemapResolverTest`.
2. **Consistency Check**: Verify `BuildConfigConsistencyTest` passes for Phase 2.2h5R9D.
3. **Packaging Workflow**: Build `no-key` APKs using `./gradlew assembleDebug -PMAPTILER_API_KEY= -PVERIFY_NO_KEY=true`.
- **Result**: 640 PASSED (Phase 2.2h5R9D)

### 2. Static Analysis
- **Scope**: Linting for code quality and schema consistency.
- **Execution**: `./gradlew lintDebug`
- **Result**: PASSED (Phase 2.2h5R9D) - 0 errors, 248 warnings, 1 hint.

### 3. Instrumented UI Tests (Targeted Connected)
- **Scope**: Cross-component interactions, database migrations, and **"Add Something" workflow**.
- **Note**: The full connected suite is reserved for release candidates. Instrumented compilation verifies hierarchy and wiring.

## Manual Verification Checklist (Phase 2.2h5R Basemaps)

### Basemap Selection
- [ ] **Default Streets**: Clear data -> Open Map -> Confirm MapTiler Streets is default.
- [ ] **Primary Lineup**: Verify exactly 5 choices: Streets, Base, Topo, Satellite Hybrid, Outdoor.
- [ ] **Order**: Confirm they appear in the approved order.
- [ ] **Descriptions**: Confirm MapTiler descriptions match approved text.
- [ ] **Guidance**: Confirm "Can’t see your house..." hint appears for Satellite Hybrid.

### Readiness and Recreation
- [ ] **Cold Start**: Stored Topo preference -> App Launch -> Confirm Topo loads immediately without Streets flash.
- [ ] **Rotation during Primary Load**: Start Switch -> Rotate -> Confirm requested primary load continues/rebinds correctly.
- [ ] **Rotation during Backup Load**: Forced timeout -> Backup starts -> Rotate -> Confirm requested backup load continues.
- [ ] **Rotation after terminal failure**: Rotate while FAILED -> Confirm it remains FAILED and does not auto-retry.
- [ ] **Deferred Reset**: Request backup (A) -> Timeout -> request primary (B) while no session -> Confirm (B) primary is attempted in new session.

### Resilient Fallback and Repair
- [ ] **Simulated Failure**: Use invalid API key -> Confirm primary fail -> Confirm automatic switch to OFM.
- [ ] **Late Primary Success**: Forced timeout (Backup active) -> simulated primary success -> Confirm primary rejected and re-asserted if applied.
- [ ] **Stale Repair**: Re-assert current source if a stale native style changes the map background.

### Camera Persistence Guard
- [ ] **Style Load Pan**: Rotate/Switch Map -> Pan during load -> Confirm older captured camera does NOT restore on top of user pan.
- [ ] **No Persistence on Idles**: Programmatic moves (initial focus, restoration) do not update Plan database coordinates.
- [ ] **Pan Persistence**: Genuine user pan after load completes updates the database normally.

### Attribution
- [ ] **MapTiler Logo**: Confirm official SVG logo is visible only on MapTiler primary maps.
- [ ] **Links**: Confirm logo opens MapTiler website; separate OSM link opens copyright page.
- [ ] **Contrast**: Verify attribution remains readable on both dark and light map backgrounds.

[PRESERVE OLDER CHECKLISTS AS ARCHIVE IF NEEDED]
