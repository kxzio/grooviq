package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.backEnd.dataStructures.CurrentSongTimeProgress
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.playerStatus
import com.example.groviq.backEnd.dataStructures.repeatMods
import com.example.groviq.backEnd.dataStructures.setSongProgress
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.formatTime
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.background
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements.activityButtons
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements.*
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements.drawPagerForSongs
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements.sliderWithDigits
import com.example.groviq.frontEnd.bottomBars.audioBottomBar.playerInputHandlers
import com.example.groviq.frontEnd.bottomBars.openTrackSettingsBottomBar
import com.example.groviq.frontEnd.grooviqUI
import com.example.groviq.frontEnd.subscribeMe
import dev.chrisbanes.haze.HazeState

@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun openedBar(mainViewModel : PlayerViewModel, onToogleSheet: () -> Unit, songProgressUi: State<CurrentSongTimeProgress>,
              searchViewModel: SearchViewModel, sheetState: SheetState, hazeState: HazeState
)
{

    // current reactive variables for subscribe //
    val currentStatus       by mainViewModel.uiState.subscribeMe { it.currentStatus  }
    val playingHash         by mainViewModel.uiState.subscribeMe { it.playingHash    }
    val allAudioData        by mainViewModel.uiState.subscribeMe { it.allAudioData   }
    val repeatMode          by mainViewModel.uiState.subscribeMe { it.repeatMode     }
    val songsLoader         by mainViewModel.uiState.subscribeMe { it.songsLoader    }
    val isShuffle           by mainViewModel.uiState.subscribeMe { it.isShuffle      }

    ModalBottomSheet(
        onDismissRequest = { onToogleSheet() },
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        dragHandle = null,
        containerColor = Color(0, 0, 0, 255),
        contentColor = Color.White,
        shape = RectangleShape
        //windowInsets = WindowInsets(0, 0, 0, 0)
    ) {

        val song = allAudioData[playingHash]

        Box(
            modifier = Modifier
                .fillMaxSize()
        )
        {
            if (song != null) { background(song) }

            Column(modifier = Modifier
                .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            )
            {

                if (songsLoader == true)
                {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(100.dp))
                    }
                    return@Column
                }

                //interactive pager
                grooviqUI.elements.openedElements.drawPagerForSongs(mainViewModel, searchViewModel, allAudioData)
                Spacer(Modifier.height(16.dp))

                //current song content
                Column(Modifier.offset(y = -16.dp).fillMaxSize())
                {
                    grooviqUI.elements.openedElements.titleBar(song!!, onToogleSheet)
                    Spacer(Modifier.height(16.dp))

                    grooviqUI.elements.openedElements.sliderWithDigits(song!!, songProgressUi)
                    Spacer(Modifier.height(16.dp))

                    grooviqUI.elements.openedElements.activityButtons(songProgressUi, currentStatus)
                    Spacer(modifier = Modifier.weight(1f))

                    grooviqUI.elements.openedElements.lowerActivityButtons(
                        mainViewModel,
                        song,
                        isShuffle,
                        repeatMode
                    )

                }


            }
        }

    }
}