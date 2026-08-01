package com.jumastappworks.mapstead.ui.components

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

enum class TextFieldSemanticType {
    PROPER_NAME,
    ADDRESS,
    PROSE,
    EMAIL,
    URL,
    IDENTIFIER,
    NUMERIC,
    COORDINATE,
    POSTAL_CODE,
    MULTILINE_PROSE,
    SEARCH
}

object KeyboardPolicy {
    fun getOptions(
        type: TextFieldSemanticType,
        imeAction: ImeAction? = null
    ): KeyboardOptions {
        val capitalization = when (type) {
            TextFieldSemanticType.PROPER_NAME,
            TextFieldSemanticType.ADDRESS -> KeyboardCapitalization.Words
            TextFieldSemanticType.PROSE,
            TextFieldSemanticType.MULTILINE_PROSE -> KeyboardCapitalization.Sentences
            else -> KeyboardCapitalization.None
        }
        
        val keyboardType = when (type) {
            TextFieldSemanticType.EMAIL -> KeyboardType.Email
            TextFieldSemanticType.URL -> KeyboardType.Uri
            TextFieldSemanticType.NUMERIC -> KeyboardType.Number
            TextFieldSemanticType.COORDINATE -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }

        val finalImeAction = when {
            imeAction != null -> imeAction
            type == TextFieldSemanticType.MULTILINE_PROSE -> ImeAction.Default
            type == TextFieldSemanticType.SEARCH -> ImeAction.Search
            else -> ImeAction.Next
        }
        
        return KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = finalImeAction
        )
    }

    fun getActions(
        focusManager: FocusManager,
        onDone: (() -> Unit)? = null,
        onSearch: (() -> Unit)? = null
    ): KeyboardActions {
        return KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = {
                if (onDone != null) onDone()
                else focusManager.clearFocus()
            },
            onSearch = {
                if (onSearch != null) onSearch()
                else if (onDone != null) onDone()
                else focusManager.clearFocus()
            }
        )
    }
}
