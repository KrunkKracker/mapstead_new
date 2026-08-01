package com.jumastappworks.mapstead.ui.settings

import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.data.help.SettingsSectionId
import com.jumastappworks.mapstead.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun `settings sections are correctly ordered in production model`() {
        val expectedOrder = listOf(
            SettingsSectionId.APPEARANCE,
            SettingsSectionId.MEASUREMENTS,
            SettingsSectionId.MAP_LOCATION,
            SettingsSectionId.GUIDANCE_HELP,
            SettingsSectionId.DATA_BACKUP,
            SettingsSectionId.ABOUT_SAFETY
        )
        // Verify the production enum has the correct items in order
        assertEquals(expectedOrder, SettingsSectionId.entries)
    }

    @Test
    fun `help topic routes resolve correctly`() {
        val route = Route.HelpTopic(HelpTopicId.GPS_AND_ACCURACY)
        assertEquals(HelpTopicId.GPS_AND_ACCURACY, route.topicId)
    }
}
