package com.example.groviq.frontEnd.bottomBars

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist
import com.example.groviq.frontEnd.screenConnectorNavigation

var isTrackSettingsOpened: MutableState<Boolean> = mutableStateOf(false)
var currentTrackHashForSettings: MutableState<String?> = mutableStateOf(null)

fun openTrackSettingsBottomBar(requestHash: String) {
    currentTrackHashForSettings.value = requestHash
    isTrackSettingsOpened.value = true
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun trackSettingsBottomBar(mainViewModel : PlayerViewModel)
{
    val isOpened by rememberUpdatedState(isTrackSettingsOpened.value)
    val requestHash = currentTrackHashForSettings.value ?: return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(isOpened) {
        if (isOpened) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            isTrackSettingsOpened.value = false
            currentTrackHashForSettings.value = null
        },
        sheetState = sheetState,
        containerColor = Color(
            0xFF171717
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            drawSettingsBottomBar(mainViewModel, requestHash, {
                isTrackSettingsOpened.value = false
                currentTrackHashForSettings.value = null
            } )
        }
    }

}


@Composable
fun buttonForSettingBar(title : String, imageVector: ImageVector, onClick : () -> Unit )
{
    Button(
        onClick = {
            onClick()
        },
        modifier = Modifier.padding(
            bottom = 10.dp
        ).fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 10.dp
        )
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        )
        {
            Icon(
                imageVector,
                "Icons_Selector1"
            )
            Text(
                title,
                fontSize = 15.sp,
                modifier = Modifier.padding(
                    start = 15.dp
                ),
                color = Color(
                    255,
                    255,
                    255,
                    255
                )
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun drawSettingsBottomBar(mainViewModel : PlayerViewModel, requestHash : String, onClose : () -> Unit)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    if (mainUiState.allAudioData[requestHash] == null) {
        onClose()
        return
    }

    val track = mainUiState.allAudioData[requestHash]

    Row()
    {
        Image(track!!.art!!.asImageBitmap(), null, Modifier.size(35.dp))

        Column()
        {
            Text(track.title, color = Color.White)
            Text(
                track.artists.joinToString { it.title },
                maxLines = 1,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }

    val liked by mainViewModel.isAudioSourceContainsSong(track!!.link, "Favourite")
        .collectAsState(initial = false)

    if (!liked) {
        buttonForSettingBar("Добавить в любимое", Icons.Rounded.FavoriteBorder, {
            mainViewModel.addSongToAudioSource(track!!.link, "Favourite")
            onClose()
        })
    }
    else {
        buttonForSettingBar("Убрать из любимых", Icons.Rounded.Favorite, {
            mainViewModel.removeSongFromAudioSource(track!!.link, "Favourite")
            onClose()
        })
    }

    buttonForSettingBar("Перейти к альбому", Icons.Rounded.Favorite, {
        openAlbum(track!!.album_original_link)
        onClose()
    })

    buttonForSettingBar("Перейти к артисту", Icons.Rounded.Favorite, {
        openArtist(track!!.artists.first().url)
        onClose()
    })






}