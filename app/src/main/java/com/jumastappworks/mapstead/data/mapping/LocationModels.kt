package com.jumastappworks.mapstead.data.mapping

import java.util.UUID

enum class LocationRequestPurpose { LocateOnly, CreatePoint }
enum class LocationAccuracyQuality { Good, Moderate, Poor }
enum class LocationIssueType { 
    PermissionDenied, 
    PermissionPermanentlyDenied, 
    ProviderDisabled, 
    Timeout, 
    LocationUnavailable, 
    CachedLocation, 
    PoorAccuracy, 
    CachedAndPoorAccuracy, 
    GenericError 
}

data class LocationIssue(
    val type: LocationIssueType,
    val messageRes: Int,
    val canRetry: Boolean = false,
    val canOpenAppSettings: Boolean = false,
    val canOpenLocationSettings: Boolean = false,
    val canContinueManually: Boolean = false,
    val canUseAnyway: Boolean = false,
    val purpose: LocationRequestPurpose? = null,
    val cachedLocation: LocationResult.Success? = null
)

sealed interface PendingPhotoPurpose {
    data class SavedFeatureAttachment(val featureId: UUID) : PendingPhotoPurpose
    data class GuidedFeatureCreation(val featureId: UUID) : PendingPhotoPurpose
}

sealed interface PendingEditDiscardAction {
    data object CancelLineEdit : PendingEditDiscardAction
    data object CancelPolygonEdit : PendingEditDiscardAction
    data object DiscardNewPoint : PendingEditDiscardAction
    data object DiscardNewLine : PendingEditDiscardAction
    data object DiscardNewPolygon : PendingEditDiscardAction
    data object DiscardGuidedCreation : PendingEditDiscardAction
    data class ChangeProperty(val propertyId: UUID) : PendingEditDiscardAction
    data class ChangePlan(val planId: UUID?) : PendingEditDiscardAction
}
