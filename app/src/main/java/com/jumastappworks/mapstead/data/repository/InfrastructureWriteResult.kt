package com.jumastappworks.mapstead.data.repository

import java.util.UUID

sealed interface InfrastructureWriteResult {
    data class Success(val itemId: UUID) : InfrastructureWriteResult
    data object NotFound : InfrastructureWriteResult
    data object OwnershipMismatch : InfrastructureWriteResult
    data object ValidationFailure : InfrastructureWriteResult
    data class Error(val message: String?) : InfrastructureWriteResult
}
