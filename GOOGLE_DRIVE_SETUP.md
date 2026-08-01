# Google Drive Setup for Mapstead

> [!WARNING]
> `Phase 2A — Paused: implementation preserved, customer access disabled pending destructive restore testing`
> This feature is currently **disabled** in customer builds of Mapstead. OAuth configuration is **not** required to use the current public app, and OAuth setup is not required for current use. This documentation is retained as future development documentation for a disabled feature.

This document describes the steps required to enable Google Drive backup and restore in Mapstead.

## 1. Google Cloud Project
1.  Go to the [Google Cloud Console](https://console.cloud.google.com/).
2.  Create a new project named "Mapstead".
3.  Enable the **Google Drive API** for this project.

## 2. OAuth Consent Screen
1.  Configure the **OAuth Consent Screen**.
2.  App Name: **Mapstead**.
3.  Support email and Developer contact information are required.
4.  **Scopes**: Add the narrow `https://www.googleapis.com/auth/drive.file` scope. This allows Mapstead to read/write only the files it creates.
5.  Set the publishing status to **Testing** while developing. Add your test Google accounts to the "Test users" list.

## 3. Android Credentials
1.  Navigate to **Credentials** -> **Create Credentials** -> **OAuth client ID**.
2.  Application type: **Android**.
3.  Package name: `com.jumastappworks.mapstead`.
4.  **SHA-1 fingerprint**:
    - For debug builds: Run `./gradlew signingReport` and copy the SHA-1 from the `debug` variant.
    - For release builds: Use the SHA-1 from your production keystore.

## 4. Security Notes
- Mapstead uses the modern **Google Identity Services** `AuthorizationClient`.
- Authorization is requested incrementally only when the user taps "Connect" or starts a backup/restore.
- Access tokens are never stored persistently; they are obtained fresh for each session.

## Completion Status
- **Status**: Phase 2A — Paused: implementation preserved, customer access disabled pending destructive restore testing
