package com.jumastappworks.mapstead.data.help

import org.junit.Assert.*
import org.junit.Test

class GettingStartedStepBuilderTest {

    @Test
    fun `checklist contains exactly seven unique ordered steps`() {
        val progress = GettingStartedProgress(false, false, false, false, false, false, false)
        val steps = GettingStartedStepBuilder.buildSteps(progress, propertySelected = false)
        
        assertEquals(7, steps.size)
        val expectedIds = GettingStartedStepId.entries
        steps.forEachIndexed { index, step ->
            assertEquals(expectedIds[index], step.stepId)
        }
    }

    @Test
    fun `no properties enables only Create Property`() {
        val progress = GettingStartedProgress(hasProperty = false, false, false, false, false, false, false)
        val steps = GettingStartedStepBuilder.buildSteps(progress, propertySelected = false)
        
        val createPropStep = steps.find { it.stepId == GettingStartedStepId.CREATE_PROPERTY }
        val createMapStep = steps.find { it.stepId == GettingStartedStepId.CREATE_MAP }
        
        assertTrue(createPropStep!!.isEnabled)
        assertFalse(createMapStep!!.isEnabled)
    }

    @Test
    fun `property selected enables dependent steps`() {
        val progress = GettingStartedProgress(hasProperty = true, hasMap = true, false, false, false, false, false)
        val steps = GettingStartedStepBuilder.buildSteps(progress, propertySelected = true)
        
        val createMapStep = steps.find { it.stepId == GettingStartedStepId.CREATE_MAP }
        val addFeatureStep = steps.find { it.stepId == GettingStartedStepId.ADD_FEATURE }
        
        assertTrue(createMapStep!!.isEnabled)
        assertTrue(addFeatureStep!!.isEnabled)
    }

    @Test
    fun `no property selected disables property-specific steps`() {
        val progress = GettingStartedProgress(hasProperty = true, hasMap = true, hasMappedItem = true, hasInfrastructure = true, hasMaintenance = true, hasAttachment = true, emergencyReviewed = true)
        val steps = GettingStartedStepBuilder.buildSteps(progress, propertySelected = false)
        
        // CREATE_PROPERTY is always enabled if needed (or even if completed)
        assertTrue(steps.find { it.stepId == GettingStartedStepId.CREATE_PROPERTY }!!.isEnabled)
        
        // All others should be disabled if no property is selected for context
        val others = steps.filter { it.stepId != GettingStartedStepId.CREATE_PROPERTY }
        others.forEach { step ->
            assertFalse("Step ${step.stepId} should be disabled", step.isEnabled)
        }
    }
}
