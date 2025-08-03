package com.example.groviq.frontEnd.appScreens.searchingScreen

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchType
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.searchingNavigation
import com.example.groviq.globalContext

sealed class searchTabs(val route: String) {
    object results : searchTabs("results")
}

@Composable
fun drawSearchScreen()
{
    val searchingScreenNav = searchingNavigation.current

    var searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val searchUiState by searchViewModel.uiState.collectAsState()

    var mainViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val mainUiState by mainViewModel.uiState.collectAsState()

    NavHost(
        navController = searchingScreenNav,
        startDestination = searchTabs.results.route,
    ) {
        composable(searchTabs.results.route) { searchResultsNavigation() }

        composable(route = "album/{album_url}", arguments = listOf(navArgument("album_url") {
            type = NavType.StringType
        })
        ) { backStackEntry ->

            val rawEncoded = backStackEntry.arguments
                ?.getString("album_url")
                ?: return@composable

            // 2) Декодируем, и если получили пусто — выходим
            val albumUrl = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
                ?: return@composable

            if (globalContext != null) {

                LaunchedEffect(albumUrl) {
                    searchViewModel.getAlbum(
                        context = globalContext!!,
                        request = albumUrl,
                        mainViewModel
                    )
                }

                val songs = mainUiState.audioData[albumUrl]?.songIds
                    ?.mapNotNull { mainUiState.allAudioData[it] }
                    ?: emptyList()

                LazyColumn {
                    items(songs) { song ->
                        Text(song.title, Modifier.padding(16.dp))
                    }
                }
            }
        }

        composable(route = "artist/{artist_url}", arguments = listOf(navArgument("artist_url") {
            type = NavType.StringType
        })
        ) { backStackEntry ->
            val artistUrl = backStackEntry.arguments?.getString("artist_url") ?: return@composable

        }
    }


}