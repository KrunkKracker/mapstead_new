package com.jumastappworks.mapstead.ui.infrastructure

import com.jumastappworks.mapstead.data.repository.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class InfrastructureItemEditorViewModelTest {

    private val repository = mockk<InfrastructureRepository>(relaxed = true)
    
    private lateinit var viewModel: InfrastructureItemEditorViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = InfrastructureItemEditorViewModel(repository)
        val propId = UUID.randomUUID()
        viewModel.loadItem(propId, null) // Set to Ready state
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `validation fails on empty name or category`() {
        viewModel.name = ""
        viewModel.category = "Utility"
        viewModel.saveItem {}
        assertNotNull(viewModel.nameError)
        
        viewModel.name = "Well"
        viewModel.category = ""
        viewModel.saveItem {}
        assertNotNull(viewModel.categoryError)
    }

    @Test
    fun `duplicate save calls are ignored`() = runTest {
        viewModel.name = "Pump"
        viewModel.category = "Water"
        
        // Ensure repository takes some "time"
        coEvery { repository.insertItem(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
        }

        viewModel.saveItem {}
        val state = viewModel.uiState.value as? InfrastructureItemEditorUiState.Ready
        assertTrue("Expected Saving to be true but was ${state?.isSaving}", state?.isSaving == true)
        
        // Second call should return early
        viewModel.saveItem {}
        
        advanceUntilIdle()
        
        coVerify(exactly = 1) { repository.insertItem(any()) }
    }
}
