package com.example.groviq.backEnd.localFileProcessor

import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import com.example.groviq.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

@OptIn(
    UnstableApi::class
)
private val supportedExtensions = setOf(
    "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "mkv", "mp4", "webm"
)

suspend fun findAudioInFolderRecursively(folderUri: Uri): List<Uri> = coroutineScope {
    val root = DocumentFile.fromTreeUri(MyApplication.globalContext, folderUri)
        ?: return@coroutineScope emptyList()

    val result = Collections.synchronizedList(ArrayList<Uri>(4096))

    val dispatcher = Dispatchers.IO.limitedParallelism(8)

    suspend fun traverse(dir: DocumentFile) {
        try {
            val children = dir.listFiles()
            for (file in children) {
                if (file.isDirectory) {
                    launch(dispatcher) { traverse(file) }
                } else {
                    val name = file.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExtensions) {
                        result.add(file.uri)
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    traverse(root)

    return@coroutineScope result
}
