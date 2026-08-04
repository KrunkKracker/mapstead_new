package com.jumastappworks.mapstead.ui.prototype

import java.util.UUID

data class PrototypePropertyItem(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val category: String,
    val locationDescription: String,
    val isEmergency: Boolean = false,
    val status: String = "Good"
)

data class PrototypeTask(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val status: PrototypeTaskStatus,
    val relatedItemId: UUID? = null
)

enum class PrototypeTaskStatus {
    OVERDUE, DUE_SOON, UPCOMING, COMPLETED
}

val FakeItems = listOf(
    PrototypePropertyItem(name = "Main Water Shutoff", category = "Water & Plumbing", locationDescription = "Front of house, near driveway", isEmergency = true),
    PrototypePropertyItem(name = "Pool Pump", category = "Pool Equipment", locationDescription = "Behind equipment wall near shed"),
    PrototypePropertyItem(name = "Well", category = "Water & Plumbing", locationDescription = "North pasture, marked by well house"),
    PrototypePropertyItem(name = "Septic Tank", category = "Water & Plumbing", locationDescription = "South lawn, access near oak tree"),
    PrototypePropertyItem(name = "Electrical Panel", category = "Power & Electrical", locationDescription = "Garage, west wall", isEmergency = true),
    PrototypePropertyItem(name = "Propane Tank", category = "Power & Electrical", locationDescription = "Side of house, near generator", isEmergency = true),
    PrototypePropertyItem(name = "North Fence", category = "Boundaries & Access", locationDescription = "Along County Road 4"),
    PrototypePropertyItem(name = "Equipment Shed", category = "Buildings & Structures", locationDescription = "Adjacent to garden area"),
    PrototypePropertyItem(name = "Pond", category = "Outdoor & Land", locationDescription = "Center of property")
)

val FakeTasks = listOf(
    PrototypeTask(title = "Replace pool-pump filter", status = PrototypeTaskStatus.OVERDUE, relatedItemId = FakeItems.find { it.name == "Pool Pump" }?.id),
    PrototypeTask(title = "Inspect well pressure tank", status = PrototypeTaskStatus.DUE_SOON, relatedItemId = FakeItems.find { it.name == "Well" }?.id),
    PrototypeTask(title = "Service generator", status = PrototypeTaskStatus.UPCOMING),
    PrototypeTask(title = "Septic inspection", status = PrototypeTaskStatus.COMPLETED, relatedItemId = FakeItems.find { it.name == "Septic Tank" }?.id)
)
