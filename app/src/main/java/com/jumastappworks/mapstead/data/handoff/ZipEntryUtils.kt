package com.jumastappworks.mapstead.data.handoff

object ZipEntryUtils {

    fun createSafeEntryName(
        category: String,
        displayName: String,
        extension: String,
        existingNames: Set<String>
    ): String {
        val sanitizedCategory = sanitizeToken(category)
        val sanitizedBase = sanitizeToken(displayName).take(40).ifBlank { "attachment" }
        val sanitizedExt = sanitizeToken(extension).take(5).ifBlank { "bin" }

        var candidate = "attachments/$sanitizedCategory/$sanitizedBase.$sanitizedExt"
        var counter = 2
        
        while (existingNames.contains(candidate)) {
            candidate = "attachments/$sanitizedCategory/${sanitizedBase}_$counter.$sanitizedExt"
            counter++
        }
        
        return candidate
    }

    fun sanitizeToken(token: String): String {
        return token.map { 
            if (it.isLetterOrDigit() || it == '_') it else ' ' 
        }.joinToString("")
        .trim()
        .replace(Regex("\\s+"), "_")
    }

    fun isValidZipEntryName(name: String): Boolean {
        return name.isNotBlank() && 
                !name.startsWith("/") && 
                !name.startsWith("\\") && 
                !name.contains("../") && 
                !name.contains("..\\") &&
                !name.contains(":") &&
                !name.contains("\u0000")
    }
}
