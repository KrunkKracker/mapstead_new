package com.jumastappworks.mapstead.data.repository

import java.util.UUID

sealed interface MaintenanceWriteResult {
    data class Success(val id: UUID) : MaintenanceWriteResult
    data class SuccessWithSchedulingWarning(val id: UUID, val warningRes: Int) : MaintenanceWriteResult
    data class SavedDisabledAfterSchedulingFailure(val id: UUID, val messageRes: Int) : MaintenanceWriteResult
    data class Error(val message: String, val messageRes: Int? = null) : MaintenanceWriteResult
    data object NotFound : MaintenanceWriteResult
    data object OwnershipMismatch : MaintenanceWriteResult
    data object InvalidLink : MaintenanceWriteResult
}
