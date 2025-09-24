package com.example.groviq.frontEnd.appScreens.mainScreen

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Button
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.frontEnd.Screen
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.bottomBars.isCreatePlaylistOpened
import com.example.groviq.frontEnd.drawPlaylistCover
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.frontEnd.subscribeMe
import com.example.groviq.ui.theme.SfProDisplay
import com.example.groviq.ui.theme.clashFont
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.filter

fun randomBrightColor(): Color {
    val hue = (0..360).random().toFloat()
    val saturation = 0.7f + (0..30).random() / 100f  // 0.7–1.0
    val value = 0.8f + (0..20).random() / 100f       // 0.8–1.0
    val hsv = floatArrayOf(hue, saturation, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
@Composable
fun AutoFitText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Bold,
    padding: Dp = 8.dp
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { (maxWidth - padding * 2).toPx() }
        val maxHeightPx = with(density) { (maxHeight - padding * 2).toPx() }

        var fontSize by remember { mutableStateOf(20.sp) }
        var readyToDraw by remember { mutableStateOf(false) }

        val textMeasurer = rememberTextMeasurer()

        val words = text.split(" ")
        val processedLines = if (words.size <= maxLines) {
            words
        } else {
            words.take(maxLines - 1) + listOf(words[maxLines - 1] + "…")
        }
        val processedText = processedLines.joinToString("\n")

        LaunchedEffect(processedText, maxWidthPx, maxHeightPx) {
            var currentFontSize = maxHeight / maxLines
            var currentFontSizeSp = with(density) { currentFontSize.toSp() }

            var fits = false
            while (!fits && currentFontSizeSp.value > 6f) {
                val result = textMeasurer.measure(
                    text = AnnotatedString(processedText),
                    style = TextStyle(
                        fontSize = currentFontSizeSp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        lineHeight = currentFontSizeSp * 1.2f
                    ),
                    constraints = Constraints(maxWidth = maxWidthPx.toInt()),
                    softWrap = false
                )

                fits = result.size.height <= maxHeightPx && result.lineCount <= maxLines

                if (!fits) currentFontSizeSp *= 0.95f
            }

            fontSize = currentFontSizeSp
            readyToDraw = true
        }

        if (readyToDraw) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = processedText,
                    fontSize = fontSize,
                    maxLines = maxLines,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}





@Composable
@OptIn(
    UnstableApi::class
)
fun mainScreen(mainViewModel : PlayerViewModel, playlistNavigationLocal: NavHostController)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    val audioData               by mainViewModel.uiState.subscribeMe { it.audioData }
    val allAudioData            by mainViewModel.uiState.subscribeMe { it.allAudioData }

    val playlistsAutoGenerated = remember(mainUiState.audioData) {
        mainUiState.audioData.entries.filter { it.value.autoGenerated }
    }

    val haze = remember { HazeState() }

    Box()
    {
        val listState = rememberLazyGridState()

        val today =   playlistsAutoGenerated.filter{
            it.key.contains("DAILY_PLAYLIST_AUTOGENERATED")
        }

        if (today.firstOrNull() != null)
        {
            Box(Modifier.hazeSource(haze))
            {
                grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                    today.firstOrNull()!!.key,
                    audioData    = audioData,
                    allAudioData = allAudioData,
                    modifier = Modifier.alpha(0.05f),
                    blur = 3f,
                    drawOnlyFirst = true
                )
            }
        }


        Column(
            Modifier.padding(16.dp))
        {

            val genres = playlistsAutoGenerated.filter {
                mainUiState.audioData[it.key]?.nameOfAudioSource!!.contains("My mood") ||
                mainUiState.audioData[it.key]?.nameOfAudioSource!!.contains("You may like")
            }

            val artists = playlistsAutoGenerated - today - genres

            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(255, 255, 255, 30))
                .hazeEffect(haze))
            {
                LazyVerticalGrid(
                    state = listState,
                    verticalArrangement   = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(16.dp)
                ) {

                    item(span = { GridItemSpan(2) }) {
                        Text("Listen today", fontSize = 22.sp)
                    }
                    items(today) { result ->

                        Box(
                            modifier = Modifier
                                .height(50.dp)
                                .clip(
                                    RoundedCornerShape(8.dp)
                                )
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.0f))
                                .clickable {
                                    val encoded = Uri.encode(result.key)
                                    playlistNavigationLocal.navigate("${Screen.Home.route}/playlist/$encoded")
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center)
                            {
                                grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                                    result.key,
                                    audioData    = audioData,
                                    allAudioData = allAudioData,
                                    modifier = Modifier.alpha(0.7f).graphicsLayer
                                    {
                                        scaleX = 5f
                                        scaleY = 5f
                                    },
                                    blur = 80f,
                                    drawOnlyFirst = true
                                )

                                Text(
                                    text = mainUiState.audioData[result.key]?.nameOfAudioSource ?: "",
                                    fontFamily = SfProDisplay,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )

                            }

                        }

                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(255, 255, 255, 30))
                .hazeEffect(haze))
            {
                LazyVerticalGrid(
                    state = listState,
                    verticalArrangement   = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(16.dp)
                ) {

                    item(span = { GridItemSpan(2) }) {
                        Text("Genres and moods", fontSize = 22.sp)
                    }
                    items(genres) { result ->

                        Box(
                            modifier = Modifier
                                .height(50.dp)
                                .clip(
                                    RoundedCornerShape(8.dp)
                                )
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.0f))
                                .clickable {
                                    val encoded = Uri.encode(result.key)
                                    playlistNavigationLocal.navigate("${Screen.Home.route}/playlist/$encoded")
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center)
                            {
                                grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                                    result.key,
                                    audioData    = audioData,
                                    allAudioData = allAudioData,
                                    modifier = Modifier.alpha(0.7f).graphicsLayer
                                    {
                                        scaleX = 5f
                                        scaleY = 5f
                                    },
                                    blur = 80f,
                                    drawOnlyFirst = true
                                )

                                Text(
                                    text = mainUiState.audioData[result.key]?.nameOfAudioSource ?: "",
                                    fontFamily = SfProDisplay,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )

                            }

                        }

                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(255, 255, 255, 30))
                .hazeEffect(haze))
            {
                LazyVerticalGrid(
                    state = listState,
                    verticalArrangement   = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(16.dp)
                ) {

                    item(span = { GridItemSpan(2) }) {
                        Text("Favourite artists", fontSize = 22.sp)
                    }
                    items(artists) { result ->

                        Box(
                            modifier = Modifier
                                .height(50.dp)
                                .clip(
                                    RoundedCornerShape(8.dp)
                                )
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.0f))
                                .clickable {
                                    val encoded = Uri.encode(result.key)
                                    playlistNavigationLocal.navigate("${Screen.Home.route}/playlist/$encoded")
                                },
                            contentAlignment = Alignment.Center
                        ) {

                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center)
                            {
                                grooviqUI.elements.albumCoverPresenter.drawPlaylistCover(
                                    result.key,
                                    audioData    = audioData,
                                    allAudioData = allAudioData,
                                    modifier = Modifier.alpha(0.7f).graphicsLayer
                                    {
                                        scaleX = 5f
                                        scaleY = 5f
                                    },
                                    blur = 80f,
                                    drawOnlyFirst = true
                                )

                                Text(
                                    text = mainUiState.audioData[result.key]?.nameOfAudioSource ?: "",
                                    fontFamily = SfProDisplay,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )

                            }

                        }

                    }
                }
            }

        }
    }
}
