package com.example.groviq.frontEnd.bottomBars.audioBottomBar

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.songProgressState
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.closedBar.closedBar
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedBar
import com.example.groviq.frontEnd.subscribeMe
import dev.chrisbanes.haze.HazeState

//the request of navigation radio for track
val showSheet = mutableStateOf<Boolean>(false)

@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun audioBottomSheet(mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, hazeState: HazeState, content: @Composable () -> Unit) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    mainSheetDraw(sheetState, showSheet.value, { showSheet.value = !showSheet.value}, mainViewModel,searchViewModel, content, hazeState)
}

@SuppressLint(
    "UnusedCrossfadeTargetStateParameter"
)
@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mainSheetDraw(sheetState: SheetState, showSheet: Boolean, onToogleSheet: () -> Unit, mainViewModel : PlayerViewModel, searchViewModel: SearchViewModel, content: @Composable () -> Unit, hazeState: HazeState) {

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
            songProgressUi      = songProgressUi,
            hazeState = hazeState
        )

        //scaffold
        if (showSheet) {
            openedBar(
                mainViewModel       = mainViewModel,
                searchViewModel     = searchViewModel,
                onToogleSheet       = onToogleSheet,
                songProgressUi      = songProgressUi,
                sheetState          = sheetState,
                hazeState = hazeState
            )
        }
    }
}

