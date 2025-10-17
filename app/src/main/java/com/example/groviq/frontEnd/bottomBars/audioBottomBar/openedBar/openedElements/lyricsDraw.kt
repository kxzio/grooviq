package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements

import androidx.annotation.OptIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.lyricsProducer.parseLrc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(UnstableApi::class)
@Composable
fun LyricsView(
    lyrics: String?,
) {
    if (lyrics.isNullOrEmpty()) return

    val lines = remember(lyrics) { parseLrc(lyrics) }
    val listState = rememberLazyListState()
    var activeLineIndex by remember { mutableStateOf(0) }

    var isUserScrolling by remember { mutableStateOf(false) }
    var lastUserScrollTime by remember { mutableStateOf(0L) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                isUserScrolling = scrolling
                if (scrolling) lastUserScrollTime = System.currentTimeMillis()
            }
    }

    LaunchedEffect(AppViewModels.player.playerManager.player) {
        while (true) {
            val pos = AppViewModels.player.playerManager.player.currentPosition
            activeLineIndex = lines.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)

            val elapsed = System.currentTimeMillis() - lastUserScrollTime
            if (!isUserScrolling && elapsed > 5000) {
                listState.animateScrollToItem(activeLineIndex)
            }
            delay(100)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeLineIndex
            Text(
                text = line.text,
                color = if (isActive) Color.White else Color(255, 255, 255, 100),
                fontSize = 18.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable {
                        lastUserScrollTime = System.currentTimeMillis()
                        AppViewModels.player.playerManager.player.seekTo(line.timeMs)
                    }
            )
        }
    }
}
