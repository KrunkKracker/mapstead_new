package com.jumastappworks.mapstead.ui.maintenance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.work.ReminderScheduler
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import com.jumastappworks.mapstead.util.MaintenanceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface MaintenanceUiState {
    data object Loading : MaintenanceUiState
    data object NotFound : MaintenanceUiState
    data class Ready(
        val property: PropertyEntity,
        val allRecords: List<MaintenanceRecordEntity>,
        val filteredRecords: List<MaintenanceRecordEntity>,
        val infrastructureItems: List<InfrastructureItemEntity>,
        val reminders: List<ReminderEntity>,
        val selectedFilter: MaintenanceFilter,
        val counts: MaintenanceCounts,
        val filteredInfrastructureItemId: UUID? = null
    ) : MaintenanceUiState
    data class Error(val message: String) : MaintenanceUiState
}

enum class MaintenanceFilter { All, Due, Upcoming, Completed }

data class MaintenanceCounts(
    val overdue: Int = 0,
    val dueToday: Int = 0,
    val dueSoon: Int = 0,
    val scheduled: Int = 0,
    val unscheduled: Int = 0
)

enum class MaintenanceDueState {
    OVERDUE, DUE_TODAY, DUE_SOON, SCHEDULED, COMPLETED, UNSCHEDULED, CANCELLED
}

sealed interface MaintenanceRecordEditorUiState {
    data object Loading : MaintenanceRecordEditorUiState
    data class Ready(
        val propertyId: UUID,
        val infrastructureItems: List<InfrastructureItemEntity>,
        val recordId: UUID? = null,
        val title: String = "",
        val category: String = "",
        val description: String = "",
        val serviceDate: LocalDate = LocalDate.now(),
        val nextDueDate: LocalDate? = null,
        val provider: String = "",
        val cost: String = "",
        val currencyCode: String = "USD",
        val status: String = "Scheduled",
        val selectedInfrastructureItemId: UUID? = null,
        val validationErrors: Map<String, Int> = emptyMap(),
        val isSaving: Boolean = false,
        val saveErrorRes: Int? = null,
        val initialSnapshot: MaintenanceRecordSnapshot? = null
    ) : MaintenanceRecordEditorUiState {
        fun isDirty(): Boolean {
            val s = initialSnapshot ?: return title.isNotBlank() || category.isNotBlank() || description.isNotBlank()
            return title != s.title || category != s.category || description != s.description || 
                   serviceDate != s.serviceDate || nextDueDate != s.nextDueDate || provider != s.provider || 
                   cost != s.cost || status != s.status || selectedInfrastructureItemId != s.selectedInfrastructureItemId
        }
    }
    data class Saved(val recordId: UUID) : MaintenanceRecordEditorUiState
    data object NotFound : MaintenanceRecordEditorUiState
}

data class MaintenanceRecordSnapshot(
    val title: String,
    val category: String,
    val description: String,
    val serviceDate: LocalDate,
    val nextDueDate: LocalDate?,
    val provider: String,
    val cost: String,
    val status: String,
    val selectedInfrastructureItemId: UUID?
)

sealed interface MaintenanceDetailsActionState {
    data object Idle : MaintenanceDetailsActionState
    data class Working(val action: String) : MaintenanceDetailsActionState
    data class Success(val action: String, val affectedRecordId: UUID?) : MaintenanceDetailsActionState
    data class Error(val messageRes: Int) : MaintenanceDetailsActionState
}

sealed interface MaintenanceDetailsEvent {
    data object NavigateBackAfterDelete : MaintenanceDetailsEvent
    data class NavigateToRecord(val recordId: UUID) : MaintenanceDetailsEvent
    data class ShowSchedulingWarning(val messageRes: Int) : MaintenanceDetailsEvent
}

sealed interface MaintenanceRecordDetailsUiState {
    data object Loading : MaintenanceRecordDetailsUiState
    data class Ready(
        val record: MaintenanceRecordEntity,
        val infrastructureItem: InfrastructureItemEntity? = null,
        val reminder: ReminderEntity? = null,
        val hasMappedFeature: Boolean = false,
        val attachments: List<AttachmentListItemUiModel> = emptyList(),
        val actionState: MaintenanceDetailsActionState = MaintenanceDetailsActionState.Idle
    ) : MaintenanceRecordDetailsUiState
    data object NotFound : MaintenanceRecordDetailsUiState
    data class Error(val message: String) : MaintenanceRecordDetailsUiState
}

sealed interface ReminderEditorUiState {
    data object Loading : ReminderEditorUiState
    data class Ready(
        val propertyId: UUID,
        val reminderId: UUID? = null,
        val maintenanceRecordId: UUID? = null,
        val infrastructureItemId: UUID? = null,
        val title: String = "",
        val description: String = "",
        val dueDate: LocalDate = LocalDate.now().plusDays(1),
        val enabled: Boolean = true,
        val isSaving: Boolean = false,
        val initialSnapshot: ReminderSnapshot? = null
    ) : ReminderEditorUiState {
        fun isDirty(): Boolean {
            val s = initialSnapshot ?: return title.isNotBlank() || description.isNotBlank()
            return title != s.title || description != s.description || dueDate != s.dueDate || enabled != s.enabled
        }
    }
    data class Saved(val reminderId: UUID) : ReminderEditorUiState
    data class SavedDisabled(val reminderId: UUID, val messageRes: Int) : ReminderEditorUiState
    data object NotFound : ReminderEditorUiState
}

data class ReminderSnapshot(
    val title: String,
    val description: String,
    val dueDate: LocalDate,
    val enabled: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository,
    private val propertyRepository: PropertyRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val mapRepository: MapRepository,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _selectedFilter = MutableStateFlow(MaintenanceFilter.All)
    private val _filteredInfrastructureItemId = MutableStateFlow<UUID?>(null)

    // Hub UI State
    val uiState: StateFlow<MaintenanceUiState> = combine(
        _propertyId,
        _selectedFilter,
        _filteredInfrastructureItemId
    ) { id, filter, infraId -> Triple(id, filter, infraId) }.flatMapLatest { (id, filter, infraId) ->
        if (id == null) flowOf(MaintenanceUiState.Loading)
        else {
            val propertyFlow = flow { emit(propertyRepository.getPropertyById(id)) }
            val recordsFlow = maintenanceRepository.getRecordsForProperty(id)
            val itemsFlow = infrastructureRepository.getItemsForProperty(id)
            val remindersFlow = maintenanceRepository.getRemindersForProperty(id)

            combine(propertyFlow, recordsFlow, itemsFlow, remindersFlow) { property, records, items, reminders ->
                if (property == null) MaintenanceUiState.NotFound
                else {
                    val today = LocalDate.now()
                    var filtered = when (filter) {
                        MaintenanceFilter.All -> records
                        MaintenanceFilter.Due -> records.filter { 
                            val state = getDueState(it, today)
                            state == MaintenanceDueState.OVERDUE || state == MaintenanceDueState.DUE_TODAY
                        }
                        MaintenanceFilter.Upcoming -> records.filter {
                            getDueState(it, today) == MaintenanceDueState.DUE_SOON || getDueState(it, today) == MaintenanceDueState.SCHEDULED
                        }
                        MaintenanceFilter.Completed -> records.filter {
                            getDueState(it, today) == MaintenanceDueState.COMPLETED
                        }
                    }

                    if (infraId != null) {
                        filtered = filtered.filter { it.infrastructureItemId == infraId }
                    }

                    val counts = MaintenanceCounts(
                        overdue = records.count { getDueState(it, today) == MaintenanceDueState.OVERDUE },
                        dueToday = records.count { getDueState(it, today) == MaintenanceDueState.DUE_TODAY },
                        dueSoon = records.count { getDueState(it, today) == MaintenanceDueState.DUE_SOON },
                        scheduled = records.count { getDueState(it, today) == MaintenanceDueState.SCHEDULED },
                        unscheduled = records.count { getDueState(it, today) == MaintenanceDueState.UNSCHEDULED }
                    )

                    MaintenanceUiState.Ready(
                        property = property,
                        allRecords = records,
                        filteredRecords = filtered,
                        infrastructureItems = items,
                        reminders = reminders,
                        selectedFilter = filter,
                        counts = counts,
                        filteredInfrastructureItemId = infraId
                    )
                }
            }.catch { e -> emit(MaintenanceUiState.Error(e.message ?: "Unknown error")) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaintenanceUiState.Loading)

    // Details State
    private val _detailsRecordId = MutableStateFlow<UUID?>(null)
    private val _detailsActionState = MutableStateFlow<MaintenanceDetailsActionState>(MaintenanceDetailsActionState.Idle)

    val detailsState: StateFlow<MaintenanceRecordDetailsUiState> = combine(
        _detailsRecordId,
        _detailsActionState,
        _propertyId
    ) { rid, actionState, pid -> Triple(rid, actionState, pid) }.flatMapLatest { (rid, actionState, pid) ->
        if (rid == null || pid == null) flowOf(MaintenanceRecordDetailsUiState.Loading)
        else {
            maintenanceRepository.getRecordById(rid).flatMapLatest { record ->
                if (record == null || record.propertyId != pid) flowOf(MaintenanceRecordDetailsUiState.NotFound)
                else {
                    val itemFlow = if (record.infrastructureItemId != null) {
                        flow<InfrastructureItemEntity?> { emit(infrastructureRepository.getItemById(record.infrastructureItemId)) }
                    } else flowOf(null)
                    
                    val reminderFlow = maintenanceRepository.getRemindersForRecord(rid).map { it.firstOrNull() }
                    
                    val featuresFlow = if (record.infrastructureItemId != null) {
                        mapRepository.getFeaturesForItem(record.infrastructureItemId)
                    } else flowOf(emptyList())

                    val attachmentsFlow = attachmentRepository.getAttachmentsForMaintenanceRecord(pid, rid).map { list ->
                        list.map { entity ->
                            val fileState = attachmentRepository.resolveAttachmentFile(pid, entity.id, verifyHash = false)
                            AttachmentListItemUiModel(
                                attachment = entity,
                                previewUri = (fileState as? AttachmentFileState.Available)?.uri,
                                isMissing = fileState is AttachmentFileState.Missing,
                                isDamaged = fileState is AttachmentFileState.Damaged
                            )
                        }
                    }

                    combine(itemFlow, reminderFlow, featuresFlow, attachmentsFlow) { item, reminder, features, attachments ->
                        MaintenanceRecordDetailsUiState.Ready(
                            record = record,
                            infrastructureItem = item,
                            reminder = reminder,
                            hasMappedFeature = features.isNotEmpty(),
                            attachments = attachments,
                            actionState = actionState
                        )
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaintenanceRecordDetailsUiState.Loading)

    // Editor State
    private val _editorState = MutableStateFlow<MaintenanceRecordEditorUiState>(MaintenanceRecordEditorUiState.Loading)
    val editorState: StateFlow<MaintenanceRecordEditorUiState> = _editorState.asStateFlow()

    // Reminder Editor State
    private val _reminderEditorState = MutableStateFlow<ReminderEditorUiState>(ReminderEditorUiState.Loading)
    val reminderEditorState: StateFlow<ReminderEditorUiState> = _reminderEditorState.asStateFlow()

    var notificationPermissionRequested by mutableStateOf(false)

    fun onNotificationPermissionResult(granted: Boolean) {
        notificationPermissionRequested = true
        if (granted) {
            saveReminder()
        }
    }

    // UI Events
    private val _detailsEvents = Channel<MaintenanceDetailsEvent>(Channel.BUFFERED)
    val detailsEvents = _detailsEvents.receiveAsFlow()

    fun setPropertyId(propertyId: UUID) {
        _propertyId.value = propertyId
    }

    fun setFilter(filter: MaintenanceFilter) {
        _selectedFilter.value = filter
    }

    fun setInfrastructureFilter(itemId: UUID?) {
        _filteredInfrastructureItemId.value = itemId
    }

    fun openRecordDetails(recordId: UUID) {
        _detailsActionState.value = MaintenanceDetailsActionState.Idle
        _detailsRecordId.value = recordId
    }

    fun startEditing(propertyId: UUID, recordId: UUID?, infrastructureItemId: UUID? = null) {
        viewModelScope.launch {
            _editorState.value = MaintenanceRecordEditorUiState.Loading
            val items = infrastructureRepository.getItemsForProperty(propertyId).first()
            if (recordId != null) {
                val record = maintenanceRepository.getRecordForProperty(propertyId, recordId)
                if (record != null) {
                    val snapshot = MaintenanceRecordSnapshot(
                        title = record.title,
                        category = record.category,
                        description = record.description ?: "",
                        serviceDate = record.serviceDate,
                        nextDueDate = record.nextDueDate,
                        provider = record.provider ?: "",
                        cost = record.cost?.toString() ?: "",
                        status = record.status,
                        selectedInfrastructureItemId = record.infrastructureItemId
                    )
                    _editorState.value = MaintenanceRecordEditorUiState.Ready(
                        propertyId = propertyId,
                        infrastructureItems = items,
                        recordId = recordId,
                        title = snapshot.title,
                        category = snapshot.category,
                        description = snapshot.description,
                        serviceDate = snapshot.serviceDate,
                        nextDueDate = snapshot.nextDueDate,
                        provider = snapshot.provider,
                        cost = snapshot.cost,
                        currencyCode = record.currencyCode ?: "USD",
                        status = snapshot.status,
                        selectedInfrastructureItemId = snapshot.selectedInfrastructureItemId,
                        initialSnapshot = snapshot
                    )
                } else {
                    _editorState.value = MaintenanceRecordEditorUiState.NotFound
                }
            } else {
                val ready = MaintenanceRecordEditorUiState.Ready(
                    propertyId = propertyId,
                    infrastructureItems = items,
                    selectedInfrastructureItemId = infrastructureItemId
                )
                _editorState.value = ready.copy(
                    initialSnapshot = MaintenanceRecordSnapshot(
                        title = ready.title,
                        category = ready.category,
                        description = ready.description,
                        serviceDate = ready.serviceDate,
                        nextDueDate = ready.nextDueDate,
                        provider = ready.provider,
                        cost = ready.cost,
                        status = ready.status,
                        selectedInfrastructureItemId = ready.selectedInfrastructureItemId
                    )
                )
            }
        }
    }

    fun startReminderEditing(propertyId: UUID, reminderId: UUID?, maintenanceRecordId: UUID? = null, infrastructureItemId: UUID? = null) {
        viewModelScope.launch {
            _reminderEditorState.value = ReminderEditorUiState.Loading
            if (reminderId != null) {
                val reminder = maintenanceRepository.getReminderForProperty(propertyId, reminderId)
                if (reminder != null) {
                    val snapshot = ReminderSnapshot(
                        title = reminder.title,
                        description = reminder.description ?: "",
                        dueDate = reminder.dueDate,
                        enabled = reminder.enabled
                    )
                    _reminderEditorState.value = ReminderEditorUiState.Ready(
                        propertyId = propertyId,
                        reminderId = reminderId,
                        maintenanceRecordId = reminder.maintenanceRecordId,
                        infrastructureItemId = reminder.infrastructureItemId,
                        title = snapshot.title,
                        description = snapshot.description,
                        dueDate = snapshot.dueDate,
                        enabled = snapshot.enabled,
                        initialSnapshot = snapshot
                    )
                } else {
                    _reminderEditorState.value = ReminderEditorUiState.NotFound
                }
            } else {
                val ready = ReminderEditorUiState.Ready(
                    propertyId = propertyId,
                    maintenanceRecordId = maintenanceRecordId,
                    infrastructureItemId = infrastructureItemId
                )
                val snapshot = ReminderSnapshot(
                    title = ready.title,
                    description = ready.description,
                    dueDate = ready.dueDate,
                    enabled = ready.enabled
                )
                _reminderEditorState.value = ready.copy(
                    initialSnapshot = snapshot
                )
            }
        }
    }

    fun updateEditorTitle(title: String) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(title = title)
    }

    fun updateEditorCategory(category: String) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(category = category)
    }

    fun updateEditorDescription(description: String) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(description = description)
    }

    fun updateEditorServiceDate(date: LocalDate) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(serviceDate = date)
    }

    fun updateEditorNextDueDate(date: LocalDate?) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(nextDueDate = date)
    }

    fun updateEditorProvider(provider: String) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(provider = provider)
    }

    fun updateEditorCost(cost: String) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(cost = cost)
    }

    fun updateEditorInfrastructureItem(itemId: UUID?) {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        _editorState.value = current.copy(selectedInfrastructureItemId = itemId)
    }

    fun saveEditorRecord() {
        val current = _editorState.value as? MaintenanceRecordEditorUiState.Ready ?: return
        if (current.isSaving) return
        
        val errors = mutableMapOf<String, Int>()
        if (current.title.isBlank()) errors["title"] = com.jumastappworks.mapstead.R.string.error_name_required
        if (current.category.isBlank()) errors["category"] = com.jumastappworks.mapstead.R.string.error_category_required
        
        val costValue = if (current.cost.isBlank()) null else current.cost.toDoubleOrNull()
        if (current.cost.isNotBlank() && (costValue == null || !costValue.isFinite() || costValue < 0)) {
            errors["cost"] = com.jumastappworks.mapstead.R.string.error_invalid_cost
        }
        
        if (errors.isNotEmpty()) {
            _editorState.value = current.copy(validationErrors = errors)
            return
        }
        
        _editorState.value = current.copy(isSaving = true, saveErrorRes = null)
        
        viewModelScope.launch {
            val record = MaintenanceRecordEntity(
                id = current.recordId ?: UUID.randomUUID(),
                propertyId = current.propertyId,
                infrastructureItemId = current.selectedInfrastructureItemId,
                title = current.title.trim(),
                category = current.category.trim(),
                description = current.description.takeIf { it.isNotBlank() }?.trim(),
                serviceDate = current.serviceDate,
                nextDueDate = current.nextDueDate,
                provider = current.provider.takeIf { it.isNotBlank() }?.trim(),
                cost = costValue,
                currencyCode = current.currencyCode,
                status = current.status
            )
            
            try {
                val result = maintenanceRepository.saveRecordForProperty(current.propertyId, record)
                when (result) {
                    is MaintenanceWriteResult.Success -> {
                        _editorState.value = MaintenanceRecordEditorUiState.Saved(result.id)
                    }
                    is MaintenanceWriteResult.SuccessWithSchedulingWarning -> {
                        _editorState.value = MaintenanceRecordEditorUiState.Saved(result.id)
                    }
                    else -> {
                        _editorState.value = current.copy(isSaving = false, saveErrorRes = com.jumastappworks.mapstead.R.string.error_save_failed)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _editorState.value = current.copy(isSaving = false, saveErrorRes = com.jumastappworks.mapstead.R.string.error_save_failed)
            }
        }
    }

    fun updateReminderTitle(title: String) {
        val current = _reminderEditorState.value as? ReminderEditorUiState.Ready ?: return
        _reminderEditorState.value = current.copy(title = title)
    }

    fun updateReminderDescription(description: String) {
        val current = _reminderEditorState.value as? ReminderEditorUiState.Ready ?: return
        _reminderEditorState.value = current.copy(description = description)
    }

    fun updateReminderDueDate(date: LocalDate) {
        val current = _reminderEditorState.value as? ReminderEditorUiState.Ready ?: return
        _reminderEditorState.value = current.copy(dueDate = date)
    }

    fun updateReminderEnabled(enabled: Boolean) {
        val current = _reminderEditorState.value as? ReminderEditorUiState.Ready ?: return
        _reminderEditorState.value = current.copy(enabled = enabled)
    }

    fun saveReminder() {
        val current = _reminderEditorState.value as? ReminderEditorUiState.Ready ?: return
        if (current.isSaving) return
        if (current.title.isBlank()) return
        
        _reminderEditorState.value = current.copy(isSaving = true)
        
        viewModelScope.launch {
            val reminder = ReminderEntity(
                id = current.reminderId ?: UUID.randomUUID(),
                propertyId = current.propertyId,
                maintenanceRecordId = current.maintenanceRecordId,
                infrastructureItemId = current.infrastructureItemId,
                title = current.title.trim(),
                description = current.description.takeIf { it.isNotBlank() }?.trim(),
                dueDate = current.dueDate,
                enabled = current.enabled
            )
            
            try {
                val result = maintenanceRepository.saveReminderForProperty(current.propertyId, reminder)
                when (result) {
                    is MaintenanceWriteResult.Success -> {
                        _reminderEditorState.value = ReminderEditorUiState.Saved(result.id)
                    }
                    is MaintenanceWriteResult.SuccessWithSchedulingWarning -> {
                        _reminderEditorState.value = ReminderEditorUiState.Saved(result.id)
                    }
                    is MaintenanceWriteResult.SavedDisabledAfterSchedulingFailure -> {
                        _reminderEditorState.value = ReminderEditorUiState.SavedDisabled(result.id, result.messageRes)
                    }
                    else -> {
                        _reminderEditorState.value = current.copy(isSaving = false)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _reminderEditorState.value = current.copy(isSaving = false)
            }
        }
    }

    fun saveReminderWithoutNotifications() {
        val current = _reminderEditorState.value as? ReminderEditorUiState.Ready ?: return
        updateReminderEnabled(false)
        saveReminder()
    }

    fun deleteRecord(recordId: UUID) {
        val propId = _propertyId.value ?: return
        if (_detailsActionState.value is MaintenanceDetailsActionState.Working) return

        _detailsActionState.value = MaintenanceDetailsActionState.Working("delete")
        viewModelScope.launch {
            try {
                val result = maintenanceRepository.deleteRecordForProperty(propId, recordId)
                when (result) {
                    is MaintenanceWriteResult.Success -> {
                        _detailsActionState.value = MaintenanceDetailsActionState.Success("delete", null)
                        _detailsEvents.send(MaintenanceDetailsEvent.NavigateBackAfterDelete)
                    }
                    is MaintenanceWriteResult.SuccessWithSchedulingWarning -> {
                        _detailsActionState.value = MaintenanceDetailsActionState.Success("delete", null)
                        _detailsEvents.send(MaintenanceDetailsEvent.NavigateBackAfterDelete)
                        _detailsEvents.send(MaintenanceDetailsEvent.ShowSchedulingWarning(result.warningRes))
                    }
                    else -> {
                        _detailsActionState.value = MaintenanceDetailsActionState.Error(R.string.error_delete_failed)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _detailsActionState.value = MaintenanceDetailsActionState.Error(R.string.error_delete_failed)
            }
        }
    }

    fun markRecordComplete(record: MaintenanceRecordEntity, date: LocalDate) {
        val propId = _propertyId.value ?: return
        if (_detailsActionState.value is MaintenanceDetailsActionState.Working) return

        _detailsActionState.value = MaintenanceDetailsActionState.Working("complete")
        viewModelScope.launch {
            try {
                val result = maintenanceRepository.completeMaintenanceRecord(propId, record.id, date)
                when (result) {
                    is MaintenanceWriteResult.Success -> {
                        _detailsActionState.value = MaintenanceDetailsActionState.Success("complete", record.id)
                    }
                    is MaintenanceWriteResult.SuccessWithSchedulingWarning -> {
                        _detailsActionState.value = MaintenanceDetailsActionState.Success("complete", record.id)
                        _detailsEvents.send(MaintenanceDetailsEvent.ShowSchedulingWarning(result.warningRes))
                    }
                    else -> {
                        _detailsActionState.value = MaintenanceDetailsActionState.Error(R.string.error_occurred)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _detailsActionState.value = MaintenanceDetailsActionState.Error(R.string.error_occurred)
            }
        }
    }

    fun rescheduleRecord(record: MaintenanceRecordEntity, date: LocalDate, nextDue: LocalDate?) {
        val propId = _propertyId.value ?: return
        if (_detailsActionState.value is MaintenanceDetailsActionState.Working) return

        _detailsActionState.value = MaintenanceDetailsActionState.Working("reschedule")
        viewModelScope.launch {
            try {
                val result = maintenanceRepository.rescheduleRecordForProperty(propId, record.id, date, nextDue)
                if (result is MaintenanceWriteResult.Success) {
                    _detailsActionState.value = MaintenanceDetailsActionState.Success("reschedule", result.id)
                    _detailsEvents.send(MaintenanceDetailsEvent.NavigateToRecord(result.id))
                } else {
                    _detailsActionState.value = MaintenanceDetailsActionState.Error(R.string.error_save_failed)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _detailsActionState.value = MaintenanceDetailsActionState.Error(R.string.error_save_failed)
            }
        }
    }

    fun clearActionState() {
        _detailsActionState.value = MaintenanceDetailsActionState.Idle
    }

    fun openLinkedMapFeature(recordId: UUID, onNavigate: (UUID, UUID, String) -> Unit) {
        val pid = _propertyId.value ?: return

        viewModelScope.launch {
            val feature = maintenanceRepository.getLinkedFeatureForRecord(pid, recordId)
            if (feature != null) {
                onNavigate(pid, feature.planId, feature.id.toString())
            }
        }
    }

    fun retry() {
        _propertyId.value?.let { setPropertyId(it) }
    }

    fun createCameraCapture(): TemporaryCameraCapture? {
        return attachmentRepository.createTempCameraUri().getOrNull()
    }

    fun deleteCameraCapture(token: String) {
        attachmentRepository.deleteTempCameraCapture(token)
    }

    private fun getDueState(record: MaintenanceRecordEntity, today: LocalDate): MaintenanceDueState {
        if (MaintenanceStatus.isCompleted(record.status)) return MaintenanceDueState.COMPLETED
        if (record.status.trim().equals("Cancelled", ignoreCase = true)) return MaintenanceDueState.CANCELLED
        
        val nextDue = record.nextDueDate ?: return MaintenanceDueState.UNSCHEDULED
        
        return when {
            nextDue.isBefore(today) -> MaintenanceDueState.OVERDUE
            nextDue.isEqual(today) -> MaintenanceDueState.DUE_TODAY
            nextDue.isBefore(today.plusDays(30)) -> MaintenanceDueState.DUE_SOON
            else -> MaintenanceDueState.SCHEDULED
        }
    }
}
