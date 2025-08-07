package com.example.groviq.frontEnd.appScreens.playlistsScreen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.searchType
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showArtistFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showAudioSourceFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchResultsNavigation
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchTabs

sealed class playlistTabs(val route: String) {
    object results : playlistTabs("playlist_list")
}

@Composable
fun drawPlaylistScreen(
    mainViewModel   : PlayerViewModel, //player view
) {

    val playlistNavigationLocal        = rememberNavController()

    val mainUiState     by mainViewModel.uiState.collectAsState()

    NavHost(
        navController =  playlistNavigationLocal,
        startDestination = playlistTabs.results.route,
    ) {

        //home page - list of playlists
        composable(playlistTabs.results.route) { playlistList(mainViewModel, playlistNavigationLocal) }

        //playlist page
        composable(route = "playlist/{playlist_name}", arguments = listOf(navArgument("playlist_name") {
            type = NavType.StringType
        })
        ) { backStackEntry ->
            playlistDetailList(backStackEntry, mainViewModel)
        }


    }


}