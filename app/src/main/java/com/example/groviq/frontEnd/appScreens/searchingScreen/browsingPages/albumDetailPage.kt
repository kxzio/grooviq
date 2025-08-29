package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.groviq.AppViewModels
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.playEngine.updatePosInQueue
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.backEnd.searchEngine.searchState
import com.example.groviq.backEnd.streamProcessor.fetchAudioSource
import com.example.groviq.backEnd.streamProcessor.fetchAudioStream
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.asyncedImage


@OptIn(
    UnstableApi::class
)
@Composable
fun showAudioSourceFromSurf(backStackEntry: NavBackStackEntry,
                            searchViewModel : SearchViewModel, //search view
                            mainViewModel   : PlayerViewModel, //player view
                            searchingScreenNav: NavHostController
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

    if (MyApplication.globalContext != null) {

        if (mainUiState.audioData.containsKey(albumUrl).not())
        {
            LaunchedEffect(albumUrl) {
                searchViewModel.getAlbum(
                    context = MyApplication.globalContext!!,
                    request = albumUrl,
                    mainViewModel
                )
            }
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

            LaunchedEffect(albumUrl)
            {
                fetchAudioSource(albumUrl, mainViewModel)
            }

            //update focus to prevent deleting the audio source if this path is UI opened
            mainViewModel.updateBrowserHashFocus(albumUrl)

            showDefaultAudioSource(albumUrl, mainViewModel, searchViewModel)
        }
    }
}

@OptIn(
    UnstableApi::class
)
@Composable
fun showAudioSourceOfRadio(backStackEntry: NavBackStackEntry,
                            searchViewModel : SearchViewModel, //search view
                            mainViewModel   : PlayerViewModel, //player view
                            searchingScreenNav: NavHostController
)
{

    val searchUiState   by searchViewModel.uiState.collectAsState()
    val mainUiState     by mainViewModel.uiState.collectAsState()

    val rawEncoded = backStackEntry.arguments
        ?.getString("track_url")
        ?: return

    //getting the decoded full link to album
    val albumUrl = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
        ?: return

    val sourceKey = remember(albumUrl) { "${albumUrl}_source-related-tracks_radio" }

    if (MyApplication.globalContext != null) {

        if (!mainUiState.audioData.containsKey(sourceKey)) {
            LaunchedEffect(albumUrl) {
                searchViewModel.addRelatedTracksToAudioSource(
                    context = MyApplication.globalContext!!,
                    request = albumUrl,
                    mainViewModel = mainViewModel
                )
            }
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

            LaunchedEffect(sourceKey)
            {
                fetchAudioSource(sourceKey, mainViewModel)
            }

            //update focus to prevent deleting the audio source if this path is UI opened
            mainViewModel.updateBrowserHashFocus(sourceKey)

            showDefaultAudioSource(sourceKey, mainViewModel, searchViewModel)
        }
    }
}

//render albums or playlists detail screens
@OptIn(
    UnstableApi::class
)
@Composable
fun showDefaultAudioSource(audioSourcePath : String, mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel )
{

    val mainUiState     by mainViewModel.uiState.collectAsState()

    val audioSource = mainUiState.audioData[audioSourcePath]

    if (audioSource == null)
        return

    val songs = mainUiState.audioData[audioSourcePath]?.songIds
        ?.mapNotNull { mainUiState.allAudioData[it] }
        ?: emptyList()

    val isPlaylist = mainViewModel.isPlaylist(audioSourcePath)

    Column {

        Column()
        {
            asyncedImage(
                songs.firstOrNull(),
                Modifier.size(85.dp),
                onEmptyImageCallback = {
                    Icon(Icons.Rounded.PlaylistPlay, "", Modifier.size(85.dp))
                }
            )

            if (audioSource!!.nameOfAudioSource.isNullOrEmpty())
            {
                Text(audioSourcePath)
            }
            else
            {
                Text(audioSource!!.nameOfAudioSource)
            }

            if (audioSource!!.artistsOfAudioSource.isNullOrEmpty().not())
            {
                Column {

                    audioSource!!.artistsOfAudioSource.forEach { artist ->

                        Row()
                        {
                            Icon(Icons.Rounded.Person, "", tint = Color(0, 0, 0), modifier = Modifier.background(
                                shape = CircleShape, color = Color(255, 255, 255) ) )
                            Text(artist.title, Modifier.clickable {
                                openArtist(artist.url)
                            })
                        }


                    }


                }

            }


            if (audioSource!!.yearOfAudioSource.isNullOrEmpty().not())
            {
                Text(audioSource!!.yearOfAudioSource)
            }

            if (isPlaylist.not())
            {
                Button(onClick = {
                    mainViewModel.toggleStrictSaveAudioSource(audioSourcePath)
                    mainViewModel.saveAudioSourcesToRoom()
                    mainViewModel.saveSongsFromSourceToRoom(audioSourcePath)
                })
                {
                    if (mainUiState.audioData[audioSourcePath]?.shouldBeSavedStrictly ?: false)
                    {
                        Text("Удалить")
                    }
                    else
                    {
                        Text("Сохранить")
                    }
                }
            }

        }

        if (songs.isEmpty())
            return@Column

        LazyColumn {
            items(
                songs
            ) { song ->

                SwipeToQueueItem(audioSource = audioSourcePath, song = song, mainViewModel = mainViewModel,
                    Modifier.clickable
                    {
                        mainViewModel.setPlayingAudioSourceHash(audioSourcePath)
                        updatePosInQueue(mainViewModel, song.link)
                        mainViewModel.deleteUserAdds()
                        AppViewModels.player.playerManager.play(song.link, mainViewModel, searchViewModel,true)
                    })

            }

            item {
                if (mainUiState.allAudioData[mainUiState.playingHash] != null) {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }


}