package com.example.groviq.frontEnd.appScreens.albumsScreen

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.bottomBars.isCreatePlaylistOpened

@Composable
@OptIn(
    UnstableApi::class
)
fun albumLists(searchingScreenNav: NavHostController,
               searchViewModel: SearchViewModel,
               mainViewModel: PlayerViewModel
)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    val audioSources = mainUiState.audioData.entries

    val albums    = audioSources.filter { it.key.contains("http") }

    Column()
    {
        val listState = rememberLazyListState()

        Column(
            Modifier.padding(5.dp))
        {
            Text(
                "Сохраненные альбомы : "
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    albums
                ) { result ->

                    Row(
                        Modifier.clickable
                        {
                            val encoded =
                                Uri.encode(
                                    result.key
                                )
                            searchingScreenNav.navigate(
                                "${Screen.Albums.route}/album/" + encoded
                            )

                        })
                    {
                        val songs = mainUiState.audioData[result.key]?.songIds
                            ?.mapNotNull { mainUiState.allAudioData[it] }
                            ?: emptyList()

                        val mainArt = songs.firstOrNull()?.art

                        if (mainArt != null)
                        {
                            Image(mainArt.asImageBitmap(), null, Modifier.size(85.dp))
                        }
                        else
                        {
                            if (songs.firstOrNull()?.art_link != null)
                            {
                                AsyncImage(
                                    model = songs.firstOrNull()?.art_link,
                                    contentDescription = null,
                                    Modifier.size(85.dp)
                                )
                            }
                            else
                            {
                                Icon(Icons.Rounded.Album, "", Modifier.size(85.dp))
                            }

                        }
                        Text(
                            mainUiState.audioData[result.key]?.nameOfAudioSource ?: "Неизвестный источник"
                        )
                        Text(
                            mainUiState.audioData[result.key]?.artistsOfAudioSource?.joinToString { it.title } ?: "Неизвестный исполнитель"
                        )
                    }

                }
            }
        }
    }
}