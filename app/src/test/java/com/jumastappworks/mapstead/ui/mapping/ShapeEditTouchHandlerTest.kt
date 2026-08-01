package com.jumastappworks.mapstead.ui.mapping

import android.view.MotionEvent
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import org.maplibre.android.maps.UiSettings
import org.maplibre.geojson.Point

class ShapeEditTouchHandlerTest {

    private val viewModel = mockk<MapViewModel>(relaxed = true)
    private val map = mockk<MapLibreMap>(relaxed = true)
    private val projection = mockk<Projection>(relaxed = true)
    private val uiSettings = mockk<UiSettings>(relaxed = true)
    private val handler = ShapeEditTouchHandler(viewModel, 24f)

    @Test
    fun testMidpointTapLifecycle() {
        every { map.projection } returns projection
        every { map.uiSettings } returns uiSettings
        
        mockkObject(ShapeEditHitResolver)
        every { ShapeEditHitResolver.resolveNearest(any(), any(), any(), any()) } returns ShapeEditHitTarget.Midpoint(1, 1.5, 1.5)
        
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 100f
        every { downEvent.y } returns 100f
        
        // 1. ACTION_DOWN inserts vertex and sets consumed flag
        assertTrue(handler.handleTouch(downEvent, map, MapEditingMode.EditLine))
        verify(exactly = 1) { viewModel.insertVertex(1, Pair(1.5, 1.5)) }
        assertTrue(handler.midpointTapConsumed)
        verify(exactly = 0) { uiSettings.isScrollGesturesEnabled = false }

        // 2. ACTION_MOVE is consumed but performs no new action
        val moveEvent = mockk<MotionEvent>()
        every { moveEvent.actionMasked } returns MotionEvent.ACTION_MOVE
        assertTrue(handler.handleTouch(moveEvent, map, MapEditingMode.EditLine))
        verify(exactly = 1) { viewModel.insertVertex(any(), any()) }

        // 3. ACTION_UP resets flag and is consumed
        val upEvent = mockk<MotionEvent>()
        every { upEvent.actionMasked } returns MotionEvent.ACTION_UP
        assertTrue(handler.handleTouch(upEvent, map, MapEditingMode.EditLine))
        assertFalse(handler.midpointTapConsumed)
        
        unmockkObject(ShapeEditHitResolver)
    }

    @Test
    fun testVertexDragLifecycle() {
        every { map.projection } returns projection
        every { map.uiSettings } returns uiSettings
        
        mockkObject(ShapeEditHitResolver)
        every { ShapeEditHitResolver.resolveNearest(any(), any(), any(), any()) } returns ShapeEditHitTarget.Vertex(0)
        
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 100f
        every { downEvent.y } returns 100f
        
        // 1. ACTION_DOWN starts drag and disables scrolling
        assertTrue(handler.handleTouch(downEvent, map, MapEditingMode.EditLine))
        verify { viewModel.beginVertexDrag(0) }
        assertTrue(handler.dragActive)
        verify { uiSettings.isScrollGesturesEnabled = false }

        // 2. ACTION_UP finishes drag and restores scrolling
        val upEvent = mockk<MotionEvent>()
        every { upEvent.actionMasked } returns MotionEvent.ACTION_UP
        every { upEvent.x } returns 110f // Not strictly needed but good practice
        every { upEvent.y } returns 110f
        assertTrue(handler.handleTouch(upEvent, map, MapEditingMode.EditLine))
        verify { viewModel.finishVertexDrag() }
        assertFalse(handler.dragActive)
        verify { uiSettings.isScrollGesturesEnabled = true }
        
        unmockkObject(ShapeEditHitResolver)
    }

    @Test
    fun testDisposalCleanup() {
        every { map.projection } returns projection
        every { map.uiSettings } returns uiSettings
        
        mockkObject(ShapeEditHitResolver)
        every { ShapeEditHitResolver.resolveNearest(any(), any(), any(), any()) } returns ShapeEditHitTarget.Vertex(0)
        
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 100f
        every { downEvent.y } returns 100f
        
        handler.handleTouch(downEvent, map, MapEditingMode.EditLine)
        assertTrue(handler.dragActive)
        
        handler.onDispose()
        
        assertFalse(handler.dragActive)
        verify { viewModel.cancelVertexDrag() }
        verify { uiSettings.isScrollGesturesEnabled = true }
        
        unmockkObject(ShapeEditHitResolver)
    }

    @Test
    fun testModeSwitchResetsState() {
        every { map.projection } returns projection
        every { map.uiSettings } returns uiSettings
        
        mockkObject(ShapeEditHitResolver)
        every { ShapeEditHitResolver.resolveNearest(any(), any(), any(), any()) } returns ShapeEditHitTarget.Vertex(0)
        
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 100f
        every { downEvent.y } returns 100f
        
        handler.handleTouch(downEvent, map, MapEditingMode.EditLine)
        assertTrue(handler.dragActive)
        
        // Mode change to Select
        handler.handleTouch(downEvent, map, MapEditingMode.Select)
        
        assertFalse(handler.dragActive)
        verify { viewModel.cancelVertexDrag() }
        
        unmockkObject(ShapeEditHitResolver)
    }

    @Test
    fun testPolygonMidpointClosingEdgeRouting() {
        every { map.projection } returns projection
        
        mockkObject(ShapeEditHitResolver)
        // Closing edge insertion index is 5 for a 5-vertex polygon
        every { ShapeEditHitResolver.resolveNearest(any(), any(), any(), any()) } returns ShapeEditHitTarget.Midpoint(5, 10.0, 20.0)
        
        val downEvent = mockk<MotionEvent>()
        every { downEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { downEvent.x } returns 50f
        every { downEvent.y } returns 50f
        
        handler.handleTouch(downEvent, map, MapEditingMode.EditPolygon)
        
        verify { viewModel.insertPolygonVertex(5, Pair(10.0, 20.0)) }
        unmockkObject(ShapeEditHitResolver)
    }
}
