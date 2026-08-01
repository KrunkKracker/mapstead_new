package com.jumastappworks.mapstead.util

import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MeasurementFormatterTest {

    private val US = Locale.US
    private val GERMANY = Locale.GERMANY

    @Test
    fun `formatDistance imperial feet`() {
        val meters = 10.0
        val result = MeasurementFormatter.formatDistance(meters, MeasurementSystem.IMPERIAL, US)
        assertEquals("32.8 ft", result)
    }

    @Test
    fun `formatDistance imperial miles`() {
        val meters = 2000.0
        val result = MeasurementFormatter.formatDistance(meters, MeasurementSystem.IMPERIAL, US)
        assertEquals("1.2 mi", result)
    }

    @Test
    fun `formatDistance metric meters`() {
        val meters = 150.5
        val result = MeasurementFormatter.formatDistance(meters, MeasurementSystem.METRIC, US)
        assertEquals("150.5 m", result)
    }

    @Test
    fun `formatDistance metric kilometers`() {
        val meters = 1500.0
        val result = MeasurementFormatter.formatDistance(meters, MeasurementSystem.METRIC, US)
        assertEquals("1.5 km", result)
    }

    @Test
    fun `formatArea imperial sq ft`() {
        val sqMeters = 100.0
        val result = MeasurementFormatter.formatArea(sqMeters, MeasurementSystem.IMPERIAL, US)
        assertEquals("1,076.4 sq ft", result)
    }

    @Test
    fun `formatArea imperial acres`() {
        val sqMeters = 10000.0
        val result = MeasurementFormatter.formatArea(sqMeters, MeasurementSystem.IMPERIAL, US)
        assertEquals("2.47 ac", result)
    }

    @Test
    fun `formatArea metric sq meters`() {
        val sqMeters = 500.0
        val result = MeasurementFormatter.formatArea(sqMeters, MeasurementSystem.METRIC, US)
        assertEquals("500 m\u00b2", result)
    }

    @Test
    fun `formatArea metric hectares`() {
        val sqMeters = 50000.0
        val result = MeasurementFormatter.formatArea(sqMeters, MeasurementSystem.METRIC, US)
        assertEquals("5 ha", result)
    }

    @Test
    fun `formatAccuracy imperial`() {
        val meters = 5.0
        val result = MeasurementFormatter.formatAccuracy(meters, MeasurementSystem.IMPERIAL, US)
        assertEquals("\u00b116.4 ft", result)
    }

    @Test
    fun `formatAccuracy metric`() {
        val meters = 5.0
        val result = MeasurementFormatter.formatAccuracy(meters, MeasurementSystem.METRIC, US)
        assertEquals("\u00b15 m", result)
    }

    @Test
    fun `parseAccuracyInput localized US`() {
        val input = "1,250.5"
        val result = MeasurementFormatter.parseAccuracyInputToMeters(input, MeasurementSystem.METRIC, US)
        assertEquals(1250.5, result.getOrThrow(), 0.001)
    }

    @Test
    fun `parseAccuracyInput localized Germany`() {
        val input = "1.250,5"
        val result = MeasurementFormatter.parseAccuracyInputToMeters(input, MeasurementSystem.METRIC, GERMANY)
        assertEquals(1250.5, result.getOrThrow(), 0.001)
    }

    @Test
    fun `parseAccuracyInput invalid number`() {
        val result = MeasurementFormatter.parseAccuracyInputToMeters("abc", MeasurementSystem.METRIC, US)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseAccuracyInput blank rejection`() {
        val result = MeasurementFormatter.parseAccuracyInputToMeters("  ", MeasurementSystem.METRIC, US)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseAccuracyInput negative rejection`() {
        val result = MeasurementFormatter.parseAccuracyInputToMeters("-10", MeasurementSystem.METRIC, US)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseAccuracyInput NaN rejection`() {
        val result = MeasurementFormatter.parseAccuracyInputToMeters("NaN", MeasurementSystem.METRIC, US)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseAccuracyInput Infinity rejection`() {
        val result = MeasurementFormatter.parseAccuracyInputToMeters("Infinity", MeasurementSystem.METRIC, US)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseAccuracyInput trailing characters rejection`() {
        val result = MeasurementFormatter.parseAccuracyInputToMeters("12.5 meters", MeasurementSystem.METRIC, US)
        assertTrue(result.isFailure)
    }

    @Test
    fun `displayAccuracyInput round trip US`() {
        val originalMeters = 10.0
        val display = MeasurementFormatter.displayAccuracyInput(originalMeters, MeasurementSystem.IMPERIAL, US)
        val parsedResult = MeasurementFormatter.parseAccuracyInputToMeters(display, MeasurementSystem.IMPERIAL, US)
        assertEquals(originalMeters, parsedResult.getOrThrow(), 0.01)
    }

    @Test
    fun `displayAccuracyInput round trip Metric`() {
        val originalMeters = 10.0
        val display = MeasurementFormatter.displayAccuracyInput(originalMeters, MeasurementSystem.METRIC, US)
        val parsedResult = MeasurementFormatter.parseAccuracyInputToMeters(display, MeasurementSystem.METRIC, US)
        assertEquals(originalMeters, parsedResult.getOrThrow(), 0.01)
    }
}
