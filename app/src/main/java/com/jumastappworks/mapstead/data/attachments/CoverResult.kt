package com.jumastappworks.mapstead.data.attachments

sealed interface CoverResult {
    data object Set : CoverResult
    data object Cleared : CoverResult
    data object AlreadyClear : CoverResult
    data object AttachmentNotFound : CoverResult
    data object FeatureNotFound : CoverResult
    data object InvalidOwner : CoverResult
    data object UnsupportedType : CoverResult
    data object MissingFile : CoverResult
    data object DamagedFile : CoverResult
    data class Error(val message: String?) : CoverResult
}
