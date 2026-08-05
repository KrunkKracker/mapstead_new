package com.jumastappworks.mapstead.ui.prototype

import java.util.UUID

data class PrototypeProperty(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val type: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSample: Boolean = false
)

data class PrototypePropertyItem(
    val id: UUID = UUID.randomUUID(),
    val propertyId: UUID,
    val name: String,
    val category: String,
    val locationDescription: String,
    val isEmergency: Boolean = false,
    val status: String = "Good",
    val note: String? = null,
    val hasPhoto: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val needsLocation: Boolean = false,
    val lineVertices: List<Pair<Double, Double>>? = null,
    val areaVertices: List<Pair<Double, Double>>? = null
)

data class PrototypeTask(
    val id: UUID = UUID.randomUUID(),
    val propertyId: UUID,
    val title: String,
    val status: PrototypeTaskStatus,
    val relatedItemId: UUID? = null
)

enum class PrototypeTaskStatus {
    OVERDUE, DUE_SOON, UPCOMING, COMPLETED
}
