package com.jumastappworks.mapstead.ui.mapping

import android.graphics.PointF
import android.view.MotionEvent
import org.maplibre.android.maps.MapLibreMap

class ShapeEditTouchHandler(
    private val viewModel: MapViewModel,
    private val hitRadiusPx: Float
) {
    var dragActive = false
        private set
    var midpointTapConsumed = false
        private set
    private var dragMap: MapLibreMap? = null
    var currentDragMode: MapEditingMode? = null
        private set
    var isPointMoveDrag = false
        private set

    fun handleTouch(event: MotionEvent, map: MapLibreMap, mode: MapEditingMode, isPointMoveActive: Boolean = false): Boolean {
        if (mode != MapEditingMode.EditLine && mode != MapEditingMode.EditPolygon && !isPointMoveActive) {
            resetInactiveTouchStateIfNeeded()
            return false
        }

        if (midpointTapConsumed) {
            return when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    midpointTapConsumed = false
                    true
                }
                else -> true
            }
        }

        val hitBox = android.graphics.RectF(
            event.x - hitRadiusPx, event.y - hitRadiusPx,
            event.x + hitRadiusPx, event.y + hitRadiusPx
        )

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (dragActive) return true
                midpointTapConsumed = false
                isPointMoveDrag = false

                if (isPointMoveActive) {
                    val moveHandle = map.queryRenderedFeatures(hitBox, MapsteadMapOverlayInstaller.POINT_MOVE_LAYER_ID)
                    if (moveHandle.isNotEmpty()) {
                        dragActive = true
                        dragMap = map
                        isPointMoveDrag = true
                        map.uiSettings.isScrollGesturesEnabled = false
                        return true
                    }
                    return false
                }

                val vertexLayerId = if (mode == MapEditingMode.EditLine) MapsteadMapOverlayInstaller.EDIT_VERTICES_LAYER_ID 
                              else MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_LAYER_ID
                val midpointLayerId = if (mode == MapEditingMode.EditLine) MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_LAYER_ID 
                              else MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_LAYER_ID
                
                val vertices = map.queryRenderedFeatures(hitBox, vertexLayerId)
                val midpoints = map.queryRenderedFeatures(hitBox, midpointLayerId)
                
                val touchPoint = PointF(event.x, event.y)
                val target = ShapeEditHitResolver.resolveNearest(touchPoint, vertices, midpoints, map)

                when (target) {
                    is ShapeEditHitTarget.Vertex -> {
                        if (mode == MapEditingMode.EditLine) viewModel.beginVertexDrag(target.index)
                        else viewModel.beginPolygonVertexDrag(target.index)
                        
                        dragActive = true
                        dragMap = map
                        currentDragMode = mode
                        map.uiSettings.isScrollGesturesEnabled = false
                        return true
                    }
                    is ShapeEditHitTarget.Midpoint -> {
                        if (mode == MapEditingMode.EditLine) {
                            viewModel.insertVertex(target.insertionIndex, Pair(target.longitude, target.latitude))
                        } else {
                            viewModel.insertPolygonVertex(target.insertionIndex, Pair(target.longitude, target.latitude))
                        }
                        midpointTapConsumed = true
                        return true
                    }
                    null -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragActive) {
                    val latLng = map.projection.fromScreenLocation(PointF(event.x, event.y))
                    if (isPointMoveDrag) {
                        viewModel.proposePointMove(latLng.longitude, latLng.latitude, isDragging = true)
                    } else if (currentDragMode == MapEditingMode.EditLine) {
                        viewModel.updateVertexDrag(latLng.longitude, latLng.latitude)
                    } else {
                        viewModel.updatePolygonVertexDrag(latLng.longitude, latLng.latitude)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragActive) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (isPointMoveDrag) {
                            viewModel.finishPointMoveDrag()
                        } else if (currentDragMode == MapEditingMode.EditLine) {
                            viewModel.finishVertexDrag()
                        } else {
                            viewModel.finishPolygonVertexDrag()
                        }
                    } else {
                        // CANCEL
                        if (isPointMoveDrag) {
                            viewModel.cancelPointMoveDrag()
                        } else if (currentDragMode == MapEditingMode.EditLine) {
                            viewModel.cancelVertexDrag()
                        } else {
                            viewModel.cancelPolygonVertexDrag()
                        }
                    }
                    dragMap?.uiSettings?.isScrollGesturesEnabled = true
                    dragActive = false
                    isPointMoveDrag = false
                    dragMap = null
                    currentDragMode = null
                    return true
                }
            }
        }
        return false
    }
    
    private fun resetInactiveTouchStateIfNeeded() {
        if (dragActive || midpointTapConsumed) {
            onDispose()
        }
    }

    fun onDispose() {
        if (dragActive) {
            when (currentDragMode) {
                MapEditingMode.EditLine -> viewModel.cancelVertexDrag()
                MapEditingMode.EditPolygon -> viewModel.cancelPolygonVertexDrag()
                else -> {}
            }
        }
        
        dragMap?.uiSettings?.isScrollGesturesEnabled = true
        
        dragActive = false
        midpointTapConsumed = false
        dragMap = null
        currentDragMode = null
    }
}
