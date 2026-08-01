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
    tableName = "item_relationships",
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
            childColumns = ["sourceItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InfrastructureItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["sourceItemId"]),
        Index(value = ["targetItemId"])
    ]
)
@Serializable
data class ItemRelationshipEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    @Serializable(with = UuidSerializer::class) val sourceItemId: UUID,
    @Serializable(with = UuidSerializer::class) val targetItemId: UUID,
    val relationshipType: String,
    val description: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
