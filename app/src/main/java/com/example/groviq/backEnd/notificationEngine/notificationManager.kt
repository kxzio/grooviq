package com.example.groviq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.NotificationUtil.createNotificationChannel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerNotificationManager
import com.example.groviq.MainActivity
import com.example.groviq.R
import com.example.groviq.backEnd.playEngine.AudioPlayerManager
import com.example.groviq.backEnd.playEngine.createListeners
import com.example.groviq.globalContext
import com.example.groviq.playerManager
import com.example.groviq.service.PlayerService.Companion.NOTIF_CHANNEL_ID
import com.example.groviq.service.PlayerService.Companion.NOTIF_ID
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel


private val COMMAND_NEXT = SessionCommand("NEXT", Bundle.EMPTY)
private val COMMAND_PREV = SessionCommand("PREV", Bundle.EMPTY)

@UnstableApi
class PlayerService : MediaSessionService() {

    companion object {
        const val NOTIF_CHANNEL_ID = "default_channel_id"
        const val NOTIF_CHANNEL_NAME = "Audio Player"
        const val NOTIF_ID = 4123
    }

    private lateinit var mediaSession: MediaSession
    private lateinit var playerNotificationManager: androidx.media3.ui.PlayerNotificationManager

    // scope for background work (loading bitmaps etc.)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (playerManager == null)
        {
            playerManager = AudioPlayerManager(globalContext!!)
        }


        mediaSession = MediaSession.Builder(this, playerManager.player)
            .setId("app_media_session")
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = SessionCommands.Builder()
                        .add(COMMAND_NEXT)
                        .add(COMMAND_PREV)
                        .build()
                    val playerCommands = Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_PREPARE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    return when (customCommand) {
                        COMMAND_NEXT -> {
                            sendBroadcast(Intent("ACTION_NEXT_SONG"))
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        COMMAND_PREV -> {
                            sendBroadcast(Intent("ACTION_PREV_SONG"))
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
                }
            })
            .build()

        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession.setSessionActivity(pending)

        playerNotificationManager = androidx.media3.ui.PlayerNotificationManager.Builder(
            this,
            NOTIF_ID,
            NOTIF_CHANNEL_ID
        ).setMediaDescriptionAdapter(object : androidx.media3.ui.PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                return player.mediaMetadata.title?.toString() ?: "Unknown"
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val actIntent = packageManager.getLaunchIntentForPackage(packageName)
                return PendingIntent.getActivity(
                    this@PlayerService,
                    0,
                    actIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return player.mediaMetadata.artist?.toString() ?: ""
            }

            override fun getCurrentLargeIcon(player: Player, callback: androidx.media3.ui.PlayerNotificationManager.BitmapCallback): Bitmap? {
                // Попробуем получить artworkData из metadata (bytes) — это синхронный путь
                val artworkBytes = player.mediaMetadata.artworkData
                if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                    return BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
                }

                return null
            }
        }).setNotificationListener(object : androidx.media3.ui.PlayerNotificationManager.NotificationListener {
            override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {

                val builder = NotificationCompat.Builder(globalContext!!, NOTIF_CHANNEL_ID)
                    .setContentTitle("Title")
                    .setContentText("Artist")
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setStyle(
                        MediaStyleNotificationHelper.MediaStyle(mediaSession)
                            .setShowActionsInCompactView(0, 1, 2)
                    )

                startForeground(notificationId, builder.build())
            }

            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopForeground(true)
            }
        }).build()

        playerNotificationManager.setPlayer(playerManager.player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            playerNotificationManager.setPlayer(null)
        } catch (e: Throwable) { /* ignore */ }

        try {
            mediaSession.release()
        } catch (e: Throwable) { /* ignore */ }

        try {
            playerManager.player.release()
        } catch (e: Throwable) { /* ignore */ }

        serviceScope.cancel()
    }
}

