package com.example.groviq.frontEnd.appScreens.searchingScreen

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.publucErrors
import com.example.groviq.backEnd.searchEngine.searchType
import com.example.groviq.frontEnd.InfiniteRoundedCircularProgress
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.errorButton
import com.example.groviq.frontEnd.errorsPlaceHoldersScreen
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.frontEnd.iconOutlineButton
import com.example.groviq.frontEnd.subscribeMe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//the request of search navigation
val searchingRequest = mutableStateOf<String>("")

@OptIn(
    UnstableApi::class
)
@Composable
fun searchResultsNavigation(searchingScreenNav: NavHostController, searchViewModel : SearchViewModel, mainViewModel : PlayerViewModel)
{

    //focus manager to reset keyboard
    val focusManager = LocalFocusManager.current

    //search job to update results the second after user entered his request
    var searchJob by remember { mutableStateOf<Job?>(null) }

    //reactibe subscribes
    val publicErrors        by searchViewModel.uiState.subscribeMe { it.publicErrors    }
    val searchInProgress    by searchViewModel.uiState.subscribeMe { it.searchInProcess }
    val searchResults       by searchViewModel.uiState.subscribeMe { it.searchResults   }


    Column()
    {
        OutlinedTextField(
            value = searchingRequest.value,
            onValueChange = { newValue ->
                searchingRequest.value = newValue

                searchJob?.cancel()

                searchJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(350L)
                    if (MyApplication.globalContext != null) {
                        searchViewModel.getResultsOfSearchByString(
                            MyApplication.globalContext!!,
                            searchingRequest.value
                        )
                    }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.Search, "")},
            label = {
                Text("Search...")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {

                    focusManager.clearFocus()
                    searchJob?.cancel()

                    if (MyApplication.globalContext != null) {
                        searchViewModel.getResultsOfSearchByString(
                            MyApplication.globalContext!!,
                            searchingRequest.value
                        )
                    }
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        grooviqUI.elements.screenPlaceholders.errorsPlaceHoldersScreen(
            publicErrors    = publicErrors,
            path            = "search",
            retryCallback   = {
                searchViewModel.getResultsOfSearchByString(
                    MyApplication.globalContext!!,
                    searchingRequest.value
                )
            }
        )

        val listState = rememberLazyListState()

        Column(Modifier.padding(horizontal = 16.dp))
        {
            if (searchInProgress == true)
            {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InfiniteRoundedCircularProgress(modifier = Modifier.size(100.dp))
                }

                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(searchResults) { count, result ->

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable
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

                            val trackLink = "https://music.youtube.com/watch?v=${result.link_id}"

                            //request to get info
                            searchViewModel.getTrack(MyApplication.globalContext!!, trackLink, mainViewModel)

                            //wait track and play
                            mainViewModel.waitTrackAndPlay(searchViewModel, trackLink, trackLink)

                        }
                    })
                    {
                        val normalizedResult = result.title.trim().replace("\\s+".toRegex(), " ").lowercase()
                        val normalizedSearch = searchingRequest.value.trim().replace("\\s+".toRegex(), " ").lowercase()

                        val sizeModifierIfResultIsCool = normalizedResult == normalizedSearch && count == 0

                        val sizeModValue = 15

                            asyncedImage(
                                result.image_url,
                                modifier = Modifier
                                    .size(65.dp + if (sizeModifierIfResultIsCool) sizeModValue.dp else 0.dp)
                                    .clip(RoundedCornerShape(if (result.type == searchType.ARTIST) 45.dp else 8.dp))
                            )

                            Column(
                                Modifier.padding(horizontal = 16.dp, vertical = if (sizeModifierIfResultIsCool) 16.dp else 0.dp))
                            {
                                Text(
                                    fontWeight = if (sizeModifierIfResultIsCool) FontWeight.Normal else FontWeight.Normal,
                                    text = result.title,
                                    fontSize = if (sizeModifierIfResultIsCool) 22.sp else 16.sp
                                )

                                if (result.author.isNullOrEmpty().not())
                                {
                                    Text(
                                        text = result.author,
                                        color = Color(
                                            255,
                                            255,
                                            255,
                                            150
                                        ),
                                        fontSize = if (sizeModifierIfResultIsCool) 18.sp else 16.sp
                                    )
                                }

                                var typeText =
                                    ""
                                if (result.type == searchType.SONG) {
                                    typeText =
                                        "Song"
                                } else if (result.type == searchType.ALBUM) {
                                    typeText =
                                        "Album"
                                } else if (result.type == searchType.ARTIST) {
                                    typeText =
                                        "Artist"
                                }

                                Text(
                                    text = typeText,
                                    fontSize = if (sizeModifierIfResultIsCool) 18.sp else 16.sp,
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