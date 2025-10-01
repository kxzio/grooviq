package com.example.groviq.backEnd.localFileProcessor

import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import com.example.groviq.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(
    UnstableApi::class
)
suspend fun findAudioInFolderRecursively(folderUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
    val docFile = DocumentFile.fromTreeUri(MyApplication.globalContext, folderUri)
    val result = mutableListOf<Uri>()

    fun traverse(file: DocumentFile?) {
        if (file == null) return
        if (file.isDirectory) {
            file.listFiles().forEach { traverse(it) }
        } else if (file.isFile && file.name?.endsWith(".mp3", true) == true) {
            result.add(file.uri)
        }
    }

    traverse(docFile)
    result
}
