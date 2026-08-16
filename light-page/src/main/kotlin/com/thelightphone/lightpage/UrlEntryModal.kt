package com.thelightphone.lightpage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.viewmodel.EnQwertyLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import com.thelightphone.sdk.ui.keyboard.LightEmbeddedLp3Keyboard
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * URL entry modal backed by the embedded LP3 keyboard.
 *
 * Provides a native Compose text field, a fixed shortcut row (`https://`,
 * `.com`, `/`), and the SDK's `LightEmbeddedLp3Keyboard` so input goes through
 * the LP3 keyboard path.
 */
@Composable
fun UrlEntryModal(
    current: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state: TextFieldState = rememberTextFieldState(current)
    val keyboardOptionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) }
    val callback = remember(state) {
        UrlKeyboardCallback(
            state = state,
            onReturn = { onSubmit(state.text.toString()) }
        )
    }
    val keyboardViewModel: Lp3KeyboardViewModel<*> = viewModel<EnQwertyLp3KeyboardViewModel<*>>(
        key = "UrlEntryModal-keyboard",
        factory = factory(callback, keyboardOptionsFlow, initialCaps = false)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        LightText(
            text = state.text.toString(),
            variant = LightTextVariant.Copy,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            ShortcutChip("https://") {
                state.insertAtCursor("https://")
            }
            ShortcutChip(".com") {
                state.insertAtCursor(".com")
            }
            ShortcutChip("/") {
                state.insertAtCursor("/")
            }
        }

        LightEmbeddedLp3Keyboard(
            viewModel = keyboardViewModel,
            bottomBar = {
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text("Cancel", onClick = onDismiss),
                        LightBarButton.Text("Load") {
                            onSubmit(state.text.toString())
                        }
                    )
                )
            }
        )
    }
}

@Composable
private fun ShortcutChip(text: String, onClick: () -> Unit) {
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        modifier = Modifier
            .lightClickable(onClick = onClick)
            .padding(end = 12.dp)
    )
}

private fun TextFieldState.insertAtCursor(text: String) {
    edit {
        val start = selection.min
        val end = selection.max
        replace(start, end, text)
        selection = TextRange(start + text.length)
    }
}

private fun factory(
    callback: com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback,
    keyboardOptionsFlow: StateFlow<KeyboardOptions>,
    initialCaps: Boolean,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EnQwertyLp3KeyboardViewModel<Unit>(
            callback,
            keyboardOptionsFlow = keyboardOptionsFlow,
            optionsForLayout = {
                val showCloseButton = !it.isRootLayout
                LayoutOptions(showCloseButton)
            },
        ).apply {
            if (initialCaps) setCapsMode(true)
        } as T
    }
}
