package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.backEnd.searchEngine.searchState
import com.example.groviq.backEnd.streamProcessor.fetchAudioStream
import com.example.groviq.backEnd.streamProcessor.getStreamForAudio
import com.example.groviq.globalContext
import com.example.groviq.playerManager

@Composable
fun showAudioSourceFromSurf(backStackEntry: NavBackStackEntry,
                            searchViewModel : SearchViewModel, //search view
                            mainViewModel   : PlayerViewModel, //player view
                            )
{

    val searchUiState   by searchViewModel.uiState.collectAsState()
    val mainUiState     by mainViewModel.uiState.collectAsState()

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

            showDefaultAudioSource(albumUrl, searchViewModel, mainViewModel)

        }
    }
}

//render albums or playlists detail screens
@Composable
fun showDefaultAudioSource(audioSourcePath : String, searchViewModel : SearchViewModel, mainViewModel : PlayerViewModel)
{

    val searchUiState   by searchViewModel.uiState.collectAsState()
    val mainUiState     by mainViewModel.uiState.collectAsState()

    val audioSource = mainUiState.audioData[audioSourcePath]

    val songs = mainUiState.audioData[audioSourcePath]?.songIds
        ?.mapNotNull { mainUiState.allAudioData[it] }
        ?: emptyList()

    LazyColumn {
        items(
            songs
        ) { song ->

            Row(Modifier.clickable { playerManager.play(song.link, mainViewModel) })
            {
                if (song.art != null) {
                    Image(song.art!!.asImageBitmap(), null, Modifier.size(35.dp))
                }

                Column()
                {

                    Text(
                        song.title,
                        Modifier.padding(
                            5.dp
                        )
                    )
                    Row()
                    {
                        song.artists.forEach { artist ->

                            Text(
                                artist.title + if (artist != song.artists.last()) ", " else "",
                                Modifier.padding(
                                    5.dp
                                ),
                                maxLines = 1
                            )

                        }
                    }
                    Text(
                        song.stream.streamUrl,
                        Modifier.padding(
                            5.dp
                        ),
                        maxLines = 1,
                        fontSize = 10.sp,
                        color = Color(255, 255, 255, 100)
                    )
                }

            }
        }
    }

}