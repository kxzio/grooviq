package com.example.groviq.frontEnd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

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

    CompositionLocalProvider(
        screenConnectorNavigation provides screenConnectorNavigationLocal,
        searchingNavigation       provides searchingNavigationLocal

    ) {
        connectScreens()
    }

}