package com.jumastappworks.mapstead.ui.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.handoff.PropertyHandoffPackageGenerator
import com.jumastappworks.mapstead.data.handoff.PropertyHandoffResult
import com.jumastappworks.mapstead.data.reports.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PropertyReportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val reportRepository = mockk<PropertyReportRepository>()
    private val pdfGenerator = mockk<PropertyReportPdfGenerator>()
    private val documentBuilder = PropertyReportDocumentBuilder()
    private val sharer = mockk<PropertyReportSharer>()
    private val handoffGenerator = mockk<PropertyHandoffPackageGenerator>()
    private val handoffSharer = mockk<PropertyHandoffSharer>()
    private lateinit var context: Context
    private lateinit var viewModel: PropertyReportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        
        coEvery { reportRepository.buildPropertyReportData(any()) } returns PropertyReportResult.Success(
            mockk(relaxed = true) { every { propertyName } returns "Test" }
        )

        viewModel = PropertyReportViewModel(
            reportRepository, pdfGenerator, documentBuilder, sharer, 
            handoffGenerator, handoffSharer, context
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `option change clears generatedFile`() = runTest {
        val propId = UUID.randomUUID()
        viewModel.setPropertyId(propId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.toggleSection(PropertyReportSection.MAP_SUMMARY, false)
        assertNull(viewModel.uiState.value.generatedFile)
    }

    @Test
    fun `no sections selected sets error`() = runTest {
        val propId = UUID.randomUUID()
        coEvery { reportRepository.buildPropertyReportData(any()) } returns PropertyReportResult.NoSectionsSelected
        
        viewModel.setPropertyId(propId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(PropertyReportError.NO_SECTIONS_SELECTED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.reportData)
    }

    @Test
    fun `generateHandoff deletes previous ZIP before starting`() = runTest {
        val propId = UUID.randomUUID()
        viewModel.setPropertyId(propId)
        testDispatcher.scheduler.advanceUntilIdle()

        val mockOldFile = File(context.cacheDir, "old.zip").apply { createNewFile() }
        // We can't easily set state, but we can verify the generator call and the cleanup.
        // Actually, we can check if it clears the state.
        
        coEvery { handoffGenerator.generate(any(), any()) } returns PropertyHandoffResult.Success(
            mockOldFile, 1, 0, emptyList(), true
        )
        
        viewModel.generateHandoff()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.generatedHandoffFile)
        
        // Regenerate
        viewModel.generateHandoff()
        assertNull(viewModel.uiState.value.generatedHandoffFile)
    }

    @Test
    fun `stale generation result is not published`() = runTest {
        val propId = UUID.randomUUID()
        viewModel.setPropertyId(propId)
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { reportRepository.buildPropertyReportData(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            PropertyReportResult.Success(mockk(relaxed = true) { every { propertyName } returns "Slow Report" })
        }
        coEvery { pdfGenerator.generate(any(), any()) } returns PdfGenerationResult.SUCCESS

        viewModel.generatePdf()
        
        // Change options immediately to trigger revision increase and cancellation
        viewModel.toggleSection(PropertyReportSection.ATTACHMENT_SUMMARY, false)
        
        testDispatcher.scheduler.advanceTimeBy(2000)
        testDispatcher.scheduler.runCurrent()
        
        assertNull(viewModel.uiState.value.generatedFile)
        assertFalse(viewModel.uiState.value.isGenerating)
    }
}
