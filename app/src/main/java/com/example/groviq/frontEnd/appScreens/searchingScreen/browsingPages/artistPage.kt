package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.appScreens.openArtist

@Composable
fun showArtistFromSurf(backStackEntry: NavBackStackEntry,
                       searchViewModel : SearchViewModel, //search view
                       mainViewModel   : PlayerViewModel, //player view
                       searchingScreenNav: NavHostController

)
{

    val searchUiState   by searchViewModel.uiState.collectAsState()
    val mainUiState     by mainViewModel.uiState.collectAsState()

    val rawEncoded = backStackEntry.arguments
        ?.getString("artist_url")
        ?: return

    //decode artist url to real path
    val artistUrl = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
        ?: return

    if (MyApplication.globalContext != null) {

        if (artistUrl != searchUiState.currentArtist.url)
        {
            LaunchedEffect(artistUrl) {
                searchViewModel.getArtist(
                    context = MyApplication.globalContext!!,
                    request = artistUrl,
                    mainViewModel
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ошибки и прогресс
            if (searchUiState.publicErrors != publucErrors.CLEAN) {
                item {
                    when (searchUiState.publicErrors) {
                        publucErrors.NO_INTERNET -> Text("Нет подключения к интернету")
                        publucErrors.NO_RESULTS  -> Text("Ничего не найдено")
                        else -> Unit
                    }
                }
            }

            if (searchUiState.gettersInProcess == true) {
                item {
                    CircularProgressIndicator(modifier = Modifier.size(100.dp))
                }
                return@LazyColumn
            }

            // Обложка артиста
            item {
                AsyncImage(
                    model = searchUiState.currentArtist.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            // Название артиста
            item {
                Text(searchUiState.currentArtist.title)
            }

            // Топ треки
            val topSongs = mainUiState.audioData[artistUrl]?.songIds
                ?.mapNotNull { mainUiState.allAudioData[it] }
                ?: emptyList()

            items(topSongs) { song ->
                SwipeToQueueItem(
                    audioSource = artistUrl,
                    song = song,
                    mainViewModel = mainViewModel,
                    modifier = Modifier.clickable {
                        mainViewModel.setPlayingAudioSourceHash(artistUrl)
                        updatePosInQueue(mainViewModel, song.link)
                        mainViewModel.deleteUserAdds()
                        AppViewModels.player.playerManager.play(song.link, mainViewModel, searchViewModel, true)
                    }
                )
            }


            // Альбомы заголовок
            item {
                Text("Альбомы : ")
            }

            // Альбомы - LazyRow
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchUiState.currentArtist.albums) { album ->
                        Column(
                            Modifier.clickable {
                                val link = album.link
                                val encoded = Uri.encode(link)
                                searchingScreenNav.navigate("${Screen.Searching.route}/album/$encoded")
                            }
                        ) {
                            AsyncImage(
                                model = album.image_url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(album.album, Modifier.padding(top = 5.dp))
                            Text(album.year ?: "", Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }

            // Похожие артисты заголовок
            item {
                Text("Похожие артисты : ")
            }

            // Похожие артисты - LazyRow
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchUiState.currentArtist.relatedArtists) { item ->
                        Column(Modifier.clickable { openArtist(item.url) }) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                            )
                            Text(text = item.title, color = Color.White)
                        }
                    }
                }
            }

            item {
                if (mainUiState.allAudioData[mainUiState.playingHash] != null) {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

}