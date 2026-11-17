package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.service.DownloadNotificationHelper
import com.example.ui.navigation.FlowBrowserAppNav
import com.example.ui.theme.FlowBrowserTheme

class MainActivity : ComponentActivity() {

    private var openDownloadsTab by mutableStateOf(false)
    private var pendingVideoIdToOpen by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNavigationIntent(intent)

        val appContainer = (application as FlowBrowserApp).container

        setContent {
            FlowBrowserTheme {
                FlowBrowserAppNav(
                    container = appContainer,
                    openDownloadsRequested = openDownloadsTab,
                    onConsumedOpenDownloads = { openDownloadsTab = false },
                    initialVideoIdToOpen = pendingVideoIdToOpen,
                    onConsumedInitialVideoId = { pendingVideoIdToOpen = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent == null) return
        val openDownloads = intent.getBooleanExtra(DownloadNotificationHelper.EXTRA_OPEN_DOWNLOADS, false)
        if (openDownloads) {
            openDownloadsTab = true
        }
        val videoId = intent.getLongExtra(DownloadNotificationHelper.EXTRA_OPEN_VIDEO_ID, -1L)
        if (videoId > 0L) {
            pendingVideoIdToOpen = videoId
        }
    }
}
