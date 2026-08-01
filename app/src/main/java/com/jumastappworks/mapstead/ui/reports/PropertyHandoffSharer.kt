package com.jumastappworks.mapstead.ui.reports

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.reports.isFileInsideDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyHandoffSharer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun share(file: File): ReportShareResult {
        if (!file.exists() || !file.isFile) return ReportShareResult.FileMissing

        return try {
            val handoffDir = File(context.cacheDir, "handoff")
            if (!isFileInsideDirectory(handoffDir, file)) {
                return ReportShareResult.InvalidFileLocation
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file.canonicalFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.handoff_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri(context.getString(R.string.handoff_share_label), uri)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.handoff_share_chooser_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(chooser)
            ReportShareResult.Started
        } catch (e: ActivityNotFoundException) {
            ReportShareResult.NoShareTarget
        } catch (e: SecurityException) {
            ReportShareResult.PermissionFailure
        } catch (e: IOException) {
            ReportShareResult.Error
        } catch (e: Exception) {
            ReportShareResult.Error
        }
    }
}
