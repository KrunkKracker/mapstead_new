package com.jumastappworks.mapstead.ui.infrastructure

import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class InfrastructureAttachmentsViewModelTest {

    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private lateinit var viewModel: InfrastructureAttachmentsViewModel

    private val propertyId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()
    private val item = InfrastructureItemEntity(id = itemId, propertyId = propertyId, name = "Test Item", category = "Test", status = "Active")
    private val attachmentsFlow = MutableStateFlow<List<AttachmentEntity>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(propertyId, itemId) } returns attachmentsFlow
        coEvery { infraRepo.getActiveItemForProperty(propertyId, itemId) } returns item
        
        viewModel = InfrastructureAttachmentsViewModel(infraRepo, attachmentRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadAttachments emits Loading then Ready`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.loadAttachments(propertyId, itemId)
        assertTrue(viewModel.uiState.value is InfrastructureAttachmentsUiState.Loading)
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is InfrastructureAttachmentsUiState.Ready)
        val ready = viewModel.uiState.value as InfrastructureAttachmentsUiState.Ready
        assertEquals(itemId, ready.item.id)
    }

    @Test
    fun `loadAttachments returns NotFound for missing item`() = runTest {
        coEvery { infraRepo.getActiveItemForProperty(propertyId, itemId) } returns null
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.loadAttachments(propertyId, itemId)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is InfrastructureAttachmentsUiState.NotFound)
    }

    @Test
    fun `loadAttachments returns Error on repository failure`() = runTest {
        coEvery { infraRepo.getActiveItemForProperty(propertyId, itemId) } throws RuntimeException("DB Error")
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.loadAttachments(propertyId, itemId)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is InfrastructureAttachmentsUiState.Error)
    }

    @Test
    fun `attachment Flow updates refresh the list`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.loadAttachments(propertyId, itemId)
        advanceUntilIdle()
        
        val attachment = AttachmentEntity(id = UUID.randomUUID(), propertyId = propertyId, infrastructureItemId = itemId, displayName = "Photo", mimeType = "image/jpeg", attachmentType = "PHOTO", localUri = "camera://test")
        attachmentsFlow.value = listOf(attachment)
        advanceUntilIdle()
        
        val ready = viewModel.uiState.value as InfrastructureAttachmentsUiState.Ready
        assertEquals(1, ready.attachments.size)
        assertEquals("Photo", ready.attachments[0].attachment.displayName)
    }

    @Test
    fun `retryAttachments reloads data`() = runTest {
        coEvery { infraRepo.getActiveItemForProperty(propertyId, itemId) } throws RuntimeException("Fail")
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.loadAttachments(propertyId, itemId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is InfrastructureAttachmentsUiState.Error)
        
        coEvery { infraRepo.getActiveItemForProperty(propertyId, itemId) } returns item
        viewModel.retryAttachments()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is InfrastructureAttachmentsUiState.Ready)
    }
}
