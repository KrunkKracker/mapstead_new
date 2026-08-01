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
    tableName = "attachments",
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
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MapFeatureEntity::class,
            parentColumns = ["id"],
            childColumns = ["mapFeatureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["infrastructureItemId"]),
        Index(value = ["maintenanceRecordId"]),
        Index(value = ["mapFeatureId"])
    ]
)
@Serializable
data class AttachmentEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    @Serializable(with = UuidSerializer::class) val infrastructureItemId: UUID? = null,
    @Serializable(with = UuidSerializer::class) val maintenanceRecordId: UUID? = null,
    @Serializable(with = UuidSerializer::class) val mapFeatureId: UUID? = null,
    val attachmentType: String,
    val localUri: String,
    val appManagedCopyPath: String? = null,
    val displayName: String,
    val mimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val sha256: String? = null,
    val caption: String? = null,
    val isCover: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
