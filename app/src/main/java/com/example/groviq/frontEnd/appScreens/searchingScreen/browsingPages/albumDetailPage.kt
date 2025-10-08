package com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
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
import com.example.groviq.backEnd.streamProcessor.DownloadManager
import com.example.groviq.backEnd.streamProcessor.fetchAudioSource
import com.example.groviq.backEnd.streamProcessor.fetchAudioStream
import com.example.groviq.frontEnd.InfiniteRoundedCircularProgress
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchingRequest
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.drawPlaylistCover
import com.example.groviq.frontEnd.errorButton
import com.example.groviq.frontEnd.errorsPlaceHoldersScreen
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.frontEnd.iconOutlineButton
import com.example.groviq.frontEnd.subscribeMe
import com.example.groviq.ui.theme.clashFont
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


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

    val audioData                   by mainViewModel.uiState.subscribeMe    { it.audioData }
    val publicErrors                by searchViewModel.uiState.subscribeMe  { it.publicErrors }
    val gettersInProcess            by searchViewModel.uiState.subscribeMe  { it.gettersInProcess }

    val rawEncoded = backStackEntry.arguments
        ?.getString("album_url")
        ?: return

    //getting the decoded full link to album
    val albumUrl = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
        ?: return

    val navigationSaver = Screen.Searching.route + "/album/" + albumUrl

    if (MyApplication.globalContext != null) {

        if (audioData.containsKey(albumUrl).not())
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
            grooviqUI.elements.screenPlaceholders.errorsPlaceHoldersScreen(
                publicErrors    = publicErrors,
                path            = navigationSaver,
                retryCallback   = {
                    searchViewModel.getAlbum(
                        context = MyApplication.globalContext!!,
                        request = albumUrl,
                        mainViewModel
                    )
                },
                addRetryToNothingFound = true
            )

            if (gettersInProcess[navigationSaver] == true || audioData.get(albumUrl)?.isInGenerationProcess == true)
            {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InfiniteRoundedCircularProgress(modifier = Modifier.size(100.dp))
                }
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

    val audioData                   by mainViewModel.uiState.subscribeMe    { it.audioData }
    val publicErrors                by searchViewModel.uiState.subscribeMe  { it.publicErrors }
    val gettersInProcess            by searchViewModel.uiState.subscribeMe  { it.gettersInProcess }

    val rawEncoded = backStackEntry.arguments
        ?.getString("track_url")
        ?: return

    //getting the decoded full link to album
    val albumUrl = Uri.decode(rawEncoded).takeIf { it.isNotBlank() }
        ?: return

    val sourceKey = remember(albumUrl) { "${albumUrl}_source-related-tracks_radio" }

    if (MyApplication.globalContext != null) {

        if (!audioData.containsKey(sourceKey)) {
            LaunchedEffect(albumUrl) {
                searchViewModel.addRelatedTracksToAudioSource(MyApplication.globalContext!!, albumUrl,mainViewModel)
            }
        }

        val navigationSaver = Screen.Searching.route + "/radio/" + albumUrl

        Column()
        {
            grooviqUI.elements.screenPlaceholders.errorsPlaceHoldersScreen(
                publicErrors    = publicErrors,
                path            = navigationSaver,
                retryCallback   = {
                    CoroutineScope(Dispatchers.Main).launch {
                        searchViewModel.addRelatedTracksToAudioSource(MyApplication.globalContext!!, albumUrl,mainViewModel)
                    }
                },
                addRetryToNothingFound = true
            )

            if (gettersInProcess[navigationSaver] == true || audioData.get(sourceKey)?.isInGenerationProcess == true)
            {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InfiniteRoundedCircularProgress(modifier = Modifier.size(100.dp))
                }
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

    val audioData                       by mainViewModel.uiState.subscribeMe    { it.audioData }
    val allAudioData                    by mainViewModel.uiState.subscribeMe    { it.allAudioData }
    val playingHash                     by mainViewModel.uiState.subscribeMe    { it.playingHash }
    val listState                       = rememberLazyListState()

    var isPreviewVisible                by remember { mutableStateOf(false) }
    var scale                           by remember { mutableStateOf(1f) }

    val audioSource = audioData[audioSourcePath]

    if (audioSource == null)
        return

    val songs = audioData[audioSourcePath]?.songIds
        ?.mapNotNull { allAudioData[it] }
        ?: emptyList()


    if (audioSource?.isInGenerationProcess == true)
    {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InfiniteRoundedCircularProgress(modifier = Modifier.size(100.dp))
        }
        return
    }

    val isPlaylist = mainViewModel.isPlaylist(audioSourcePath)

    Box {

        if (songs.isEmpty())
            return@Box


        Column(Modifier.fillMaxSize()) {
            LazyColumn(state = listState) {

                item {

                    Box(Modifier.fillMaxSize())
                    {
                        Box(Modifier.matchParentSize())
                        {
                            grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                                audioSourcePath,
                                audioData = audioData,
                                allAudioData = allAudioData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .alpha(0.6f)
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0.40f to Color.Black,
                                                1.0f to Color.Transparent,
                                                startY = 0f,
                                                endY = size.height * 1.0f
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                ,
                                blur = 30f,
                                drawOnlyFirst = true
                            )
                        }

                        Column()
                        {
                            Spacer(Modifier.height(8.dp))

                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally)
                            {
                                Spacer(Modifier.height(24.dp))

                                Box(Modifier.fillMaxWidth(0.7f))
                                {
                                    grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                                        audioSourcePath,
                                        audioData = audioData,
                                        allAudioData = allAudioData,
                                        modifier = Modifier. fillMaxSize()
                                            .aspectRatio(1f).background(Color(0, 0, 0, 0)).clickable {
                                                isPreviewVisible = true
                                                scale = 0.8f
                                            }.clip(RoundedCornerShape(8.dp))
                                    )
                                }


                                Spacer(Modifier.height(15.dp))

                                if (audioSource!!.nameOfAudioSource.isNullOrEmpty())
                                {
                                    Text(audioSourcePath, fontSize = 33.sp, letterSpacing = 0.015.em,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                else
                                {
                                    Text(audioSource!!.nameOfAudioSource, fontSize = 33.sp, letterSpacing = 0.015.em,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }


                                if (audioSource!!.artistsOfAudioSource.isNullOrEmpty().not())
                                {
                                    Spacer(Modifier.height(15.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier

                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(40, 40, 40, 20))
                                    .border(1.dp, Color(255, 255, 255, 30), RoundedCornerShape(8.dp))

                                    ) {

                                        audioSource!!.artistsOfAudioSource.forEach { artist ->

                                            Row(modifier = Modifier.padding(6.dp))
                                            {
                                                Icon(Icons.Rounded.Person, "", tint = Color(255, 255, 255, 150))

                                                Text(text = artist.title, color = Color(255, 255, 255, 150), modifier = Modifier.padding(horizontal = 3.dp).clickable {
                                                    openArtist(artist.url)
                                                })
                                            }


                                        }


                                    }

                                }

                                if (audioSource!!.yearOfAudioSource.isNullOrEmpty().not())
                                {
                                    Spacer(Modifier.height(16.dp))
                                    Text(text = audioSource!!.yearOfAudioSource, fontSize = 16.sp, color = Color(255, 255, 255, 100))
                                }

                                Spacer(Modifier.height(40.dp))


                            }

                            Column(Modifier.padding(horizontal = 16.dp)) {

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                )
                                {

                                    if (audioSource.isExternal)
                                    {
                                        Row(verticalAlignment = Alignment.CenterVertically)
                                        {
                                            Icon(Icons.Rounded.AudioFile, "", tint = Color(255, 255, 255, 90),)

                                            Text("From local storage",
                                                color = Color(255, 255, 255, 90),
                                                fontWeight = FontWeight.Normal,
                                                letterSpacing = 0.04.em,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }

                                    }

                                    if (isPlaylist.not() && audioSource.isExternal.not())
                                    {
                                        Button(
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(40, 40, 40, 20)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(255, 255, 255, 20)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            onClick = {
                                                mainViewModel.toggleStrictSaveAudioSource(audioSourcePath)
                                                mainViewModel.saveAudioSourcesToRoom()
                                                mainViewModel.saveSongsFromSourceToRoom(audioSourcePath)
                                            })
                                        {
                                            if (audioData[audioSourcePath]?.shouldBeSavedStrictly ?: false)
                                            {
                                                Icon(Icons.Rounded.Close, "", tint = Color(255, 255, 255, 170))
                                            }
                                            else
                                            {
                                                Icon(Icons.Rounded.Add, "", tint = Color(255, 255, 255, 170))
                                            }
                                        }
                                    }

                                    if (songs.isNullOrEmpty().not() && audioSource.isExternal.not() )
                                    {
                                        Button(
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(40, 40, 40, 20)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(255, 255, 255, 20)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            onClick = {

                                                if (songs.all { it.localExists() })
                                                {
                                                    songs.forEach { track ->
                                                        DownloadManager.deleteDownloadedAudioFile(mainViewModel, track.link)
                                                    }
                                                }
                                                else
                                                {
                                                    songs.forEach { track ->
                                                        if (track.isExternal == false)
                                                            DownloadManager.enqueue(mainViewModel, track.link)
                                                    }
                                                    DownloadManager.start()
                                                }

                                            })
                                        {
                                            if (songs.all { it.localExists() } )
                                            {
                                                Icon(Icons.Rounded.DeleteOutline, "", tint = Color(255, 255, 255, 170))

                                                Text("Delete", Modifier.padding(start = 6.dp),
                                                    color = Color(255, 255, 255, 170),
                                                    fontWeight = FontWeight.Normal,
                                                    letterSpacing = 0.04.em
                                                )
                                            }
                                            else {
                                                Icon(Icons.Rounded.Download, "", tint = Color(255, 255, 255, 170))

                                                Text("Download", Modifier.padding(start = 6.dp),
                                                    color = Color(255, 255, 255, 170),
                                                    fontWeight = FontWeight.Normal,
                                                    letterSpacing = 0.04.em
                                                )
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }

                }

                item {
                    Spacer(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
                }

                items(
                    songs, key = { it.link }
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
                    Spacer(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
                }

                item {
                   Spacer(Modifier.height(85.dp))
                }

                item {
                    if (allAudioData[playingHash] != null) {
                        Spacer(Modifier.height(95.dp))
                    }
                }
            }


        }

        if (isPreviewVisible) {

            val alpha by animateFloatAsState(targetValue = 0.7f)

            BackHandler { isPreviewVisible = false }

            val scale = remember { mutableStateOf(1f) }
            val offset = remember { mutableStateOf(Offset.Zero) }

            val density = LocalDensity.current
            val config = LocalConfiguration.current

            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (scale.value * zoomChange).coerceIn(1f, 3f)
                scale.value = newScale

                val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
                val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

                val maxX = ((scale.value - 1) * 0.9f * screenWidthPx / 2)
                val maxY = ((scale.value - 1) * 0.9f * screenHeightPx / 2)

                offset.value = Offset(
                    x = (offset.value.x + panChange.x).coerceIn(-maxX, maxX),
                    y = (offset.value.y + panChange.y).coerceIn(-maxY, maxY)
                )
            }

            Popup(

            )
            {
                Box(
                    Modifier
                        .zIndex(3f)
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = alpha))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isPreviewVisible = false }
                )

                Box(
                    Modifier
                        .zIndex(4f).fillMaxSize()                    .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isPreviewVisible = false }
                        .transformable(state = transformableState),
                    contentAlignment = Alignment.Center
                ) {
                    grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                        audioSourcePath,
                        audioData = audioData,
                        allAudioData = allAudioData,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                translationX = offset.value.x
                                translationY = offset.value.y
                            }
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

        }
    }

}


