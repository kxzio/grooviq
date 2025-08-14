package com.example.groviq

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.groviq.backEnd.playEngine.AudioPlayerManager
import com.example.groviq.frontEnd.drawLayout
import com.example.groviq.ui.theme.GroviqTheme
import io.github.shalva97.initNewPipe
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import kotlin.system.exitProcess
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.groviq.backEnd.dataStructures.PlayerViewModel
import com.example.groviq.backEnd.dataStructures.PlayerViewModelFactory
import com.example.groviq.backEnd.headerTaker.YTMusicWebView
import com.example.groviq.backEnd.saveSystem.AppDatabase
import com.example.groviq.backEnd.saveSystem.DataRepository
import com.example.groviq.service.PlayerService
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModelFactory

var globalContext : Context? = null

lateinit var playerManager: AudioPlayerManager

private lateinit var nextReceiver: BroadcastReceiver
private lateinit var prevReceiver: BroadcastReceiver


class MainActivity :
    ComponentActivity() {

    @SuppressLint(
        "UnspecifiedRegisterReceiverFlag"
    )
    @OptIn(
        UnstableApi::class
    )
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        globalContext = this.applicationContext

        //player init
        if (playerManager == null)
        {
            playerManager = AudioPlayerManager(globalContext!!)
        }

        //start python
        if (!Python.isStarted()) {
            Python.start(
                AndroidPlatform(this.applicationContext)
            )
        }

        //start pipe for streams
        initNewPipe()

        //init viewmodel
        val db          = AppDatabase.getInstance(this.applicationContext)
        val repo        = DataRepository(db)

        val factory     = PlayerViewModelFactory(repo)
        val viewModel   = ViewModelProvider(this, factory).get(PlayerViewModel::class.java)

        val factorySearch     = SearchViewModelFactory()
        val viewModelSearch   = ViewModelProvider(this, factorySearch).get(SearchViewModel::class.java)


        nextReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                playerManager.nextSong(viewModel, viewModelSearch)
            }
        }

        prevReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                playerManager.prevSong(viewModel, viewModelSearch)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(nextReceiver, IntentFilter("ACTION_NEXT_SONG"), RECEIVER_NOT_EXPORTED)
            registerReceiver(prevReceiver, IntentFilter("ACTION_PREV_SONG"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(nextReceiver, IntentFilter("ACTION_NEXT_SONG"))
            registerReceiver(prevReceiver, IntentFilter("ACTION_PREV_SONG"))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val locale = Locale("en", "US")
        NewPipe.setupLocalization(
            Localization.fromLocale(locale)
        )


        enableEdgeToEdge()
        setContent {
            GroviqTheme {
                start(viewModel, viewModelSearch)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(nextReceiver)
        unregisterReceiver(prevReceiver)
    }

}

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            appendLog(
                this,
                "UncaughtException",
                "Crash in thread ${thread.name}: ${throwable.message}",
                throwable
            )

            Thread.sleep(500)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }

    }
}

@Composable
fun start(viewModel: PlayerViewModel, searchView : SearchViewModel)
{
    drawLayout(viewModel, searchView)
}

