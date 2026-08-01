package com.jumastappworks.mapstead.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jumastappworks.mapstead.util.InstantSerializer
import com.jumastappworks.mapstead.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "layers",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["planId"])
    ]
)
@Serializable
data class LayerEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    @Serializable(with = UuidSerializer::class) val planId: UUID,
    val name: String,
    val category: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val displayOrder: Int = 0,
    val opacity: Float = 1.0f,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
