package com.example.groviq.frontEnd.appScreens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.screenConnectorNavigation

//the requst of navigation artist
val artistPendingNavigation = mutableStateOf<String?>(null)

//the request of navigation album
val albumPendingNavigation = mutableStateOf<String?>(null)

//the request of navigation radio for track
val trackRadioPendingNavigation = mutableStateOf<String?>(null)


fun openRadio(link: String) {
    trackRadioPendingNavigation.value = link
}

fun openArtist(link: String) {
    artistPendingNavigation.value = link
}

fun openAlbum(link: String) {
    albumPendingNavigation.value = link
}