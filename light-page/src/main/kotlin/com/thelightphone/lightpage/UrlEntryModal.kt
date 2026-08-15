package com.thelightphone.lightpage

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * URL entry modal backed by the LP3 keyboard.
 *
 * Spec M4.2–M4.4 call for an in-screen modal with a fixed shortcut row
 * (`https://`, `.com`, `/`) and a direct `Lp3KeyboardWrapper` embedding. The
 * SDK only exposes the wrapper through
 * [com.thelightphone.sdk.ui.keyboard.LightEmbeddedLp3Keyboard], which requires
 * an internal `Lp3KeyboardViewModel` and a `viewModel()` Compose dependency not
 * declared in the `:light-page` module. Per M4.6's fallback allowance, this
 * composable uses [LightTextInputEditor], the SDK's public full-screen text
 * editor with the embedded LP3 keyboard, and maps the editor's back affordance
 * to the modal dismiss action.
 *
 * Deviation recorded: fixed shortcut row is not present; the full-screen editor
 * replaces the in-screen modal. Functional requirements (normalize, validate,
 * submit, cancel) are preserved.
 */
@Composable
fun UrlEntryModal(
    current: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state: TextFieldState = rememberTextFieldState(current)

    LightTextInputEditor(
        title = "Enter URL",
        state = state,
        onSubmit = { onSubmit(state.text.toString()) },
        onBack = onDismiss,
        keyboardOptionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) },
        singleLine = true,
        submitLabel = "LOAD"
    )
}
