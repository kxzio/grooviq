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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.bottomBars.isCreatePlaylistOpened
import com.example.groviq.frontEnd.subscribeMe

//the requst of navigation artist
val tabForAlbums = mutableStateOf<Int>(0)

@Composable
@OptIn(
    UnstableApi::class
)
fun albumLists(searchingScreenNav: NavHostController,
               searchViewModel: SearchViewModel,
               mainViewModel: PlayerViewModel
)
{
    // current reactive variables for subscribe //
    val audioData           by mainViewModel.uiState.subscribeMe { it.audioData      }
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData   }

    val audioSources = audioData.entries

    val albums = when (tabForAlbums.value)
    {

        0 -> { audioSources.filter {
            !mainViewModel.isPlaylist(it.key)
                    && audioData[it.key]?.shouldBeSavedStrictly ?: false
                    && audioData[it.key]?.isExternal == false } }

        1 -> { audioSources.filter {
            !mainViewModel.isPlaylist(it.key)
                    && audioData[it.key]?.shouldBeSavedStrictly ?: false
                    && audioData[it.key]?.isExternal == true } }

        else -> { audioSources.filter {
            !mainViewModel.isPlaylist(it.key)
                    && audioData[it.key]?.shouldBeSavedStrictly ?: false
                    && audioData[it.key]?.isExternal == true } }
    }

    Column()
    {
        val listState = rememberLazyListState()

        Column(
            Modifier.padding(5.dp))
        {
            Text(
                "Albums"
            )

            Row(Modifier.fillMaxWidth())
            {
                Button({
                    tabForAlbums.value = 0
                },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tabForAlbums.value == 0) MaterialTheme.colorScheme.primary else
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.weight(1f)
                    )
                {
                    Text("Streaming")
                }

                Button({
                    tabForAlbums.value = 1
                },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tabForAlbums.value == 1) MaterialTheme.colorScheme.primary else
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.weight(1f)
                )
                {
                    Text("Local files")
                }
            }

            LaunchedEffect(albums) {
                listState.scrollToItem(0)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(albums) { result ->
                    Row(
                        Modifier.clickable
                        {
                            val encoded = Uri.encode(result.key)
                            searchingScreenNav.navigate("${Screen.Albums.route}/album/" + encoded)
                        })
                    {
                        val songs = audioData[result.key]?.songIds
                            ?.mapNotNull { allAudioData[it] }
                            ?: emptyList()

                        asyncedImage(
                            songs.firstOrNull(),
                            Modifier.size(85.dp),
                            onEmptyImageCallback = {
                                Icon(Icons.Rounded.Album, "", Modifier.size(85.dp))
                            }
                        )

                        Column{
                            Text(
                                audioData[result.key]?.nameOfAudioSource ?: "Неизвестный источник"
                            )
                            Text(
                                audioData[result.key]?.artistsOfAudioSource?.joinToString { it.title } ?: "Неизвестный исполнитель"
                            )
                        }

                    }

                }
            }
        }
    }
}