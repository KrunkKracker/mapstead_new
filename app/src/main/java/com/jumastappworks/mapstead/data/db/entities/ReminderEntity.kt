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
    tableName = "reminders",
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
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaintenanceRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["maintenanceRecordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["infrastructureItemId"]),
        Index(value = ["maintenanceRecordId"])
    ]
)
@Serializable
data class ReminderEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    @Serializable(with = UuidSerializer::class) val infrastructureItemId: UUID? = null,
    @Serializable(with = UuidSerializer::class) val maintenanceRecordId: UUID? = null,
    val title: String,
    val description: String? = null,
    @Serializable(with = LocalDateSerializer::class) val dueDate: LocalDate,
    val repeatMonths: Int? = null,
    val enabled: Boolean = true,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
