package com.jumastappworks.mapstead.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jumastappworks.mapstead.util.InstantSerializer
import com.jumastappworks.mapstead.util.LocalDateSerializer
import com.jumastappworks.mapstead.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "infrastructure_items",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InfrastructureItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["parentItemId"])
    ]
)
@Serializable
data class InfrastructureItemEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    @Serializable(with = UuidSerializer::class) val parentItemId: UUID? = null,
    val name: String,
    val category: String,
    val subtype: String? = null,
    val status: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    @Serializable(with = LocalDateSerializer::class) val installationDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val warrantyExpirationDate: LocalDate? = null,
    val serviceProvider: String? = null,
    val phoneNumber: String? = null,
    val website: String? = null,
    val specificationsJson: String? = null,
    val instructions: String? = null,
    val emergencyInstructions: String? = null,
    val notes: String? = null,
    val isEmergencyItem: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
