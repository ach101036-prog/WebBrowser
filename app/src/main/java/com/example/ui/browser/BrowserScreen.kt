package com.example.ui.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.MonoBadgeTextStyle

@Composable
fun BrowserScreen(
    uiState: BrowserUiState,
    viewModel: BrowserViewModel,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activeTab = uiState.activeTab
    val webController = remember(activeTab.id) { BrowserWebController() }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startBackgroundDownload(context)
        onNavigateToDownloads()
    }

    fun launchDownloadWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startBackgroundDownload(context)
        onNavigateToDownloads()
    }

    BackHandler(enabled = !activeTab.isHomePage) {
        if (activeTab.canGoBack) {
            webController.goBack()
        } else {
            viewModel.goHome()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Browser Omnibox Chrome
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    activeTab.isHomePage -> Icons.Rounded.Search
                                    activeTab.url.startsWith("https://", ignoreCase = true) -> Icons.Rounded.Lock
                                    else -> Icons.Rounded.Public
                                },
                                contentDescription = null,
                                tint = if (activeTab.url.startsWith("https://")) CyanElectric else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (uiState.addressInput.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.omnibox_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = uiState.addressInput,
                                    onValueChange = viewModel::onAddressInputChange,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(CyanElectric),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            focusManager.clearFocus()
                                            viewModel.submitAddress()
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("omnibox_input")
                                )
                            }

                            if (uiState.addressInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onAddressInputChange("") },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("omnibox_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear URL",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!activeTab.isHomePage) {
                        IconButton(
                            onClick = viewModel::toggleBookmark,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bookmark_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isCurrentPageBookmarked) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = "Toggle bookmark",
                                tint = if (uiState.isCurrentPageBookmarked) CyanElectric else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("browser_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Browser menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Bookmarks, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.setShowBookmarksSheet(true)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (uiState.desktopMode) "Desktop Site: ON" else stringResource(R.string.desktop_mode))
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DesktopWindows,
                                        contentDescription = null,
                                        tint = if (uiState.desktopMode) CyanElectric else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleDesktopMode()
                                }
                            )
                            if (!activeTab.isHomePage) {
                                DropdownMenuItem(
                                    text = { Text("Scan Page for Videos") },
                                    leadingIcon = { Icon(Icons.Rounded.VideoFile, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        webController.rescanVideos()
                                    }
                                )
                            }
                        }
                    }
                }

                if (activeTab.isLoading && !activeTab.isHomePage) {
                    LinearProgressIndicator(
                        progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                        color = CyanElectric,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }
            }
        }

        // Main Browser Viewport + Floating Video Detection Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab.isHomePage) {
                BrowserStartPage(
                    bookmarkCount = uiState.bookmarks.size,
                    onOpenUrl = { url -> viewModel.navigateActiveTab(url) },
                    onOpenBookmarks = { viewModel.setShowBookmarksSheet(true) }
                )
            } else {
                key(activeTab.id) {
                    BrowserWebView(
                        tabId = activeTab.id,
                        initialUrl = activeTab.url,
                        pendingLoadUrl = uiState.pendingLoadUrl,
                        navigationNonce = uiState.navigationNonce,
                        desktopMode = uiState.desktopMode,
                        savedBundle = viewModel.getWebViewBundle(activeTab.id),
                        controller = webController,
                        onConsumedPendingLoadUrl = viewModel::consumePendingLoadUrl,
                        onSaveBundle = viewModel::saveWebViewBundle,
                        onPageStarted = viewModel::onPageStarted,
                        onPageFinished = viewModel::onPageFinished,
                        onPageProgress = viewModel::onPageProgress,
                        onJsVideoEvent = viewModel::onJsVideoEvent,
                        onNetworkVideoDetected = viewModel::onNetworkVideoDetected,
                        onDirectVideoDownloadRequested = { detected ->
                            viewModel.openDownloadSheet(detected)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val primaryVideo = activeTab.primaryVideo
            androidx.compose.animation.AnimatedVisibility(
                visible = !activeTab.isHomePage && primaryVideo != null,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                if (primaryVideo != null) {
                    VideoDetectedDownloadButton(
                        isPlaying = activeTab.hasPlayingVideo,
                        streamCount = activeTab.detectedVideos.size,
                        resolutionBadge = primaryVideo.resolutionBadge,
                        onClick = { viewModel.openDownloadSheet(primaryVideo) }
                    )
                }
            }
        }

        // Bottom Web Navigation Strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = {
                        if (activeTab.canGoBack) {
                            webController.goBack()
                        } else if (!activeTab.isHomePage) {
                            viewModel.goHome()
                        }
                    },
                    enabled = activeTab.canGoBack || !activeTab.isHomePage,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                IconButton(
                    onClick = { webController.goForward() },
                    enabled = !activeTab.isHomePage && activeTab.canGoForward,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Forward"
                    )
                }

                IconButton(
                    onClick = viewModel::goHome,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Start Page"
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.setShowTabsSheet(true) }
                        .testTag("browser_tabs_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.tabs.size.toString(),
                            style = MonoBadgeTextStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (activeTab.isLoading) {
                            webController.stopLoading()
                        } else {
                            webController.reload()
                            webController.rescanVideos()
                        }
                    },
                    enabled = !activeTab.isHomePage,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_refresh_button")
                ) {
                    Icon(
                        imageVector = if (activeTab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (activeTab.isLoading) "Stop" else "Reload"
                    )
                }
            }
        }
    }

    val selectedStream = activeTab.detectedVideos.firstOrNull { it.id == uiState.selectedVideoId }
        ?: activeTab.primaryVideo
    if (uiState.showDownloadSheet && selectedStream != null) {
        VideoDownloadSheet(
            detectedVideos = activeTab.detectedVideos,
            selectedVideo = selectedStream,
            selectedResolution = uiState.selectedResolution,
            customFileName = uiState.customFileName,
            onSelectVideo = viewModel::selectVideoInSheet,
            onSelectResolution = viewModel::selectResolution,
            onFileNameChange = viewModel::updateCustomFileName,
            onConfirmDownload = { launchDownloadWithPermissionCheck() },
            onDismiss = viewModel::dismissDownloadSheet
        )
    }

    if (uiState.showTabsSheet) {
        TabsBottomSheet(
            tabs = uiState.tabs,
            activeTabId = activeTab.id,
            onSelectTab = viewModel::selectTab,
            onCloseTab = viewModel::closeTab,
            onCreateTab = { viewModel.createNewTab() },
            onDismiss = { viewModel.setShowTabsSheet(false) }
        )
    }

    if (uiState.showBookmarksSheet) {
        BookmarksBottomSheet(
            bookmarks = uiState.bookmarks,
            onOpenUrl = viewModel::navigateActiveTab,
            onDeleteBookmark = viewModel::deleteBookmark,
            onDismiss = { viewModel.setShowBookmarksSheet(false) }
        )
    }
}

@Composable
private fun VideoDetectedDownloadButton(
    isPlaying: Boolean,
    streamCount: Int,
    resolutionBadge: String,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "videoPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (isPlaying) CoralPulse else CyanElectric,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .testTag("detected_video_download_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .alpha(pulseAlpha)
                        .background(Color.White, CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = Color(0xFF001F26),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.download_mp4_fab),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) Color.White else Color(0xFF001F26)
                )
                Text(
                    text = if (isPlaying) {
                        "${stringResource(R.string.video_playing_badge)} • $resolutionBadge"
                    } else {
                        "${stringResource(R.string.video_detected_badge)} • $resolutionBadge"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPlaying) Color.White.copy(alpha = 0.9f) else Color(0xFF002A33)
                )
            }

            if (streamCount > 1) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = streamCount.toString(),
                        style = MonoBadgeTextStyle,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
