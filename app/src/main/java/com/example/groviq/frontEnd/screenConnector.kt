package com.example.groviq.frontEnd

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.groviq.MyApplication
import com.example.groviq.R
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModelHelpers
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
import com.example.groviq.frontEnd.appScreens.settingScreen.settingsPage
import com.example.groviq.frontEnd.appScreens.trackRadioPendingNavigation
import com.example.groviq.getAppMemoryUsage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay

suspend fun NavHostController.awaitGraphReady() {

    val ready = try {
        this.graph
        true
    } catch (e: IllegalStateException) {
        false
    }
    if (ready) return

    snapshotFlow { this.currentBackStackEntry }
        .filterNotNull()
        .first()
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home         : Screen("home",        "Главная",      Icons.Rounded.Home)
    object Albums       : Screen("albums",      "Альбомы",      Icons.Rounded.Album)
    object Playlists    : Screen("playlists",   "Плейлисты",    Icons.Rounded.PlaylistPlay)
    object Searching    : Screen("searching",   "Поиск",        Icons.Rounded.Search)
    object Settings     : Screen("settings",    "Настройки",    Icons.Rounded.Settings)
}


@SuppressLint(
    "UnusedMaterial3ScaffoldPaddingParameter"
)
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


    Box(Modifier)
    {
        Scaffold(Modifier.hazeSource(state = hazeState)
        ) { innerPadding ->

            val pendingArtistLink = artistPendingNavigation.value
            LaunchedEffect(pendingArtistLink) {
                    if (pendingArtistLink != null) {
                        val encoded = Uri.encode(pendingArtistLink)
                        currentTab = Screen.Searching
                        val targetController = navControllers[Screen.Searching]!!

                        try {
                            targetController.awaitGraphReady()
                        } catch (t: Throwable) {
                            Log.w("ScreenConnector", "Failed waiting for graph ready", t)
                            artistPendingNavigation.value = null
                            return@LaunchedEffect
                        }

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

                        try {
                            targetController.awaitGraphReady()
                        } catch (t: Throwable) {
                            Log.w("ScreenConnector", "Failed waiting for graph ready", t)
                            artistPendingNavigation.value = null
                            return@LaunchedEffect
                        }

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

                        try {
                            targetController.awaitGraphReady()
                        } catch (t: Throwable) {
                            Log.w("ScreenConnector", "Failed waiting for graph ready", t)
                            artistPendingNavigation.value = null
                            return@LaunchedEffect
                        }

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

            val stateHolder = rememberSaveableStateHolder()

            Box(modifier = Modifier.padding(top = innerPadding.calculateTopPadding()).fillMaxSize()) {
                val controller = navControllers[currentTab]!!
                stateHolder.SaveableStateProvider(currentTab.route) {
                    NavHost(
                        navController = controller,
                        startDestination = currentTab.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (currentTab) {
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
                            Screen.Settings    -> {
                                composable(Screen.Settings.route) {
                                    settingsPage(mainViewModel)
                                }
                            }
                            else -> {
                                composable(currentTab.route) {
                                    Text("Screen ${currentTab.route}")
                                }
                            }
                        }
                    }
                }

            }

            Box(Modifier.fillMaxHeight())
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(150.dp)
                        .drawWithCache {
                            val gradient = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.2f)
                                ),
                                startY = 0f,
                                endY = size.height * 0.2f
                            )
                            onDrawBehind {
                                drawRect(brush = gradient)
                            }
                        }
                )
            }



        }


        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 15.dp, end = 15.dp, bottom = 15.dp, )
                .height(70.dp)
                .clip(RoundedCornerShape(36.dp))
                .hazeEffect(hazeState)
        ) {
            Box(Modifier.border(1.dp, Color(255, 255, 255, 28), RoundedCornerShape(36.dp)).
            background(Color(20, 20, 20, 140))
               )
            {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.0f),
                    modifier = Modifier.height(70.dp).padding(horizontal = 10.dp).padding(top = 6.dp)
                ) {
                    items.forEach { screen ->

                        val controller = navControllers[screen]!!
                        val backStackEntry by controller.currentBackStackEntryAsState()
                        val currentRoute = backStackEntry?.destination?.route

                        NavigationBarItem(
                            colors = NavigationBarItemColors(
                                selectedIconColor = Color(255, 255, 255),
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                selectedIndicatorColor = Color.Transparent,
                                unselectedIconColor = Color(255, 255, 255).copy(alpha = 0.3f),
                                unselectedTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledIconColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledTextColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            ),
                            icon = { Icon(screen.icon, contentDescription = null, modifier = Modifier.size(23.dp)) },
                            label = { },
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
        }
    }

    return

    Box(Modifier.statusBarsPadding()
        .navigationBarsPadding())
    {
        var memoryInfo by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            while (true) {
                memoryInfo = getAppMemoryUsage(MyApplication.globalContext)
                delay(2000)
            }
        }

        Text(
            text = memoryInfo,
            color = Color.Gray,
            fontSize = 12.sp,
        )
    }

    //DISSAMBLED
    //
    //
    //
    //

    return

    val audioData by mainViewModel.uiState.subscribeMe { it.audioData }
    val allAudioData by mainViewModel.uiState.subscribeMe { it.allAudioData }

    LaunchedEffect(Unit) {
        snapshotFlow { audioData }
            .filter { allAudioData.isNotEmpty() && it.isNotEmpty() }
            .collect { data ->

                val playlistsAutoGenerated = data.entries.filter { it.value.autoGenerated }

                // --- Daily playlist ---
                val dailyKey = "DAILY_PLAYLIST_AUTOGENERATED"
                val daily = playlistsAutoGenerated.find { it.key == dailyKey }

                if (daily != null) {
                    val ds = mainViewModel.uiState.value.audioData[dailyKey]
                    if (ds != null && !ds.isInGenerationProcess && ds.shouldBeRegenerated()) {
                        mainViewModel.markSourceGenerationInProgress(dailyKey, true)

                        SearchViewModelHelpers.requestGenerationIfNeeded(
                            flagKey = dailyKey,
                            mainViewModel = mainViewModel
                        ) { vm ->
                            AppViewModels.search.createDailyPlaylist(vm)
                            AppViewModels.search.createFourSpecialPlaylists(vm)
                        }
                    }
                }
            }
    }

}
