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
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.groviq.backEnd.searchEngine.SearchViewModel
import com.example.groviq.backEnd.searchEngine.SearchViewModelFactory


class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // start python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this.applicationContext))
        }

        // start pipe for streams
        initNewPipe()

        // permissions for notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }

        // set localization
        val locale = Locale("en", "US")
        NewPipe.setupLocalization(Localization.fromLocale(locale))

        // UI
        enableEdgeToEdge()
        setContent {
            GroviqTheme {
                start(AppViewModels.player, AppViewModels.search)
            }
        }
    }
}

@UnstableApi
class MyApplication : Application(), ViewModelStoreOwner {

    companion object {
        @Volatile
        lateinit var instance: MyApplication
            private set
        @Volatile
        lateinit var globalContext: Context
            private set
    }

    // именно как val, а не метод
    override val viewModelStore: ViewModelStore by lazy { ViewModelStore() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        globalContext = applicationContext

        // глобальный обработчик ошибок
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

// --------------------------- GLOBAL VIEWMODELS ----------------------------
@UnstableApi
object AppViewModels {
    val player: PlayerViewModel by lazy {
        val app = MyApplication.instance
        val db = AppDatabase.getInstance(app.applicationContext)
        val repo = DataRepository(db)
        val factory = PlayerViewModelFactory(repo)
        ViewModelProvider(app, factory).get(PlayerViewModel::class.java)
    }

    val search: SearchViewModel by lazy {
        val app = MyApplication.instance
        val factory = SearchViewModelFactory()
        ViewModelProvider(app, factory).get(SearchViewModel::class.java)
    }
}


// -------------------- ViewModels Singleton --------------------
@UnstableApi
object ViewModels {
    private var playerViewModel: PlayerViewModel? = null

    fun player(repo: DataRepository): PlayerViewModel {
        if (playerViewModel == null) {
            val factory = PlayerViewModelFactory(repo)
            playerViewModel = ViewModelProvider(
                MyApplication.instance,
                factory
            ).get(PlayerViewModel::class.java)
        }
        return playerViewModel!!
    }
}


@OptIn(
    UnstableApi::class
)
@Composable
fun start(viewModel: PlayerViewModel, searchView : SearchViewModel)
{
    drawLayout(viewModel, searchView)
}

