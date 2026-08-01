package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfrastructureRepositoryImpl @Inject constructor(
    private val database: MapsteadDatabase
) : InfrastructureRepository {
    private val dao = database.infrastructureDao()

    override fun getItemsForProperty(propertyId: UUID): Flow<List<InfrastructureItemEntity>> =
        dao.getItemsForProperty(propertyId)

    override fun getEmergencyItems(propertyId: UUID): Flow<List<InfrastructureItemEntity>> =
        dao.getEmergencyItems(propertyId)

    override suspend fun getItemById(id: UUID): InfrastructureItemEntity? =
        dao.getItemById(id)

    override suspend fun getActiveItemForProperty(propertyId: UUID, itemId: UUID): InfrastructureItemEntity? =
        dao.getActiveItemForProperty(propertyId, itemId)

    override suspend fun insertItem(item: InfrastructureItemEntity) {
        dao.insertItem(item.copy(
            updatedAt = Instant.now()
        ))
    }

    override suspend fun updateItem(item: InfrastructureItemEntity) {
        dao.updateActivePropertyItem(
            id = item.id,
            propertyId = item.propertyId,
            name = item.name,
            category = item.category,
            subtype = item.subtype,
            status = item.status,
            manufacturer = item.manufacturer,
            model = item.model,
            serialNumber = item.serialNumber,
            serviceProvider = item.serviceProvider,
            phoneNumber = item.phoneNumber,
            website = item.website,
            instructions = item.instructions,
            emergencyInstructions = item.emergencyInstructions,
            notes = item.notes,
            isEmergencyItem = item.isEmergencyItem
        )
    }

    override suspend fun updateItemForProperty(propertyId: UUID, item: InfrastructureItemEntity): InfrastructureWriteResult {
        return try {
            val affected = dao.updateActivePropertyItem(
                id = item.id,
                propertyId = propertyId,
                name = item.name,
                category = item.category,
                subtype = item.subtype,
                status = item.status,
                manufacturer = item.manufacturer,
                model = item.model,
                serialNumber = item.serialNumber,
                serviceProvider = item.serviceProvider,
                phoneNumber = item.phoneNumber,
                website = item.website,
                instructions = item.instructions,
                emergencyInstructions = item.emergencyInstructions,
                notes = item.notes,
                isEmergencyItem = item.isEmergencyItem
            )
            if (affected == 1) {
                InfrastructureWriteResult.Success(item.id)
            } else {
                InfrastructureWriteResult.NotFound
            }
        } catch (e: Exception) {
            InfrastructureWriteResult.Error(e.message)
        }
    }

    override suspend fun softDeleteItem(id: UUID) {
        dao.softDeleteItem(id, Instant.now(), Instant.now())
    }

    override suspend fun softDeleteItemForProperty(propertyId: UUID, itemId: UUID): InfrastructureWriteResult {
        return try {
            val affected = dao.softDeletePropertyItem(propertyId, itemId, Instant.now(), Instant.now())
            if (affected == 1) {
                InfrastructureWriteResult.Success(itemId)
            } else {
                InfrastructureWriteResult.NotFound
            }
        } catch (e: Exception) {
            InfrastructureWriteResult.Error(e.message)
        }
    }
}
