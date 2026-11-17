package com.example.ui.browser

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.browser.BrowserTab
import com.example.data.browser.DetectedVideo
import com.example.data.browser.UrlStreamClassifier
import com.example.data.browser.VideoResolution
import com.example.data.local.BookmarkEntity
import com.example.data.local.BookmarkRepository
import com.example.service.DownloadCoordinator
import com.example.service.DownloadRequest
import com.example.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = "tab_initial")),
    val activeTabId: String = "tab_initial",
    val addressInput: String = "",
    val desktopMode: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val showDownloadSheet: Boolean = false,
    val selectedVideoId: String? = null,
    val selectedResolution: VideoResolution = VideoResolution.P720,
    val customFileName: String = "",
    val showTabsSheet: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val navigationNonce: Long = 0L,
    val pendingLoadUrl: String? = null
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val isCurrentPageBookmarked: Boolean
        get() = !activeTab.isHomePage && bookmarks.any { it.url == activeTab.url }
}

class BrowserViewModel(
    private val bookmarkRepository: BookmarkRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    private val tabWebViewBundles = mutableMapOf<String, Bundle>()

    val uiState: StateFlow<BrowserUiState> = combine(
        _state,
        bookmarkRepository.observeBookmarks()
    ) { state, bookmarks ->
        state.copy(bookmarks = bookmarks)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BrowserUiState()
    )

    fun saveWebViewBundle(tabId: String, bundle: Bundle) {
        tabWebViewBundles[tabId] = bundle
    }

    fun getWebViewBundle(tabId: String): Bundle? = tabWebViewBundles[tabId]

    fun onAddressInputChange(value: String) {
        _state.update { it.copy(addressInput = value) }
    }

    fun submitAddress(rawInput: String = _state.value.addressInput) {
        val normalized = UrlStreamClassifier.normalizeAddressInput(rawInput)
        navigateActiveTab(normalized)
    }

    fun navigateActiveTab(url: String) {
        val activeId = _state.value.activeTabId
        _state.update { current ->
            val updatedTabs = current.tabs.map { tab ->
                if (tab.id == activeId) {
                    tab.copy(
                        url = url,
                        title = if (url == BrowserTab.HOME_URL) "Start Page" else UrlStreamClassifier.extractDomain(url),
                        isLoading = url != BrowserTab.HOME_URL,
                        progress = if (url == BrowserTab.HOME_URL) 0 else 10,
                        detectedVideos = emptyList()
                    )
                } else {
                    tab
                }
            }
            current.copy(
                tabs = updatedTabs,
                addressInput = if (url == BrowserTab.HOME_URL) "" else url,
                pendingLoadUrl = if (url == BrowserTab.HOME_URL) null else url,
                navigationNonce = current.navigationNonce + 1L,
                showTabsSheet = false,
                showBookmarksSheet = false
            )
        }
    }

    fun consumePendingLoadUrl() {
        _state.update { it.copy(pendingLoadUrl = null) }
    }

    fun goHome() {
        navigateActiveTab(BrowserTab.HOME_URL)
    }

    fun toggleDesktopMode() {
        _state.update { it.copy(desktopMode = !it.desktopMode) }
    }

    fun toggleBookmark() {
        val tab = uiState.value.activeTab
        val isBookmarked = uiState.value.isCurrentPageBookmarked
        viewModelScope.launch {
            bookmarkRepository.toggleBookmark(
                title = tab.title,
                url = tab.url,
                currentlyBookmarked = isBookmarked
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
        }
    }

    fun setShowTabsSheet(show: Boolean) {
        _state.update { it.copy(showTabsSheet = show) }
    }

    fun setShowBookmarksSheet(show: Boolean) {
        _state.update { it.copy(showBookmarksSheet = show) }
    }

    fun createNewTab(initialUrl: String = BrowserTab.HOME_URL) {
        val newId = "tab_${UUID.randomUUID().toString().take(8)}"
        val newTab = BrowserTab(
            id = newId,
            url = initialUrl,
            title = if (initialUrl == BrowserTab.HOME_URL) "Start Page" else UrlStreamClassifier.extractDomain(initialUrl)
        )
        _state.update { current ->
            current.copy(
                tabs = current.tabs + newTab,
                activeTabId = newId,
                addressInput = if (initialUrl == BrowserTab.HOME_URL) "" else initialUrl,
                pendingLoadUrl = if (initialUrl == BrowserTab.HOME_URL) null else initialUrl,
                navigationNonce = current.navigationNonce + 1L,
                showTabsSheet = false
            )
        }
    }

    fun selectTab(tabId: String) {
        _state.update { current ->
            val target = current.tabs.firstOrNull { it.id == tabId } ?: return@update current
            current.copy(
                activeTabId = tabId,
                addressInput = if (target.isHomePage) "" else target.url,
                showTabsSheet = false
            )
        }
    }

    fun closeTab(tabId: String) {
        tabWebViewBundles.remove(tabId)
        _state.update { current ->
            val remaining = current.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab(id = "tab_${UUID.randomUUID().toString().take(8)}")
                current.copy(
                    tabs = listOf(fresh),
                    activeTabId = fresh.id,
                    addressInput = "",
                    showTabsSheet = false
                )
            } else {
                val newActive = if (current.activeTabId == tabId) remaining.last() else {
                    remaining.firstOrNull { it.id == current.activeTabId } ?: remaining.last()
                }
                current.copy(
                    tabs = remaining,
                    activeTabId = newActive.id,
                    addressInput = if (newActive.isHomePage) "" else newActive.url
                )
            }
        }
    }

    fun onPageStarted(tabId: String, url: String) {
        _state.update { current ->
            val updated = current.tabs.map { tab ->
                if (tab.id == tabId) {
                    val pageChanged = tab.url != url
                    tab.copy(
                        url = url,
                        isLoading = true,
                        progress = 15,
                        detectedVideos = if (pageChanged) emptyList() else tab.detectedVideos
                    )
                } else {
                    tab
                }
            }
            val newAddress = if (current.activeTabId == tabId) url else current.addressInput
            current.copy(tabs = updated, addressInput = newAddress)
        }
    }

    fun onPageFinished(
        tabId: String,
        url: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        _state.update { current ->
            val updated = current.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url,
                        title = title?.takeIf { it.isNotBlank() } ?: UrlStreamClassifier.extractDomain(url),
                        isLoading = false,
                        progress = 100,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
            val newAddress = if (current.activeTabId == tabId && url != BrowserTab.HOME_URL) {
                url
            } else {
                current.addressInput
            }
            current.copy(tabs = updated, addressInput = newAddress)
        }
    }

    fun onPageProgress(tabId: String, progress: Int, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        _state.update { current ->
            val updated = current.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        progress = progress,
                        isLoading = progress < 100,
                        title = title?.takeIf { it.isNotBlank() } ?: tab.title,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
            current.copy(tabs = updated)
        }
    }

    fun onJsVideoEvent(
        tabId: String,
        rawJson: String,
        cookies: String,
        userAgent: String
    ) {
        val parsed = runCatching { JSONObject(rawJson) }.getOrNull() ?: return
        val videoUrl = parsed.optString("url", "").trim()
        val pageUrl = parsed.optString("pageUrl", "").trim()
        val title = parsed.optString("title", "Web Video").trim()
        val width = parsed.optInt("width", 0)
        val height = parsed.optInt("height", 0)
        val durationSec = parsed.optDouble("durationSec", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
        val isPlaying = parsed.optBoolean("isPlaying", false)

        if (videoUrl.isBlank()) return

        // If <video> uses MSE blob:, mark existing HLS/DASH streams on this tab as playing with metadata!
        if (videoUrl.startsWith("blob:", ignoreCase = true)) {
            _state.update { current ->
                val updatedTabs = current.tabs.map { tab ->
                    if (tab.id == tabId && tab.detectedVideos.isNotEmpty()) {
                        val updatedVideos = tab.detectedVideos.mapIndexed { idx, vid ->
                            if (idx == 0 || vid.isHls || vid.isDash) {
                                vid.copy(
                                    isPlaying = isPlaying || vid.isPlaying,
                                    sourceWidth = if (width > 0) width else vid.sourceWidth,
                                    sourceHeight = if (height > 0) height else vid.sourceHeight,
                                    durationSec = if (durationSec > 0.0) durationSec else vid.durationSec,
                                    title = if (title.isNotBlank() && title != "Web Video") title else vid.title
                                )
                            } else {
                                vid
                            }
                        }
                        tab.copy(detectedVideos = updatedVideos)
                    } else {
                        tab
                    }
                }
                current.copy(tabs = updatedTabs)
            }
            return
        }

        if (!videoUrl.startsWith("http://", ignoreCase = true) &&
            !videoUrl.startsWith("https://", ignoreCase = true)
        ) {
            return
        }

        val classification = UrlStreamClassifier.classifyVideoUrl(videoUrl)
        val mimeType = classification?.mimeType ?: "video/mp4"
        val isHls = classification?.isHls ?: videoUrl.contains(".m3u8", ignoreCase = true)
        val isDash = classification?.isDash ?: videoUrl.contains(".mpd", ignoreCase = true)

        upsertDetectedVideo(
            tabId = tabId,
            incoming = DetectedVideo(
                id = videoUrl.hashCode().toString(),
                url = videoUrl,
                pageUrl = pageUrl,
                title = title.ifBlank { UrlStreamClassifier.extractDomain(pageUrl) },
                mimeType = mimeType,
                sourceWidth = width,
                sourceHeight = height,
                durationSec = durationSec,
                isPlaying = isPlaying,
                isHls = isHls,
                isDash = isDash,
                cookies = cookies,
                userAgent = userAgent,
                referer = pageUrl
            )
        )
    }

    fun onNetworkVideoDetected(tabId: String, video: DetectedVideo) {
        upsertDetectedVideo(tabId, video)
    }

    private fun upsertDetectedVideo(tabId: String, incoming: DetectedVideo) {
        _state.update { current ->
            val updatedTabs = current.tabs.map { tab ->
                if (tab.id != tabId) return@map tab
                val existing = tab.detectedVideos.firstOrNull { it.url == incoming.url }
                val merged = if (existing != null) {
                    existing.copy(
                        title = if (incoming.title.isNotBlank() && incoming.title != "Web Video") incoming.title else existing.title,
                        sourceWidth = if (incoming.sourceWidth > 0) incoming.sourceWidth else existing.sourceWidth,
                        sourceHeight = if (incoming.sourceHeight > 0) incoming.sourceHeight else existing.sourceHeight,
                        durationSec = if (incoming.durationSec > 0.0) incoming.durationSec else existing.durationSec,
                        isPlaying = incoming.isPlaying || existing.isPlaying,
                        cookies = incoming.cookies.ifBlank { existing.cookies },
                        userAgent = incoming.userAgent.ifBlank { existing.userAgent },
                        referer = incoming.referer.ifBlank { existing.referer }
                    )
                } else {
                    incoming
                }
                val others = tab.detectedVideos.filterNot { it.url == incoming.url }
                val reordered = (listOf(merged) + others)
                    .sortedWith(compareByDescending<DetectedVideo> { it.isPlaying }.thenByDescending { it.detectedAtMillis })
                    .take(12)
                tab.copy(detectedVideos = reordered)
            }
            current.copy(tabs = updatedTabs)
        }
    }

    fun openDownloadSheet(preselectedVideo: DetectedVideo? = null) {
        val tab = uiState.value.activeTab
        val video = preselectedVideo ?: tab.primaryVideo ?: return
        val baseTitle = video.title
            .ifBlank { tab.title }
            .ifBlank { UrlStreamClassifier.extractDomain(tab.url) }
        val cleanName = UrlStreamClassifier.sanitizeFileName(baseTitle).removeSuffix(".mp4")
        _state.update {
            it.copy(
                showDownloadSheet = true,
                selectedVideoId = video.id,
                customFileName = cleanName
            )
        }
    }

    fun dismissDownloadSheet() {
        _state.update { it.copy(showDownloadSheet = false) }
    }

    fun selectVideoInSheet(video: DetectedVideo) {
        val cleanName = UrlStreamClassifier.sanitizeFileName(video.title).removeSuffix(".mp4")
        _state.update {
            it.copy(
                selectedVideoId = video.id,
                customFileName = cleanName
            )
        }
    }

    fun selectResolution(resolution: VideoResolution) {
        _state.update { it.copy(selectedResolution = resolution) }
    }

    fun updateCustomFileName(name: String) {
        _state.update { it.copy(customFileName = name) }
    }

    fun startBackgroundDownload(context: Context) {
        val snapshot = _state.value
        val tab = snapshot.activeTab
        val video = tab.detectedVideos.firstOrNull { it.id == snapshot.selectedVideoId }
            ?: tab.primaryVideo
            ?: return

        val finalName = UrlStreamClassifier.sanitizeFileName(
            snapshot.customFileName.ifBlank { video.title }
        )
        val request = DownloadRequest(
            jobId = UUID.randomUUID().toString(),
            video = video,
            resolution = snapshot.selectedResolution,
            outputFileName = finalName
        )
        DownloadService.enqueueDownload(context, request, downloadCoordinator)
        _state.update { it.copy(showDownloadSheet = false) }
    }

    class Factory(
        private val bookmarkRepository: BookmarkRepository,
        private val downloadCoordinator: DownloadCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowserViewModel(bookmarkRepository, downloadCoordinator) as T
        }
    }
}
