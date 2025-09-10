package com.example.groviq.frontEnd

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchState
import com.example.groviq.frontEnd.appScreens.albumPendingNavigation
import com.example.groviq.frontEnd.appScreens.albumsScreen.albumLists
import com.example.groviq.frontEnd.appScreens.artistPendingNavigation
import com.example.groviq.frontEnd.appScreens.mainScreen.mainScreen
import com.example.groviq.frontEnd.appScreens.playlistsScreen.playlistDetailList
import com.example.groviq.frontEnd.appScreens.playlistsScreen.playlistList
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showArtistFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showAudioSourceFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showAudioSourceOfRadio
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchResultsNavigation
import com.example.groviq.frontEnd.appScreens.trackRadioPendingNavigation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import dev.chrisbanes.haze.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home         : Screen("home",        "Главная",      Icons.Rounded.Home)
    object Albums       : Screen("albums",      "Альбомы",      Icons.Rounded.Album)
    object Playlists    : Screen("playlists",   "Плейлисты",    Icons.Rounded.PlaylistPlay)
    object Searching    : Screen("searching",   "Поиск",        Icons.Rounded.Search)
    object Settings     : Screen("settings",    "Настройки",    Icons.Rounded.Settings)
}


@OptIn(
    UnstableApi::class
)
@Composable
fun connectScreens(
    searchViewModel : SearchViewModel, //search view
    mainViewModel   : PlayerViewModel, //player view
    hazeState: HazeState
)
{
    val items = listOf(
        Screen.Home,
        Screen.Albums,
        Screen.Playlists,
        Screen.Searching,
        Screen.Settings,
    )

    val screensByRoute = mapOf(
        Screen.Home.route to Screen.Home,
        Screen.Albums.route to Screen.Albums,
        Screen.Playlists.route to Screen.Playlists,
        Screen.Searching.route to Screen.Searching,
        Screen.Settings.route to Screen.Settings,
    )

    val screenSaver = Saver<Screen, String>(
        save = { it.route },
        restore = { route -> screensByRoute[route] ?: Screen.Searching } // fallback
    )

    var currentTab: Screen by rememberSaveable(stateSaver = screenSaver) {
        mutableStateOf(Screen.Searching)
    }

    val navControllers = remember {
        mutableStateMapOf<Screen, NavHostController>()
    }

    items.forEach { screen ->
        if (navControllers[screen] == null) {
            navControllers[screen] = rememberNavController()
        }
    }

    Box(Modifier.hazeSource(state = hazeState))
    {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.0f),
                    modifier = Modifier.height(65.dp)
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            colors = NavigationBarItemColors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                selectedIndicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                unselectedTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledIconColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            ),
                            icon = { Icon(screen.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = { },
                            selected = currentTab == screen,
                            onClick = { currentTab = screen }
                        )
                    }
                }

            }
        ) { innerPadding ->

            val pendingArtistLink = artistPendingNavigation.value
            LaunchedEffect(pendingArtistLink) {
                if (pendingArtistLink != null) {
                    val encoded = Uri.encode(pendingArtistLink)
                    currentTab = Screen.Searching
                    val targetController = navControllers[Screen.Searching]!!

                    val route = "${Screen.Searching.route}/artist/$encoded"
                    if (targetController.graph.findNode(route) != null)
                    {
                        targetController.navigate(route) {
                            launchSingleTop = true
                        }
                    }

                    artistPendingNavigation.value = null
                }
            }

            val pendingAlbumLink = albumPendingNavigation.value
            LaunchedEffect(pendingAlbumLink) {
                if (pendingAlbumLink != null) {
                    val encoded = Uri.encode(pendingAlbumLink)
                    currentTab = Screen.Searching
                    val targetController = navControllers[Screen.Searching]!!
                    val route = "${Screen.Searching.route}/album/$encoded"
                    if (targetController.graph.findNode(route) != null)
                    {
                        targetController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                    albumPendingNavigation.value = null
                }
            }

            val pendingRadioLink = trackRadioPendingNavigation.value
            LaunchedEffect(pendingRadioLink) {
                if (pendingRadioLink != null) {
                    val encoded = Uri.encode(pendingRadioLink)
                    currentTab = Screen.Searching
                    val targetController = navControllers[Screen.Searching]!!
                    val route = "${Screen.Searching.route}/radio/$encoded"
                    if (targetController.graph.findNode(route) != null)
                    {
                        targetController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                    trackRadioPendingNavigation.value = null
                }
            }

            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                items.forEach { screen ->
                    val controller = navControllers[screen]!!
                    val isVisible = screen == currentTab

                    // animate alpha to make transition smooth
                    val targetAlpha = if (isVisible) 1f else 0f
                    val alpha by animateFloatAsState(targetValue = targetAlpha)

                    // put the NavHost into composition always, but change alpha and zIndex
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isVisible) 1f else 0f)
                            .alpha(alpha)
                            // prevent pointer events when fully invisible (optional but recommended)
                            .then(
                                if (alpha < 0.01f) {
                                    Modifier.pointerInput(Unit) {
                                        // consume all touches so invisible layers don't get interaction
                                        while (true) {
                                            awaitPointerEventScope {
                                                val ev = awaitPointerEvent()
                                                ev.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                } else Modifier
                            )
                    ) {
                        NavHost(
                            navController = controller,
                            startDestination = screen.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when (screen) {
                                Screen.Searching -> {

                                    composable(Screen.Searching.route) {
                                        searchResultsNavigation(controller, searchViewModel, mainViewModel)
                                    }
                                    composable("${Screen.Searching.route}/album/{album_url}",
                                        arguments = listOf(navArgument("album_url") { type = NavType.StringType })
                                    ) {
                                        showAudioSourceFromSurf(it, searchViewModel, mainViewModel, controller)
                                    }
                                    composable("${Screen.Searching.route}/radio/{track_url}",
                                        arguments = listOf(navArgument("track_url") { type = NavType.StringType })
                                    ) {
                                        showAudioSourceOfRadio(it, searchViewModel, mainViewModel, controller)
                                    }
                                    composable("${Screen.Searching.route}/artist/{artist_url}",
                                        arguments = listOf(navArgument("artist_url") { type = NavType.StringType })
                                    ) {
                                        showArtistFromSurf(it, searchViewModel, mainViewModel, controller)
                                    }
                                }
                                Screen.Playlists -> {
                                    composable(Screen.Playlists.route) {
                                        playlistList(mainViewModel, controller)
                                    }
                                    composable("${Screen.Playlists.route}/playlist/{playlist_name}",
                                        arguments = listOf(navArgument("playlist_name") { type = NavType.StringType })
                                    ) {
                                        playlistDetailList(it, searchViewModel, mainViewModel)
                                    }
                                }
                                Screen.Albums    -> {
                                    composable(Screen.Albums.route) {
                                        albumLists(controller, searchViewModel, mainViewModel)
                                    }
                                    composable("${Screen.Albums.route}/album/{album_url}",
                                        arguments = listOf(navArgument("album_url") { type = NavType.StringType })
                                    ) {
                                        showAudioSourceFromSurf(it, searchViewModel, mainViewModel, controller)
                                    }
                                }
                                Screen.Home    -> {
                                    composable(Screen.Home.route) {
                                        mainScreen(mainViewModel, controller)
                                    }
                                    composable("${Screen.Home.route}/playlist/{playlist_name}",
                                        arguments = listOf(navArgument("playlist_name") { type = NavType.StringType })
                                    ) {
                                        playlistDetailList(it, searchViewModel, mainViewModel)
                                    }
                                }
                                else -> {
                                    composable(screen.route) {
                                        Text("Screen ${screen.route}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    val audioData              by mainViewModel.uiState.subscribeMe { it.audioData }
    val allAudioData           by mainViewModel.uiState.subscribeMe { it.allAudioData }


    val playlistsAutoGenerated = remember(audioData) {
        audioData.entries.filter { it.value.autoGenerated }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { playlistsAutoGenerated }
            .filter { allAudioData.isNotEmpty() }
            .collect { playlists ->
                val daily = playlists.find { it.key == "DAILY_PLAYLIST_AUTOGENERATED" }
                if (daily?.value?.shouldBeRegenerated() != false) {
                    AppViewModels.search.createDailyPlaylist(mainViewModel)
                }

                val moods = playlists.filter { it.key.startsWith("MOOD_PLAYLIST_AUTOGENERATED") }
                if (moods.any { it.value.shouldBeRegenerated() }) {
                    AppViewModels.search.createFourSpecialPlaylists(mainViewModel)
                }
            }
    }


}