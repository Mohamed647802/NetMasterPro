package com.netmaster.pro.presentation.webview

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController

private const val DARK_MODE_CSS = """
    html, body, * {
        background-color: #0D1117 !important;
        color: #E0E0E0 !important;
        border-color: #1A2332 !important;
    }
    a { color: #00FFCC !important; }
    input, select, textarea, button {
        background-color: #1A2332 !important;
        color: #E0E0E0 !important;
        border: 1px solid #00FFCC44 !important;
    }
"""

private const val DARK_MODE_JS = """
    (function() {
        var style = document.createElement('style');
        style.id = 'netmaster-dark-mode';
        style.textContent = `$DARK_MODE_CSS`;
        document.head.appendChild(style);
    })();
"""

private const val REMOVE_DARK_MODE_JS = """
    (function() {
        var el = document.getElementById('netmaster-dark-mode');
        if (el) el.remove();
    })();
"""

private const val MOBILE_VIEWPORT_JS = """
    (function() {
        var meta = document.querySelector('meta[name=viewport]');
        if (!meta) {
            meta = document.createElement('meta');
            meta.name = 'viewport';
            document.head.appendChild(meta);
        }
        meta.content = 'width=device-width, initial-scale=1, maximum-scale=5';
    })();
"""

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterWebViewScreen(
    navController: NavController? = null,
    routerUrl: String = "http://192.168.1.1",
    onBack: (() -> Unit)? = null
) {
    var currentUrl  by remember { mutableStateOf(routerUrl) }
    var pageTitle   by remember { mutableStateOf("لوحة الراوتر") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isDarkMode  by remember { mutableStateOf(false) }
    var webViewRef  by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text     = pageTitle,
                            style    = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text     = currentUrl,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() ?: navController?.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isDarkMode = !isDarkMode
                    webViewRef?.evaluateJavascript(
                        if (isDarkMode) DARK_MODE_JS else REMOVE_DARK_MODE_JS, null
                    )
                },
                containerColor = if (isDarkMode) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = "وضع داكن",
                    modifier = Modifier.size(20.dp),
                    tint = if (isDarkMode) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // ── Loading progress ──────────────────────────────────────────
            if (loadProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── WebView ───────────────────────────────────────────────────
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory  = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled        = true
                            domStorageEnabled         = true
                            allowFileAccess           = false
                            databaseEnabled           = true
                            setSupportZoom(true)
                            builtInZoomControls       = true
                            displayZoomControls       = false
                            useWideViewPort           = true
                            loadWithOverviewMode      = true
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress == 100) {
                                    view?.evaluateJavascript(MOBILE_VIEWPORT_JS, null)
                                    if (isDarkMode) {
                                        view?.evaluateJavascript(DARK_MODE_JS, null)
                                    }
                                }
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title ?: "لوحة الراوتر"
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?
                            ): Boolean {
                                request?.url?.toString()?.let { currentUrl = it }
                                return false
                            }
                        }
                        loadUrl(routerUrl)
                        webViewRef = this
                    }
                }
            )

            // ── Bottom navigation bar ─────────────────────────────────────
            Surface(
                tonalElevation = 3.dp,
                modifier       = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webViewRef?.goBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "السابق")
                    }
                    IconButton(onClick = { webViewRef?.goForward() }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "التالي")
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة تحميل")
                    }
                    IconButton(onClick = {
                        webViewRef?.loadUrl(routerUrl)
                        currentUrl = routerUrl
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "الرئيسية")
                    }
                    Text(
                        text     = currentUrl,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
