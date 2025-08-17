package com.example.groviq.frontEnd.bottomBars

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
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.frontEnd.appScreens.searchingScreen.searchingRequest
import com.example.groviq.globalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


var isCreatePlaylistOpened: MutableState<Boolean> = mutableStateOf(false)

var playlistName: MutableState<String> = mutableStateOf("")


@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun createPlaylistBar(mainViewModel : PlayerViewModel)
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


@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun drawPlaylistCreateBar(mainViewModel : PlayerViewModel, onClose : () -> Unit)
{
    val mainUiState     by mainViewModel.uiState.collectAsState()

    OutlinedTextField(
        value = playlistName.value,
        onValueChange = { newValue ->
            playlistName.value = newValue
        },
        label = {
            Text("Название плейлиста")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                mainViewModel.createAudioSource(playlistName.value)
                onClose()
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )

    Button(onClick =
    {
        mainViewModel.createAudioSource(playlistName.value)
        onClose()
    },
        Modifier.fillMaxWidth()){
        Text("Создать плейлист")
    }


}