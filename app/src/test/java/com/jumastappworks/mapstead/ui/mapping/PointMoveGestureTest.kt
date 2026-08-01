package com.jumastappworks.mapstead.ui.mapping

import android.view.MotionEvent
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import android.graphics.PointF
import org.maplibre.android.geometry.LatLng
import java.util.UUID

class PointMoveGestureTest {

    private val viewModel = mockk<MapViewModel>(relaxed = true)
    private val handler = ShapeEditTouchHandler(viewModel, 24f)
    private val map = mockk<MapLibreMap>(relaxed = true)
    private val projection = mockk<Projection>(relaxed = true)

    @Test
    fun `touch down on move handle starts point drag`() {
        val event = mockk<MotionEvent>()
        every { event.actionMasked } returns MotionEvent.ACTION_DOWN
        every { event.x } returns 100f
        every { event.y } returns 100f

        // Mock move handle hit
        // Use anyVararg for the String... varargs parameter
        every { map.queryRenderedFeatures(any<android.graphics.RectF>(), *anyVararg()) } returns listOf(mockk())
        
        val handled = handler.handleTouch(event, map, MapEditingMode.Select, isPointMoveActive = true)
        
        assertTrue("Drag should be active", handled)
        assertTrue("Drag should be active in handler", handler.dragActive)
        assertTrue("Is point move drag", handler.isPointMoveDrag)
        verify { map.uiSettings.isScrollGesturesEnabled = false }
    }

    @Test
    fun `touch move updates point move proposal`() {
        // Start drag
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 100f
        every { downEvent.y } returns 100f
        every { map.queryRenderedFeatures(any<android.graphics.RectF>(), *anyVararg()) } returns listOf(mockk())
        handler.handleTouch(downEvent, map, MapEditingMode.Select, isPointMoveActive = true)

        // Move
        val moveEvent = mockk<MotionEvent>()
        every { moveEvent.actionMasked } returns MotionEvent.ACTION_MOVE
        every { moveEvent.x } returns 150f
        every { moveEvent.y } returns 150f
        
        every { map.projection } returns projection
        every { projection.fromScreenLocation(any()) } returns LatLng(45.0, -122.0)

        val handled = handler.handleTouch(moveEvent, map, MapEditingMode.Select, isPointMoveActive = true)
        
        assertTrue(handled)
        verify { viewModel.proposePointMove(-122.0, 45.0, isDragging = true) }
    }

    @Test
    fun `touch up restores map panning`() {
        // Start drag
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 100f
        every { downEvent.y } returns 100f
        every { map.queryRenderedFeatures(any<android.graphics.RectF>(), *anyVararg()) } returns listOf(mockk())
        handler.handleTouch(downEvent, map, MapEditingMode.Select, isPointMoveActive = true)

        // Up
        val upEvent = mockk<MotionEvent>()
        every { upEvent.actionMasked } returns MotionEvent.ACTION_UP
        every { upEvent.x } returns 100f
        every { upEvent.y } returns 100f

        val handled = handler.handleTouch(upEvent, map, MapEditingMode.Select, isPointMoveActive = true)
        
        assertTrue(handled)
        assertFalse(handler.dragActive)
        verify { map.uiSettings.isScrollGesturesEnabled = true }
        verify { viewModel.finishPointMoveDrag() }
    }
}
