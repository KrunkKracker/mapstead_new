package com.jumastappworks.mapstead.ui.properties

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddPropertyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val addressResolver = mockk<AddressLocationResolver>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: AddPropertyViewModel

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.getString(R.string.setup_property_map_name) } returns "Property Map"
        
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        viewModel = AddPropertyViewModel(propertyRepo, attachmentRepo, locationProvider, addressResolver, userPrefs, context, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is step 1 and new target`() {
        val state = viewModel.uiState.value
        assertEquals(SetupStep.NAME_AND_TYPE, state.currentStep)
        assertTrue(state.target is PropertySetupTarget.New)
    }

    @Test
    fun `loadExistingProperty sets existing target and loads data`() = runTest {
        val propId = UUID.randomUUID()
        val prop = PropertyEntity(id = propId, name = "Existing", propertyType = "Farm", latitude = 10.0, longitude = 20.0)
        coEvery { propertyRepo.getPropertyById(propId) } returns prop
        
        viewModel.loadExistingProperty(propId)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.target is PropertySetupTarget.Existing)
        assertEquals(propId, state.target.id)
        assertEquals("Existing", state.propertyName)
        assertEquals(SetupStep.LOCATE, state.currentStep)
        assertTrue(state.existingPropertyLoaded)
    }

    @Test
    fun `address search generation prevents older result overwrite`() = runTest {
        coEvery { addressResolver.search("first") } coAnswers {
            delay(100)
            AddressSearchResult.Success(listOf(AddressLocationMatch("First Result", 1.0, 1.0)))
        }
        viewModel.searchAddress("first")
        
        coEvery { addressResolver.search("second") } returns AddressSearchResult.Success(listOf(AddressLocationMatch("Second Result", 2.0, 2.0)))
        viewModel.searchAddress("second")
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals("Second Result", state.addressResults.first().displayAddress)
        assertFalse(state.isSearchingAddress)
    }

    @Test
    fun `GPS success creates candidate only`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Success(
            45.0, -90.0, 10f, System.currentTimeMillis(), 
            LocationResult.Success.Source.Fresh, true
        )
        
        viewModel.requestGpsLocation()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNotNull(state.candidateLocation)
        assertEquals(45.0, state.candidateLocation?.latitude!!, 1e-10)
        
        viewModel.confirmCandidate()
        assertEquals(45.0, viewModel.uiState.value.confirmedLocation?.latitude!!, 1e-10)
    }

    @Test
    fun `createProperty for new property calls insertPropertyWithDefaultMap`() = runTest {
        viewModel.setName("New Property")
        viewModel.proceedToLocate()
        viewModel.deferLocation()
        
        val draftId = viewModel.uiState.value.target.id
        
        viewModel.createProperty()
        advanceUntilIdle()
        
        coVerify { propertyRepo.insertPropertyWithDefaultMap(match { it.id == draftId && it.name == "New Property" }, "Property Map") }
    }

    @Test
    fun `createProperty for existing property calls updatePropertyLocation`() = runTest {
        val propId = UUID.randomUUID()
        coEvery { propertyRepo.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "Existing", propertyType = "Home")
        
        viewModel.loadExistingProperty(propId)
        advanceUntilIdle()
        
        viewModel.setMapCandidate(45.0, -90.0)
        viewModel.confirmCandidate()
        advanceUntilIdle()
        
        viewModel.createProperty()
        advanceUntilIdle()
        
        coVerify { propertyRepo.updatePropertyLocationWithOptionalFirstMap(propId, 45.0, -90.0, true) }
    }

    @Test
    fun `permission denial persistence`() {
        viewModel.markLocationPermissionRequested()
        val restoredVm = AddPropertyViewModel(propertyRepo, attachmentRepo, locationProvider, addressResolver, userPrefs, context, savedStateHandle)
        assertTrue(restoredVm.uiState.value.permissionRequested)
    }

    @Test
    fun `manual coordinate keyboard accepts negative decimals`() {
        viewModel.setManualInputs("-12.345", "123.456")
        viewModel.validateAndSetManualCandidate()
        
        val cand = viewModel.uiState.value.candidateLocation
        assertNotNull(cand)
        assertEquals(-12.345, cand?.latitude!!, 1e-10)
    }

    @Test
    fun `savedStateHandle restoration works for target and name`() {
        val stableId = UUID.randomUUID()
        savedStateHandle["setup_target_id"] = stableId.toString()
        savedStateHandle["setup_name"] = "Restored Name"
        
        val restoredVm = AddPropertyViewModel(propertyRepo, attachmentRepo, locationProvider, addressResolver, userPrefs, context, savedStateHandle)
        assertEquals("Restored Name", restoredVm.uiState.value.propertyName)
    }

    @Test
    fun `clearStagedPhoto deletes temporary capture if token exists`() = runTest {
        viewModel.setStagedPhoto("content://path", "token123")
        viewModel.clearStagedPhoto()
        advanceUntilIdle()
        
        coVerify { attachmentRepo.deleteTempCameraCapture("token123") }
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
    }

    @Test
    fun `successful createProperty clears staged photo state`() = runTest {
        viewModel.setName("New Prop")
        viewModel.setStagedPhoto("content://photo", "token")
        coEvery { attachmentRepo.importAttachment(any(), any(), any(), any(), any(), any()) } returns AttachmentWriteResult.Success(UUID.randomUUID())
        
        viewModel.createProperty()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
    }

    @Test
    fun `createProperty failure retains photo state for retry`() = runTest {
        viewModel.setName("New Prop")
        viewModel.setStagedPhoto("content://photo", "token")
        coEvery { attachmentRepo.importAttachment(any(), any(), any(), any(), any(), any()) } returns AttachmentWriteResult.CopyFailed
        
        viewModel.createProperty()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.Ready)
        assertTrue(viewModel.uiState.value.outcome is PropertySetupOutcome.PropertyCreatedWithPhotoWarning)
    }

    @Test
    fun `retryPropertyPhoto does not recreate property`() = runTest {
        val propertyId = UUID.randomUUID()
        viewModel.setStagedPhoto("content://photo", "token")
        
        viewModel.retryPropertyPhoto(propertyId)
        advanceUntilIdle()
        
        coVerify(exactly = 0) { propertyRepo.insertPropertyWithDefaultMap(any(), any()) }
        coVerify(atLeast = 1) { attachmentRepo.importAttachment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `successful createProperty sets PropertyCreated outcome`() = runTest {
        viewModel.setName("New Prop")
        coEvery { propertyRepo.insertPropertyWithDefaultMap(any(), any()) } returns UUID.randomUUID()
        
        viewModel.createProperty()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.outcome is PropertySetupOutcome.PropertyCreated)
    }

    @Test
    fun `continueWithoutPhoto sets PropertyCreated outcome and clears capture`() = runTest {
        val propertyId = UUID.randomUUID()
        viewModel.setStagedPhoto("content://photo", "token")
        
        viewModel.continueWithoutPhoto(propertyId)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.outcome is PropertySetupOutcome.PropertyCreated)
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
        coVerify { attachmentRepo.deleteTempCameraCapture("token") }
    }

    @Test
    fun `GPS failure PermissionDenied shows appropriate issue`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.PermissionDenied
        
        viewModel.requestGpsLocation()
        advanceUntilIdle()
        
        val issue = viewModel.uiState.value.locationIssue
        assertNotNull(issue)
        assertEquals(LocationIssueType.PermissionDenied, issue?.type)
    }

    @Test
    fun `GPS failure ProviderDisabled shows appropriate issue`() = runTest {
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.ProviderDisabled
        
        viewModel.requestGpsLocation()
        advanceUntilIdle()
        
        val issue = viewModel.uiState.value.locationIssue
        assertNotNull(issue)
        assertEquals(LocationIssueType.ProviderDisabled, issue?.type)
        assertTrue(issue?.canOpenLocationSettings == true)
    }

    @Test
    fun `camera result Ready stages photo regardless of success boolean`() = runTest {
        viewModel.setInFlightCapture("content://photo", "token")
        coEvery { attachmentRepo.inspectTempCameraCapture("token", any()) } returns TempCameraCaptureInspectionResult.Ready
        
        viewModel.handleCameraResult(false) // OEM false success
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.Ready)
    }

    @Test
    fun `camera result Missing with success false clears state`() = runTest {
        viewModel.setInFlightCapture("content://photo", "token")
        coEvery { attachmentRepo.inspectTempCameraCapture("token", any()) } returns TempCameraCaptureInspectionResult.Missing
        
        viewModel.handleCameraResult(false)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
    }

    @Test
    fun `transient denial sets canRetry to true`() = runTest {
        viewModel.handleTransientDenial()
        advanceUntilIdle()
        
        val issue = viewModel.uiState.value.locationIssue
        assertNotNull(issue)
        assertEquals(LocationIssueType.PermissionDenied, issue?.type)
        assertTrue(issue?.canRetry == true)
    }

    @Test
    fun `permanent denial shows Open Settings`() = runTest {
        viewModel.handlePermanentDenial()
        advanceUntilIdle()
        
        val issue = viewModel.uiState.value.locationIssue
        assertNotNull(issue)
        assertEquals(LocationIssueType.PermissionPermanentlyDenied, issue?.type)
        assertTrue(issue?.canOpenAppSettings == true)
    }

    @Test
    fun `GPS success clears pending request state`() = runTest {
        viewModel.setPendingLocationPurpose(LocationRequestPurpose.LocateOnly)
        viewModel.setLocationPermissionLaunchInProgress(true)
        
        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Success(
            45.0, -90.0, 10f, System.currentTimeMillis(), 
            LocationResult.Success.Source.Fresh, true
        )
        
        viewModel.requestGpsLocation()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNull(state.pendingLocationPurpose)
        assertFalse(state.locationPermissionLaunchInProgress)
    }

    @Test
    fun `cancelLocationIssue clears pending request state`() = runTest {
        viewModel.setPendingLocationPurpose(LocationRequestPurpose.LocateOnly)
        viewModel.setLocationPermissionLaunchInProgress(true)
        
        viewModel.cancelLocationIssue()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNull(state.pendingLocationPurpose)
        assertFalse(state.locationPermissionLaunchInProgress)
    }

    @Test
    fun `handleTransientDenial clears pending request state and allows retry`() = runTest {
        viewModel.setPendingLocationPurpose(LocationRequestPurpose.LocateOnly)
        viewModel.setLocationPermissionLaunchInProgress(true)
        
        viewModel.handleTransientDenial()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNull("Logical purpose should be cleared on terminal denial state for launcher state cleanup", state.pendingLocationPurpose)
        assertFalse(state.locationPermissionLaunchInProgress)
        assertTrue(state.locationIssue?.canRetry == true)
    }
}
