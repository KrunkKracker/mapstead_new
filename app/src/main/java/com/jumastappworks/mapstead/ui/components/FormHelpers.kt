package com.jumastappworks.mapstead.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class FormFieldVisibilityController(
    val requester: BringIntoViewRequester
) {
    var isFocused by mutableStateOf(false)

    fun bringIntoView(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            try {
                requester.bringIntoView()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // Ignore if not attached or other relocation errors
            }
        }
    }
}

@Composable
fun rememberFormFieldVisibilityController(): FormFieldVisibilityController {
    val requester = remember { BringIntoViewRequester() }
    return remember(requester) { FormFieldVisibilityController(requester) }
}

fun Modifier.bringIntoViewOnFocus(controller: FormFieldVisibilityController): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Re-trigger when IME height changes while focused
    LaunchedEffect(imeBottom, controller.isFocused) {
        if (controller.isFocused && imeBottom > 0) {
            // Viewport should have updated by now in the same frame or next
            controller.bringIntoView(scope)
        }
    }

    this.bringIntoViewRequester(controller.requester)
        .onFocusEvent {
            controller.isFocused = it.isFocused || it.hasFocus
            if (controller.isFocused) {
                controller.bringIntoView(scope)
            }
        }
}
