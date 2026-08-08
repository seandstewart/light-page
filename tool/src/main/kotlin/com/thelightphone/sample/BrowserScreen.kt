package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class BrowserScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, BrowserViewModel>(sealedActivity) {

    override val viewModelClass: Class<BrowserViewModel>
        get() = BrowserViewModel::class.java

    override fun createViewModel(): BrowserViewModel {
        return BrowserViewModel()
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(24.dp)
            ) {
                LightText(
                    text = "Light Reader",
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LightText(
                    text = state.status,
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                LightText(
                    text = "Toggle theme",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.lightClickable { LightThemeController.toggle() },
                )
            }
        }
    }
}
