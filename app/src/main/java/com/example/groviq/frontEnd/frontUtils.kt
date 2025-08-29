package com.example.groviq.frontEnd

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.groviq.backEnd.dataStructures.songData

@Composable
fun asyncedImage(songData: songData?, modifier: Modifier = Modifier, onEmptyImageCallback: (@Composable () -> Unit)? = null)
{
    if (songData == null)
        return

    if (songData.art != null)
    {
        Image(
            bitmap = songData!!.art!!.asImageBitmap(),
            contentDescription = null,
            modifier,
            contentScale = ContentScale.Crop
            )

    }
    else if (songData.art_link.isNullOrEmpty().not())
    {
        val painter = rememberAsyncImagePainter(
            model = songData.art_link,
            contentScale = ContentScale.Crop
        )

        if (painter.state is AsyncImagePainter.State.Loading) {

            Box(
                modifier.background(Color(255, 255, 255, 50)),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints {

                    val iconSize = maxWidth * 0.7f

                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier,
            contentScale = ContentScale.Crop
        )
    }
    else
    {
        if (onEmptyImageCallback != null) {
            onEmptyImageCallback()
        }
        else
        {
            Box(
                modifier.background(Color(255, 255, 255, 100)),
                contentAlignment = Alignment.Center
            )
            {
                Icon(Icons.Rounded.ImageNotSupported, "Image not supported")
            }
        }

    }

}

@Composable
fun asyncedImage(link : String?, modifier: Modifier = Modifier, onEmptyImageCallback: (@Composable () -> Unit)? = null)
{

    if (link.isNullOrEmpty().not())
    {
        val painter = rememberAsyncImagePainter(
            model = link,
            contentScale = ContentScale.Crop
        )

        if (painter.state is AsyncImagePainter.State.Loading) {

            Box(
                modifier.background(Color(255, 255, 255, 50)),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints {

                    val iconSize = maxWidth * 0.7f

                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier,
            contentScale = ContentScale.Crop
        )

    }
    else
    {
        if (onEmptyImageCallback != null) {
            onEmptyImageCallback()
        }
        else
        {
            Box(
                modifier.background(Color(255, 255, 255, 50)),
                contentAlignment = Alignment.Center
            )
            {
                Icon(Icons.Rounded.ImageNotSupported, "Image not supported")
            }
        }
    }

}