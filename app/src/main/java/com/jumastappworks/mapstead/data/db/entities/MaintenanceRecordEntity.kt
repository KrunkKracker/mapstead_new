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
    tableName = "maintenance_records",
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
            childColumns = ["infrastructureItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["infrastructureItemId"])
    ]
)
@Serializable
data class MaintenanceRecordEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    @Serializable(with = UuidSerializer::class) val infrastructureItemId: UUID? = null,
    val title: String,
    val category: String,
    val description: String? = null,
    @Serializable(with = LocalDateSerializer::class) val serviceDate: LocalDate,
    val provider: String? = null,
    val cost: Double? = null,
    val currencyCode: String? = null,
    @Serializable(with = LocalDateSerializer::class) val nextDueDate: LocalDate? = null,
    val status: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
