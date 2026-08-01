package com.jumastappworks.mapstead.data.mapping

import java.util.UUID

data class PendingSystemItemDraft(
    val id: UUID = UUID.randomUUID(),
    val propertyId: UUID,
    val name: String,
    val category: String,
    val subtype: String? = null,
    val isEmergencyItem: Boolean = false,
    val emergencyInstructions: String? = null
)
