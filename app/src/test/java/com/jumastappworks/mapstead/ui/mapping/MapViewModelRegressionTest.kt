package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelRegressionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { userPrefs.userPreferencesFlow } returns flowOf(com.jumastappworks.mapstead.data.prefs.UserPreferences())
        
        viewModel = MapViewModel(
            mockk(relaxed = true), attachmentRepo, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            userPrefs, mockk(relaxed = true), mockk(relaxed = true), SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createCameraCapture returns Result from suspend call`() = runTest {
        val mockUri = mockk<android.net.Uri>()
        val capture = TemporaryCameraCapture(mockUri, "token")
        coEvery { attachmentRepo.createTempCameraUri() } returns Result.success(capture)
        
        val result = viewModel.createCameraCapture()
        
        assertTrue(result.isSuccess)
        assertEquals(capture, result.getOrNull())
        coVerify(exactly = 1) { attachmentRepo.createTempCameraUri() }
    }
}
