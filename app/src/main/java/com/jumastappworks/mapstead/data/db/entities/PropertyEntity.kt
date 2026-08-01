package com.jumastappworks.mapstead.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jumastappworks.mapstead.util.InstantSerializer
import com.jumastappworks.mapstead.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Entity(tableName = "properties")
@Serializable
data class PropertyEntity(
    @PrimaryKey @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val name: String,
    val propertyType: String,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val stateOrRegion: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val parcelNumber: String? = null,
    val acreage: Double? = null,
    val description: String? = null,
    val heroPhotoUri: String? = null,
    val isArchived: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val revision: Long = 1L
)
