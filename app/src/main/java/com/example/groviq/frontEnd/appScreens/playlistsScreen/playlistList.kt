package com.example.groviq.frontEnd.appScreens.playlistsScreen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.frontEnd.Screen

@Composable
fun playlistList(mainViewModel : PlayerViewModel, playlistNavigationLocal: NavHostController)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    val audioSources = mainUiState.audioData.entries

    val playlists    = audioSources.filter { !it.key.contains("http") }

    Column()
    {
        val listState = rememberLazyListState()

        Column(Modifier.padding(5.dp))
        {
            Text(
                "Плейлисты : "
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    playlists
                ) { result ->

                    Row(
                        Modifier.clickable
                        {
                            val encoded =
                                Uri.encode(
                                    result.key
                                )
                            playlistNavigationLocal.navigate(
                                "${Screen.Playlists.route}/playlist/" + encoded
                            )

                        })
                    {
                        Text(
                            result.key
                        )
                    }

                }
            }
        }
    }
}