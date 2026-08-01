package com.jumastappworks.mapstead.data.handoff

import android.content.Context
import com.jumastappworks.mapstead.BuildConfig
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.reports.*
import com.jumastappworks.mapstead.data.repository.AttachmentExportFileResult
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyHandoffPackageGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MapsteadDatabase,
    private val propertyRepository: PropertyRepository,
    private val reportRepository: PropertyReportRepository,
    private val documentBuilder: PropertyReportDocumentBuilder,
    private val pdfGenerator: PropertyReportPdfGenerator,
    private val attachmentRepository: AttachmentRepository
) {
    private val json = Json { prettyPrint = true }

    suspend fun generate(
        options: PropertyHandoffOptions,
        onProgress: (PropertyHandoffProgress) -> Unit
    ): PropertyHandoffResult = withContext(Dispatchers.IO) {
        val operationId = UUID.randomUUID().toString()
        val stagingDir = File(context.cacheDir, "handoff_staging/$operationId")
        val handoffDir = File(context.cacheDir, "handoff")
        val reportsDir = File(context.cacheDir, "reports")
        
        var partialZip: File? = null
        var tempPdf: File? = null
        var packageFinalized = false
        
        try {
            if (options.enabledComponents.isEmpty()) return@withContext PropertyHandoffResult.NothingSelected

            onProgress(PropertyHandoffProgress(PropertyHandoffStage.LOADING_PROPERTY))
            val property = propertyRepository.getPropertyById(options.propertyId)
                ?: return@withContext PropertyHandoffResult.PropertyNotFound
            
            if (property.deletedAt != null) return@withContext PropertyHandoffResult.PropertyNotFound

            stagingDir.mkdirs()
            handoffDir.mkdirs()
            reportsDir.mkdirs()

            val sanitizedName = ZipEntryUtils.sanitizeToken(property.name).take(30).ifBlank { "Property" }
            val zipFileName = "Mapstead_${sanitizedName}_${LocalDate.now()}_${operationId.take(4)}.zip"
            val finalFile = File(handoffDir, zipFileName)
            partialZip = File(handoffDir, "$zipFileName.partial")

            if (!isFileInsideDirectory(handoffDir, partialZip!!)) return@withContext PropertyHandoffResult.StorageUnavailable

            val warnings = mutableListOf<PropertyHandoffWarning>()
            val manifestAttachments = mutableListOf<ManifestAttachment>()
            val existingEntryNames = mutableSetOf<String>()
            var pdfActuallyIncluded = false
            var includedAttachmentCount = 0
            var skippedAttachmentCount = 0

            FileOutputStream(partialZip).use { fos ->
                ZipOutputStream(fos.buffered()).use { zos ->
                    
                    // 1. PDF Report
                    if (options.includes(PropertyHandoffComponent.PDF_REPORT)) {
                        onProgress(PropertyHandoffProgress(PropertyHandoffStage.GENERATING_REPORT))
                        val reportOptions = PropertyReportOptions(options.propertyId)
                        val reportDataResult = reportRepository.buildPropertyReportData(reportOptions)
                        
                        if (reportDataResult is PropertyReportResult.Success) {
                            val doc = documentBuilder.build(reportDataResult.data, reportOptions)
                            tempPdf = File(reportsDir, "handoff_$operationId.pdf")
                            
                            val genResult = pdfGenerator.generate(doc, tempPdf!!)
                            if (genResult == PdfGenerationResult.SUCCESS && tempPdf!!.exists()) {
                                addFileToZipCancellable(zos, tempPdf!!, "property-report.pdf")
                                pdfActuallyIncluded = true
                            } else {
                                return@withContext PropertyHandoffResult.PdfReportFailed
                            }
                        } else {
                            return@withContext PropertyHandoffResult.PdfReportFailed
                        }
                    }

                    // 2. Attachments
                    val anyAttachmentEnabled = options.enabledComponents.any { it != PropertyHandoffComponent.PDF_REPORT }
                    
                    if (anyAttachmentEnabled) {
                        onProgress(PropertyHandoffProgress(PropertyHandoffStage.VALIDATING_ATTACHMENTS))
                        val allAttachments = database.attachmentDao().getAttachmentsForProperty(property.id).first()
                            .filter { it.deletedAt == null && it.propertyId == property.id }
                        
                        val totalToProcess = allAttachments.size
                        
                        allAttachments.forEachIndexed { index, attachment ->
                            currentCoroutineContext().ensureActive()
                            onProgress(PropertyHandoffProgress(PropertyHandoffStage.COPYING_ATTACHMENTS, index + 1, totalToProcess))
                            
                            // A. Pre-classification
                            val storedOwnerResult = attachmentRepository.parseStoredAttachmentOwner(attachment)
                            if (storedOwnerResult !is StoredAttachmentOwnerResult.Valid) {
                                skippedAttachmentCount++
                                warnings.add(PropertyHandoffWarning("Invalid Owner", attachment.displayName))
                                return@forEachIndexed
                            }
                            
                            val owner = storedOwnerResult.owner
                            val component = when (owner) {
                                is AttachmentOwner.Property -> PropertyHandoffComponent.PROPERTY_ATTACHMENTS
                                is AttachmentOwner.InfrastructureItem -> PropertyHandoffComponent.INFRASTRUCTURE_ATTACHMENTS
                                is AttachmentOwner.MaintenanceRecord -> PropertyHandoffComponent.MAINTENANCE_ATTACHMENTS
                                is AttachmentOwner.MapFeature -> PropertyHandoffComponent.MAP_FEATURE_ATTACHMENTS
                            }
                            
                            if (!options.includes(component)) return@forEachIndexed

                            // B. Authoritative export validation
                            val exportResult = attachmentRepository.resolveVerifiedAttachmentFileForExport(property.id, attachment.id)
                            
                            when (exportResult) {
                                is AttachmentExportFileResult.Available -> {
                                    val authoritativeAttachment = exportResult.attachment
                                    val authoritativeOwner = exportResult.owner
                                    val sourceFile = exportResult.file
                                    
                                    // Re-check category from authoritative owner
                                    val authComponent = when (authoritativeOwner) {
                                        is AttachmentOwner.Property -> PropertyHandoffComponent.PROPERTY_ATTACHMENTS
                                        is AttachmentOwner.InfrastructureItem -> PropertyHandoffComponent.INFRASTRUCTURE_ATTACHMENTS
                                        is AttachmentOwner.MaintenanceRecord -> PropertyHandoffComponent.MAINTENANCE_ATTACHMENTS
                                        is AttachmentOwner.MapFeature -> PropertyHandoffComponent.MAP_FEATURE_ATTACHMENTS
                                    }
                                    
                                    if (!options.includes(authComponent)) return@forEachIndexed
                                    
                                    val ownerDirName = when (authoritativeOwner) {
                                        is AttachmentOwner.Property -> "Property"
                                        is AttachmentOwner.InfrastructureItem -> "Systems & Equipment"
                                        is AttachmentOwner.MaintenanceRecord -> "Maintenance"
                                        is AttachmentOwner.MapFeature -> "Map Feature"
                                    }
                                    
                                    val entryName = ZipEntryUtils.createSafeEntryName(
                                        category = ownerDirName.lowercase().replace(" ", "-"),
                                        displayName = authoritativeAttachment.displayName,
                                        extension = sourceFile.extension,
                                        existingNames = existingEntryNames
                                    )

                                    if (ZipEntryUtils.isValidZipEntryName(entryName)) {
                                        val stagedFile = File(stagingDir, "staged_${UUID.randomUUID()}")
                                        try {
                                            copyFileCancellable(sourceFile, stagedFile)
                                            
                                            // Verify staged copy
                                            if (stagedFile.exists() && 
                                                stagedFile.length() == authoritativeAttachment.fileSizeBytes) {
                                                
                                                addFileToZipCancellable(zos, stagedFile, entryName)
                                                existingEntryNames.add(entryName)
                                                includedAttachmentCount++
                                                manifestAttachments.add(ManifestAttachment(
                                                    relativePath = entryName,
                                                    displayName = authoritativeAttachment.displayName,
                                                    type = authoritativeAttachment.attachmentType,
                                                    ownerCategory = ownerDirName
                                                ))
                                            } else {
                                                skippedAttachmentCount++
                                                warnings.add(PropertyHandoffWarning("Damaged file", authoritativeAttachment.displayName))
                                            }
                                        } catch (c: CancellationException) {
                                            throw c
                                        } catch (e: Exception) {
                                            skippedAttachmentCount++
                                            warnings.add(PropertyHandoffWarning("File could not be read", authoritativeAttachment.displayName))
                                        } finally {
                                            if (stagedFile.exists()) stagedFile.delete()
                                        }
                                    } else {
                                        skippedAttachmentCount++
                                        warnings.add(PropertyHandoffWarning("Invalid path", authoritativeAttachment.displayName))
                                    }
                                }
                                AttachmentExportFileResult.AttachmentNotFound -> {
                                    skippedAttachmentCount++
                                    warnings.add(PropertyHandoffWarning("Attachment no longer available", attachment.displayName))
                                }
                                AttachmentExportFileResult.InvalidOwner -> {
                                    skippedAttachmentCount++
                                    warnings.add(PropertyHandoffWarning("Invalid owner", attachment.displayName))
                                }
                                AttachmentExportFileResult.Missing -> {
                                    skippedAttachmentCount++
                                    warnings.add(PropertyHandoffWarning("Missing file", attachment.displayName))
                                }
                                AttachmentExportFileResult.Damaged -> {
                                    skippedAttachmentCount++
                                    warnings.add(PropertyHandoffWarning("Damaged file", attachment.displayName))
                                }
                                AttachmentExportFileResult.InvalidPath -> {
                                    skippedAttachmentCount++
                                    warnings.add(PropertyHandoffWarning("Invalid storage location", attachment.displayName))
                                }
                                AttachmentExportFileResult.Unreadable -> {
                                    skippedAttachmentCount++
                                    warnings.add(PropertyHandoffWarning("File could not be read", attachment.displayName))
                                }
                            }
                        }
                    }

                    // 3. Manifest
                    onProgress(PropertyHandoffProgress(PropertyHandoffStage.WRITING_MANIFEST))
                    val manifest = HandoffManifest(
                        propertyName = property.name,
                        generatedAt = Instant.now().toString(),
                        appVersion = BuildConfig.VERSION_NAME,
                        contents = ManifestContents(
                            pdfIncluded = pdfActuallyIncluded,
                            includedAttachments = includedAttachmentCount,
                            skippedAttachments = skippedAttachmentCount
                        ),
                        attachments = manifestAttachments,
                        warnings = warnings.map { PropertyHandoffWarningSerial(it.category, it.displayName) }
                    )
                    val manifestJson = json.encodeToString(manifest)
                    val manifestFile = File(stagingDir, "manifest.json").apply { writeText(manifestJson) }
                    addFileToZipCancellable(zos, manifestFile, "manifest.json")
                }
            }

            onProgress(PropertyHandoffProgress(PropertyHandoffStage.CREATING_ARCHIVE))
            if (partialZip.exists() && partialZip.length() > 0 && partialZip.renameTo(finalFile)) {
                if (finalFile.exists() && finalFile.length() > 0 && !partialZip.exists()) {
                    packageFinalized = true
                    onProgress(PropertyHandoffProgress(PropertyHandoffStage.COMPLETE))
                    PropertyHandoffResult.Success(
                        packageFile = finalFile,
                        includedAttachmentCount = includedAttachmentCount,
                        skippedAttachmentCount = skippedAttachmentCount,
                        warnings = warnings,
                        pdfActuallyIncluded = pdfActuallyIncluded
                    )
                } else {
                    PropertyHandoffResult.Error
                }
            } else {
                PropertyHandoffResult.Error
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            PropertyHandoffResult.Error
        } finally {
            if (!packageFinalized) {
                partialZip?.let { if (it.exists()) it.delete() }
            }
            stagingDir.deleteRecursively()
            tempPdf?.let { if (it.exists()) it.delete() }
        }
    }

    private suspend fun addFileToZipCancellable(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                currentCoroutineContext().ensureActive()
                zos.write(buffer, 0, bytesRead)
            }
            zos.closeEntry()
        }
    }

    private suspend fun copyFileCancellable(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }
}

@Serializable
data class HandoffManifest(
    val format: String = "Mapstead Property Handoff",
    val formatVersion: Int = 1,
    val generatedAt: String,
    val appVersion: String,
    val propertyName: String,
    val contents: ManifestContents,
    val attachments: List<ManifestAttachment>,
    val warnings: List<PropertyHandoffWarningSerial>
)

@Serializable
data class ManifestContents(
    val pdfIncluded: Boolean,
    val includedAttachments: Int,
    val skippedAttachments: Int
)

@Serializable
data class ManifestAttachment(
    val relativePath: String,
    val displayName: String,
    val type: String,
    val ownerCategory: String
)

@Serializable
data class PropertyHandoffWarningSerial(
    val category: String,
    val displayName: String
)
