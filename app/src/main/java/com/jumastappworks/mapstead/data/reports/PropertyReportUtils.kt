package com.jumastappworks.mapstead.data.reports

import java.io.File
import java.time.LocalDate

fun createPropertyReportFilename(propertyName: String, date: LocalDate): String {
    val sanitized = propertyName.map { 
        if (it.isLetterOrDigit() || it == '_') it else ' ' 
    }.joinToString("")
    .trim()
    .replace(Regex("\\s+"), "_")
    .take(30)
    
    val name = if (sanitized.isBlank()) "Property" else sanitized
    return "Mapstead_${name}_${date}.pdf"
}

fun isFileInsideDirectory(rootDirectory: File, candidate: File): Boolean {
    return try {
        val rootPath = rootDirectory.canonicalFile.toPath().normalize()
        val candidatePath = candidate.canonicalFile.toPath().normalize()
        candidatePath != rootPath && candidatePath.startsWith(rootPath)
    } catch (e: Exception) {
        false
    }
}
