package com.example.groviq.frontEnd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.playEngine.createListeners
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.audioBottomSheet
import com.example.groviq.frontEnd.bottomBars.trackSettingsBottomBar

//global nav controller for screens
val screenConnectorNavigation = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

@Composable
fun drawLayout(mainViewModel: PlayerViewModel, searchViewModel : SearchViewModel)
{
    val screenConnectorNavigationLocal = rememberNavController()

    //we get view now. now we have to attach listeners for view update
    LaunchedEffect(Unit) {
        createListeners(searchViewModel, mainViewModel)
        mainViewModel.loadAllFromRoom()
    }

    BackHandler {
        //prevent user from back handler auto-exit
    }

    //local provider for globalization
    CompositionLocalProvider(
        screenConnectorNavigation provides screenConnectorNavigationLocal,
    ) {
        Box(Modifier.fillMaxSize())
        {
            audioBottomSheet(mainViewModel, searchViewModel)
            {
                connectScreens(searchViewModel, mainViewModel)
            }
            trackSettingsBottomBar(mainViewModel)
        }

    }

}