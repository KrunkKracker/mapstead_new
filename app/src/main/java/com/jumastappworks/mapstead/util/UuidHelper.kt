package com.jumastappworks.mapstead.util

import java.util.UUID

object UuidHelper {
    fun safeParse(value: String?): UUID? {
        if (value.isNullOrBlank()) return null
        return try {
            UUID.fromString(value)
        } catch (e: Exception) {
            null
        }
    }
}
