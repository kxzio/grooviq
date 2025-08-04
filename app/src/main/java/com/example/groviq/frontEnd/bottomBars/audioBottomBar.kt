package com.example.groviq.frontEnd.bottomBars

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.playerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun audioBottomSheet(mainViewModel : PlayerViewModel, content: @Composable () -> Unit) {

    val mainUiState     by mainViewModel.uiState.collectAsState()


    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    var showSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        content()

        if (mainUiState.playingHash.isNullOrEmpty())
            return@Box

        if (mainUiState.allAudioData[mainUiState.playingHash] == null)
            return@Box

        //mini box
        if (true) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 15.dp, end = 15.dp)
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(35, 35, 35)
                    )
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        showSheet = true
                    }
            )
            {
                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(
                        onClick =
                        {
                            if (mainUiState.currentStatus == playerStatus.PAUSE)
                                playerManager.resume()
                            if (mainUiState.currentStatus == playerStatus.PLAYING)
                                playerManager.pause()
                        },
                    ) {
                        Icon(
                            imageVector = if (mainUiState.currentStatus == playerStatus.PAUSE)
                                Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                            ,
                            contentDescription = "Pause/Play",
                            tint = Color(255, 255, 255)
                        )
                    }

                    Column()
                    {
                        val song = mainUiState.allAudioData[mainUiState.playingHash]

                        Text(mainUiState.currentStatus.toString(), color = Color(255, 255, 255))

                        Text(song!!.title, color = Color(255, 255, 255))

                        Row()
                        {
                            song.artists.forEach { artist ->

                                Text(
                                    artist.title + if (artist != song.artists.last()) ", " else "",
                                    maxLines = 1, color = Color(255, 255, 255)
                                )

                            }
                        }
                    }

                }

            }
        }

        //scaffold
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                modifier = Modifier.fillMaxSize(),
                dragHandle = null,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
    }
}