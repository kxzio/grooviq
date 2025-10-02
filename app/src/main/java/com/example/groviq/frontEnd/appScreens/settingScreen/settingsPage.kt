package com.example.groviq.frontEnd.appScreens.settingScreen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.frontEnd.appScreens.searchingScreen.browsingPages.showDefaultAudioSource
import com.example.groviq.frontEnd.subscribeMe
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.OptIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.groviq.AppViewModels
import com.example.groviq.MyApplication
import com.example.groviq.backEnd.dataStructures.ViewFolder

@OptIn(UnstableApi::class)
@Composable
fun settingsPage(mainViewModel: PlayerViewModel) {

    val context = LocalContext.current

    val folders by mainViewModel.uiState.subscribeMe { it.localFilesFolders  }

    val audioData                       by mainViewModel.uiState.subscribeMe    { it.audioData }
    val allAudioData                    by mainViewModel.uiState.subscribeMe    { it.allAudioData }
    val playingHash                     by mainViewModel.uiState.subscribeMe    { it.playingHash }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {

            val context = MyApplication.globalContext
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val folder = ViewFolder(uri = it.toString(), displayName = it.lastPathSegment ?: "Папка")
            mainViewModel.updateState { state ->
                state.copy(
                    localFilesFolders = state.localFilesFolders.toMutableList().apply {
                        add(folder)
                    }
                )
            }
            mainViewModel.generateSongsFromFolder(folder)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Folders :", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add new folder")
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(folders, key = { it.uri }) { folder ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = folder.displayName.replace("primary:", ""),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row()
                            {
                                IconButton(onClick = {
                                    mainViewModel.generateSongsFromFolder(folder)
                                }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                }

                                IconButton(onClick = {
                                    mainViewModel.updateState { state ->
                                        state.copy(
                                            localFilesFolders = state.localFilesFolders.toMutableList().apply {
                                                remove(folder)
                                            }
                                        )
                                    }
                                    mainViewModel.deleteSongsFromFolder(folder)
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = null)
                                }
                            }

                        }
                        LinearProgressIndicator(folder.progressOfLoading, Modifier.fillMaxWidth())
                    }

                }
            }

            item {
                if (allAudioData[playingHash] != null) {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        /*
                LazyColumn(Modifier.height(200.dp)) {
            items(allAudioData.keys.toMutableList())
            { d->
                Text(d, fontSize = 12.sp)
            }
        }
        Text("____")
        LazyColumn(Modifier.height(200.dp)) {
            items(audioData.keys.toMutableList())
            { d->
                Text(d, fontSize = 12.sp)
            }
        }
        */


    }
}
