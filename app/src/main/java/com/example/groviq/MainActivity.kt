package com.example.groviq

import android.app.Application
import android.content.Context
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

var globalContext : Context? = null

lateinit var playerManager: AudioPlayerManager

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        //start python
        if (!Python.isStarted()) {
            Python.start(
                AndroidPlatform(this.applicationContext)
            )
        }

        //start pipe for streams
        initNewPipe()

        //player init
        playerManager = AudioPlayerManager(this)


        val locale = Locale("en", "US")
        NewPipe.setupLocalization(
            Localization.fromLocale(locale)
        )

        globalContext = this.applicationContext

        enableEdgeToEdge()
        setContent {
            GroviqTheme {
                start()
            }
        }
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
fun start()
{
    drawLayout()
}

