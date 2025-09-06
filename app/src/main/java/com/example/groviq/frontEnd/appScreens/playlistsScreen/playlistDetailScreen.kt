package com.example.groviq.frontEnd.appScreens.playlistsScreen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showDefaultAudioSource
import com.example.groviq.frontEnd.subscribeMe

@Composable
fun playlistDetailList(backStackEntry: NavBackStackEntry, searchViewModel: SearchViewModel, mainViewModel : PlayerViewModel) {

    val audioData           by mainViewModel.uiState.subscribeMe { it.audioData }

    val rawEncoded = backStackEntry.arguments
        ?.getString("playlist_name")
        ?: return

    //getting the decoded full link to album
    val playlistName = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
        ?: return

    val audioSource = audioData[playlistName]

    if (audioSource == null)
        return

    showDefaultAudioSource(playlistName, mainViewModel, searchViewModel)

}