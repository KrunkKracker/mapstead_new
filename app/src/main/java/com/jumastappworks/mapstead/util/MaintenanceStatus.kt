package com.jumastappworks.mapstead.util

object MaintenanceStatus {
    const val SCHEDULED = "Scheduled"
    const val COMPLETED = "Completed"
    const val CANCELLED = "Cancelled"
    const val IN_PROGRESS = "In Progress"

    fun isCompleted(status: String?): Boolean {
        return status?.trim()?.equals(COMPLETED, ignoreCase = true) == true
    }
}
