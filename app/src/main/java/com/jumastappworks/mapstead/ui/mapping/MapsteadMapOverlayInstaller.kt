package com.jumastappworks.mapstead.ui.mapping

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource

data class MapFeatureVisualStyle(
    val color: String,
    val outlineColor: String,
    val lineWidth: Float,
    val dashed: Boolean,
    val fillOpacity: Float
)

enum class PresetVisualClass {
    LEGACY,
    BOUNDARY,
    BUILDING,
    FENCE,
    UTILITY_ROUTE,
    UTILITY_AREA,
    WATER_FEATURE,
    DRIVEWAY,
    OUTDOOR_AREA
}

data class PresetStyleSpec(
    val visualClass: PresetVisualClass,
    val color: String,
    val outlineColor: String,
    val lineWidth: Float,
    val dashed: Boolean,
    val fillOpacity: Float
)

class MapsteadMapOverlayInstaller {

    companion object {
        const val STYLE_BOUNDARY = "property_boundary"
        const val STYLE_BUILDING = "building"
        const val STYLE_FENCE = "fence"
        const val STYLE_UTILITY_ROUTE = "utility_route"
        const val STYLE_UTILITY_AREA = "utility_area"
        const val STYLE_WATER_FEATURE = "water_feature"
        const val STYLE_DRIVEWAY = "driveway"
        const val STYLE_OUTDOOR_AREA = "outdoor_area"

        const val COLOR_BOUNDARY = "#9C27B0"
        const val COLOR_BUILDING = "#757575"
        const val COLOR_FENCE = "#8B4513"
        const val COLOR_UTILITY = "#FFA500"
        const val COLOR_WATER = "#2196F3"
        const val COLOR_WATER_OUTLINE = "#1976D2"
        const val COLOR_DRIVEWAY = "#BDBDBD"
        const val COLOR_OUTDOOR = "#4CAF50"
        const val COLOR_OUTDOOR_OUTLINE = "#2E7D32"
        const val COLOR_LEGACY = "#FF0000"

        fun getStyleSpec(styleId: String?, geometryType: String): PresetStyleSpec {
            return when (styleId) {
                STYLE_BOUNDARY -> PresetStyleSpec(
                    visualClass = PresetVisualClass.BOUNDARY,
                    color = COLOR_BOUNDARY,
                    outlineColor = COLOR_BOUNDARY,
                    lineWidth = 3f,
                    dashed = true,
                    fillOpacity = 0.0f
                )
                STYLE_BUILDING -> PresetStyleSpec(
                    visualClass = PresetVisualClass.BUILDING,
                    color = COLOR_BUILDING,
                    outlineColor = "#000000",
                    lineWidth = 2f,
                    dashed = false,
                    fillOpacity = 0.3f
                )
                STYLE_FENCE -> PresetStyleSpec(
                    visualClass = PresetVisualClass.FENCE,
                    color = COLOR_FENCE,
                    outlineColor = COLOR_FENCE,
                    lineWidth = 4f,
                    dashed = false,
                    fillOpacity = 0.0f
                )
                STYLE_UTILITY_ROUTE -> PresetStyleSpec(
                    visualClass = PresetVisualClass.UTILITY_ROUTE,
                    color = COLOR_UTILITY,
                    outlineColor = COLOR_UTILITY,
                    lineWidth = 4f,
                    dashed = true,
                    fillOpacity = 0.0f
                )
                STYLE_UTILITY_AREA -> PresetStyleSpec(
                    visualClass = PresetVisualClass.UTILITY_AREA,
                    color = COLOR_UTILITY,
                    outlineColor = "#E68A00",
                    lineWidth = 2f,
                    dashed = false,
                    fillOpacity = 0.3f
                )
                STYLE_WATER_FEATURE -> PresetStyleSpec(
                    visualClass = PresetVisualClass.WATER_FEATURE,
                    color = COLOR_WATER,
                    outlineColor = COLOR_WATER_OUTLINE,
                    lineWidth = 2f,
                    dashed = false,
                    fillOpacity = 0.3f
                )
                STYLE_DRIVEWAY -> PresetStyleSpec(
                    visualClass = PresetVisualClass.DRIVEWAY,
                    color = COLOR_DRIVEWAY,
                    outlineColor = "#9E9E9E",
                    lineWidth = if (geometryType == "LINESTRING") 4f else 2f,
                    dashed = false,
                    fillOpacity = 0.3f
                )
                STYLE_OUTDOOR_AREA -> PresetStyleSpec(
                    visualClass = PresetVisualClass.OUTDOOR_AREA,
                    color = COLOR_OUTDOOR,
                    outlineColor = COLOR_OUTDOOR_OUTLINE,
                    lineWidth = 2f,
                    dashed = false,
                    fillOpacity = 0.3f
                )
                else -> PresetStyleSpec(
                    visualClass = PresetVisualClass.LEGACY,
                    color = COLOR_LEGACY,
                    outlineColor = COLOR_LEGACY,
                    lineWidth = if (geometryType == "LINESTRING") 4f else 2f,
                    dashed = false,
                    fillOpacity = 0.3f
                )
            }
        }

        fun classifyPresetStyle(styleId: String?): PresetVisualClass {
            return getStyleSpec(styleId, "POINT").visualClass
        }

        fun resolvePresetVisualStyle(
            geometryType: String,
            presetStyle: String?
        ): MapFeatureVisualStyle {
            val spec = getStyleSpec(presetStyle, geometryType)
            return MapFeatureVisualStyle(
                color = spec.color,
                outlineColor = spec.outlineColor,
                lineWidth = spec.lineWidth,
                dashed = spec.dashed,
                fillOpacity = spec.fillOpacity
            )
        }

        private fun getAllStyleIds() = listOf(
            STYLE_BOUNDARY, STYLE_BUILDING, STYLE_FENCE, STYLE_UTILITY_ROUTE,
            STYLE_UTILITY_AREA, STYLE_WATER_FEATURE, STYLE_DRIVEWAY, STYLE_OUTDOOR_AREA
        )

        const val FEATURES_SOURCE_ID = "mapstead-features-source"
        const val POINTS_LAYER_ID = "mapstead-features-points-layer"
        const val LINES_LAYER_ID = "mapstead-features-lines-layer"
        const val POINTS_HIGHLIGHT_LAYER_ID = "mapstead-features-points-highlight"
        const val LINES_HIGHLIGHT_LAYER_ID = "mapstead-features-lines-highlight"

        const val PHONE_POINT_SOURCE_ID = "mapstead-phone-location-point-source"
        const val PHONE_ACCURACY_SOURCE_ID = "mapstead-phone-accuracy-source"
        const val PHONE_ACCURACY_FILL_ID = "mapstead-phone-accuracy-fill"
        const val PHONE_ACCURACY_OUTLINE_ID = "mapstead-phone-accuracy-outline"
        const val PHONE_LOCATION_CIRCLE_ID = "mapstead-phone-location-circle"

        const val DRAFT_LINE_SOURCE_ID = "mapstead-draft-line-source"
        const val DRAFT_LINE_LAYER_ID = "mapstead-draft-line-layer"
        const val DRAFT_VERTICES_SOURCE_ID = "mapstead-draft-vertices-source"
        const val DRAFT_VERTICES_LAYER_ID = "mapstead-draft-vertices-layer"

        const val EDIT_LINE_SOURCE_ID = "mapstead-edit-line-source"
        const val EDIT_LINE_LAYER_ID = "mapstead-edit-line-layer"
        const val EDIT_VERTICES_SOURCE_ID = "mapstead-edit-vertices-source"
        const val EDIT_VERTICES_LAYER_ID = "mapstead-edit-vertices-layer"
        const val EDIT_MIDPOINTS_SOURCE_ID = "mapstead-edit-midpoints-source"
        const val EDIT_MIDPOINTS_LAYER_ID = "mapstead-edit-midpoints-layer"
        const val EDIT_SELECTED_VERTEX_LAYER_ID = "mapstead-edit-selected-vertex-highlight"

        const val DRAFT_POLYGON_SOURCE_ID = "mapstead-polygon-draft-source"
        const val DRAFT_POLYGON_FILL_LAYER_ID = "mapstead-polygon-draft-fill-layer"
        const val DRAFT_POLYGON_OUTLINE_LAYER_ID = "mapstead-polygon-draft-outline-layer"
        const val DRAFT_POLYGON_VERTICES_SOURCE_ID = "mapstead-polygon-draft-vertices-source"
        const val DRAFT_POLYGON_VERTICES_LAYER_ID = "mapstead-polygon-draft-vertices-layer"

        const val SAVED_POLYGONS_FILL_LAYER_ID = "mapstead-polygons-fill-layer"
        const val SAVED_POLYGONS_OUTLINE_LAYER_ID = "mapstead-polygons-outline-layer"
        const val SAVED_POLYGONS_HIGHLIGHT_LAYER_ID = "mapstead-polygons-highlight-layer"

        const val ACTIVE_EDIT_HIGHLIGHT_SOURCE_ID = "mapstead-active-edit-source"
        const val ACTIVE_EDIT_POINTS_HIGHLIGHT_LAYER_ID = "mapstead-active-edit-points-highlight"
        const val ACTIVE_EDIT_LINES_HIGHLIGHT_LAYER_ID = "mapstead-active-edit-lines-highlight"
        const val ACTIVE_EDIT_POLYGONS_HIGHLIGHT_LAYER_ID = "mapstead-active-edit-polygons-highlight"

        const val ORIGINAL_LOCATION_GHOST_SOURCE_ID = "mapstead-original-location-ghost-source"
        const val ORIGINAL_LOCATION_GHOST_LAYER_ID = "mapstead-original-location-ghost-layer"

        const val POLYGON_EDIT_LINE_SOURCE_ID = "mapstead-polygon-edit-line-source"
        const val POLYGON_EDIT_LINE_LAYER_ID = "mapstead-polygon-edit-line-layer"
        const val POLYGON_EDIT_VERTICES_SOURCE_ID = "mapstead-polygon-edit-vertices-source"
        const val POLYGON_EDIT_VERTICES_LAYER_ID = "mapstead-polygon-edit-vertices-layer"
        const val POLYGON_EDIT_MIDPOINTS_SOURCE_ID = "mapstead-polygon-edit-midpoints-source"
        const val POLYGON_EDIT_MIDPOINTS_LAYER_ID = "mapstead-polygon-edit-midpoints-layer"
        const val POLYGON_EDIT_SELECTED_VERTEX_LAYER_ID = "mapstead-polygon-edit-selected-vertex-highlight"

        const val POINT_MOVE_SOURCE_ID = "mapstead-point-move-source"
        const val POINT_MOVE_LAYER_ID = "mapstead-point-move-layer"

        fun installOrUpdateSource(style: Style, sourceId: String, geoJson: String) {
            val source = style.getSource(sourceId) as? GeoJsonSource
            if (source != null) {
                source.setGeoJson(geoJson)
            } else {
                style.addSource(GeoJsonSource(sourceId, geoJson))
            }
        }

        fun removeSourceAndLayers(style: Style, sourceId: String, layerIds: List<String>) {
            layerIds.forEach { layerId ->
                if (style.getLayer(layerId) != null) {
                    style.removeLayer(layerId)
                }
            }
            if (style.getSource(sourceId) != null) {
                style.removeSource(sourceId)
            }
        }

        fun installFeaturesLayers(style: Style) {
            val defaultColor = Color.parseColor(COLOR_LEGACY)
            val defaultLineWidth = 4f
            val defaultFillOpacity = 0.3f

            // Points Layer
            if (style.getLayer(POINTS_LAYER_ID) == null) {
                val points = CircleLayer(POINTS_LAYER_ID, FEATURES_SOURCE_ID)
                points.withFilter(Expression.eq(Expression.get("geom_type"), Expression.literal("POINT")))
                
                val pointColorStops = getAllStyleIds().map { id ->
                    Expression.stop(id, Color.parseColor(getStyleSpec(id, "POINT").color))
                }.toTypedArray()

                points.setProperties(
                    PropertyFactory.circleColor(Expression.match(Expression.get("preset_style"), Expression.literal(defaultColor), *pointColorStops)),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleOpacity(Expression.get("opacity"))
                )
                style.addLayer(points)
            }

            // Lines Layer
            if (style.getLayer(LINES_LAYER_ID) == null) {
                val lines = LineLayer(LINES_LAYER_ID, FEATURES_SOURCE_ID)
                lines.withFilter(Expression.eq(Expression.get("geom_type"), Expression.literal("LINESTRING")))
                
                val lineColorStops = getAllStyleIds().map { id ->
                    Expression.stop(id, Color.parseColor(getStyleSpec(id, "LINESTRING").color))
                }.toTypedArray()

                val lineDashStops = getAllStyleIds().map { id ->
                    val spec = getStyleSpec(id, "LINESTRING")
                    val dash = if (spec.dashed) {
                        if (id == STYLE_BOUNDARY) arrayOf(2f, 2f) else arrayOf(4f, 1f)
                    } else emptyArray<Float>()
                    Expression.stop(id, dash)
                }.toTypedArray()

                lines.setProperties(
                    PropertyFactory.lineColor(Expression.match(Expression.get("preset_style"), Expression.literal(defaultColor), *lineColorStops)),
                    PropertyFactory.lineWidth(defaultLineWidth),
                    PropertyFactory.lineOpacity(Expression.get("opacity")),
                    PropertyFactory.lineDasharray(Expression.match(Expression.get("preset_style"), Expression.literal(emptyArray<Float>()), *lineDashStops))
                )
                if (style.getLayer(POINTS_LAYER_ID) != null) {
                    style.addLayerBelow(lines, POINTS_LAYER_ID)
                } else {
                    style.addLayer(lines)
                }
            }

            // Polygons Layer
            if (style.getLayer(SAVED_POLYGONS_FILL_LAYER_ID) == null) {
                val fill = FillLayer(SAVED_POLYGONS_FILL_LAYER_ID, FEATURES_SOURCE_ID)
                fill.withFilter(Expression.eq(Expression.get("geom_type"), Expression.literal("POLYGON")))
                
                val fillColorStops = getAllStyleIds().map { id ->
                    val spec = getStyleSpec(id, "POLYGON")
                    val color = if (id == STYLE_BOUNDARY) Color.TRANSPARENT else Color.parseColor(spec.color)
                    Expression.stop(id, color)
                }.toTypedArray()

                val fillOpacityStops = getAllStyleIds().map { id ->
                    val spec = getStyleSpec(id, "POLYGON")
                    Expression.stop(id, Expression.product(Expression.get("opacity"), Expression.literal(spec.fillOpacity)))
                }.toTypedArray()

                fill.setProperties(
                    PropertyFactory.fillColor(Expression.match(Expression.get("preset_style"), Expression.literal(defaultColor), *fillColorStops)),
                    PropertyFactory.fillOpacity(Expression.match(Expression.get("preset_style"), Expression.product(Expression.get("opacity"), Expression.literal(defaultFillOpacity)), *fillOpacityStops))
                )
                style.addLayerBelow(fill, LINES_LAYER_ID)
                
                val outline = LineLayer(SAVED_POLYGONS_OUTLINE_LAYER_ID, FEATURES_SOURCE_ID)
                outline.withFilter(Expression.eq(Expression.get("geom_type"), Expression.literal("POLYGON")))
                
                val outlineColorStops = getAllStyleIds().map { id ->
                    val spec = getStyleSpec(id, "POLYGON")
                    Expression.stop(id, Color.parseColor(spec.outlineColor))
                }.toTypedArray()

                val outlineWidthStops = getAllStyleIds().map { id ->
                    val spec = getStyleSpec(id, "POLYGON")
                    Expression.stop(id, spec.lineWidth)
                }.toTypedArray()

                val outlineDashStops = getAllStyleIds().map { id ->
                    val spec = getStyleSpec(id, "POLYGON")
                    val dash = if (spec.dashed) arrayOf(3f, 3f) else emptyArray<Float>()
                    Expression.stop(id, dash)
                }.toTypedArray()

                outline.setProperties(
                    PropertyFactory.lineColor(Expression.match(Expression.get("preset_style"), Expression.literal(defaultColor), *outlineColorStops)),
                    PropertyFactory.lineWidth(Expression.match(Expression.get("preset_style"), Expression.literal(2f), *outlineWidthStops)),
                    PropertyFactory.lineOpacity(Expression.get("opacity")),
                    PropertyFactory.lineDasharray(Expression.match(Expression.get("preset_style"), Expression.literal(emptyArray<Float>()), *outlineDashStops))
                )
                style.addLayerAbove(outline, SAVED_POLYGONS_FILL_LAYER_ID)
            }
        }

        fun installHighlights(style: Style, selectedFeatureId: String?) {
            listOf(POINTS_HIGHLIGHT_LAYER_ID, LINES_HIGHLIGHT_LAYER_ID, SAVED_POLYGONS_HIGHLIGHT_LAYER_ID).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (selectedFeatureId != null) {
                val pointsHighlight = CircleLayer(POINTS_HIGHLIGHT_LAYER_ID, FEATURES_SOURCE_ID)
                pointsHighlight.withFilter(
                    Expression.all(
                        Expression.eq(Expression.get("geom_type"), Expression.literal("POINT")),
                        Expression.eq(Expression.get("id"), Expression.literal(selectedFeatureId))
                    )
                )
                pointsHighlight.setProperties(
                    PropertyFactory.circleColor(Color.BLUE),
                    PropertyFactory.circleRadius(11f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
                style.addLayer(pointsHighlight)

                val linesHighlight = LineLayer(LINES_HIGHLIGHT_LAYER_ID, FEATURES_SOURCE_ID)
                linesHighlight.withFilter(
                    Expression.all(
                        Expression.eq(Expression.get("geom_type"), Expression.literal("LINESTRING")),
                        Expression.eq(Expression.get("id"), Expression.literal(selectedFeatureId))
                    )
                )
                linesHighlight.setProperties(
                    PropertyFactory.lineColor(Color.BLUE),
                    PropertyFactory.lineWidth(7f)
                )
                if (style.getLayer(POINTS_HIGHLIGHT_LAYER_ID) != null) {
                    style.addLayerBelow(linesHighlight, POINTS_HIGHLIGHT_LAYER_ID)
                } else {
                    style.addLayer(linesHighlight)
                }

                val polyHighlight = LineLayer(SAVED_POLYGONS_HIGHLIGHT_LAYER_ID, FEATURES_SOURCE_ID)
                polyHighlight.withFilter(
                    Expression.all(
                        Expression.eq(Expression.get("geom_type"), Expression.literal("POLYGON")),
                        Expression.eq(Expression.get("id"), Expression.literal(selectedFeatureId))
                    )
                )
                polyHighlight.setProperties(
                    PropertyFactory.lineColor(Color.BLUE),
                    PropertyFactory.lineWidth(5f)
                )
                style.addLayerAbove(polyHighlight, SAVED_POLYGONS_OUTLINE_LAYER_ID)
            }
        }

        fun installActiveEditHighlights(style: Style, activeEditFeatureId: String?) {
            listOf(
                ACTIVE_EDIT_POINTS_HIGHLIGHT_LAYER_ID,
                ACTIVE_EDIT_LINES_HIGHLIGHT_LAYER_ID,
                ACTIVE_EDIT_POLYGONS_HIGHLIGHT_LAYER_ID
            ).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (activeEditFeatureId != null) {
                // Editing Highlight: Distinct magenta emphasis beneath the working geometry
                
                val pointsHighlight = CircleLayer(ACTIVE_EDIT_POINTS_HIGHLIGHT_LAYER_ID, FEATURES_SOURCE_ID)
                pointsHighlight.withFilter(
                    Expression.all(
                        Expression.eq(Expression.get("geom_type"), Expression.literal("POINT")),
                        Expression.eq(Expression.get("id"), Expression.literal(activeEditFeatureId))
                    )
                )
                pointsHighlight.setProperties(
                    PropertyFactory.circleColor(Color.MAGENTA),
                    PropertyFactory.circleRadius(14f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleOpacity(0.4f)
                )
                style.addLayer(pointsHighlight)

                val linesHighlight = LineLayer(ACTIVE_EDIT_LINES_HIGHLIGHT_LAYER_ID, FEATURES_SOURCE_ID)
                linesHighlight.withFilter(
                    Expression.all(
                        Expression.eq(Expression.get("geom_type"), Expression.literal("LINESTRING")),
                        Expression.eq(Expression.get("id"), Expression.literal(activeEditFeatureId))
                    )
                )
                linesHighlight.setProperties(
                    PropertyFactory.lineColor(Color.MAGENTA),
                    PropertyFactory.lineWidth(10f),
                    PropertyFactory.lineOpacity(0.3f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
                style.addLayerBelow(linesHighlight, ACTIVE_EDIT_POINTS_HIGHLIGHT_LAYER_ID)

                val polyHighlight = FillLayer(ACTIVE_EDIT_POLYGONS_HIGHLIGHT_LAYER_ID, FEATURES_SOURCE_ID)
                polyHighlight.withFilter(
                    Expression.all(
                        Expression.eq(Expression.get("geom_type"), Expression.literal("POLYGON")),
                        Expression.eq(Expression.get("id"), Expression.literal(activeEditFeatureId))
                    )
                )
                polyHighlight.setProperties(
                    PropertyFactory.fillColor(Color.MAGENTA),
                    PropertyFactory.fillOpacity(0.2f)
                )
                // Place below outlines
                style.addLayerBelow(polyHighlight, SAVED_POLYGONS_OUTLINE_LAYER_ID)
            }
        }

        fun installOriginalLocationGhost(style: Style) {
            if (style.getLayer(ORIGINAL_LOCATION_GHOST_LAYER_ID) != null) {
                style.removeLayer(ORIGINAL_LOCATION_GHOST_LAYER_ID)
            }

            if (style.getSource(ORIGINAL_LOCATION_GHOST_SOURCE_ID) != null) {
                val ghost = CircleLayer(ORIGINAL_LOCATION_GHOST_LAYER_ID, ORIGINAL_LOCATION_GHOST_SOURCE_ID)
                ghost.setProperties(
                    PropertyFactory.circleColor(Color.GRAY),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f),
                    PropertyFactory.circleOpacity(0.5f)
                )
                // Ghost should be below the move handle
                val aboveLayerId = when {
                    style.getLayer(POINT_MOVE_LAYER_ID) != null -> POINT_MOVE_LAYER_ID
                    else -> null
                }
                if (aboveLayerId != null) {
                    style.addLayerBelow(ghost, aboveLayerId)
                } else {
                    style.addLayer(ghost)
                }
            }
        }

        fun installPhoneLocationLayers(style: Style) {
            listOf(PHONE_ACCURACY_FILL_ID, PHONE_ACCURACY_OUTLINE_ID, PHONE_LOCATION_CIRCLE_ID).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (style.getSource(PHONE_ACCURACY_SOURCE_ID) != null) {
                val fill = FillLayer(PHONE_ACCURACY_FILL_ID, PHONE_ACCURACY_SOURCE_ID)
                fill.setProperties(PropertyFactory.fillColor(Color.parseColor("#442196F3")))
                
                val outline = LineLayer(PHONE_ACCURACY_OUTLINE_ID, PHONE_ACCURACY_SOURCE_ID)
                outline.setProperties(PropertyFactory.lineColor(Color.parseColor("#2196F3")), PropertyFactory.lineWidth(1f))
                
                val belowLayerId = when {
                    style.getLayer(LINES_LAYER_ID) != null -> LINES_LAYER_ID
                    style.getLayer(POINTS_LAYER_ID) != null -> POINTS_LAYER_ID
                    else -> null
                }
                if (belowLayerId != null) {
                    style.addLayerBelow(fill, belowLayerId)
                    style.addLayerAbove(outline, PHONE_ACCURACY_FILL_ID)
                } else {
                    style.addLayer(fill)
                    style.addLayerAbove(outline, PHONE_ACCURACY_FILL_ID)
                }
            }

            if (style.getSource(PHONE_POINT_SOURCE_ID) != null) {
                val dot = CircleLayer(PHONE_LOCATION_CIRCLE_ID, PHONE_POINT_SOURCE_ID)
                dot.setProperties(
                    PropertyFactory.circleColor(Color.parseColor("#2196F3")),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(dot)
            }
        }

        fun installDraftLayers(style: Style) {
            listOf(DRAFT_LINE_LAYER_ID, DRAFT_VERTICES_LAYER_ID).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (style.getSource(DRAFT_LINE_SOURCE_ID) != null) {
                val draftLine = LineLayer(DRAFT_LINE_LAYER_ID, DRAFT_LINE_SOURCE_ID)
                draftLine.setProperties(
                    PropertyFactory.lineColor(Color.BLUE),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                )
                style.addLayer(draftLine)
            }

            if (style.getSource(DRAFT_VERTICES_SOURCE_ID) != null) {
                val draftVertices = CircleLayer(DRAFT_VERTICES_LAYER_ID, DRAFT_VERTICES_SOURCE_ID)
                draftVertices.setProperties(
                    PropertyFactory.circleColor(Color.BLUE),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                )
                style.addLayer(draftVertices)
            }
        }

        fun installEditLayers(style: Style, selectedVertexIndex: Int?) {
            listOf(EDIT_LINE_LAYER_ID, EDIT_VERTICES_LAYER_ID, EDIT_MIDPOINTS_LAYER_ID, EDIT_SELECTED_VERTEX_LAYER_ID).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (style.getSource(EDIT_LINE_SOURCE_ID) != null) {
                val line = LineLayer(EDIT_LINE_LAYER_ID, EDIT_LINE_SOURCE_ID)
                line.setProperties(
                    PropertyFactory.lineColor(Color.GREEN),
                    PropertyFactory.lineWidth(4f)
                )
                style.addLayer(line)
            }

            if (style.getSource(EDIT_MIDPOINTS_SOURCE_ID) != null) {
                val midpoints = CircleLayer(EDIT_MIDPOINTS_LAYER_ID, EDIT_MIDPOINTS_SOURCE_ID)
                midpoints.setProperties(
                    PropertyFactory.circleColor(Color.WHITE),
                    PropertyFactory.circleRadius(5f),
                    PropertyFactory.circleStrokeColor(Color.GREEN),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(midpoints)
            }

            if (style.getSource(EDIT_VERTICES_SOURCE_ID) != null) {
                val vertices = CircleLayer(EDIT_VERTICES_LAYER_ID, EDIT_VERTICES_SOURCE_ID)
                vertices.setProperties(
                    PropertyFactory.circleColor(Color.GREEN),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(vertices)
            }

            if (selectedVertexIndex != null && style.getSource(EDIT_VERTICES_SOURCE_ID) != null) {
                val highlight = CircleLayer(EDIT_SELECTED_VERTEX_LAYER_ID, EDIT_VERTICES_SOURCE_ID)
                highlight.withFilter(Expression.eq(Expression.get("index"), Expression.literal(selectedVertexIndex)))
                highlight.setProperties(
                    PropertyFactory.circleColor(Color.YELLOW),
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleStrokeColor(Color.BLACK),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(highlight)
            }
        }

        fun installPolygonDraftLayers(style: Style) {
            listOf(DRAFT_POLYGON_FILL_LAYER_ID, DRAFT_POLYGON_OUTLINE_LAYER_ID, DRAFT_POLYGON_VERTICES_LAYER_ID).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (style.getSource(DRAFT_POLYGON_SOURCE_ID) != null) {
                val fill = FillLayer(DRAFT_POLYGON_FILL_LAYER_ID, DRAFT_POLYGON_SOURCE_ID)
                fill.setProperties(
                    PropertyFactory.fillColor(Color.BLUE),
                    PropertyFactory.fillOpacity(0.3f)
                )
                style.addLayer(fill)

                val outline = LineLayer(DRAFT_POLYGON_OUTLINE_LAYER_ID, DRAFT_POLYGON_SOURCE_ID)
                outline.setProperties(
                    PropertyFactory.lineColor(Color.BLUE),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                )
                style.addLayerAbove(outline, DRAFT_POLYGON_FILL_LAYER_ID)
            }

            if (style.getSource(DRAFT_POLYGON_VERTICES_SOURCE_ID) != null) {
                val vertices = CircleLayer(DRAFT_POLYGON_VERTICES_LAYER_ID, DRAFT_POLYGON_VERTICES_SOURCE_ID)
                vertices.setProperties(
                    PropertyFactory.circleColor(Color.BLUE),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                )
                style.addLayer(vertices)
            }
        }

        fun installPointMoveLayers(style: Style) {
            if (style.getLayer(POINT_MOVE_LAYER_ID) != null) {
                style.removeLayer(POINT_MOVE_LAYER_ID)
            }
            if (style.getSource(POINT_MOVE_SOURCE_ID) != null) {
                val layer = CircleLayer(POINT_MOVE_LAYER_ID, POINT_MOVE_SOURCE_ID)
                layer.setProperties(
                    PropertyFactory.circleColor(Color.YELLOW),
                    PropertyFactory.circleRadius(16f), // Larger for finger acquisition
                    PropertyFactory.circleStrokeColor(Color.BLACK),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleOpacity(0.9f)
                )
                style.addLayer(layer)
            }
        }

        fun installPolygonEditLayers(style: Style, selectedVertexIndex: Int?) {
            listOf(POLYGON_EDIT_LINE_LAYER_ID, POLYGON_EDIT_VERTICES_LAYER_ID, POLYGON_EDIT_MIDPOINTS_LAYER_ID, POLYGON_EDIT_SELECTED_VERTEX_LAYER_ID).forEach {
                if (style.getLayer(it) != null) style.removeLayer(it)
            }

            if (style.getSource(POLYGON_EDIT_LINE_SOURCE_ID) != null) {
                val line = LineLayer(POLYGON_EDIT_LINE_LAYER_ID, POLYGON_EDIT_LINE_SOURCE_ID)
                line.setProperties(
                    PropertyFactory.lineColor(Color.GREEN),
                    PropertyFactory.lineWidth(4f)
                )
                style.addLayer(line)
            }

            if (style.getSource(POLYGON_EDIT_MIDPOINTS_SOURCE_ID) != null) {
                val midpoints = CircleLayer(POLYGON_EDIT_MIDPOINTS_LAYER_ID, POLYGON_EDIT_MIDPOINTS_SOURCE_ID)
                midpoints.setProperties(
                    PropertyFactory.circleColor(Color.WHITE),
                    PropertyFactory.circleRadius(5f),
                    PropertyFactory.circleStrokeColor(Color.GREEN),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(midpoints)
            }

            if (style.getSource(POLYGON_EDIT_VERTICES_SOURCE_ID) != null) {
                val vertices = CircleLayer(POLYGON_EDIT_VERTICES_LAYER_ID, POLYGON_EDIT_VERTICES_SOURCE_ID)
                vertices.setProperties(
                    PropertyFactory.circleColor(Color.GREEN),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(vertices)
            }

            if (selectedVertexIndex != null && style.getSource(POLYGON_EDIT_VERTICES_SOURCE_ID) != null) {
                val highlight = CircleLayer(POLYGON_EDIT_SELECTED_VERTEX_LAYER_ID, POLYGON_EDIT_VERTICES_SOURCE_ID)
                highlight.withFilter(Expression.eq(Expression.get("index"), Expression.literal(selectedVertexIndex)))
                highlight.setProperties(
                    PropertyFactory.circleColor(Color.YELLOW),
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleStrokeColor(Color.BLACK),
                    PropertyFactory.circleStrokeWidth(2f)
                )
                style.addLayer(highlight)
            }
        }
    }
}
