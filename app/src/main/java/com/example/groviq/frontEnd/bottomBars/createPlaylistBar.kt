package com.example.groviq.frontEnd.bottomBars

import GlassTextField
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchingRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


var isCreatePlaylistOpened: MutableState<Boolean>   = mutableStateOf(false)
var playlistName: MutableState<String>              = mutableStateOf("")

var isRenamePlaylistOpened: MutableState<Boolean>   = mutableStateOf(false)
var originalPlaylistName  : MutableState<String>        = mutableStateOf("")
var renamePlaylistName    : MutableState<String>        = mutableStateOf("")



@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun createPlaylistBar(mainViewModel : PlayerViewModel)
{
    if (isCreatePlaylistOpened.value)
    {
        val isOpened by rememberUpdatedState(isCreatePlaylistOpened.value)

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

        if (isOpened.not())
            return

        ModalBottomSheet(
            shape = RectangleShape,
            onDismissRequest = {
                isCreatePlaylistOpened.value = false
                playlistName.value = ""
            },
            sheetState = sheetState,
            containerColor = Color(15, 15, 15),
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp)) {
                drawPlaylistCreateBar(mainViewModel)
                {
                    isCreatePlaylistOpened.value = false
                    playlistName.value = ""
                }
            }
        }
    }
    else if (isRenamePlaylistOpened.value)
    {
        renamePlaylistBar(mainViewModel)
    }

}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun renamePlaylistBar(mainViewModel : PlayerViewModel)
{
    val isOpened by rememberUpdatedState(isRenamePlaylistOpened.value)

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    renamePlaylistName.value  = originalPlaylistName.value

    LaunchedEffect(isOpened) {
        if (isOpened) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    if (isOpened.not())
        return

    ModalBottomSheet(
        shape = RectangleShape,
        onDismissRequest = {
            isRenamePlaylistOpened.value = false
            renamePlaylistName.value = ""
        },
        sheetState = sheetState,
        containerColor = Color(15, 15, 15),
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp)) {
            drawPlaylistCreateBar(mainViewModel, true)
            {
                isRenamePlaylistOpened.value = false
                renamePlaylistName.value = ""
            }
        }
    }

}


@androidx.annotation.OptIn(
    UnstableApi::class
)
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun drawPlaylistCreateBar(mainViewModel : PlayerViewModel, isRename : Boolean = false, onClose : () -> Unit)
{
    GlassTextField(
        value = if (isRename.not()) playlistName.value else renamePlaylistName.value,
        onValueChange = { newValue ->
            if (isRename.not()) playlistName.value = newValue else renamePlaylistName.value = newValue
        },
        placeholder =
            if (isRename.not()) "Playlist name" else "New playlist name"
        ,
        onDone = {

            if (isRename.not()) {

                if (mainViewModel.isAudioSourceAlreadyHad(playlistName.value))
                {
                    Toast.makeText(MyApplication.globalContext, "This name already in use. Try another name", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    mainViewModel.createAudioSource(playlistName.value)
                    onClose()
                }
            }
            else
            {
                if (mainViewModel.isAudioSourceAlreadyHad(renamePlaylistName.value))
                {
                    Toast.makeText(MyApplication.globalContext, "This name already in use. Try another name", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    mainViewModel.renameAudioSourceAndMoveSongs(
                        originalPlaylistName.value, renamePlaylistName.value)
                    onClose()
                }
            }

        },
    )

    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp))
    {
        OutlinedButton(onClick =
        {
            if (isRename.not()) {

                if (mainViewModel.isAudioSourceAlreadyHad(playlistName.value))
                {
                    Toast.makeText(MyApplication.globalContext, "This name already in use. Try another name", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    mainViewModel.createAudioSource(playlistName.value)
                    mainViewModel.saveAudioSourcesToRoom()
                    onClose()
                }
            }
            else
            {
                if (mainViewModel.isAudioSourceAlreadyHad(renamePlaylistName.value))
                {
                    Toast.makeText(MyApplication.globalContext, "This name already in use. Try another name", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    mainViewModel.renameAudioSourceAndMoveSongs(
                        originalPlaylistName.value, renamePlaylistName.value)

                    mainViewModel.saveAudioSourcesToRoom()

                    onClose()
                }
            }
        },
            Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors().copy(
                containerColor = Color(20, 20, 20, 0)
            ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp,Color(255, 255, 255, 40) )
        ){
            Text(if (isRename.not()) "Create" else "Rename", fontSize = 16.sp, color = Color(255, 255, 255, 140), modifier =
            Modifier.padding(12.dp))
        }

        val interactionSource_ = remember { MutableInteractionSource() }

        val isPressed       = interactionSource_.collectIsPressedAsState().value
        var timerForDelete by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(isPressed) {
            while (isActive) {
                if (isPressed) {
                    if (timerForDelete < 1f)
                        timerForDelete += 0.02f
                } else {
                    if (timerForDelete > 0f)
                        timerForDelete -= 0.04f
                }

                timerForDelete = timerForDelete.coerceIn(0f, 1f)

                if (timerForDelete >= 1f) {
                    mainViewModel.deleteAudioSource(originalPlaylistName.value)
                    onClose()
                    mainViewModel.saveAudioSourcesToRoom()
                    mainViewModel.saveSongsFromSourceToRoom(originalPlaylistName.value)
                    timerForDelete = 0f
                    break
                }

                delay(16L)
            }
        }

        if (isRename)
        {
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier.weight(1f).height(60.dp).border(1.dp, Color(255, 65, 65, 100), RoundedCornerShape(18.dp))
                    .clickable (
                        interactionSource = interactionSource_,
                        indication = null,
                        onClick = { }
                    ).clip(RoundedCornerShape(18.dp))
            ) {
                Box(Modifier.fillMaxWidth())
                {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(timerForDelete)
                            .background(Color(255, 65, 65, 100))
                    )

                    Text("Delete", fontSize = 16.sp, color = Color(
                        255,
                        65,
                        65,
                        100
                    ), modifier =
                    Modifier.align(
                        Alignment.Center)
                    )

                }

            }
        }
    }


}