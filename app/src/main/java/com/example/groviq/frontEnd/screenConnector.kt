package com.example.groviq.frontEnd

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchState
import com.example.groviq.frontEnd.appScreens.playlistsScreen.drawPlaylistScreen
import com.example.groviq.frontEnd.appScreens.searchingScreen.drawSearchScreen

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
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(
                                        screen.route
                                    ) {
                                        popUpTo(
                                            navController.graph.startDestinationId
                                        ) {
                                            saveState =
                                                true
                                        }
                                        launchSingleTop =
                                            true
                                        restoreState =
                                            true
                                    }
                                }
                            }
                        )
                    }

            }
        }
    )
    { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Searching.route,
            modifier = Modifier.padding(
                innerPadding
            )
        ) {
            composable(Screen.Playlists.route) { drawPlaylistScreen (mainViewModel) }
            composable(Screen.Searching.route) { drawSearchScreen   (searchViewModel, mainViewModel) }

        }
    }

}