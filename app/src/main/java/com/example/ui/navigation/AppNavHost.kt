package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.R
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.BrowserViewModel
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.downloads.DownloadsViewModel
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.CyanElectricContainer
import kotlinx.serialization.Serializable

@Serializable
object BrowserRoute

@Serializable
object DownloadsRoute

@Serializable
data class PlayerRoute(val videoId: Long)

@Composable
fun FlowBrowserAppNav(
    container: AppContainer,
    openDownloadsRequested: Boolean,
    onConsumedOpenDownloads: () -> Unit,
    initialVideoIdToOpen: Long?,
    onConsumedInitialVideoId: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep browser tabs & saved WebView state alive across tab switches
    val browserVm: BrowserViewModel = viewModel(
        factory = BrowserViewModel.Factory(
            bookmarkRepository = container.bookmarkRepository,
            downloadCoordinator = container.downloadCoordinator
        )
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination?.hasRoute<PlayerRoute>() != true

    val jobs by container.downloadCoordinator.jobs.collectAsStateWithLifecycle()
    val activeJobCount = jobs.count { it.isActive }
    val bannerEvent by container.downloadCoordinator.bannerEvent.collectAsStateWithLifecycle()

    LaunchedEffect(openDownloadsRequested, initialVideoIdToOpen) {
        if (initialVideoIdToOpen != null && initialVideoIdToOpen > 0L) {
            navController.navigate(PlayerRoute(initialVideoIdToOpen))
            onConsumedInitialVideoId()
            onConsumedOpenDownloads()
        } else if (openDownloadsRequested) {
            navController.navigate(DownloadsRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onConsumedOpenDownloads()
        }
    }

    LaunchedEffect(bannerEvent) {
        val evt = bannerEvent ?: return@LaunchedEffect
        val actionLabel = if (!evt.isError && evt.savedVideoId != null) "Play" else null
        val result = snackbarHostState.showSnackbar(
            message = evt.message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && evt.savedVideoId != null) {
            navController.navigate(PlayerRoute(evt.savedVideoId))
        }
        container.downloadCoordinator.dismissBanner()
    }

    fun navigateToTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val browserSelected = currentDestination?.hasRoute<BrowserRoute>() == true
                        val downloadsSelected = currentDestination?.hasRoute<DownloadsRoute>() == true

                        NavigationBarItem(
                            selected = browserSelected,
                            onClick = { navigateToTopLevel(BrowserRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (browserSelected) Icons.Rounded.Language else Icons.Outlined.Language,
                                    contentDescription = stringResource(R.string.nav_browser)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_browser)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = CyanElectricContainer,
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric
                            ),
                            modifier = Modifier.testTag("nav_browser")
                        )

                        NavigationBarItem(
                            selected = downloadsSelected,
                            onClick = { navigateToTopLevel(DownloadsRoute) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (activeJobCount > 0) {
                                            Badge { Text(activeJobCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (downloadsSelected) Icons.Rounded.Download else Icons.Outlined.Download,
                                        contentDescription = stringResource(R.string.nav_downloads)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_downloads)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = CyanElectricContainer,
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric
                            ),
                            modifier = Modifier.testTag("nav_downloads")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        val browserSelected = currentDestination?.hasRoute<BrowserRoute>() == true
                        val downloadsSelected = currentDestination?.hasRoute<DownloadsRoute>() == true

                        NavigationRailItem(
                            selected = browserSelected,
                            onClick = { navigateToTopLevel(BrowserRoute) },
                            icon = {
                                Icon(
                                    imageVector = if (browserSelected) Icons.Rounded.Language else Icons.Outlined.Language,
                                    contentDescription = stringResource(R.string.nav_browser)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_browser)) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = CyanElectricContainer,
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric
                            ),
                            modifier = Modifier.testTag("rail_browser")
                        )

                        NavigationRailItem(
                            selected = downloadsSelected,
                            onClick = { navigateToTopLevel(DownloadsRoute) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (activeJobCount > 0) {
                                            Badge { Text(activeJobCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (downloadsSelected) Icons.Rounded.Download else Icons.Outlined.Download,
                                        contentDescription = stringResource(R.string.nav_downloads)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_downloads)) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = CyanElectricContainer,
                                selectedIconColor = CyanElectric,
                                selectedTextColor = CyanElectric
                            ),
                            modifier = Modifier.testTag("rail_downloads")
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = BrowserRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<BrowserRoute> {
                        val browserState by browserVm.uiState.collectAsStateWithLifecycle()
                        BrowserScreen(
                            uiState = browserState,
                            viewModel = browserVm,
                            onNavigateToDownloads = { navigateToTopLevel(DownloadsRoute) }
                        )
                    }

                    composable<DownloadsRoute> {
                        val downloadsVm: DownloadsViewModel = viewModel(
                            factory = DownloadsViewModel.Factory(
                                downloadRepository = container.downloadRepository,
                                downloadCoordinator = container.downloadCoordinator
                            )
                        )
                        val state by downloadsVm.uiState.collectAsStateWithLifecycle()
                        DownloadsScreen(
                            uiState = state,
                            onSearchQueryChange = downloadsVm::onSearchQueryChange,
                            onCancelJob = { jobId -> downloadsVm.cancelJob(context, jobId) },
                            onDismissJob = downloadsVm::dismissInactiveJob,
                            onPlayVideo = { video ->
                                navController.navigate(PlayerRoute(video.id))
                            },
                            onDeleteVideo = downloadsVm::deleteVideo
                        )
                    }

                    composable<PlayerRoute> { entry ->
                        val route = entry.toRoute<PlayerRoute>()
                        val playerVm: PlayerViewModel = viewModel(
                            factory = PlayerViewModel.Factory(
                                videoId = route.videoId,
                                downloadRepository = container.downloadRepository
                            )
                        )
                        val state by playerVm.uiState.collectAsStateWithLifecycle()
                        PlayerScreen(
                            uiState = state,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
