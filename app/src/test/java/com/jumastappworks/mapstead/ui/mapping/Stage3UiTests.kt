package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.util.RelativeAge
import com.jumastappworks.mapstead.util.TimeUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class Stage3UiTests {

    @Test
    fun testGuidedPresetsGeometryMapping() {
        val well = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.WELL }
        assertEquals(GuidedMapGeometry.LOCATION, well?.geometry)
        
        val fence = GuidedMapPresets.ROUTES.find { it.id == GuidedMapPresetId.FENCE }
        assertEquals(GuidedMapGeometry.ROUTE, fence?.geometry)
        
        val house = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.HOUSE }
        assertEquals(GuidedMapGeometry.AREA, house?.geometry)
    }

    @Test
    fun testBoundaryAcknowledgmentRequired() {
        val boundary = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.PROPERTY_BOUNDARY }
        assertTrue(boundary?.requiresBoundaryAcknowledgment ?: false)
        
        val shed = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.SHED }
        assertFalse(shed?.requiresBoundaryAcknowledgment ?: true)
    }

    @Test
    fun testRelativeAgeResolution() {
        val now = 1000000000L
        
        // Future
        assertEquals(RelativeAge.JUST_NOW, TimeUtils.resolveRelativeAge(now + 1000, now).age)
        
        // Just now
        assertEquals(RelativeAge.JUST_NOW, TimeUtils.resolveRelativeAge(now - 30000, now).age)
        
        // 1 minute
        assertEquals(RelativeAge.ONE_MINUTE_AGO, TimeUtils.resolveRelativeAge(now - 61000, now).age)
        
        // 5 minutes
        val mins5 = TimeUtils.resolveRelativeAge(now - 300000, now)
        assertEquals(RelativeAge.MINUTES_AGO, mins5.age)
        assertEquals(5, mins5.value)
        
        // 3 hours
        val hours3 = TimeUtils.resolveRelativeAge(now - 10800000, now)
        assertEquals(RelativeAge.HOURS_AGO, hours3.age)
        assertEquals(3, hours3.value)
        
        // 2 days
        val days2 = TimeUtils.resolveRelativeAge(now - 172800000, now)
        assertEquals(RelativeAge.DAYS_AGO, days2.age)
        assertEquals(2, days2.value)
    }

    @Test
    fun testSuggestionResolutionScoping() {
        val housePreset = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.HOUSE }!!
        val sessionId = UUID.randomUUID()
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val draftId = UUID.randomUUID()
        
        val session = GuidedMappingSession(
            sessionId = sessionId,
            propertyId = propId,
            planId = planId,
            preset = housePreset,
            expectedGeometry = GuidedMapGeometry.AREA,
            suggestedLabel = "House",
            targetDraftId = draftId,
            phase = GuidedMappingPhase.REVIEWING
        )
        
        // 1. Correct target and draft ID
        val suggestions = MapViewModel.resolveSuggestions(session, FeatureEditorTarget.NewPolygon(draftId), propId, planId)
        assertEquals(housePreset, suggestions)
        
        // 2. Unrelated persisted feature
        val persistedSuggestions = MapViewModel.resolveSuggestions(session, FeatureEditorTarget.Persisted(UUID.randomUUID()), propId, planId)
        assertNull(persistedSuggestions)
        
        // 3. Different phase
        val drawingSession = session.copy(phase = GuidedMappingPhase.DRAWING)
        assertNull(MapViewModel.resolveSuggestions(drawingSession, FeatureEditorTarget.NewPolygon(draftId), propId, planId))
        
        // 4. Different geometry
        assertNull(MapViewModel.resolveSuggestions(session, FeatureEditorTarget.NewPoint(draftId), propId, planId))

        // 5. Cross-property rejection
        assertNull(MapViewModel.resolveSuggestions(session, FeatureEditorTarget.NewPolygon(draftId), UUID.randomUUID(), planId))

        // 6. Cross-plan rejection
        assertNull(MapViewModel.resolveSuggestions(session, FeatureEditorTarget.NewPolygon(draftId), propId, UUID.randomUUID()))
    }

    @Test
    fun testStyleResolution() {
        val boundary = MapsteadMapOverlayInstaller.resolvePresetVisualStyle("POLYGON", "property_boundary")
        assertTrue(boundary.dashed)
        assertEquals("#9C27B0", boundary.outlineColor)
        
        val building = MapsteadMapOverlayInstaller.resolvePresetVisualStyle("POLYGON", "building")
        assertFalse(building.dashed)
        assertEquals("#757575", building.color)
        
        val legacy = MapsteadMapOverlayInstaller.resolvePresetVisualStyle("POINT", null)
        assertEquals("#FF0000", legacy.color)
    }

    @Test
    fun testCustomCatalogInclusion() {
        // Objective 5: Custom actions inside their practical categories
        assertTrue(GuidedMapPresets.LOCATIONS.any { it.id == GuidedMapPresetId.CUSTOM_LOCATION })
        assertTrue(GuidedMapPresets.ROUTES.any { it.id == GuidedMapPresetId.CUSTOM_ROUTE })
        assertTrue(GuidedMapPresets.AREAS.any { it.id == GuidedMapPresetId.CUSTOM_AREA })
        
        // Ensure they are last
        assertEquals(GuidedMapPresetId.CUSTOM_LOCATION, GuidedMapPresets.LOCATIONS.last().id)
        assertEquals(GuidedMapPresetId.CUSTOM_ROUTE, GuidedMapPresets.ROUTES.last().id)
        assertEquals(GuidedMapPresetId.CUSTOM_AREA, GuidedMapPresets.AREAS.last().id)
    }

    @Test
    fun testGuidedStartDecisions() {
        val housePreset = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.HOUSE }!!
        val sessionId = UUID.randomUUID()
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        
        val session = GuidedMappingSession(
            sessionId = sessionId,
            propertyId = propId,
            planId = planId,
            preset = housePreset,
            expectedGeometry = GuidedMapGeometry.AREA,
            suggestedLabel = "House",
            targetDraftId = UUID.randomUUID(),
            phase = GuidedMappingPhase.SELECTING_PLACEMENT
        )
        
        // 1. Authorized starts
        val canStart = MapViewModel.canStartGuidedWorkflow(
            sessionId = sessionId,
            session = session,
            expectedGeometry = GuidedMapGeometry.AREA,
            currentPropertyId = propId,
            currentPlanId = planId,
            hasBlockingWorkflow = { false },
            hasActiveLayer = true
        )
        assertTrue(canStart)
        
        // 2. Wrong session ID
        assertFalse(MapViewModel.canStartGuidedWorkflow(UUID.randomUUID(), session, GuidedMapGeometry.AREA, propId, planId, { false }, true))
        
        // 3. Blocking workflow
        assertFalse(MapViewModel.canStartGuidedWorkflow(sessionId, session, GuidedMapGeometry.AREA, propId, planId, { true }, true))
        
        // 4. Locked/Missing layer
        assertFalse(MapViewModel.canStartGuidedWorkflow(sessionId, session, GuidedMapGeometry.AREA, propId, planId, { false }, false))
        
        // 5. Cross-property rejection
        assertFalse(MapViewModel.canStartGuidedWorkflow(sessionId, session, GuidedMapGeometry.AREA, UUID.randomUUID(), planId, { false }, true))
    }

    @Test
    fun testStyleProductionClassification() {
        assertEquals(PresetVisualClass.BOUNDARY, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_BOUNDARY))
        assertEquals(PresetVisualClass.BUILDING, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_BUILDING))
        assertEquals(PresetVisualClass.FENCE, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_FENCE))
        assertEquals(PresetVisualClass.UTILITY_ROUTE, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_UTILITY_ROUTE))
        assertEquals(PresetVisualClass.UTILITY_AREA, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_UTILITY_AREA))
        assertEquals(PresetVisualClass.WATER_FEATURE, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_WATER_FEATURE))
        assertEquals(PresetVisualClass.DRIVEWAY, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_DRIVEWAY))
        assertEquals(PresetVisualClass.OUTDOOR_AREA, MapsteadMapOverlayInstaller.classifyPresetStyle(MapsteadMapOverlayInstaller.STYLE_OUTDOOR_AREA))
        assertEquals(PresetVisualClass.LEGACY, MapsteadMapOverlayInstaller.classifyPresetStyle("unknown"))
        assertEquals(PresetVisualClass.LEGACY, MapsteadMapOverlayInstaller.classifyPresetStyle(null))
    }

    @Test
    fun testStyleSpecUnification() {
        val boundary = MapsteadMapOverlayInstaller.getStyleSpec(MapsteadMapOverlayInstaller.STYLE_BOUNDARY, "POLYGON")
        assertEquals(PresetVisualClass.BOUNDARY, boundary.visualClass)
        assertTrue(boundary.dashed)
        
        val building = MapsteadMapOverlayInstaller.getStyleSpec(MapsteadMapOverlayInstaller.STYLE_BUILDING, "POLYGON")
        assertEquals(PresetVisualClass.BUILDING, building.visualClass)
        assertFalse(building.dashed)
        assertEquals("#000000", building.outlineColor)
        
        val legacy = MapsteadMapOverlayInstaller.getStyleSpec("unknown", "POINT")
        assertEquals(PresetVisualClass.LEGACY, legacy.visualClass)
        assertEquals("#FF0000", legacy.color)
    }
}
