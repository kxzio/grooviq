package com.example.groviq.frontEnd.appScreens.searchingScreen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.backEnd.searchEngine.searchType
import com.example.groviq.frontEnd.Screen
import com.example.groviq.globalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//the request of search navigation
val searchingRequest = mutableStateOf<String>("")

@Composable
fun searchResultsNavigation(searchingScreenNav: NavHostController, searchViewModel : SearchViewModel, mainViewModel : PlayerViewModel)
{

    //focus manager to reset keyboard
    val focusManager = LocalFocusManager.current

    //search job to update results the second after user entered his request
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val searchUiState by searchViewModel.uiState.collectAsState()

    Column()
    {
        OutlinedTextField(
            value = searchingRequest.value,
            onValueChange = { newValue ->
                searchingRequest.value = newValue

                // Отменяем предыдущую задачу поиска
                searchJob?.cancel()

                // Запускаем новую через 1 секунду
                searchJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(350L)
                    if (globalContext != null) {
                        searchViewModel.getResultsOfSearchByString(
                            globalContext!!,
                            searchingRequest.value
                        )
                    }
                }
            },
            label = {
                Text("Поиск треков, альбомов, артистов")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {

                    focusManager.clearFocus()
                    searchJob?.cancel()

                    if (globalContext != null) {
                        searchViewModel.getResultsOfSearchByString(
                            globalContext!!,
                            searchingRequest.value
                        )
                    }
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

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

        val listState = rememberLazyListState()

        Column()
        {
            if (searchUiState.searchInProcess == true)
            {
                CircularProgressIndicator(modifier = Modifier.size(100.dp))
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    searchUiState.searchResults
                ) { result ->

                    Row(Modifier.clickable
                    {
                        if (result.type == searchType.ALBUM) {
                            val link = "https://music.youtube.com/browse/${result.link_id}"
                            val encoded = Uri.encode(link)
                            searchingScreenNav.navigate(
                                "${Screen.Searching.route}/album/$encoded"
                            )
                        }
                        else if (result.type == searchType.ARTIST) {
                            val link = "https://music.youtube.com/channel/${result.link_id}"
                            val encoded = Uri.encode(link)
                            searchingScreenNav.navigate(
                                "${Screen.Searching.route}/artist/$encoded"
                            )
                        }
                        else if (result.type == searchType.SONG) {
                            //val link = "${result.album_url}"
                            //val encoded = Uri.encode(link)
                            //searchingScreenNav.navigate(
                            //    "${Screen.Searching.route}/album/$encoded"
                            //)

                            val trackLink = "https://music.youtube.com/watch?v=${result.link_id}"

                            //request to get info
                            searchViewModel.getTrack(globalContext!!, trackLink, mainViewModel)

                            //wait track and play
                            mainViewModel.waitTrackAndPlay(searchViewModel, trackLink, trackLink)

                        }
                    })
                    {
                        AsyncImage(
                            model = result.image_url,
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
                                text = "https://music.youtube.com/channel/${result.link_id}"
                            )
                            Text(
                                text = result.author,
                                color = Color(
                                    255,
                                    255,
                                    255,
                                    150
                                )
                            )

                            var typeText =
                                ""
                            if (result.type == searchType.SONG) {
                                typeText =
                                    "Композиция"
                            } else if (result.type == searchType.ALBUM) {
                                typeText =
                                    "Альбом"
                            } else if (result.type == searchType.ARTIST) {
                                typeText =
                                    "Артист"
                            }

                            Text(
                                text = typeText,
                                color = Color(
                                    255,
                                    255,
                                    255,
                                    60
                                )
                            )
                        }
                    }

                }
            }
        }

    }
}