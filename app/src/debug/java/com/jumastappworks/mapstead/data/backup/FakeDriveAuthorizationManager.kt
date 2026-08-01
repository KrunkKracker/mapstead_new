package com.jumastappworks.mapstead.data.backup

import android.app.Activity
import android.content.Intent

class FakeDriveAuthorizationManager : DriveAuthorizationManager {
    var authorized = false
    var shouldFail = false
    var authorizeCallCount = 0

    override suspend fun authorize(activity: Activity): DriveAuthorizationResult {
        authorizeCallCount++
        return if (shouldFail) {
            DriveAuthorizationResult.Failure(DriveError.Unknown("Fake error"))
        } else {
            authorized = true
            DriveAuthorizationResult.Authorized("fake-token", "test@example.com")
        }
    }

    override fun getAuthorizationResult(intent: Intent?): DriveAuthorizationResult {
        return if (shouldFail) {
            DriveAuthorizationResult.Failure(DriveError.Unknown("Fake error"))
        } else {
            authorized = true
            DriveAuthorizationResult.Authorized("fake-token", "test@example.com")
        }
    }

    override suspend fun clearToken(token: String): Result<Unit> {
        authorized = false
        return Result.success(Unit)
    }
}
