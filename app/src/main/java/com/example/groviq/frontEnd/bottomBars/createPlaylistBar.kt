package com.example.groviq.frontEnd.bottomBars

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            onDismissRequest = {
                isCreatePlaylistOpened.value = false
                playlistName.value = ""
            },
            sheetState = sheetState,
            containerColor = Color(
                0xFF171717
            ),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
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
        onDismissRequest = {
            isRenamePlaylistOpened.value = false
            renamePlaylistName.value = ""
        },
        sheetState = sheetState,
        containerColor = Color(
            0xFF171717
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
    OutlinedTextField(
        value = if (isRename.not()) playlistName.value else renamePlaylistName.value,
        onValueChange = { newValue ->
            if (isRename.not()) playlistName.value = newValue else renamePlaylistName.value = newValue
        },
        label = {
            Text(if (isRename.not()) "Название плейлиста" else "Новое название плейлиста")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
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

            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )

    Button(onClick =
    {
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
        Modifier.fillMaxWidth()){
        Text(if (isRename.not()) "Создать плейлист" else "Переименовать")
    }


}