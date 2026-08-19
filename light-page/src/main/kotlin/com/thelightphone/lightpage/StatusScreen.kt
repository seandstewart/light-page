package com.thelightphone.lightpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@Composable
fun StatusOverlay(state: BrowserUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background.copy(alpha = 0.85f))
            .padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightThemeTokens.colors.background)
                .padding(2f.gridUnitsAsDp())
        ) {
            StatusRow(label = "URL", value = state.committedUrl ?: state.requestedUrl)
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            StatusRow(label = "Loading", value = state.loading.toString())
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            StatusRow(label = "Theme", value = state.pageTheme.name)
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            StatusRow(label = "Reader requested", value = state.readerRequested.toString())
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            StatusRow(label = "Reader applied", value = state.readerApplied.toString())
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            StatusRow(label = "CSS injection enabled", value = state.cssInjectionEnabled.toString())
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            StatusRow(label = "Error", value = state.error?.message() ?: "None")
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LightText(
            text = label,
            variant = LightTextVariant.Detail,
            color = LightThemeTokens.colors.content,
            lighten = true
        )
        LightText(
            text = value,
            variant = LightTextVariant.Copy,
            color = LightThemeTokens.colors.content,
            maxLines = 3
        )
    }
}

@Composable
fun EmptyStatePrompt(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .lightClickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        LightText(
            text = "Tap to open drawer",
            variant = LightTextVariant.Copy,
            color = LightThemeTokens.colors.content
        )
    }
}
