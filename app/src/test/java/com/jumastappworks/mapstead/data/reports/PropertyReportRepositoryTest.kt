package com.jumastappworks.mapstead.data.reports

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.AttachmentDao
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PropertyReportRepositoryTest {

    private val database = mockk<MapsteadDatabase>()
    private val propertyRepository = mockk<PropertyRepository>()
    private val infrastructureRepository = mockk<InfrastructureRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private val mapRepository = mockk<MapRepository>()
    private val attachmentRepository = mockk<AttachmentRepository>()
    private val attachmentDao = mockk<AttachmentDao>()

    private lateinit var repository: PropertyReportRepository

    @Before
    fun setup() {
        every { database.attachmentDao() } returns attachmentDao
        repository = PropertyReportRepository(
            database, propertyRepository, infrastructureRepository,
            maintenanceRepository, mapRepository, attachmentRepository
        )
    }

    @Test
    fun `deleted property returns PropertyNotFound`() = runTest {
        val propId = UUID.randomUUID()
        coEvery { propertyRepository.getPropertyById(propId) } returns PropertyEntity(
            id = propId, name = "Deleted", propertyType = "Home", deletedAt = Instant.now()
        )

        val result = repository.buildPropertyReportData(PropertyReportOptions(propId))
        assertEquals(PropertyReportResult.PropertyNotFound, result)
    }

    @Test
    fun `no sections selected returns NoSectionsSelected`() = runTest {
        val propId = UUID.randomUUID()
        coEvery { propertyRepository.getPropertyById(propId) } returns PropertyEntity(
            id = propId, name = "Prop", propertyType = "Home"
        )

        val options = PropertyReportOptions(
            propertyId = propId,
            enabledSections = emptySet()
        )

        val result = repository.buildPropertyReportData(options)
        assertEquals(PropertyReportResult.NoSectionsSelected, result)
    }

    @Test
    fun `empty property still produces valid report data`() = runTest {
        val propId = UUID.randomUUID()
        coEvery { propertyRepository.getPropertyById(propId) } returns PropertyEntity(
            id = propId, name = "Empty Prop", propertyType = "Land"
        )
        
        every { infrastructureRepository.getItemsForProperty(propId) } returns flowOf(emptyList())
        every { maintenanceRepository.getRecordsForProperty(propId) } returns flowOf(emptyList())
        every { maintenanceRepository.getRemindersForProperty(propId) } returns flowOf(emptyList())
        every { mapRepository.getPlansForProperty(propId) } returns flowOf(emptyList())
        every { attachmentDao.getAttachmentsForProperty(propId) } returns flowOf(emptyList())

        val result = repository.buildPropertyReportData(PropertyReportOptions(propId))
        
        assertTrue(result is PropertyReportResult.Success)
        val data = (result as PropertyReportResult.Success).data
        assertEquals("Empty Prop", data.propertyName)
        assertEquals(0, data.infrastructureItems.size)
        assertEquals(0, data.planCount)
        assertEquals(0, data.attachmentSummary.totalCount)
    }

    @Test
    fun `map repository is not called when map summary is deselected`() = runTest {
        val propId = UUID.randomUUID()
        coEvery { propertyRepository.getPropertyById(propId) } returns PropertyEntity(
            id = propId, name = "Prop", propertyType = "Home"
        )
        
        every { infrastructureRepository.getItemsForProperty(any()) } returns flowOf(emptyList())
        every { maintenanceRepository.getRecordsForProperty(any()) } returns flowOf(emptyList())
        every { maintenanceRepository.getRemindersForProperty(any()) } returns flowOf(emptyList())
        every { attachmentDao.getAttachmentsForProperty(any()) } returns flowOf(emptyList())

        val options = PropertyReportOptions(
            propertyId = propId,
            enabledSections = setOf(PropertyReportSection.PROPERTY_PROFILE)
        )

        repository.buildPropertyReportData(options)
        
        coVerify(exactly = 0) { mapRepository.getPlansForProperty(any()) }
    }
}
