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
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
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

enum class pendingDirection {
    EMPTY,
    TO_NEXT_SONG,
    TO_PREVIOUS_SONG
}

//the requst of navigation artist
val songPendingIntentNavigationDirection = mutableStateOf<pendingDirection>(pendingDirection.EMPTY)

//the requst of navigation artist
val nextSongHashPending = mutableStateOf<String>("")


@UnstableApi
class CustomPlayer(wrappedPlayer: Player) : ForwardingPlayer(wrappedPlayer) {

    override fun seekToNext() {
        if (mediaItemCount > 1) {
            val currentIndex = currentMediaItemIndex
            if (currentIndex != C.INDEX_UNSET && currentIndex + 1 < mediaItemCount) {
                val nextItem = getMediaItemAt(currentIndex + 1)

                val nextTag = nextItem.localConfiguration?.tag as? String

                if (nextSongHashPending.value == nextTag) {
                    super.seekToNext()
                    Toast.makeText(globalContext!!, "auto", Toast.LENGTH_SHORT).show()
                    return

                } else {
                    songPendingIntentNavigationDirection.value = pendingDirection.TO_NEXT_SONG
                    return
                }
            }
        }

        songPendingIntentNavigationDirection.value = pendingDirection.TO_NEXT_SONG
    }

    override fun seekToPrevious() {
        songPendingIntentNavigationDirection.value = pendingDirection.TO_PREVIOUS_SONG
    }

    override fun hasNextMediaItem(): Boolean {
        return true
    }

    override fun hasPreviousMediaItem(): Boolean {
        return true
    }

    override fun getAvailableCommands(): Player.Commands {
        return Player.Commands.Builder()
            .addAllCommands()
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

}

@UnstableApi
class PlayerService : androidx.media3.session.MediaSessionService() {

    companion object {
        const val NOTIF_CHANNEL_ID = "default_channel_id"
        const val NOTIF_CHANNEL_NAME = "Audio Player"
        const val NOTIF_ID = 4123
    }

    private lateinit var mediaSession: MediaSession
    private lateinit var playerNotificationManager: PlayerNotificationManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()


        mediaSession = MediaSession.Builder(this, playerManager.player)
            .setId("app_media_session")
            .build()


        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession.setSessionActivity(pendingIntent)

        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIF_ID,
            NOTIF_CHANNEL_ID
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player) =
                    player.mediaMetadata.title ?: "Загрузка..."

                override fun getCurrentContentText(player: Player) =
                    player.mediaMetadata.artist ?: ""

                override fun createCurrentContentIntent(player: Player) = pendingIntent

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ) = player.mediaMetadata.artworkData?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {

                    val prevPending = PendingIntent.getBroadcast(
                        this@PlayerService, 0,
                        Intent("ACTION_PREV_SONG"), PendingIntent.FLAG_IMMUTABLE
                    )
                    val playPausePending = PendingIntent.getBroadcast(
                        this@PlayerService, 1,
                        Intent("ACTION_TOGGLE_PLAY"), PendingIntent.FLAG_IMMUTABLE
                    )
                    val nextPending = PendingIntent.getBroadcast(
                        this@PlayerService, 2,
                        Intent("ACTION_NEXT_SONG"), PendingIntent.FLAG_IMMUTABLE
                    )

                    val prevAction = NotificationCompat.Action(
                        androidx.media3.session.R.drawable.media3_notification_seek_to_previous,
                        "Previous", prevPending
                    )
                    val playPauseAction = NotificationCompat.Action(
                        if (playerManager.player.isPlaying)
                            androidx.media3.session.R.drawable.media3_notification_pause
                        else
                            androidx.media3.session.R.drawable.media3_notification_play,
                        if (playerManager.player.isPlaying) "Pause" else "Play",
                        playPausePending
                    )
                    val nextAction = NotificationCompat.Action(
                        androidx.media3.session.R.drawable.media3_notification_seek_to_next,
                        "Next", nextPending
                    )

                    val builder = NotificationCompat.Builder(globalContext!!, NOTIF_CHANNEL_ID)
                        .setContentTitle(playerManager.player.mediaMetadata.title ?: "Title")
                        .setContentText(playerManager.player.mediaMetadata.artist ?: "Artist")
                        .setSmallIcon(androidx.media3.session.R.drawable.media_session_service_notification_ic_music_note)
                        .setStyle(
                            androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSession.sessionCompatToken)
                                .setShowActionsInCompactView(0, 1, 2)
                        )
                        .addAction(prevAction)
                        .addAction(playPauseAction)
                        .addAction(nextAction)

                    startForeground(notificationId, builder.build())
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(true)
                }
            })
            .build()

        playerNotificationManager.setMediaSessionToken(mediaSession.sessionCompatToken)


        playerNotificationManager.setPlayer(playerManager.player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

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
        try { playerNotificationManager.setPlayer(null) } catch (e: Throwable) {}
        try { mediaSession.release() } catch (e: Throwable) {}
        try { playerManager.player.release() } catch (e: Throwable) {}
    }
}

