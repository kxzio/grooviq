package com.example.groviq.frontEnd

import android.net.Uri
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchResultsNavigation
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

    var navController = screenConnectorNavigation.current

    Scaffold(
        bottomBar = {
            NavigationBar {

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = null
                                )
                            },
                            label = {
                            },
                            selected = currentRoute?.startsWith(screen.route) == true,
                            onClick = {
                                val isInsideThisTab = currentRoute?.startsWith(screen.route) == true

                                if (!isInsideThisTab) {
                                    // Переход на другую вкладку без сброса её вложенных экранов
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    // Уже в этой вкладке — сброс к её корню
                                    navController.popBackStack(screen.route, inclusive = false)
                                }
                            }
                        )
                    }

            }
        }
    )
    { innerPadding ->

        //processing the artist pending link
        val pendingArtistLink = artistPendingNavigation.value
        LaunchedEffect(pendingArtistLink) {
            if (pendingArtistLink != null) {
                val encoded = Uri.encode(pendingArtistLink)
                navController.navigate("${Screen.Searching.route}/artist/$encoded")
                artistPendingNavigation.value = null
            }
        }

        //processing the album pending link
        val pendingAlbumLink = albumPendingNavigation.value
        LaunchedEffect(pendingAlbumLink) {
            if (pendingAlbumLink != null) {
                val encoded = Uri.encode(pendingAlbumLink)
                navController.navigate("${Screen.Searching.route}/album/$encoded")
                albumPendingNavigation.value = null
            }
        }


        NavHost(
            navController = navController,
            startDestination = Screen.Searching.route,
            modifier = Modifier.padding(
                innerPadding
            )
        ) {

            //searching - results
            composable("${Screen.Searching.route}") {
                searchResultsNavigation(navController)
            }

            //searching - album
            composable("${Screen.Searching.route}/album/{album_url}",
                arguments = listOf(navArgument("album_url") { type = NavType.StringType })
            ) {
                showAudioSourceFromSurf(it, searchViewModel, mainViewModel, navController)
            }

            //searching - artist
            composable("${Screen.Searching.route}/artist/{artist_url}",
                arguments = listOf(navArgument("artist_url") { type = NavType.StringType })
            ) {
                showArtistFromSurf(it, searchViewModel, mainViewModel, navController)
            }

            //playlist - list of playlists
            composable("${Screen.Playlists.route}") {
                playlistList(mainViewModel, navController)
            }

            //playlist - playlist detail screen
            composable("${Screen.Playlists.route}/playlist/{playlist_name}",
                arguments = listOf(navArgument("playlist_name") { type = NavType.StringType })
            ) {
                playlistDetailList(it, mainViewModel)
            }
        }
    }


}