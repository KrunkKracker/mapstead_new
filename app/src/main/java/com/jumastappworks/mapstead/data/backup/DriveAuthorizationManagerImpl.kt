package com.jumastappworks.mapstead.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveAuthorizationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveAuthorizationManager {

    private val requestedScopes = listOf(
        Scope(DriveScopes.DRIVE_FILE)
    )

    override suspend fun authorize(activity: Activity): DriveAuthorizationResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .build()

            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            handleResult(result)
        } catch (e: Exception) {
            mapError(e)
        }
    }

    override fun getAuthorizationResult(intent: Intent?): DriveAuthorizationResult {
        return try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
            handleResult(result)
        } catch (e: Exception) {
            mapError(e)
        }
    }

    override suspend fun clearToken(token: String): Result<Unit> {
        return try {
            Identity.getAuthorizationClient(context).clearToken(token).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("UnusedReceiverParameter", "DEPRECATION")
    private fun com.google.android.gms.auth.api.identity.AuthorizationClient.clearToken(token: String): com.google.android.gms.tasks.Task<Void> {
        return com.google.android.gms.tasks.Tasks.call(java.util.concurrent.Executors.newSingleThreadExecutor()) {
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, token)
            null
        }
    }

    private fun handleResult(result: AuthorizationResult): DriveAuthorizationResult {
        return if (result.hasResolution()) {
            result.pendingIntent?.let {
                DriveAuthorizationResult.ResolutionRequired(it)
            } ?: DriveAuthorizationResult.Failure(DriveError.Unknown("Resolution required but pending intent is null"))
        } else {
            val grantedScopes = result.grantedScopes.map { it.toString() }
            if (!grantedScopes.contains(DriveScopes.DRIVE_FILE)) {
                return DriveAuthorizationResult.Failure(DriveError.PermissionDenied)
            }
            
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                DriveAuthorizationResult.Failure(DriveError.InvalidResponse)
            } else {
                DriveAuthorizationResult.Authorized(token, null)
            }
        }
    }

    private fun mapError(e: Exception): DriveAuthorizationResult {
        val driveError = if (e is ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> DriveError.Cancelled
                CommonStatusCodes.NETWORK_ERROR -> DriveError.NetworkUnavailable
                CommonStatusCodes.SIGN_IN_REQUIRED -> DriveError.AuthorizationExpired
                else -> DriveError.Unknown("API Error code: ${e.statusCode}", e)
            }
        } else {
            DriveError.Unknown(e.message ?: "Unknown authorization error", e)
        }
        return DriveAuthorizationResult.Failure(driveError)
    }
}
