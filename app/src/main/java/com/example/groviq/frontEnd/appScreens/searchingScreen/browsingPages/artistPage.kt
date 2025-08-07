package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.backEnd.searchEngine.searchState
import com.example.groviq.globalContext

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

    if (globalContext != null) {

        LaunchedEffect(artistUrl) {
            searchViewModel.getArtist(
                context = globalContext!!,
                request = artistUrl,
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

            AsyncImage(
                model = searchUiState.currentArtist.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(
                        120.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            4.dp
                        )
                    )
            )

            Text(searchUiState.currentArtist.title)

            Text("Albums : ")

            LazyColumn {
                items(
                    searchUiState.currentArtist.albums
                ) { album ->

                    Row(Modifier.clickable {
                        val link = album.link
                        val encoded = Uri.encode(link)
                        searchingScreenNav.navigate(
                            "album/$encoded"
                        )
                    })
                    {
                        AsyncImage(
                            model = album.image_url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(
                                    65.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        4.dp
                                    )
                                )
                        )

                        Column()
                        {
                            Text(
                                album.album,
                                Modifier.padding(
                                    16.dp
                                )
                            )
                            Text(
                                album.year,
                                Modifier.padding(
                                    16.dp
                                )
                            )
                        }
                    }

                }
            }


        }
    }

}