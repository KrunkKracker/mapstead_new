# Privacy Policy

**JuMaSt Appworks LLC**

## Local-First Architecture
Mapstead is designed as a local-first application. By default, all property data, infrastructure maps, and GPS coordinates are stored only on your device's internal storage.

## Location Data
Mapstead requests access to your device's location (Fine and Coarse) for the following purposes:
1.  **Map Centering**: To center the map on your current position.
2.  **Point Drafting**: To create a map marker at your current GPS coordinates.

**Important Notice:**
- Mapstead **only** accesses your location when the app is in the foreground and you explicitly click a location button.
- Mapstead **never** tracks your location in the background.
- Mapstead **never** uploads your precise location to a server (unless you manually perform a Google Drive Backup).

## Cloud Backups (Optional)
`Phase 2A — Paused: implementation preserved, customer access disabled pending destructive restore testing`
The Google Drive Backup & Restore feature is currently **paused and completely disabled** in customer builds. In this state, current customer builds do not request Google Drive authorization or upload backup data. All property mapping data remains strictly on your local device.

For future development or when enabled:
- Mapstead requests the narrow `drive.file` scope. This allows the app to read and write only the specific backup archives it creates.
- Mapstead cannot access your other private files, documents, or photos in Google Drive.
- Your backup data is stored in your personal Google account. JuMaSt Appworks LLC does not operate any servers that store or process your property data.
- You can revoke Mapstead's access at any time through your Google Account security settings.

## Attachments and Files
Mapstead allows you to attach photos and documents to properties, infrastructure items, and maintenance records.
- **Local Copy**: When you select a photo or document, Mapstead copies it into its private, internal storage.
- **Security**: These files are not accessible by other applications on your device, except when you explicitly choose to "Open" a file (e.g., viewing a PDF in an external reader). In this case, Mapstead uses a secure `FileProvider` to grant temporary read access only to that specific file.
- **Privacy**: No photo or document is ever uploaded to a server or shared without your explicit action.
- **Cleanup**: Deleting an attachment within Mapstead removes both the database record and the managed local file.

## Completion Status
- **Phase 5C1 Status**: COMPLETE (Secure local attachment workflows).
- **Phase 2A Status**: Paused (Implementation preserved, customer access disabled).
- **Timestamp**: 2026-07-23T08:50:00.000000000-04:00

## Data Sharing
Mapstead does not sell or share your data with third parties. You have full control over your local database.
