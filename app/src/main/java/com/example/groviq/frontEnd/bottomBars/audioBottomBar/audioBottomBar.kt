package com.example.groviq.frontEnd.bottomBars.audioBottomBar

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.dataStructures.songProgressState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.formatTime
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.asyncedImage
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

import com.example.groviq.frontEnd.bottomBars.openTrackSettingsBottomBar
import com.example.groviq.frontEnd.subscribeMe

//the request of navigation radio for track
val showSheet = mutableStateOf<Boolean>(false)

@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun audioBottomSheet(mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, content: @Composable () -> Unit) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    mainSheetDraw(sheetState, showSheet.value, { showSheet.value = !showSheet.value}, mainViewModel,searchViewModel, content)
}

@SuppressLint(
    "UnusedCrossfadeTargetStateParameter"
)
@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mainSheetDraw(sheetState: SheetState, showSheet: Boolean, onToogleSheet: () -> Unit, mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, content: @Composable () -> Unit) {

    // current reactive variables for subscribe //
    val playingHash         by mainViewModel.uiState.subscribeMe { it.playingHash    }
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData   }

    val songProgressUi = songProgressState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        content()

        if (playingHash.isNullOrEmpty())
            return@Box

        if (allAudioData[playingHash] == null)
            return@Box

        //mini box
        closedBar(
            mainViewModel       = mainViewModel,
            searchViewModel     = searchViewModel,
            onToogleSheet       = onToogleSheet,
            songProgressUi      = songProgressUi
        )

        //scaffold
        if (showSheet) {
            openedBar(
                mainViewModel       = mainViewModel,
                searchViewModel     = searchViewModel,
                onToogleSheet       = onToogleSheet,
                songProgressUi      = songProgressUi,
                sheetState          = sheetState
            )
        }
    }
}

