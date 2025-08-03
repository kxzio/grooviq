package com.example.groviq.frontEnd.appScreens.searchingScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showArtistFromSurf
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showAudioSourceFromSurf
import com.example.groviq.frontEnd.searchingNavigation

sealed class searchTabs(val route: String) {
    object results : searchTabs("results")
}

@Composable
fun drawSearchScreen()
{
    val searchingScreenNav = searchingNavigation.current

    var searchViewModel:    SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val searchUiState   by searchViewModel.uiState.collectAsState()

    var mainViewModel:      PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val mainUiState     by mainViewModel.uiState.collectAsState()

    NavHost(
        navController = searchingScreenNav,
        startDestination = searchTabs.results.route,
    ) {

        //home page - searching
        composable(searchTabs.results.route) { searchResultsNavigation() }

        //album page - browsing
        composable(route = "album/{album_url}", arguments = listOf(navArgument("album_url") {
            type = NavType.StringType
        })
        ) { backStackEntry ->
            showAudioSourceFromSurf(backStackEntry)
        }

        //artist page - browsing
        composable(route = "artist/{artist_url}", arguments = listOf(navArgument("artist_url") {
            type = NavType.StringType
        })
        ) { backStackEntry ->
            showArtistFromSurf(backStackEntry)
        }
    }


}