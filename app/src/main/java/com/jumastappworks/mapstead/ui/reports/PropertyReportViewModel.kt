package com.jumastappworks.mapstead.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.handoff.*
import com.jumastappworks.mapstead.data.reports.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class PropertyReportUiState(
    val propertyId: UUID? = null,
    val options: PropertyReportOptions? = null,
    val reportData: PropertyReportData? = null,
    val isGenerating: Boolean = false,
    val generatedFile: File? = null,
    val error: PropertyReportError? = null,
    
    // Handoff specific
    val handoffOptions: PropertyHandoffOptions? = null,
    val handoffProgress: PropertyHandoffProgress? = null,
    val isGeneratingHandoff: Boolean = false,
    val generatedHandoffFile: File? = null,
    val handoffIncludedCount: Int = 0,
    val handoffSkippedCount: Int = 0,
    val handoffWarnings: List<PropertyHandoffWarning> = emptyList(),
    val handoffError: PropertyHandoffError? = null
)

@HiltViewModel
class PropertyReportViewModel @Inject constructor(
    private val reportRepository: PropertyReportRepository,
    private val pdfGenerator: PropertyReportPdfGenerator,
    private val documentBuilder: PropertyReportDocumentBuilder,
    private val sharer: PropertyReportSharer,
    private val handoffGenerator: PropertyHandoffPackageGenerator,
    private val handoffSharer: PropertyHandoffSharer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropertyReportUiState())
    val uiState: StateFlow<PropertyReportUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null
    private var generationJob: Job? = null
    private var handoffJob: Job? = null
    private var reportRevision: Long = 0L
    private var handoffRevision: Long = 0L

    fun setPropertyId(propertyId: UUID) {
        if (_uiState.value.propertyId == propertyId) return
        
        onReportOptionsChanging()
        onHandoffOptionsChanging()
        
        _uiState.update { it.copy(
            propertyId = propertyId, 
            options = PropertyReportOptions(propertyId),
            handoffOptions = PropertyHandoffOptions(propertyId)
        ) }
        refreshPreview()
    }

    fun toggleSection(section: PropertyReportSection, enabled: Boolean) {
        onReportOptionsChanging()
        _uiState.update { state ->
            val options = state.options ?: return@update state
            val newSections = if (enabled) options.enabledSections + section else options.enabledSections - section
            state.copy(options = options.copy(enabledSections = newSections))
        }
        refreshPreview()
    }

    fun toggleHandoffComponent(component: PropertyHandoffComponent, enabled: Boolean) {
        onHandoffOptionsChanging()
        _uiState.update { state ->
            val options = state.handoffOptions ?: return@update state
            val newComponents = if (enabled) options.enabledComponents + component else options.enabledComponents - component
            state.copy(handoffOptions = options.copy(enabledComponents = newComponents))
        }
    }

    fun setMaintenanceRange(range: MaintenanceHistoryRange) {
        onReportOptionsChanging()
        _uiState.update { state ->
            val options = state.options ?: return@update state
            val today = LocalDate.now()
            val (start, end) = when (range) {
                MaintenanceHistoryRange.ALL_TIME -> null to null
                MaintenanceHistoryRange.LAST_12_MONTHS -> today.minusMonths(12) to today
                MaintenanceHistoryRange.CURRENT_YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
            }
            state.copy(
                options = options.copy(
                    maintenanceRange = range,
                    maintenanceHistoryRangeStart = start,
                    maintenanceHistoryRangeEnd = end
                )
            )
        }
        refreshPreview()
    }

    private fun onReportOptionsChanging() {
        reportRevision++
        previewJob?.cancel()
        generationJob?.cancel()
        
        val oldFile = _uiState.value.generatedFile
        _uiState.update { it.copy(
            reportData = null,
            generatedFile = null,
            isGenerating = false,
            error = null
        ) }
        
        oldFile?.let { file ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { if (file.exists()) file.delete() } catch (e: Exception) {}
            }
        }
    }

    private fun onHandoffOptionsChanging() {
        handoffRevision++
        handoffJob?.cancel()
        
        val oldFile = _uiState.value.generatedHandoffFile
        _uiState.update { it.copy(
            generatedHandoffFile = null,
            isGeneratingHandoff = false,
            handoffProgress = null,
            handoffIncludedCount = 0,
            handoffSkippedCount = 0,
            handoffWarnings = emptyList(),
            handoffError = null
        ) }
        
        oldFile?.let { file ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { if (file.exists()) file.delete() } catch (e: Exception) {}
            }
        }
    }

    private fun refreshPreview() {
        val options = _uiState.value.options ?: return
        val currentRevision = reportRevision
        
        previewJob = viewModelScope.launch {
            try {
                val result = reportRepository.buildPropertyReportData(options)
                if (currentRevision != reportRevision) return@launch
                
                when (result) {
                    is PropertyReportResult.Success -> {
                        _uiState.update { it.copy(reportData = result.data, error = null) }
                    }
                    is PropertyReportResult.PropertyNotFound -> {
                        _uiState.update { it.copy(reportData = null, error = PropertyReportError.PROPERTY_NOT_FOUND) }
                    }
                    is PropertyReportResult.NoSectionsSelected -> {
                        _uiState.update { it.copy(reportData = null, error = PropertyReportError.NO_SECTIONS_SELECTED) }
                    }
                    is PropertyReportResult.Error -> {
                        _uiState.update { it.copy(reportData = null, error = result.error) }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.update { it.copy(error = PropertyReportError.DATA_LOAD_FAILED) }
            }
        }
    }

    fun generatePdf() {
        val options = _uiState.value.options ?: return
        val currentRevision = reportRevision
        
        generationJob?.cancel()
        _uiState.update { it.copy(isGenerating = true, error = null, generatedFile = null) }
        
        generationJob = viewModelScope.launch {
            var tempFile: File? = null
            try {
                val dataResult = reportRepository.buildPropertyReportData(options)
                if (currentRevision != reportRevision) return@launch

                when (dataResult) {
                    is PropertyReportResult.Success -> {
                        val data = dataResult.data
                        val document = documentBuilder.build(data, options)
                        
                        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
                        val fileName = createPropertyReportFilename(data.propertyName, LocalDate.now())
                        val file = File(reportsDir, fileName)
                        tempFile = file
                        
                        val result = pdfGenerator.generate(document, file)
                        if (currentRevision != reportRevision) {
                            if (file.exists()) file.delete()
                            return@launch
                        }

                        if (result == PdfGenerationResult.SUCCESS) {
                            _uiState.update { it.copy(isGenerating = false, generatedFile = file) }
                        } else {
                            _uiState.update { it.copy(isGenerating = false, error = PropertyReportError.PDF_CREATION_FAILED) }
                        }
                    }
                    is PropertyReportResult.PropertyNotFound -> {
                        _uiState.update { it.copy(isGenerating = false, error = PropertyReportError.PROPERTY_NOT_FOUND) }
                    }
                    is PropertyReportResult.NoSectionsSelected -> {
                        _uiState.update { it.copy(isGenerating = false, error = PropertyReportError.NO_SECTIONS_SELECTED) }
                    }
                    is PropertyReportResult.Error -> {
                        _uiState.update { it.copy(isGenerating = false, error = dataResult.error) }
                    }
                }
            } catch (c: CancellationException) {
                tempFile?.let { if (it.exists()) it.delete() }
                throw c
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = PropertyReportError.PDF_CREATION_FAILED) }
            } finally {
                if (currentRevision == reportRevision) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    fun generateHandoff() {
        val options = _uiState.value.handoffOptions ?: return
        val currentRevision = handoffRevision
        
        handoffJob?.cancel()
        _uiState.update { it.copy(isGeneratingHandoff = true, handoffError = null, generatedHandoffFile = null) }
        
        handoffJob = viewModelScope.launch {
            try {
                val result = handoffGenerator.generate(options) { progress ->
                    if (currentRevision == handoffRevision) {
                        _uiState.update { it.copy(handoffProgress = progress) }
                    }
                }
                
                if (currentRevision != handoffRevision) {
                    if (result is PropertyHandoffResult.Success) {
                        if (result.packageFile.exists()) result.packageFile.delete()
                    }
                    return@launch
                }
                
                when (result) {
                    is PropertyHandoffResult.Success -> {
                        _uiState.update { it.copy(
                            isGeneratingHandoff = false,
                            generatedHandoffFile = result.packageFile,
                            handoffIncludedCount = result.includedAttachmentCount,
                            handoffSkippedCount = result.skippedAttachmentCount,
                            handoffWarnings = result.warnings
                        ) }
                    }
                    is PropertyHandoffResult.PropertyNotFound -> {
                        _uiState.update { it.copy(isGeneratingHandoff = false, handoffError = PropertyHandoffError.PROPERTY_NOT_FOUND) }
                    }
                    is PropertyHandoffResult.NothingSelected -> {
                        _uiState.update { it.copy(isGeneratingHandoff = false, handoffError = PropertyHandoffError.NOTHING_SELECTED) }
                    }
                    is PropertyHandoffResult.PdfReportFailed -> {
                        _uiState.update { it.copy(isGeneratingHandoff = false, handoffError = PropertyHandoffError.REPORT_GENERATION_FAILED) }
                    }
                    is PropertyHandoffResult.StorageUnavailable -> {
                        _uiState.update { it.copy(isGeneratingHandoff = false, handoffError = PropertyHandoffError.STORAGE_UNAVAILABLE) }
                    }
                    is PropertyHandoffResult.Cancelled -> {
                        // Reset state but don't show error
                    }
                    else -> {
                        _uiState.update { it.copy(isGeneratingHandoff = false, handoffError = PropertyHandoffError.PACKAGE_CREATION_FAILED) }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingHandoff = false, handoffError = PropertyHandoffError.PACKAGE_CREATION_FAILED) }
            } finally {
                if (currentRevision == handoffRevision) {
                    _uiState.update { it.copy(isGeneratingHandoff = false) }
                }
            }
        }
    }

    fun shareReport() {
        val file = _uiState.value.generatedFile ?: return
        val result = sharer.share(file)
        if (result != ReportShareResult.Started) {
            val uiError = when (result) {
                ReportShareResult.FileMissing -> PropertyReportError.GENERATED_FILE_MISSING
                ReportShareResult.InvalidFileLocation -> PropertyReportError.SHARE_PERMISSION_FAILED
                ReportShareResult.NoShareTarget -> PropertyReportError.SHARE_UNAVAILABLE
                ReportShareResult.PermissionFailure -> PropertyReportError.SHARE_PERMISSION_FAILED
                else -> PropertyReportError.SHARE_UNAVAILABLE
            }
            _uiState.update { it.copy(error = uiError) }
        }
    }

    fun shareHandoff() {
        val file = _uiState.value.generatedHandoffFile ?: return
        val result = handoffSharer.share(file)
        if (result != ReportShareResult.Started) {
            val uiError = when (result) {
                ReportShareResult.FileMissing -> PropertyHandoffError.GENERATED_FILE_MISSING
                ReportShareResult.InvalidFileLocation -> PropertyHandoffError.SHARE_PERMISSION_FAILED
                ReportShareResult.NoShareTarget -> PropertyHandoffError.SHARE_UNAVAILABLE
                ReportShareResult.PermissionFailure -> PropertyHandoffError.SHARE_PERMISSION_FAILED
                else -> PropertyHandoffError.SHARE_UNAVAILABLE
            }
            _uiState.update { it.copy(handoffError = uiError) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, handoffError = null) }
    }
}
