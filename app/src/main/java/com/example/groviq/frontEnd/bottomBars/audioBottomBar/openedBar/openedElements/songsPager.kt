package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements

import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.asyncedImage
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.frontEnd.subscribeMe
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs


@OptIn(UnstableApi::class)
@Composable
fun grooviqUI.elements.openedElements.drawPagerForSongs(mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, allAudioData: MutableMap<String, songData>)
{
    val currentQueue        by mainViewModel.uiState.subscribeMe { it.currentQueue   }
    val posInQueue          by mainViewModel.uiState.subscribeMe { it.posInQueue     }

    val songsInQueue = currentQueue

    val pagerState = rememberPagerState(
        initialPage = posInQueue,
        pageCount = { songsInQueue.size }
    )

    val nestedScrollConnection = remember {
        object :
            NestedScrollConnection {}
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = 0.2f,
            snapAnimationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                }
            }
    ) { page ->

        val songInQueue = allAudioData[songsInQueue[page].hashKey]

        val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).coerceIn(-1f, 1f)

        val alpha = 1f - abs(pageOffset) * 0.5f

        val rotationY = pageOffset * 30f

        val scale = 1f - abs(pageOffset) * 0.1f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(25.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha
                        this.rotationY = rotationY
                        this.scaleX = scale
                        this.scaleY = scale
                        this.cameraDistance = 12 * density
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                asyncedImage(
                    songInQueue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    blurRadius = 0f,
                    turnOffPlaceholders = true,
                    blendGrad = false,
                )
            }
        }
    }

    var ignoreNextPageChange by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->

                if (ignoreNextPageChange) {
                    ignoreNextPageChange = false
                    return@collect
                }

                if (page < 0 || page >= songsInQueue.size) return@collect

                if (page == mainViewModel.uiState.value.posInQueue) return@collect

                mainViewModel.setPosInQueue(page)

                try {
                    AppViewModels.player.playerManager.playAtIndex(mainViewModel, searchViewModel, page)
                } catch (e: Throwable) {

                }
            }
    }

    val coroutineScope = rememberCoroutineScope()

    val currentTrackId by mainViewModel.uiState.subscribeMe {
        it.currentQueue.getOrNull(it.posInQueue)?.id
    }

    LaunchedEffect(posInQueue, songsInQueue.size) {
        val newIndex = posInQueue

        val pageTrackId = songsInQueue.getOrNull(newIndex)?.id
        if (pageTrackId == currentTrackId && pagerState.settledPage == newIndex) {
            return@LaunchedEffect
        }

        if (newIndex != -1 && newIndex != pagerState.settledPage) {
            coroutineScope.launch {

                snapshotFlow { pagerState.pageCount }
                    .first { it == songsInQueue.size }

                ignoreNextPageChange = true
                pagerState.animateScrollToPage(
                    newIndex,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                )
                ignoreNextPageChange = false
            }
        }
    }
}