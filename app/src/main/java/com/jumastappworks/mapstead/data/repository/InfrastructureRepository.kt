package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface InfrastructureRepository {
    fun getItemsForProperty(propertyId: UUID): Flow<List<InfrastructureItemEntity>>
    fun getEmergencyItems(propertyId: UUID): Flow<List<InfrastructureItemEntity>>
    suspend fun getItemById(id: UUID): InfrastructureItemEntity?
    suspend fun getActiveItemForProperty(propertyId: UUID, itemId: UUID): InfrastructureItemEntity?
    suspend fun insertItem(item: InfrastructureItemEntity)
    suspend fun updateItem(item: InfrastructureItemEntity)
    suspend fun updateItemForProperty(propertyId: UUID, item: InfrastructureItemEntity): InfrastructureWriteResult
    suspend fun softDeleteItem(id: UUID)
    suspend fun softDeleteItemForProperty(propertyId: UUID, itemId: UUID): InfrastructureWriteResult
}
