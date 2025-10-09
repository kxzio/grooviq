package com.example.groviq.frontEnd.appScreens.albumsScreen

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.bottomBars.isCreatePlaylistOpened
import com.example.groviq.frontEnd.subscribeMe
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

//the requst of navigation artist
val tabForAlbums = mutableStateOf<Int>(0)

@Composable
@OptIn(
    UnstableApi::class
)
fun albumLists(searchingScreenNav: NavHostController,
               searchViewModel: SearchViewModel,
               mainViewModel: PlayerViewModel,
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

    val listState            = rememberLazyListState()
    val hazeState            = remember { HazeState() }


    Box(Modifier.background(MaterialTheme.colorScheme.background))
    {
        Box(
            Modifier.padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.background))
        {


            Box(Modifier.background(MaterialTheme.colorScheme.background).hazeSource(hazeState))
            {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxWidth().padding(),
                    contentPadding = PaddingValues(top = 86.dp)
                ) {


                    items(albums) { result ->
                        Row(
                            Modifier.clickable
                            {
                                val encoded = Uri.encode(result.key)
                                searchingScreenNav.navigate("${Screen.Albums.route}/album/" + encoded)
                            }.background(MaterialTheme.colorScheme.background).fillMaxWidth().padding(vertical = 8.dp))
                        {
                            val songs = audioData[result.key]?.songIds
                                ?.mapNotNull { allAudioData[it] }
                                ?: emptyList()

                            asyncedImage(
                                songs.firstOrNull(),
                                Modifier.size(85.dp).clip(
                                    RoundedCornerShape(4.dp)
                                ),
                                onEmptyImageCallback = {
                                    Icon(Icons.Rounded.Album, "", Modifier.size(85.dp))
                                },

                                )

                            Column(Modifier.padding(16.dp)){

                                Text(
                                    audioData[result.key]?.nameOfAudioSource ?: "Неизвестный источник"
                                )
                                Text(
                                    audioData[result.key]?.artistsOfAudioSource?.joinToString { it.title } ?: "Неизвестный исполнитель",
                                    color = Color(255, 255, 255, 100), modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                        }

                    }
                }
            }

            Box(Modifier.padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).
            border(1.dp, Color(255, 255, 255, 28), RoundedCornerShape(16.dp)).
            hazeEffect(hazeState).background(Color(20, 20, 20, 140))

            )
            {
                val tabs =
                    listOf(
                        "Streaming",
                        "Local files"
                    )

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val totalWidth =
                        maxWidth
                    val tabWidth =
                        totalWidth / tabs.size

                    val indicatorOffset by animateDpAsState(
                        targetValue = tabWidth * tabForAlbums.value,
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = FastOutSlowInEasing
                        )
                    )

                    Column {
                        Row(Modifier.fillMaxWidth())
                        {
                            tabs.forEachIndexed { index, title ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { tabForAlbums.value = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(title, fontSize = 16.sp, color = if ( tabForAlbums.value == index )
                                        Color(255, 255, 255) else Color(255, 255, 255, 100)
                                    )
                                }

                                if (index < tabs.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(Color(255, 255, 255, 25))
                                            .align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    2.dp
                                )
                        ) {

                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = indicatorOffset
                                    )
                                    .width(
                                        tabWidth
                                    )
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(
                                            0.dp
                                        )
                                    )
                            )
                        }
                    }
                }
            }

        }
    }

}