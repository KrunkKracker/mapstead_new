package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.R
import java.util.UUID

enum class GuidedMapGeometry {
    LOCATION,
    ROUTE,
    AREA
}

enum class GuidedMappingPhase {
    SELECTING_PLACEMENT,
    DRAWING,
    REVIEWING
}

enum class PlacementMethod {
    MY_LOCATION,
    TAP_MAP
}

enum class SuggestedMapLayer {
    BUILDINGS_BOUNDARIES,
    UTILITIES,
    OUTDOOR_FEATURES,
    SAFETY_EMERGENCY
}

enum class SystemItemPolicy {
    MAP_ONLY,
    OPTIONAL,
    AUTOMATIC
}

sealed interface SystemItemLinkSelection {
    data object None : SystemItemLinkSelection
    data class Existing(val itemId: UUID) : SystemItemLinkSelection
    data class PendingDraft(val draftId: UUID) : SystemItemLinkSelection
    data object CreateSuggested : SystemItemLinkSelection
}

data class PendingSystemItemInput(
    val name: String,
    val category: String,
    val subtype: String?,
    val isEmergencyItem: Boolean,
    val emergencyInstructions: String?
)

enum class GuidedMapPresetId {
    WELL, WATER_VALVE, ELECTRICAL_PANEL, UTILITY_METER, SEPTIC_ACCESS, GATE, FIRE_EXTINGUISHER, TREE,
    FENCE, WATER_LINE, ELECTRICAL_LINE, GAS_LINE, DRAINAGE_ROUTE, IRRIGATION_LINE, DRIVEWAY_EDGE, PROPERTY_EDGE,
    HOUSE, SHED, PROPERTY_BOUNDARY, SEPTIC_FIELD, POND, DRIVEWAY_AREA, GARDEN, POOL,
    CUSTOM_LOCATION, CUSTOM_ROUTE, CUSTOM_AREA
}

data class GuidedMapPreset(
    val id: GuidedMapPresetId,
    val geometry: GuidedMapGeometry,
    val titleRes: Int,
    val descriptionRes: Int,
    val defaultCategory: String,
    val suggestedLabelRes: Int,
    val suggestedLayer: SuggestedMapLayer?,
    val systemItemPolicy: SystemItemPolicy = SystemItemPolicy.MAP_ONLY,
    val presetStyle: String? = null,
    val requiresBoundaryAcknowledgment: Boolean = false
)

data class GuidedFeaturePrefill(
    val sessionId: UUID,
    val draftId: UUID,
    val suggestedLabelRes: Int?,
    val suggestedLabel: String? = null,
    val suggestedCategory: String?,
    val suggestedLayerId: UUID?,
    val systemItemPolicy: SystemItemPolicy = SystemItemPolicy.MAP_ONLY,
    val presetStyle: String?
)

object GuidedMapPresets {
    val LOCATIONS = listOf(
        GuidedMapPreset(GuidedMapPresetId.WELL, GuidedMapGeometry.LOCATION, R.string.preset_well, R.string.preset_well_desc, "Utility", R.string.label_well, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL),
        GuidedMapPreset(GuidedMapPresetId.WATER_VALVE, GuidedMapGeometry.LOCATION, R.string.preset_valve, R.string.preset_valve_desc, "Utility", R.string.label_valve, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL),
        GuidedMapPreset(GuidedMapPresetId.ELECTRICAL_PANEL, GuidedMapGeometry.LOCATION, R.string.preset_panel, R.string.preset_panel_desc, "Electrical", R.string.label_panel, SuggestedMapLayer.UTILITIES, SystemItemPolicy.AUTOMATIC),
        GuidedMapPreset(GuidedMapPresetId.UTILITY_METER, GuidedMapGeometry.LOCATION, R.string.preset_meter, R.string.preset_meter_desc, "Utility", R.string.label_meter, SuggestedMapLayer.UTILITIES, SystemItemPolicy.AUTOMATIC),
        GuidedMapPreset(GuidedMapPresetId.SEPTIC_ACCESS, GuidedMapGeometry.LOCATION, R.string.preset_septic, R.string.preset_septic_desc, "Sewer", R.string.label_septic, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL),
        GuidedMapPreset(GuidedMapPresetId.GATE, GuidedMapGeometry.LOCATION, R.string.preset_gate, R.string.preset_gate_desc, "Access", R.string.label_gate, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.OPTIONAL),
        GuidedMapPreset(GuidedMapPresetId.FIRE_EXTINGUISHER, GuidedMapGeometry.LOCATION, R.string.preset_extinguisher, R.string.preset_extinguisher_desc, "Safety", R.string.label_extinguisher, SuggestedMapLayer.SAFETY_EMERGENCY, SystemItemPolicy.AUTOMATIC),
        GuidedMapPreset(GuidedMapPresetId.TREE, GuidedMapGeometry.LOCATION, R.string.preset_tree, R.string.preset_tree_desc, "Landscape", R.string.label_tree, SuggestedMapLayer.OUTDOOR_FEATURES, SystemItemPolicy.MAP_ONLY),
        GuidedMapPreset(GuidedMapPresetId.CUSTOM_LOCATION, GuidedMapGeometry.LOCATION, R.string.custom_location, R.string.custom_location_desc, "Other", R.string.label_custom_location, null, SystemItemPolicy.OPTIONAL)
    )

    val ROUTES = listOf(
        GuidedMapPreset(GuidedMapPresetId.FENCE, GuidedMapGeometry.ROUTE, R.string.preset_fence, R.string.preset_fence_desc, "Structure", R.string.label_fence, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.MAP_ONLY, presetStyle = "fence"),
        GuidedMapPreset(GuidedMapPresetId.WATER_LINE, GuidedMapGeometry.ROUTE, R.string.preset_water_line, R.string.preset_water_line_desc, "Utility", R.string.label_water_line, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL, presetStyle = "utility_route"),
        GuidedMapPreset(GuidedMapPresetId.ELECTRICAL_LINE, GuidedMapGeometry.ROUTE, R.string.preset_elec_line, R.string.preset_elec_line_desc, "Electrical", R.string.label_elec_line, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL, presetStyle = "utility_route"),
        GuidedMapPreset(GuidedMapPresetId.GAS_LINE, GuidedMapGeometry.ROUTE, R.string.preset_gas_line, R.string.preset_gas_line_desc, "Utility", R.string.label_gas_line, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL, presetStyle = "utility_route"),
        GuidedMapPreset(GuidedMapPresetId.DRAINAGE_ROUTE, GuidedMapGeometry.ROUTE, R.string.preset_drainage, R.string.preset_drainage_desc, "Utility", R.string.label_drainage, SuggestedMapLayer.UTILITIES, SystemItemPolicy.MAP_ONLY, presetStyle = "utility_route"),
        GuidedMapPreset(GuidedMapPresetId.IRRIGATION_LINE, GuidedMapGeometry.ROUTE, R.string.preset_irrigation, R.string.preset_irrigation_desc, "Utility", R.string.label_irrigation, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL, presetStyle = "utility_route"),
        GuidedMapPreset(GuidedMapPresetId.DRIVEWAY_EDGE, GuidedMapGeometry.ROUTE, R.string.preset_driveway_edge, R.string.preset_driveway_edge_desc, "Structure", R.string.label_driveway, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.MAP_ONLY, presetStyle = "driveway"),
        GuidedMapPreset(GuidedMapPresetId.PROPERTY_EDGE, GuidedMapGeometry.ROUTE, R.string.preset_property_edge, R.string.preset_property_edge_desc, "Boundary", R.string.label_property_edge, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.MAP_ONLY, presetStyle = "property_boundary"),
        GuidedMapPreset(GuidedMapPresetId.CUSTOM_ROUTE, GuidedMapGeometry.ROUTE, R.string.custom_line, R.string.custom_line_desc, "Other", R.string.label_custom_route, null, SystemItemPolicy.OPTIONAL)
    )

    val AREAS = listOf(
        GuidedMapPreset(GuidedMapPresetId.HOUSE, GuidedMapGeometry.AREA, R.string.preset_house, R.string.preset_house_desc, "Structure", R.string.label_house, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.OPTIONAL, presetStyle = "building"),
        GuidedMapPreset(GuidedMapPresetId.SHED, GuidedMapGeometry.AREA, R.string.preset_shed, R.string.preset_shed_desc, "Structure", R.string.label_shed, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.OPTIONAL, presetStyle = "building"),
        GuidedMapPreset(GuidedMapPresetId.PROPERTY_BOUNDARY, GuidedMapGeometry.AREA, R.string.preset_boundary, R.string.preset_boundary_desc, "Boundary", R.string.label_boundary, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.MAP_ONLY, presetStyle = "property_boundary", requiresBoundaryAcknowledgment = true),
        GuidedMapPreset(GuidedMapPresetId.SEPTIC_FIELD, GuidedMapGeometry.AREA, R.string.preset_septic_field, R.string.preset_septic_field_desc, "Sewer", R.string.label_septic_field, SuggestedMapLayer.UTILITIES, SystemItemPolicy.OPTIONAL, presetStyle = "utility_area"),
        GuidedMapPreset(GuidedMapPresetId.POND, GuidedMapGeometry.AREA, R.string.preset_pond, R.string.preset_pond_desc, "Landscape", R.string.label_pond, SuggestedMapLayer.OUTDOOR_FEATURES, SystemItemPolicy.MAP_ONLY, presetStyle = "water_feature"),
        GuidedMapPreset(GuidedMapPresetId.DRIVEWAY_AREA, GuidedMapGeometry.AREA, R.string.preset_driveway_area, R.string.preset_driveway_area_desc, "Structure", R.string.label_driveway, SuggestedMapLayer.BUILDINGS_BOUNDARIES, SystemItemPolicy.MAP_ONLY, presetStyle = "driveway"),
        GuidedMapPreset(GuidedMapPresetId.GARDEN, GuidedMapGeometry.AREA, R.string.preset_garden, R.string.preset_garden_desc, "Landscape", R.string.label_garden, SuggestedMapLayer.OUTDOOR_FEATURES, SystemItemPolicy.MAP_ONLY, presetStyle = "outdoor_area"),
        GuidedMapPreset(GuidedMapPresetId.POOL, GuidedMapGeometry.AREA, R.string.preset_pool, R.string.preset_pool_desc, "Landscape", R.string.label_pool, SuggestedMapLayer.OUTDOOR_FEATURES, SystemItemPolicy.OPTIONAL, presetStyle = "water_feature"),
        GuidedMapPreset(GuidedMapPresetId.CUSTOM_AREA, GuidedMapGeometry.AREA, R.string.custom_area, R.string.custom_area_desc, "Other", R.string.label_custom_area, null, SystemItemPolicy.OPTIONAL)
    )
}
