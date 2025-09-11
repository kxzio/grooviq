package com.example.groviq.frontEnd.bottomBars.audioBottomBar.openedBar.openedElements

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groviq.backEnd.dataStructures.songData
import com.example.groviq.frontEnd.appScreens.openAlbum
import com.example.groviq.frontEnd.appScreens.openArtist

class bottomBarUI {

    companion object openedElements
    {
        @Composable
        fun titleBar(song : songData, onToogleSheet: () -> Unit)
        {
            Box(Modifier.fillMaxWidth().padding(horizontal = 25.dp))
            {
                Column()
                {
                    Text(text = song?.title ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.clickable {
                        openAlbum(song?.album_original_link ?: "")
                        onToogleSheet()
                    }.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                        repeatDelayMillis = 2000,
                        velocity = 40.dp
                    )
                    )


                    Spacer(
                        Modifier.height(10.dp))

                    Row()
                    {
                        song?.artists?.forEach { artist ->

                            Text(
                                artist.title + if (artist != song.artists.last()) ", " else "",
                                maxLines = 1, fontSize = 17.sp, color = Color(255, 255, 255, 150), modifier = Modifier.clickable {
                                    openArtist(artist.url)
                                    onToogleSheet()
                                }
                            )

                        }
                    }
                }

            }
        }
    }

}

