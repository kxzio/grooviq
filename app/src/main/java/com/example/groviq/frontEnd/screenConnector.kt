package com.example.groviq.frontEnd

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchState
import com.example.groviq.frontEnd.appScreens.albumPendingNavigation
import com.example.groviq.frontEnd.appScreens.artistPendingNavigation
import com.example.groviq.frontEnd.appScreens.playlistsScreen.playlistDetailList
import com.example.groviq.frontEnd.appScreens.playlistsScreen.playlistList
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showArtistFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showAudioSourceFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showAudioSourceOfRadio
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchResultsNavigation
import com.example.groviq.frontEnd.appScreens.trackRadioPendingNavigation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home         : Screen("home",        "Главная",      Icons.Rounded.Home)
    object Albums       : Screen("albums",      "Альбомы",      Icons.Rounded.Album)
    object Playlists    : Screen("playlists",   "Плейлисты",    Icons.Rounded.PlaylistPlay)
    object Searching    : Screen("searching",   "Поиск",        Icons.Rounded.Search)
    object Settings     : Screen("settings",    "Настройки",    Icons.Rounded.Settings)
}


@Composable
fun connectScreens(
    searchViewModel : SearchViewModel, //search view
    mainViewModel   : PlayerViewModel, //player view
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

    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    val controller = navControllers[screen]!!
                    val backStackEntry by controller.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route

                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { /* optional label */ },
                        selected = currentTab == screen,
                        onClick = {
                            val isInsideThisTab = currentTab == screen
                            if (!isInsideThisTab) {
                                currentTab = screen
                            } else {
                                controller.popBackStack(screen.route, inclusive = false)
                            }
                        }
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