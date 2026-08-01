package com.jumastappworks.mapstead.data.backup

import com.jumastappworks.mapstead.BuildConfig
import com.jumastappworks.mapstead.data.db.entities.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipInputStream
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

class BackupArchiveValidator @Inject constructor(
    private val json: Json,
    @ApplicationContext private val context: Context,
    private val limits: BackupArchiveLimits
) {
    private val cacheDir: File = context.cacheDir
    private val requiredFiles = setOf(
        "manifest.json",
        "checksums.json",
        "data/properties.json",
        "data/plans.json",
        "data/layers.json",
        "data/map_features.json",
        "data/infrastructure_items.json",
        "data/maintenance_records.json",
        "data/reminders.json",
        "data/attachments.json",
        "data/item_relationships.json"
    )

    fun validate(zipFile: File): Result<BackupValidationReport> {
        val extractDir = File(cacheDir, "validate_${UUID.randomUUID()}").apply { mkdirs() }
        var success = false
        return try {
            if (zipFile.length() > limits.maxArchiveBytes) {
                throw IOException("Archive too large (${zipFile.length()} > ${limits.maxArchiveBytes})")
            }
            
            val entries = unzipAndValidateStructure(zipFile, extractDir)
            validateChecksums(extractDir, entries)
            val report = validateData(extractDir).copy(extractionDir = extractDir)
            success = true
            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (!success) {
                extractDir.deleteRecursively()
            }
        }
    }

    private fun unzipAndValidateStructure(zipFile: File, destDir: File): Set<String> {
        val entries = mutableSetOf<String>()
        val normalizedPaths = mutableSetOf<String>()
        val lowercasePaths = mutableSetOf<String>()
        var manifestCount = 0
        var checksumsCount = 0
        var totalExtractedSize = 0L

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entries.size >= limits.maxEntryCount) throw IOException("Too many entries in ZIP")
                
                val rawName = entry.name
                if (rawName.startsWith("/") || rawName.startsWith("\\") || rawName.matches(Regex("^[a-zA-Z]:.*"))) {
                    throw IOException("Absolute paths are strictly rejected: $rawName")
                }
                
                val normalizedName = normalizePath(rawName)
                
                if (normalizedPaths.contains(normalizedName)) {
                    throw IOException("Collision or duplicate entry: $normalizedName")
                }
                val lowercaseName = normalizedName.lowercase(Locale.US)
                if (lowercasePaths.contains(lowercaseName)) {
                    throw IOException("Case-insensitive path collision detected: $normalizedName")
                }
                lowercasePaths.add(lowercaseName)
                normalizedPaths.add(normalizedName)
                entries.add(normalizedName)

                val file = File(destDir, normalizedName)
                val canonicalDest = destDir.canonicalPath
                if (!file.canonicalPath.startsWith(canonicalDest + File.separator) && file.canonicalPath != canonicalDest) {
                    throw IOException("Invalid ZIP entry path (potential ZIP slip): $rawName")
                }

                if (normalizedName == "manifest.json") manifestCount++
                if (normalizedName == "checksums.json") checksumsCount++

                if (!entry.isDirectory) {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { os ->
                        val buffer = ByteArray(8192)
                        var n: Int
                        var entrySize = 0L
                        while (zis.read(buffer).also { n = it } != -1) {
                            os.write(buffer, 0, n)
                            entrySize += n
                            totalExtractedSize += n
                            if (totalExtractedSize > limits.maxTotalExtractedBytes) throw IOException("Total extracted size limit exceeded")
                            if (entrySize > limits.maxEntryUncompressedBytes) throw IOException("Single entry size limit exceeded")
                        }
                        if (entrySize > 0 && (entrySize.toDouble() / zipFile.length() > limits.maxCompressionRatio)) {
                            throw IOException("Potential Zip Bomb: Compression ratio exceeded")
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (manifestCount != 1) throw IOException("Archive must contain exactly one manifest.json")
        if (checksumsCount != 1) throw IOException("Archive must contain exactly one checksums.json")
        
        val missingFiles = requiredFiles.filter { !entries.contains(it) }
        if (missingFiles.isNotEmpty()) throw IOException("Missing required files: ${missingFiles.joinToString()}")

        entries.forEach { path ->
            validateArchivePath(path)
        }

        return entries
    }

    private fun normalizePath(path: String): String {
        val normalized = path.replace('\\', '/').trim('/')
        if (normalized.contains("..")) {
            throw IOException("ZIP slip vulnerability detected: $path")
        }
        if (normalized.contains('\u0000') || normalized.contains(':')) {
            throw IOException("Illegal characters in path: $path")
        }
        val segments = normalized.split('/')
        if (segments.size > limits.maxPathDepth) throw IOException("Path depth too deep: $path")
        segments.forEach { if (it.length > limits.maxFilenameLength) throw IOException("Filename too long: $it") }
        return normalized
    }

    private fun validateArchivePath(path: String) {
        if (requiredFiles.contains(path)) return
        
        val segments = path.split('/')
        when {
            segments.size == 2 && segments[0] == "attachments" -> {
                try { UUID.fromString(segments[1]) } catch (e: Exception) { throw IOException("Invalid attachment ID format: $path") }
            }
            segments.size == 2 && segments[0] == "plans" -> {
                if (segments[1].isBlank()) throw IOException("Empty plan filename: $path")
            }
            else -> throw IOException("Unexpected entry path: $path")
        }
    }

    private fun validateChecksums(extractDir: File, entries: Set<String>) {
        val checksumsFile = File(extractDir, "checksums.json")
        if (checksumsFile.length() > limits.maxJsonBytes) throw IOException("checksums.json too large")
        
        val checksums = json.decodeFromString<BackupChecksums>(checksumsFile.readText())
        
        val seenPaths = mutableSetOf<String>()
        val seenLowercasePaths = mutableSetOf<String>()
        
        checksums.files.forEach { fileChecksum ->
            val path = fileChecksum.path
            if (seenPaths.contains(path)) {
                throw IOException("Duplicate checksum path declared: $path")
            }
            seenPaths.add(path)
            
            val lowercasePath = path.lowercase(Locale.US)
            if (seenLowercasePaths.contains(lowercasePath)) {
                throw IOException("Case-colliding checksum path declared: $path")
            }
            seenLowercasePaths.add(lowercasePath)
            
            if (fileChecksum.hash.length != 64 || !fileChecksum.hash.all { it.isDigit() || (it in 'a'..'f') }) {
                throw IOException("Invalid SHA-256 hex format for $path")
            }
            if (fileChecksum.payloadCategory.isBlank()) {
                throw IOException("Empty payloadCategory for $path")
            }
            val allowedCategories = setOf("DATA", "ATTACHMENT", "PLAN", "MANIFEST", "CHECKSUMS")
            if (!allowedCategories.contains(fileChecksum.payloadCategory.uppercase(Locale.US))) {
                throw IOException("Invalid payloadCategory: ${fileChecksum.payloadCategory} for $path")
            }
        }
        
        val declaredPaths = checksums.files.map { it.path }.toSet()
        val payloadFiles = entries.filter { it != "checksums.json" }.toSet()

        if (declaredPaths != payloadFiles) {
            val missing = payloadFiles - declaredPaths
            val extra = declaredPaths - payloadFiles
            throw IOException("Checksum coverage mismatch. Missing: $missing, Extra: $extra")
        }

        checksums.files.forEach { fileChecksum ->
            val file = File(extractDir, fileChecksum.path)
            if (file.length() != fileChecksum.size) throw IOException("Size mismatch for ${fileChecksum.path}")
            if (calculateSha256(file) != fileChecksum.hash) throw IOException("Checksum mismatch for ${fileChecksum.path}")
        }
    }

    private fun validateData(extractDir: File): BackupValidationReport {
        val dataDir = File(extractDir, "data")
        val manifestFile = File(extractDir, "manifest.json")
        if (manifestFile.length() > limits.maxJsonBytes) throw IOException("manifest.json too large")
        
        val manifest = json.decodeFromString<BackupManifest>(manifestFile.readText())
        
        val compatibility = BackupCompatibilityPolicy.evaluate(manifest, BuildConfig.VERSION_NAME)
        val warnings = mutableListOf<String>()
        
        when (compatibility) {
            is BackupCompatibilityResult.Incompatible -> {
                throw IOException("Backup is incompatible: ${compatibility.reason}")
            }
            is BackupCompatibilityResult.CompatibleWithWarning -> {
                warnings.add("Warning: ${compatibility.reason}")
            }
            BackupCompatibilityResult.Compatible -> {}
        }

        try {
            UUID.fromString(manifest.backupId)
        } catch (e: Exception) {
            throw IOException("Invalid manifest backupId UUID format: ${manifest.backupId}")
        }
        try {
            java.time.Instant.parse(manifest.createdAt)
        } catch (e: Exception) {
            throw IOException("Invalid manifest createdAt ISO-8601 format: ${manifest.createdAt}")
        }
        
        val attachmentsDir = File(extractDir, "attachments")
        val actualAttachmentBytes = attachmentsDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (actualAttachmentBytes != manifest.includedAttachmentBytes) {
            throw IOException("Attachment bytes mismatch: manifest has ${manifest.includedAttachmentBytes}, actual is $actualAttachmentBytes")
        }

        val properties = readJsonList(File(dataDir, "properties.json"), ListSerializer(PropertyEntity.serializer()))
        val plans = readJsonList(File(dataDir, "plans.json"), ListSerializer(PlanEntity.serializer()))
        val layers = readJsonList(File(dataDir, "layers.json"), ListSerializer(LayerEntity.serializer()))
        val features = readJsonList(File(dataDir, "map_features.json"), ListSerializer(MapFeatureEntity.serializer()))
        val items = readJsonList(File(dataDir, "infrastructure_items.json"), ListSerializer(InfrastructureItemEntity.serializer()))
        val maintenance = readJsonList(File(dataDir, "maintenance_records.json"), ListSerializer(MaintenanceRecordEntity.serializer()))
        val reminders = readJsonList(File(dataDir, "reminders.json"), ListSerializer(ReminderEntity.serializer()))
        val attachments = readJsonList(File(dataDir, "attachments.json"), ListSerializer(AttachmentEntity.serializer()))
        val relationships = readJsonList(File(dataDir, "item_relationships.json"), ListSerializer(ItemRelationshipEntity.serializer()))

        if (attachments.size > limits.maxAttachmentCount) throw IOException("Too many attachments")
        if (plans.size > limits.maxPlanFileCount) throw IOException("Too many plans")

        if (properties.size != manifest.propertyCount) throw IOException("Property count mismatch")
        if (plans.size != manifest.planCount) throw IOException("Plan count mismatch")
        if (layers.size != manifest.layerCount) throw IOException("Layer count mismatch")
        if (features.size != manifest.mapFeatureCount) throw IOException("Feature count mismatch")
        if (items.size != manifest.infrastructureCount) throw IOException("Infrastructure count mismatch")
        if (maintenance.size != manifest.maintenanceCount) throw IOException("Maintenance count mismatch")
        if (reminders.size != manifest.reminderCount) throw IOException("Reminder count mismatch")
        if (attachments.size != manifest.attachmentCount) throw IOException("Attachment count mismatch")
        if (relationships.size != manifest.relationshipCount) throw IOException("Relationship count mismatch")

        validateUniqueIds("Property", properties) { it.id }
        validateUniqueIds("Plan", plans) { it.id }
        validateUniqueIds("Layer", layers) { it.id }
        validateUniqueIds("Feature", features) { it.id }
        validateUniqueIds("Item", items) { it.id }
        validateUniqueIds("Record", maintenance) { it.id }
        validateUniqueIds("Reminder", reminders) { it.id }
        validateUniqueIds("Attachment", attachments) { it.id }
        validateUniqueIds("Relationship", relationships) { it.id }

        val propertyIds = properties.map { it.id }.toSet()
        val planIds = plans.map { it.id }.toSet()
        val layerIds = layers.map { it.id }.toSet()
        val itemIds = items.map { it.id }.toSet()
        val recordIds = maintenance.map { it.id }.toSet()
        val featureIds = features.map { it.id }.toSet()

        plans.forEach { 
            if (!propertyIds.contains(it.propertyId)) throw IOException("Plan ${it.id} references missing property") 
        }
        layers.forEach { 
            if (!propertyIds.contains(it.propertyId)) throw IOException("Layer ${it.id} references missing property")
            if (!planIds.contains(it.planId)) throw IOException("Layer ${it.id} references missing plan")
            val parentPlan = plans.find { p -> p.id == it.planId }
            if (parentPlan?.propertyId != it.propertyId) throw IOException("Layer ${it.id} property mismatch with its plan")
        }
        features.forEach {
            if (!propertyIds.contains(it.propertyId)) throw IOException("Feature ${it.id} references missing property")
            if (!planIds.contains(it.planId)) throw IOException("Feature ${it.id} references missing plan")
            if (!layerIds.contains(it.layerId)) throw IOException("Feature ${it.id} references missing layer")
            
            val parentLayer = layers.find { l -> l.id == it.layerId }
            if (parentLayer?.propertyId != it.propertyId) throw IOException("Feature ${it.id} property mismatch with its layer")
            if (parentLayer!!.planId != it.planId) throw IOException("Feature ${it.id} layer-plan mismatch")
            
            it.infrastructureItemId?.let { itemId -> if (!itemIds.contains(itemId)) throw IOException("Feature ${it.id} references missing infrastructure item") }
            
            val lat = it.capturedLatitude
            val lon = it.capturedLongitude
            if (lat != null && (!lat.isFinite() || lat < -90.0 || lat > 90.0)) throw IOException("Feature ${it.id} has invalid latitude: $lat")
            if (lon != null && (!lon.isFinite() || lon < -180.0 || lon > 180.0)) throw IOException("Feature ${it.id} has invalid longitude: $lon")
            
            validateGeoJson(it)
        }
        items.forEach {
            if (!propertyIds.contains(it.propertyId)) throw IOException("Item ${it.id} references missing property")
            it.parentItemId?.let { parentId -> 
                if (!itemIds.contains(parentId)) throw IOException("Item ${it.id} references missing parent")
                val parentItem = items.find { i -> i.id == parentId }
                if (parentItem?.propertyId != it.propertyId) throw IOException("Item ${it.id} parent belongs to different property")
            }
        }
        maintenance.forEach {
            if (!propertyIds.contains(it.propertyId)) throw IOException("Record ${it.id} references missing property")
            it.infrastructureItemId?.let { itemId -> 
                if (!itemIds.contains(itemId)) throw IOException("Record ${it.id} item references missing item")
                val item = items.find { i -> i.id == itemId }
                if (item?.propertyId != it.propertyId) throw IOException("Record ${it.id} item belongs to different property")
            }
            val cost = it.cost
            if (cost != null && (!cost.isFinite() || cost < 0.0)) throw IOException("Record ${it.id} has invalid cost: $cost")
        }
        reminders.forEach {
            if (!propertyIds.contains(it.propertyId)) throw IOException("Reminder ${it.id} references missing property")
            it.infrastructureItemId?.let { itemId -> 
                if (!itemIds.contains(itemId)) throw IOException("Reminder ${it.id} references missing item")
                val item = items.find { i -> i.id == itemId }
                if (item?.propertyId != it.propertyId) throw IOException("Reminder ${it.id} item belongs to different property")
            }
            it.maintenanceRecordId?.let { recordId -> 
                if (!recordIds.contains(recordId)) throw IOException("Reminder ${it.id} references missing record")
                val record = maintenance.find { r -> r.id == recordId }
                if (record?.propertyId != it.propertyId) throw IOException("Reminder ${it.id} record belongs to different property")
            }
        }
        attachments.forEach {
            if (!propertyIds.contains(it.propertyId)) throw IOException("Attachment ${it.id} references missing property")
            
            var ownerCount = 0
            if (it.infrastructureItemId != null) {
                ownerCount++
                if (!itemIds.contains(it.infrastructureItemId)) throw IOException("Attachment ${it.id} references missing infrastructure item")
                val owner = items.find { i -> i.id == it.infrastructureItemId }
                if (owner?.propertyId != it.propertyId) throw IOException("Attachment ${it.id} infrastructure item belongs to different property")
            }
            if (it.maintenanceRecordId != null) {
                ownerCount++
                if (!recordIds.contains(it.maintenanceRecordId)) throw IOException("Attachment ${it.id} references missing maintenance record")
                val owner = maintenance.find { r -> r.id == it.maintenanceRecordId }
                if (owner?.propertyId != it.propertyId) throw IOException("Attachment ${it.id} maintenance record belongs to different property")
            }
            if (it.mapFeatureId != null) {
                ownerCount++
                if (!featureIds.contains(it.mapFeatureId)) throw IOException("Attachment ${it.id} references missing map feature")
                val owner = features.find { f -> f.id == it.mapFeatureId }
                if (owner?.propertyId != it.propertyId) throw IOException("Attachment ${it.id} map feature belongs to different property")
            }
            if (ownerCount > 1) throw IOException("Attachment ${it.id} has multiple owners")
            
            if (it.isCover) {
                if (it.mapFeatureId == null) throw IOException("Attachment ${it.id} is marked as cover but has no mapFeatureId")
                if (it.mimeType?.startsWith("image/") != true) throw IOException("Attachment ${it.id} is marked as cover but is not an image")
            }
        }
        
        features.forEach { feature ->
            val featureCovers = attachments.filter { it.mapFeatureId == feature.id && it.isCover && it.deletedAt == null }
            if (featureCovers.size > 1) throw IOException("Feature ${feature.id} has multiple active covers")
        }

        relationships.forEach {
            if (!propertyIds.contains(it.propertyId)) throw IOException("Relationship ${it.id} references missing property")
            if (!itemIds.contains(it.sourceItemId)) throw IOException("Relationship ${it.id} references missing source")
            if (!itemIds.contains(it.targetItemId)) throw IOException("Relationship ${it.id} references missing target")
            val sourceItem = items.find { i -> i.id == it.sourceItemId }
            val targetItem = items.find { i -> i.id == it.targetItemId }
            if (sourceItem?.propertyId != it.propertyId) throw IOException("Relationship ${it.id} source item belongs to different property")
            if (targetItem?.propertyId != it.propertyId) throw IOException("Relationship ${it.id} target item belongs to different property")
        }

        checkInfrastructureCyclesIterative(items)
        
        return BackupValidationReport(
            manifest, properties, plans, layers, features, items, maintenance, reminders, attachments, relationships, warnings
        )
    }

    private fun <T> readJsonList(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> {
        if (!file.exists()) return emptyList()
        if (file.length() > limits.maxJsonBytes) throw IOException("${file.name} too large")
        return json.decodeFromString(serializer, file.readText())
    }

    private fun <T> validateUniqueIds(type: String, entities: List<T>, idSelector: (T) -> Any) {
        val ids = entities.map(idSelector)
        if (ids.size != ids.distinct().size) throw IOException("Duplicate $type IDs detected")
    }

    private fun validateGeoJson(feature: MapFeatureEntity) {
        val geoJson = feature.geometryJson
        try {
            val root = json.parseToJsonElement(geoJson).jsonObject
            val type = root["type"]?.jsonPrimitive?.content ?: throw IOException("GeoJSON missing type")
            
            if (feature.geometryType != type) {
                throw IOException("Feature type mismatch: Entity=${feature.geometryType}, GeoJSON=$type")
            }
            
            validateCoordinatesRecursive(root)
        } catch (e: Exception) {
            throw IOException("Invalid GeoJSON for feature ${feature.id}: ${e.message}")
        }
    }

    private fun validateCoordinatesRecursive(element: JsonElement) {
        when (element) {
            is JsonArray -> {
                if (element.size == 2 && element[0] is JsonPrimitive && element[1] is JsonPrimitive) {
                    val lon = element[0].jsonPrimitive.doubleOrNull
                    val lat = element[1].jsonPrimitive.doubleOrNull
                    if (lon == null || !lon.isFinite() || lon < -180.0 || lon > 180.0) {
                        throw IOException("Invalid longitude in GeoJSON: $lon")
                    }
                    if (lat == null || !lat.isFinite() || lat < -90.0 || lat > 90.0) {
                        throw IOException("Invalid latitude in GeoJSON: $lat")
                    }
                } else {
                    element.forEach { validateCoordinatesRecursive(it) }
                }
            }
            is JsonObject -> {
                element["coordinates"]?.let { validateCoordinatesRecursive(it) }
            }
            else -> {}
        }
    }

    private fun checkInfrastructureCyclesIterative(items: List<InfrastructureItemEntity>) {
        val itemMap = items.associateBy { it.id }
        
        items.forEach { startItem ->
            val path = LinkedHashSet<UUID>()
            var current: UUID? = startItem.id
            var depth = 0
            
            while (current != null) {
                if (path.contains(current)) {
                    throw IOException("Circular infrastructure parent relationship detected at $current")
                }
                path.add(current)
                depth++
                if (depth > 64) {
                    throw IOException("Infrastructure hierarchy too deep (max 64)")
                }
                val item = itemMap[current]
                current = item?.parentItemId
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { isStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (isStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
