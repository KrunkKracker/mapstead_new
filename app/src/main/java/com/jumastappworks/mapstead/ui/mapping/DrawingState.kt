package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.NormalizedPoint

sealed class DrawingTool {
    data object None : DrawingTool()
    data object Point : DrawingTool()
    data object Polyline : DrawingTool()
    data object Polygon : DrawingTool()
    data object Rectangle : DrawingTool()
    data object Circle : DrawingTool()
    data object TextLabel : DrawingTool()
    data object Measurement : DrawingTool()
    data object ReferencePoint : DrawingTool()
}

data class DrawingState(
    val activeTool: DrawingTool = DrawingTool.None,
    val currentPoints: List<NormalizedPoint> = emptyList(),
    val isComplete: Boolean = false
)
