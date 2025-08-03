package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.globalContext

@Composable
fun showAudioSourceFromSurf(backStackEntry: NavBackStackEntry)
{
    var searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val searchUiState by searchViewModel.uiState.collectAsState()

    var mainViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val mainUiState by mainViewModel.uiState.collectAsState()

    val rawEncoded = backStackEntry.arguments
        ?.getString("album_url")
        ?: return

    //getting the decoded full link to album
    val albumUrl = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
        ?: return

    if (globalContext != null) {

        LaunchedEffect(albumUrl) {
            searchViewModel.getAlbum(
                context = globalContext!!,
                request = albumUrl,
                mainViewModel
            )
        }

        Column()
        {
            if (searchUiState.publicErrors != publucErrors.CLEAN)
            {
                if (searchUiState.publicErrors == publucErrors.NO_INTERNET)
                {
                    Text(text = "Нет подключения к интернету")
                }
                else if (searchUiState.publicErrors == publucErrors.NO_RESULTS)
                {
                    Text(text = "Ничего не найдено")
                }
            }

            if (searchUiState.gettersInProcess == true)
            {
                CircularProgressIndicator(modifier = Modifier.size(100.dp))
                return@Column
            }

            showDefaultAudioSource(albumUrl)

        }
    }
}

//render albums or playlists detail screens
@Composable
fun showDefaultAudioSource(audioSourcePath : String)
{
    var searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val searchUiState by searchViewModel.uiState.collectAsState()

    var mainViewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val mainUiState by mainViewModel.uiState.collectAsState()

    val songs = mainUiState.audioData[audioSourcePath]?.songIds
        ?.mapNotNull { mainUiState.allAudioData[it] }
        ?: emptyList()

    LazyColumn {
        items(
            songs
        ) { song ->

            Row()
            {
                Text(
                    song.title,
                    Modifier.padding(
                        16.dp
                    )
                )
            }
        }
    }

}