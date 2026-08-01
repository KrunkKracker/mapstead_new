# Mapstead Backup Format (.mapsteadbackup) - Version 1

> [!WARNING]
> `Phase 2A — Paused: implementation preserved, customer access disabled pending destructive restore testing`
> This feature is currently **disabled** in customer builds of Mapstead. This documentation is retained as future development documentation for a disabled feature.

The `.mapsteadbackup` file is a standard ZIP archive containing serialized application data and binary attachments.

## Archive Structure
```text
/
├── manifest.json        # Backup metadata and record counts
├── checksums.json       # SHA-256 hashes and sizes for ALL included files
├── data/                # Serialized Room database content
│   ├── properties.json
│   ├── plans.json
│   ├── layers.json
│   ├── map_features.json
│   ├── infrastructure_items.json
│   ├── maintenance_records.json
│   ├── reminders.json
│   ├── attachments.json
│   └── item_relationships.json
├── attachments/         # User-attached photos (named by UUID)
```

## Manifest Schema (v1)
- `formatVersion`: 1
- `backupId`: Unique UUID for the backup instance.
- `createdAt`: ISO-8601 creation timestamp.
- `appVersionName`: Version of the app that created the backup.
- `appVersionCode`: Build number of the app.
- `databaseSchemaVersion`: Room schema version.
- `deviceManufacturer`: Manufacturer of the originating device.
- `deviceModel`: Model of the originating device.
- `androidVersion`: Android version of the originating device.
- `stats`: Counts for all entity types and total attachment size.

## Validation and Security
- **SHA-256 Checksums**: Every file in the archive (including `manifest.json` but excluding `checksums.json` itself) is hashed during creation and verified during restore.
- **Zip Slip Protection**: Restoration logic explicitly rejects ZIP entries with absolute paths or parent-directory traversal (`..`).
- **Relationship Validation**: Before restore, all foreign-key relationships are validated in memory to ensure data integrity. Circular infrastructure parent relationships are rejected.
- **Atomic Restore**: Data is restored within a single Room database transaction. If any part of the insertion fails, the entire operation is rolled back, preserving existing local data.
- **Attachment Staging**: Replacement attachments are extracted to a staging directory and verified before the active directory is swapped. Rollback to original attachments is performed if the database transaction fails.
- **Safety Backup**: A local durable safety backup of the current database is generated in persistent storage immediately before any restore operation.

## Status
- **Status**: Phase 2A — Paused: implementation preserved, customer access disabled pending destructive restore testing
