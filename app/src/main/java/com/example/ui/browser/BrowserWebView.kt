package com.example.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.browser.DetectedVideo
import com.example.data.browser.UrlStreamClassifier
import com.example.data.browser.VideoDetectionScripts

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class BrowserWebController {
    internal var webView: WebView? = null

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun rescanVideos() {
        webView?.evaluateJavascript(
            "if (window.__flowScanVideos) { window.__flowScanVideos(); }",
            null
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tabId: String,
    initialUrl: String,
    pendingLoadUrl: String?,
    navigationNonce: Long,
    desktopMode: Boolean,
    savedBundle: Bundle?,
    controller: BrowserWebController,
    onConsumedPendingLoadUrl: () -> Unit,
    onSaveBundle: (String, Bundle) -> Unit,
    onPageStarted: (tabId: String, url: String) -> Unit,
    onPageFinished: (tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageProgress: (tabId: String, progress: Int, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onJsVideoEvent: (tabId: String, json: String, cookies: String, userAgent: String) -> Unit,
    onNetworkVideoDetected: (tabId: String, video: DetectedVideo) -> Unit,
    onDirectVideoDownloadRequested: (DetectedVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestTabId by rememberUpdatedState(tabId)
    val latestOnPageStarted by rememberUpdatedState(onPageStarted)
    val latestOnPageFinished by rememberUpdatedState(onPageFinished)
    val latestOnPageProgress by rememberUpdatedState(onPageProgress)
    val latestOnJsVideo by rememberUpdatedState(onJsVideoEvent)
    val latestOnNetworkVideo by rememberUpdatedState(onNetworkVideoDetected)
    val latestOnDirectDownload by rememberUpdatedState(onDirectVideoDownloadRequested)
    val latestOnSaveBundle by rememberUpdatedState(onSaveBundle)

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var defaultUserAgent by remember { mutableStateOf("") }

    DisposableEffect(tabId) {
        onDispose {
            controller.webView?.let { wv ->
                val bundle = Bundle()
                wv.saveState(bundle)
                latestOnSaveBundle(tabId, bundle)
                wv.stopLoading()
                wv.destroy()
            }
            controller.webView = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    defaultUserAgent = settings.userAgentString
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.allowFileAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else defaultUserAgent

                    val currentWebViewRef = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(currentWebViewRef, true)
                    }

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onVideoDetected(json: String) {
                                val currentWebView = this@apply
                                currentWebView.post {
                                    val pageUrl = currentWebView.url.orEmpty()
                                    val cookies = runCatching {
                                        CookieManager.getInstance().getCookie(pageUrl).orEmpty()
                                    }.getOrDefault("")
                                    val ua = currentWebView.settings.userAgentString.orEmpty()
                                    latestOnJsVideo(latestTabId, json, cookies, ua)
                                }
                            }
                        },
                        "FlowVideoBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            latestOnPageStarted(latestTabId, url)
                            view.evaluateJavascript(VideoDetectionScripts.VIDEO_SNIFFER_JS, null)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(VideoDetectionScripts.VIDEO_SNIFFER_JS, null)
                            latestOnPageFinished(
                                latestTabId,
                                url,
                                view.title,
                                view.canGoBack(),
                                view.canGoForward()
                            )
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val reqUrl = request.url?.toString().orEmpty()
                            return !(reqUrl.startsWith("http://", ignoreCase = true) ||
                                reqUrl.startsWith("https://", ignoreCase = true))
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val reqUrl = request.url?.toString().orEmpty()
                            val classified = UrlStreamClassifier.classifyVideoUrl(reqUrl)
                            if (classified != null) {
                                val ua = request.requestHeaders["User-Agent"]
                                    ?: (if (desktopMode) DESKTOP_USER_AGENT else defaultUserAgent)
                                val referer = request.requestHeaders["Referer"].orEmpty()
                                view.post {
                                    val pageUrl = view.url.orEmpty().ifBlank { referer }
                                    val pageTitle = view.title?.takeIf { it.isNotBlank() }
                                        ?: UrlStreamClassifier.extractDomain(pageUrl)
                                    val cookies = runCatching {
                                        CookieManager.getInstance().getCookie(reqUrl)
                                            ?: CookieManager.getInstance().getCookie(pageUrl)
                                            ?: ""
                                    }.getOrDefault("")
                                    latestOnNetworkVideo(
                                        latestTabId,
                                        DetectedVideo(
                                            id = reqUrl.hashCode().toString(),
                                            url = reqUrl,
                                            pageUrl = pageUrl,
                                            title = pageTitle,
                                            mimeType = classified.mimeType,
                                            isPlaying = false,
                                            isHls = classified.isHls,
                                            isDash = classified.isDash,
                                            cookies = cookies,
                                            userAgent = ua,
                                            referer = referer.ifBlank { pageUrl }
                                        )
                                    )
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            latestOnPageProgress(
                                latestTabId,
                                newProgress,
                                view.title,
                                view.canGoBack(),
                                view.canGoForward()
                            )
                            if (newProgress in 45..95) {
                                view.evaluateJavascript(VideoDetectionScripts.VIDEO_SNIFFER_JS, null)
                            }
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                            customViewCallback = null
                        }
                    }

                    setDownloadListener { url, userAgent, _, mimetype, contentLength ->
                        val classified = UrlStreamClassifier.classifyVideoUrl(url)
                        if (classified != null || mimetype.startsWith("video/", ignoreCase = true)) {
                            val pageUrl = this.url.orEmpty().ifBlank { url }
                            val cookies = runCatching {
                                CookieManager.getInstance().getCookie(url)
                                    ?: CookieManager.getInstance().getCookie(pageUrl)
                                    ?: ""
                            }.getOrDefault("")
                            val detected = DetectedVideo(
                                id = url.hashCode().toString(),
                                url = url,
                                pageUrl = pageUrl,
                                title = title?.takeIf { it.isNotBlank() } ?: UrlStreamClassifier.extractDomain(url),
                                mimeType = classified?.mimeType ?: mimetype.ifBlank { "video/mp4" },
                                estimatedSizeBytes = contentLength.coerceAtLeast(0L),
                                isPlaying = true,
                                isHls = classified?.isHls ?: false,
                                isDash = classified?.isDash ?: false,
                                cookies = cookies,
                                userAgent = userAgent.orEmpty(),
                                referer = pageUrl
                            )
                            latestOnNetworkVideo(latestTabId, detected)
                            latestOnDirectDownload(detected)
                        }
                    }

                    controller.webView = this

                    if (savedBundle != null) {
                        restoreState(savedBundle)
                    } else if (initialUrl.isNotBlank() && initialUrl != "about:home") {
                        loadUrl(initialUrl)
                    }
                }
            },
            update = { webView ->
                controller.webView = webView
                val desiredUa = if (desktopMode) DESKTOP_USER_AGENT else defaultUserAgent
                if (desiredUa.isNotBlank() && webView.settings.userAgentString != desiredUa) {
                    webView.settings.userAgentString = desiredUa
                    webView.reload()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(navigationNonce, pendingLoadUrl) {
            val target = pendingLoadUrl
            if (!target.isNullOrBlank()) {
                controller.webView?.loadUrl(target)
                onConsumedPendingLoadUrl()
            }
        }

        if (customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { customView!! },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
