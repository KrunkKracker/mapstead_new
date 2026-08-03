package com.jumastappworks.mapstead.ui.infrastructure

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.attachments.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class InfrastructureItemDetailViewModelTest {

    private lateinit var viewModel: InfrastructureItemDetailViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)
    private val relationshipRepo = mockk<InfrastructureRelationshipRepository>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk(relaxed = true)
        
        Dispatchers.setMain(testDispatcher)
        viewModel = InfrastructureItemDetailViewModel(
            infraRepo, propertyRepo, mapRepo, attachmentRepo, maintenanceRepo, relationshipRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `valid item loads Ready state with summaries`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Test Item", category = "Utility", status = "Active")
        val property = PropertyEntity(id = propId, name = "Test Property", propertyType = "Home")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(item)
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(listOf(property))
        every { mapRepo.getFeaturesForItem(itemId) } returns MutableStateFlow(emptyList())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(propId, itemId) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(itemId) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(propId, itemId) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(propId, itemId) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        
        val state = viewModel.uiState.value
        assertTrue("State should be Ready but was $state", state is InfrastructureItemDetailUiState.Ready)
        val ready = state as InfrastructureItemDetailUiState.Ready
        assertEquals("Test Item", ready.item.name)
        assertEquals("Test Property", ready.propertyName)
    }

    @Test
    fun `wrong property ID returns NotFound`() = runTest(testDispatcher) {
        val correctPropId = UUID.randomUUID()
        val wrongPropId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = correctPropId, name = "Item", category = "T", status = "Active")
        
        every { infraRepo.observeActiveItem(wrongPropId, itemId) } returns MutableStateFlow(item)
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(itemId) } returns MutableStateFlow(emptyList())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(wrongPropId, itemId)
        
        val state = viewModel.uiState.value
        assertTrue("State should be NotFound but was $state", state is InfrastructureItemDetailUiState.NotFound)
    }

    @Test
    fun `deleted or missing item returns NotFound`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(null)
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(itemId) } returns MutableStateFlow(emptyList())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        
        val state = viewModel.uiState.value
        assertTrue("State should be NotFound but was $state", state is InfrastructureItemDetailUiState.NotFound)
    }

    @Test
    fun `multiple map locations are correctly ordered`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Item", category = "T", status = "Active")
        
        val feature1 = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "B Location")
        val feature2 = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "A Location")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(item)
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(itemId) } returns MutableStateFlow(listOf(feature1, feature2))
        every { attachmentRepo.getAttachmentsForInfrastructureItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        
        val state = viewModel.uiState.value
        assertTrue("State should be Ready but was $state", state is InfrastructureItemDetailUiState.Ready)
        val ready = state as InfrastructureItemDetailUiState.Ready
        assertEquals(2, ready.mapLocations.size)
        assertEquals("A Location", ready.mapLocations[0].label)
        assertEquals("B Location", ready.mapLocations[1].label)
    }

    @Test
    fun `delete item success triggers callback`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Item", category = "T", status = "Active")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(item)
        coEvery { infraRepo.softDeleteItemForProperty(propId, itemId) } returns InfrastructureWriteResult.Success(itemId)
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(any()) } returns MutableStateFlow(emptyList())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        
        var successCalled = false
        viewModel.deleteItem(onSuccess = { successCalled = true })
        
        assertTrue(successCalled)
        val state = viewModel.uiState.value as InfrastructureItemDetailUiState.Ready
        assertTrue(state.isDeleting)
    }

    @Test
    fun `Ready state contains resolved attachments`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Item", category = "T", status = "Active")
        
        val attachment = AttachmentEntity(
            id = UUID.randomUUID(),
            propertyId = propId,
            infrastructureItemId = itemId,
            displayName = "Photo",
            attachmentType = "Photo",
            localUri = "content://test"
        )
        val uri = android.net.Uri.parse("content://test")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(item)
        every { attachmentRepo.getAttachmentsForInfrastructureItem(propId, itemId) } returns MutableStateFlow(listOf(attachment))
        coEvery { attachmentRepo.resolveAttachmentFile(propId, attachment.id, any()) } returns AttachmentFileState.Available(uri, 100L, "hash")
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        
        val state = viewModel.uiState.value as InfrastructureItemDetailUiState.Ready
        assertEquals(1, state.attachments.size)
        assertEquals(uri, state.attachments[0].previewUri)
        assertFalse(state.attachments[0].isMissing)
    }

    @Test
    fun `property-scoped parent lookup is used`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Item", category = "T", status = "Active", parentItemId = parentId)
        val parentItem = InfrastructureItemEntity(id = parentId, propertyId = propId, name = "Parent", category = "T", status = "Active")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(item)
        coEvery { infraRepo.getActiveItemForProperty(propId, parentId) } returns parentItem
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(any()) } returns MutableStateFlow(emptyList())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        
        val state = viewModel.uiState.value as InfrastructureItemDetailUiState.Ready
        assertEquals("Parent", state.parentItem?.name)
        coVerify { infraRepo.getActiveItemForProperty(propId, parentId) }
    }

    @Test
    fun `delete failure shows error and stops deleting state`() = runTest(testDispatcher) {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Item", category = "T", status = "Active")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns MutableStateFlow(item)
        coEvery { infraRepo.softDeleteItemForProperty(propId, itemId) } returns InfrastructureWriteResult.NotFound
        every { propertyRepo.getAllProperties() } returns MutableStateFlow(emptyList<PropertyEntity>())
        every { mapRepo.getFeaturesForItem(any()) } returns MutableStateFlow(emptyList())
        every { attachmentRepo.getAttachmentsForInfrastructureItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { maintenanceRepo.getRecordsForItem(any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.observeRelationshipsForItem(any(), any()) } returns MutableStateFlow(emptyList())
        every { relationshipRepo.getChildrenForItem(any(), any()) } returns MutableStateFlow(emptyList())

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.init(propId, itemId)
        viewModel.deleteItem(onSuccess = {})
        
        val state = viewModel.uiState.value as InfrastructureItemDetailUiState.Ready
        assertFalse(state.isDeleting)
        assertNotNull(state.deleteErrorRes)
    }
}
