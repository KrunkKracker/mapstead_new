package com.jumastappworks.mapstead.data.reports

import com.jumastappworks.mapstead.data.attachments.ActiveAttachmentOwnerResult
import com.jumastappworks.mapstead.data.attachments.AttachmentOwner
import com.jumastappworks.mapstead.data.attachments.AttachmentType
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyReportRepository @Inject constructor(
    private val database: MapsteadDatabase,
    private val propertyRepository: PropertyRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val mapRepository: MapRepository,
    private val attachmentRepository: AttachmentRepository
) {
    suspend fun buildPropertyReportData(options: PropertyReportOptions): PropertyReportResult {
        try {
            val property = propertyRepository.getPropertyById(options.propertyId)
                ?: return PropertyReportResult.PropertyNotFound
            
            if (property.deletedAt != null) return PropertyReportResult.PropertyNotFound

            if (options.enabledSections.isEmpty()) return PropertyReportResult.NoSectionsSelected

            val infraItems = if (options.includes(PropertyReportSection.INFRASTRUCTURE)) {
                infrastructureRepository.getItemsForProperty(property.id).first()
                    .filter { it.deletedAt == null && it.propertyId == property.id }
                    .sortedBy { it.name }
                    .map { item ->
                        ReportInfrastructureItem(
                            name = item.name,
                            category = item.category,
                            status = item.status,
                            manufacturer = item.manufacturer,
                            model = item.model,
                            isEmergency = item.isEmergencyItem
                        )
                    }
            } else emptyList()

            val maintenanceRecords = if (options.includes(PropertyReportSection.MAINTENANCE_HISTORY)) {
                maintenanceRepository.getRecordsForProperty(property.id).first()
                    .filter { it.deletedAt == null && it.propertyId == property.id }
                    .filter { record ->
                        val startOk = options.maintenanceHistoryRangeStart?.let { !record.serviceDate.isBefore(it) } ?: true
                        val endOk = options.maintenanceHistoryRangeEnd?.let { !record.serviceDate.isAfter(it) } ?: true
                        startOk && endOk
                    }
                    .sortedByDescending { it.serviceDate }
                    .map { record ->
                        val item = record.infrastructureItemId?.let { infrastructureRepository.getActiveItemForProperty(property.id, it) }
                        ReportMaintenanceRecord(
                            date = record.serviceDate,
                            title = record.title,
                            category = record.category,
                            infrastructureName = item?.name,
                            cost = record.cost,
                            currencyCode = record.currencyCode,
                            notes = record.description
                        )
                    }
            } else emptyList()

            val upcomingTasks = if (options.includes(PropertyReportSection.UPCOMING_MAINTENANCE)) {
                maintenanceRepository.getRemindersForProperty(property.id).first()
                    .filter { it.deletedAt == null && it.completedAt == null && it.propertyId == property.id }
                    .sortedBy { it.dueDate }
                    .map { reminder ->
                        val item = reminder.infrastructureItemId?.let { infrastructureRepository.getActiveItemForProperty(property.id, it) }
                        ReportUpcomingTask(
                            dueDate = reminder.dueDate,
                            itemName = item?.name,
                            taskTitle = reminder.title,
                            isEnabled = reminder.enabled
                        )
                    }
            } else emptyList()

            var planCount = 0
            var layerCount = 0
            var pointCount = 0
            var lineCount = 0
            var areaCount = 0

            if (options.includes(PropertyReportSection.MAP_SUMMARY)) {
                val plans = mapRepository.getPlansForProperty(property.id).first()
                    .filter { it.deletedAt == null && it.propertyId == property.id }
                
                planCount = plans.size
                
                plans.forEach { plan ->
                    val planLayers = mapRepository.getLayersForPlan(plan.id).first()
                        .filter { it.deletedAt == null && it.propertyId == property.id && it.planId == plan.id }
                    layerCount += planLayers.size
                    planLayers.forEach { layer ->
                        val layerFeatures = mapRepository.getFeaturesForLayer(layer.id).first()
                            .filter { it.deletedAt == null && it.propertyId == property.id && it.planId == plan.id && it.layerId == layer.id }
                        
                        layerFeatures.forEach { feature ->
                            when (feature.geometryType.uppercase()) {
                                "POINT" -> pointCount++
                                "LINESTRING" -> lineCount++
                                "POLYGON" -> areaCount++
                            }
                        }
                    }
                }
            }

            val attachmentSummary = if (options.includes(PropertyReportSection.ATTACHMENT_SUMMARY)) {
                val allPropertyAttachments = database.attachmentDao().getAttachmentsForProperty(property.id).first()
                    .filter { it.deletedAt == null && it.propertyId == property.id }
                
                var photoCount = 0
                var documentCount = 0
                var otherCount = 0
                var propertyCount = 0
                var infrastructureCount = 0
                var maintenanceCount = 0
                var featureCount = 0
                var totalValid = 0

                allPropertyAttachments.forEach { attachment ->
                    val resolution = attachmentRepository.resolveActiveAttachmentOwner(property.id, attachment.id)
                    if (resolution is ActiveAttachmentOwnerResult.Valid) {
                        totalValid++
                        val type = AttachmentType.fromString(attachment.attachmentType)
                        when (type) {
                            AttachmentType.Photo -> photoCount++
                            AttachmentType.Document -> documentCount++
                            else -> otherCount++
                        }

                        when (resolution.owner) {
                            is AttachmentOwner.Property -> propertyCount++
                            is AttachmentOwner.InfrastructureItem -> infrastructureCount++
                            is AttachmentOwner.MaintenanceRecord -> maintenanceCount++
                            is AttachmentOwner.MapFeature -> featureCount++
                        }
                    }
                }
                
                ReportAttachmentSummary(
                    totalCount = totalValid,
                    photoCount = photoCount,
                    documentCount = documentCount,
                    otherCount = otherCount,
                    propertyLevelCount = propertyCount,
                    infrastructureCount = infrastructureCount,
                    maintenanceCount = maintenanceCount,
                    mapFeatureCount = featureCount
                )
            } else ReportAttachmentSummary(0, 0, 0, 0, 0, 0, 0, 0)

            val addressStr = listOfNotNull(property.addressLine1, property.addressLine2, property.city, property.stateOrRegion, property.postalCode)
                .filter { it.isNotBlank() }
                .joinToString(", ")

            return PropertyReportResult.Success(
                PropertyReportData(
                    propertyName = property.name,
                    propertyType = property.propertyType,
                    address = addressStr,
                    acreage = property.acreage,
                    parcelNumber = property.parcelNumber,
                    description = property.description,
                    planCount = planCount,
                    layerCount = layerCount,
                    pointCount = pointCount,
                    lineCount = lineCount,
                    areaCount = areaCount,
                    infrastructureItems = infraItems,
                    maintenanceRecords = maintenanceRecords,
                    upcomingMaintenance = upcomingTasks,
                    attachmentSummary = attachmentSummary,
                    generatedAt = Instant.now()
                )
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return PropertyReportResult.Error(PropertyReportError.DATA_LOAD_FAILED, e.message)
        }
    }
}
