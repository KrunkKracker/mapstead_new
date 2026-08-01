package com.jumastappworks.mapstead.util

import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

object MeasurementFormatter {

    private fun getNumberFormat(locale: Locale, precision: Int = 1): NumberFormat {
        return NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = precision
            minimumFractionDigits = 0
        }
    }

    fun formatDistance(meters: Double, system: MeasurementSystem, locale: Locale = Locale.getDefault()): String {
        if (!meters.isFinite() || meters < 0) return ""
        val nf = getNumberFormat(locale)
        return when (system) {
            MeasurementSystem.METRIC -> {
                if (meters >= 1000.0) {
                    "${nf.format(meters / 1000.0)} km"
                } else {
                    "${nf.format(meters)} m"
                }
            }
            MeasurementSystem.IMPERIAL -> {
                val feet = meters * 3.28084
                if (feet >= 5280.0) {
                    "${nf.format(feet / 5280.0)} mi"
                } else {
                    "${nf.format(feet)} ft"
                }
            }
        }
    }

    fun formatShortDistance(meters: Double, system: MeasurementSystem, locale: Locale = Locale.getDefault()): String {
        if (!meters.isFinite() || meters < 0) return ""
        val nf = getNumberFormat(locale)
        return when (system) {
            MeasurementSystem.METRIC -> "${nf.format(meters)} m"
            MeasurementSystem.IMPERIAL -> "${nf.format(meters * 3.28084)} ft"
        }
    }

    fun formatArea(squareMeters: Double, system: MeasurementSystem, locale: Locale = Locale.getDefault()): String {
        if (!squareMeters.isFinite() || squareMeters < 0) return ""
        val nf = getNumberFormat(locale)
        val pf = getNumberFormat(locale, precision = 2)
        return when (system) {
            MeasurementSystem.METRIC -> {
                if (squareMeters >= 10000.0) {
                    "${pf.format(squareMeters / 10000.0)} ha"
                } else {
                    "${nf.format(squareMeters)} m\u00b2"
                }
            }
            MeasurementSystem.IMPERIAL -> {
                val squareFeet = squareMeters * 10.7639
                if (squareFeet >= 43560.0) {
                    "${pf.format(squareFeet / 43560.0)} ac"
                } else {
                    "${nf.format(squareFeet)} sq ft"
                }
            }
        }
    }

    fun formatAccuracy(meters: Double, system: MeasurementSystem, locale: Locale = Locale.getDefault()): String {
        if (!meters.isFinite() || meters < 0) return ""
        val nf = getNumberFormat(locale)
        return when (system) {
            MeasurementSystem.METRIC -> "\u00b1${nf.format(meters)} m"
            MeasurementSystem.IMPERIAL -> "\u00b1${nf.format(meters * 3.28084)} ft"
        }
    }

    fun displayAccuracyInput(meters: Double, system: MeasurementSystem, locale: Locale = Locale.getDefault()): String {
        if (!meters.isFinite() || meters < 0) return ""
        val nf = getNumberFormat(locale)
        return when (system) {
            MeasurementSystem.METRIC -> nf.format(meters)
            MeasurementSystem.IMPERIAL -> nf.format(meters * 3.28084)
        }
    }

    fun parseAccuracyInputToMeters(displayValue: String, system: MeasurementSystem, locale: Locale = Locale.getDefault()): Result<Double> {
        val trimmed = displayValue.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Input is blank"))
        
        val nf = NumberFormat.getNumberInstance(locale)
        val pos = ParsePosition(0)
        val number = nf.parse(trimmed, pos)
        
        if (number == null || pos.index != trimmed.length) {
            return Result.failure(IllegalArgumentException("Invalid localized number"))
        }
        
        val value = number.toDouble()
        if (!value.isFinite() || value < 0) {
            return Result.failure(IllegalArgumentException("Value must be a finite non-negative number"))
        }
        
        return when (system) {
            MeasurementSystem.METRIC -> Result.success(value)
            MeasurementSystem.IMPERIAL -> Result.success(value / 3.28084)
        }
    }
}
