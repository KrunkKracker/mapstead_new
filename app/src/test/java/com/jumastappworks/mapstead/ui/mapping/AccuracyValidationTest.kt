package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.util.MeasurementFormatter
import org.junit.Assert.*
import org.junit.Test

class AccuracyValidationTest {

    @Test
    fun `Valid Imperial feet convert to meters`() {
        val input = "10.0"
        val system = MeasurementSystem.IMPERIAL
        val result = MeasurementFormatter.parseAccuracyInputToMeters(input, system)
        assertTrue(result.isSuccess)
        // 10 feet is approx 3.048 meters
        assertEquals(3.048, result.getOrNull()!!, 0.001)
    }

    @Test
    fun `Valid Metric meters remain meters`() {
        val input = "10.0"
        val system = MeasurementSystem.METRIC
        val result = MeasurementFormatter.parseAccuracyInputToMeters(input, system)
        assertTrue(result.isSuccess)
        assertEquals(10.0, result.getOrNull()!!, 0.001)
    }

    @Test
    fun `Invalid accuracy rejects malformed input`() {
        val input = "abc"
        val system = MeasurementSystem.METRIC
        val result = MeasurementFormatter.parseAccuracyInputToMeters(input, system)
        assertTrue(result.isFailure)
    }
}
