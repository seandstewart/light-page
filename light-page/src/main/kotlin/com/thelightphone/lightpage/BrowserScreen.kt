package com.thelightphone.lightpage

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

@InitialScreen
class BrowserScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, BrowserViewModel>(sealedActivity) {

    override val viewModelClass: Class<BrowserViewModel>
        get() = BrowserViewModel::class.java

    override fun createViewModel(): BrowserViewModel = BrowserViewModel(
        preferences = ReaderPreferences(lightContext.dataStore)
    )

    private var webView: WebView? = null

    override fun goBack(result: Unit?) {
        val state = viewModel.uiState.value
        if (state.menuVisible || state.urlDrawerVisible || state.urlEditorVisible || state.webInputEditor != null) {
            if (state.webInputEditor != null) {
                webView?.evaluateJavascript("window.__lightCancelInput()", null)
            }
            viewModel.showMenu(false)
            viewModel.showUrlDrawer(false)
            viewModel.closeUrlEditor()
            viewModel.closeWebInput()
            return
        }
        val wv = webView
        if (wv?.canGoBack() == true) {
            wv.goBack()
        } else {
            super.goBack(result)
        }
    }

    override fun willShow() {
        super.willShow()
        webView?.onResume()
    }

    override fun onAppPause() {
        super.onAppPause()
        webView?.onPause()
    }

    override fun onScreenDestroy() {
        webView?.destroy()
        webView = null
        super.onScreenDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        var lightClient by remember { mutableStateOf<LightWebViewClient?>(null) }

        LaunchedEffect(state.pageTheme) {
            if (state.pageTheme == PageTheme.LIGHT) LightThemeController.setLightTheme()
            else LightThemeController.setDarkTheme()
        }

        var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val urlPreview = trimUrlPreview(state.committedUrl ?: state.requestedUrl)

                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Exit"
                        ),
                        center = LightTopBarCenter.Text(urlPreview),
                        rightButton = LightBarButton.LightIcon(
                            icon = LightIcons.CIRCLE,
                            onClick = { viewModel.showMenu(true) },
                            contentDescription = "Menu"
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        setSupportMultipleWindows(false)
                                        javaScriptCanOpenWindowsAutomatically = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                    }
                                    setBackgroundColor(android.graphics.Color.WHITE)
                                    webChromeClient = WebChromeClient()
                                    addJavascriptInterface(
                                        ReaderStateBridge { applied ->
                                            viewModel.onWebState(WebStateUpdate(readerApplied = applied))
                                        },
                                        "__lightReaderBridge"
                                    )
                                    addJavascriptInterface(
                                        InputBridge { value, label ->
                                            viewModel.openWebInput(value, label)
                                        },
                                        "__lightInputBridge"
                                    )
                                    val client = LightWebViewClient(
                                        injection = PageInjector(context.assets),
                                        onState = viewModel::onWebState
                                    )
                                    lightClient = client
                                    webViewClient = client
                                    loadUrl(state.requestedUrl)
                                    lastLoadedUrl = state.requestedUrl
                                    webView = this
                                }
                            },
                            update = { wv ->
                                if (state.requestedUrl != lastLoadedUrl && state.requestedUrl.isNotBlank()) {
                                    wv.loadUrl(state.requestedUrl)
                                    lastLoadedUrl = state.requestedUrl
                                }
                            }
                        )

                        LaunchedEffect(webView, state) {
                            webView?.let { lightClient?.refreshState(it, state) }
                        }

                        state.error?.let { error ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(LightThemeTokens.colors.background)
                                    .padding(2f.gridUnitsAsDp())
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) awaitPointerEvent()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                LightText(
                                    text = error.message(),
                                    variant = LightTextVariant.Copy
                                )
                            }
                        }
                    }

                    BottomNavBar(
                        onBack = { goBack() },
                        onForward = { webView?.takeIf { it.canGoForward() }?.goForward() },
                        onReload = { webView?.reload() },
                        canGoForward = state.canGoForward
                    )
                }

                if (state.menuVisible) {
                    MenuDrawer(
                        state = state,
                        onClose = { viewModel.showMenu(false) },
                        onToggleReader = { viewModel.toggleReader() },
                        onToggleCss = { viewModel.toggleCssInjection() },
                        onToggleTheme = { viewModel.setPageTheme(if (state.pageTheme == PageTheme.DARK) PageTheme.LIGHT else PageTheme.DARK) },
                        onOpenUrlDrawer = { viewModel.showUrlDrawer(true) }
                    )
                }

                if (state.urlDrawerVisible) {
                    UrlDrawer(
                        state = state,
                        onClose = { viewModel.showUrlDrawer(false) },
                        onLoadUrl = { viewModel.submitUrl(it) },
                        onEditUrl = { viewModel.showUrlEditor(UrlEditorMode.Edit(it)) },
                        onAddUrl = { viewModel.showUrlEditor(UrlEditorMode.Add) },
                        onRemoveUrl = { viewModel.removeUrl(it) }
                    )
                }

                if (state.urlEditorVisible) {
                    val currentMode = state.urlEditorMode
                    UrlEditorOverlay(
                        mode = currentMode,
                        initialValue = state.urlEditorInitialValue,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        onSubmit = { url ->
                            when (currentMode) {
                                is UrlEditorMode.Add -> viewModel.submitUrl(url)
                                is UrlEditorMode.Edit -> viewModel.editUrl(currentMode.index, url)
                            }
                        },
                        onBack = { viewModel.closeUrlEditor() }
                    )
                }

                state.webInputEditor?.let { editor ->
                    WebInputEditorOverlay(
                        editor = editor,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        webView = webView,
                        onClose = { viewModel.closeWebInput() }
                    )
                }
            }
        }
    }
}

/**
 * JavaScript bridge that lets the injected page-hooks report whether the reader
 * transformation is currently applied. The WebView instance never enters the
 * ViewModel; commands flow one-way through the Compose layer.
 */
private class ReaderStateBridge(
    private val onReaderApplied: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun onReaderApplied(applied: Boolean) {
        onReaderApplied(applied)
    }
}

/**
 * JavaScript bridge that forwards focused text input values from the page to
 * the full-screen editor overlay.
 */
private class InputBridge(
    private val onFocus: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onFocus(value: String, label: String) {
        onFocus(value, label)
    }
}

private fun trimUrlPreview(url: String): String =
    url.removePrefix("https://").removePrefix("http://")

@Composable
private fun BottomNavBar(
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    canGoForward: Boolean
) {
    LightBottomBar(
        topPadding = 0.dp,
        items = listOf(
            LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back"
            ),
            LightBarButton.LightIcon(
                icon = LightIcons.REFRESH,
                onClick = onReload,
                contentDescription = "Refresh"
            ),
            LightBarButton.LightIcon(
                icon = LightIcons.ARROW_RIGHT,
                onClick = if (canGoForward) onForward else null,
                contentDescription = "Forward"
            )
        )
    )
}

@Composable
private fun MenuDrawer(
    state: BrowserUiState,
    onClose: () -> Unit,
    onToggleReader: () -> Unit,
    onToggleCss: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenUrlDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.CLOSE,
                onClick = onClose,
                contentDescription = "Close"
            ),
            center = LightTopBarCenter.Text("Menu")
        )
        MenuRow(
            label = "Reader Mode",
            subtitle = if (state.readerRequested) "On" else "Off",
            icon = if (state.readerRequested) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
            onClick = onToggleReader
        )
        MenuRow(
            label = "CSS Injection",
            subtitle = if (state.cssInjectionEnabled) "On" else "Off",
            icon = if (state.cssInjectionEnabled) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
            onClick = onToggleCss
        )
        MenuRow(
            label = "Theme",
            subtitle = if (state.pageTheme == PageTheme.LIGHT) "Light mode" else "Dark mode",
            icon = if (state.pageTheme == PageTheme.LIGHT) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
            onClick = onToggleTheme
        )
        MenuRow(
            label = "URL Drawer",
            onClick = onOpenUrlDrawer,
            showArrow = true
        )
    }
}

@Composable
private fun MenuRow(
    label: String,
    subtitle: String = "",
    icon: LightIconConfiguration? = null,
    onClick: () -> Unit,
    showArrow: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = label, variant = LightTextVariant.Copy)
            if (subtitle.isNotEmpty()) {
                LightText(
                    text = subtitle,
                    variant = LightTextVariant.Detail,
                    lighten = true
                )
            }
        }
        if (icon != null) {
            LightIcon(
                icon = icon,
                contentDescription = null
            )
        }
        if (showArrow) {
            LightIcon(
                icon = LightIcons.ARROW_RIGHT,
                contentDescription = null
            )
        }
    }
}

private data class PendingDelete(val index: Int, val url: String)

@Composable
private fun UrlDrawer(
    state: BrowserUiState,
    onClose: () -> Unit,
    onLoadUrl: (String) -> Unit,
    onEditUrl: (Int) -> Unit,
    onAddUrl: () -> Unit,
    onRemoveUrl: (Int) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onClose,
                contentDescription = "Back"
            ),
            center = LightTopBarCenter.Text("URLs")
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(
                items = state.recentUrls,
                key = { _, url -> url }
            ) { index, url ->
                SwipeUrlRow(
                    url = url,
                    onClick = { onLoadUrl(url) },
                    onEdit = { onEditUrl(index) },
                    onDelete = { pendingDelete = PendingDelete(index, url) }
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomEnd
        ) {
            LightIcon(
                icon = LightIcons.ADD,
                modifier = Modifier
                    .lightClickable(onClick = onAddUrl)
                    .padding(2f.gridUnitsAsDp()),
                contentDescription = "Add URL"
            )
        }
    }

    pendingDelete?.let { pending ->
        if (state.recentUrls.getOrNull(pending.index) == pending.url) {
            DeleteConfirmDialog(
                url = pending.url,
                onConfirm = {
                    onRemoveUrl(pending.index)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null }
            )
        } else {
            pendingDelete = null
        }
    }
}

@Composable
private fun SwipeUrlRow(
    url: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val offsetState: MutableFloatState = remember(url) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = offsetState.floatValue)
    val density = LocalDensity.current
    val deleteThreshold = 40f
    val snapThreshold = 80f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5f.gridUnitsAsDp())
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(LightThemeTokens.colors.content)
                .padding(start = 2f.gridUnitsAsDp()),
            contentAlignment = Alignment.CenterStart
        ) {
            if (animatedOffset > deleteThreshold) {
                Icon(
                    painter = painterResource(LightIcons.DELETE.drawableResource),
                    contentDescription = "Delete",
                    tint = LightThemeTokens.colors.background,
                    modifier = Modifier
                        .size(2f.gridUnitsAsDp())
                        .lightClickable { onDelete() }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightThemeTokens.colors.background)
                .offset {
                    with(density) {
                        IntOffset(animatedOffset.dp.roundToPx(), 0)
                    }
                }
                .pointerInput(url) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetState.floatValue < snapThreshold) offsetState.floatValue = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetState.floatValue =
                            (offsetState.floatValue + dragAmount / density.density).coerceAtLeast(0f)
                    }
                }
                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LightText(
                text = url,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .lightClickable(onClick = onClick)
            )
            LightIcon(
                icon = LightIcons.PENCIL,
                modifier = Modifier.lightClickable { onEdit() },
                contentDescription = "Edit"
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    url: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(2f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LightText(
                text = "Delete $url?",
                variant = LightTextVariant.Copy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
            LightBottomBar(
                items = listOf(
                    LightBarButton.Text("Cancel", onClick = onDismiss),
                    LightBarButton.Text("Delete", onClick = onConfirm)
                )
            )
        }
    }
}

@Composable
private fun UrlEditorOverlay(
    mode: UrlEditorMode,
    initialValue: String,
    keyboardOptionsFlow: StateFlow<KeyboardOptions>,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    val textState = rememberTextFieldState(initialValue)
    LaunchedEffect(initialValue) {
        textState.edit { replace(0, length, initialValue) }
    }
    val title = when (mode) {
        is UrlEditorMode.Add -> "Add URL"
        is UrlEditorMode.Edit -> "Edit URL"
    }
    LightTextInputEditor(
        title = title,
        state = textState,
        keyboardOptionsFlow = keyboardOptionsFlow,
        onSubmit = { onSubmit(it.toString()) },
        onBack = onBack,
        submitLabel = "SAVE",
        singleLine = true
    )
}

@Composable
private fun WebInputEditorOverlay(
    editor: WebInputEditorState,
    keyboardOptionsFlow: StateFlow<KeyboardOptions>,
    webView: WebView?,
    onClose: () -> Unit
) {
    val textState = rememberTextFieldState(editor.value)
    LaunchedEffect(editor.value) {
        textState.edit { replace(0, length, editor.value) }
    }
    LightTextInputEditor(
        title = editor.label,
        state = textState,
        keyboardOptionsFlow = keyboardOptionsFlow,
        onSubmit = { value ->
            webView?.evaluateJavascript(
                "window.__lightSetInputValue(${JSONObject.quote(value.toString())})",
                null
            )
            onClose()
        },
        onBack = {
            webView?.evaluateJavascript("window.__lightCancelInput()", null)
            onClose()
        },
        submitLabel = "DONE"
    )
}
