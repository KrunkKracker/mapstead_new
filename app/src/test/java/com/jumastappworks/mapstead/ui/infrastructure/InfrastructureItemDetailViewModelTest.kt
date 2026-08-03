package com.jumastappworks.mapstead.ui.infrastructure

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        Dispatchers.setMain(testDispatcher)
        viewModel = InfrastructureItemDetailViewModel(
            infraRepo, propertyRepo, mapRepo, attachmentRepo, maintenanceRepo, relationshipRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        
        viewModel.init(propId, itemId)
        
        var successCalled = false
        viewModel.deleteItem(onSuccess = { successCalled = true }, onError = {})
        
        assertTrue(successCalled)
        assertEquals(InfrastructureItemDetailUiState.Deleting, viewModel.uiState.value)
    }
}
