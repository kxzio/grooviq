package com.example.groviq.frontEnd

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.playEngine.createListeners
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.bottomBars.audioBottomSheet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.groviq.LocalActivity

//global nav controller for screens
val screenConnectorNavigation = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}
//global nav controller for screens
val searchingNavigation = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

@Composable
fun drawLayout()
{
    val screenConnectorNavigationLocal = rememberNavController()
    val searchingNavigationLocal       = rememberNavController()

    var searchViewModel: SearchViewModel = viewModel(viewModelStoreOwner = LocalActivity() )
    val searchUiState   by searchViewModel.uiState.collectAsState()

    val mainViewModel: PlayerViewModel = viewModel(viewModelStoreOwner   = LocalActivity() )
    val mainUiState     by mainViewModel.uiState.collectAsState()

    //we get view now. now we have to attach listeners for view update
    LaunchedEffect(Unit) {
        createListeners(searchViewModel, mainViewModel)
    }

    //local provider for globalization
    CompositionLocalProvider(
        screenConnectorNavigation provides screenConnectorNavigationLocal,
        searchingNavigation       provides searchingNavigationLocal

    ) {
        Box(Modifier.fillMaxSize())
        {
            audioBottomSheet(mainViewModel)
            {
                connectScreens(
                    searchViewModel,
                    mainViewModel)
            }
        }

    }

}