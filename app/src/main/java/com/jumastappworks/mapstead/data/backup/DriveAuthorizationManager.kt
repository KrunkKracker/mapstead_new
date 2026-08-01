package com.jumastappworks.mapstead.data.backup

import android.app.Activity
import android.content.Intent

interface DriveAuthorizationManager {
    suspend fun authorize(activity: Activity): DriveAuthorizationResult
    fun getAuthorizationResult(intent: Intent?): DriveAuthorizationResult
    suspend fun clearToken(token: String): Result<Unit>
}
