package com.jumastappworks.mapstead.ui.components

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.*
import org.junit.Test

class KeyboardPolicyTest {

    @Test
    fun `keyboard options for different semantic types`() {
        // Proper Name -> Words, Next
        val nameOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME)
        assertEquals(KeyboardCapitalization.Words, nameOptions.capitalization)
        assertEquals(ImeAction.Next, nameOptions.imeAction)
        assertEquals(KeyboardType.Text, nameOptions.keyboardType)

        // Coordinate -> None, Decimal, Next
        val coordOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE)
        assertEquals(KeyboardCapitalization.None, coordOptions.capitalization)
        assertEquals(ImeAction.Next, coordOptions.imeAction)
        assertEquals(KeyboardType.Decimal, coordOptions.keyboardType)

        // Multiline Prose -> Sentences, Default
        val proseOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE)
        assertEquals(KeyboardCapitalization.Sentences, proseOptions.capitalization)
        assertEquals(ImeAction.Default, proseOptions.imeAction)

        // Search -> None, Search
        val searchOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.SEARCH)
        assertEquals(ImeAction.Search, searchOptions.imeAction)

        // Postal Code -> None, Number (or text?), Next
        // Current impl maps Coordinate to Decimal, Numeric to Number, others to Text.
        // Let's check Numeric.
        val numOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.NUMERIC)
        assertEquals(KeyboardType.Number, numOptions.keyboardType)
    }

    @Test
    fun `explicit final action overrides default next`() {
        val finalCoordOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE, imeAction = ImeAction.Done)
        assertEquals(ImeAction.Done, finalCoordOptions.imeAction)
    }

    @Test
    fun `address semantic uses word capitalization`() {
        val addrOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS)
        assertEquals(KeyboardCapitalization.Words, addrOptions.capitalization)
    }
}
