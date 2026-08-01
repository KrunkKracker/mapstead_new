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
    tableName = "plans",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["propertyId"])]
)
@Serializable
data class PlanEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    val name: String,
    val planType: String,
    val backgroundType: String,
    val backgroundUri: String? = null,
    val backgroundPageNumber: Int? = null,
    val mapStyleUrl: String? = null,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val zoom: Double? = null,
    val bearing: Double? = null,
    val scaleKnown: Boolean = false,
    val scaleDistance: Double? = null,
    val scaleUnit: String? = null,
    val notes: String? = null,
    val displayOrder: Int = 0,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
