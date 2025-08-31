package com.example.groviq.frontEnd

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.content.MediaType.Companion.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
fun asyncedImage(
    songData: songData?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null
) {
    if (songData == null) return

    val contentModifier = modifier
        .then(Modifier)
        .background(Color.LightGray.copy(alpha = 0.2f))

    when {
        songData.art != null -> {
            Image(
                bitmap = songData.art!!.asImageBitmap(),
                contentDescription = null,
                modifier = contentModifier,
                contentScale = ContentScale.Crop
            )
        }

        songData.art_link.isNullOrEmpty().not() -> {
            val painter = rememberAsyncImagePainter(
                model = songData.art_link
            )

            Box(
                modifier = contentModifier,
                contentAlignment = Alignment.Center
            ) {

                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )

                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = "Loading",
                            modifier = Modifier.fillMaxSize(0.7f)
                        )
                    }

                    is AsyncImagePainter.State.Error -> {
                        Icon(
                            Icons.Rounded.ImageNotSupported,
                            contentDescription = "Error",
                            modifier = Modifier.fillMaxSize(0.7f)
                        )
                    }

                    else -> Unit
                }
            }
        }

        else -> {
            if (onEmptyImageCallback != null) {
                Box(contentModifier, contentAlignment = Alignment.Center) {
                    onEmptyImageCallback()
                }
            } else {
                Box(contentModifier, contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = "Image not supported",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun asyncedImage(
    link: String?,
    modifier: Modifier = Modifier,
    onEmptyImageCallback: (@Composable () -> Unit)? = null
) {
    if (!link.isNullOrEmpty()) {
        val painter = rememberAsyncImagePainter(
            model = link
        )

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "Loading",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = "Error",
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }
                else -> Unit
            }
        }
    } else {
        if (onEmptyImageCallback != null) {
            Box(modifier, contentAlignment = Alignment.Center) {
                onEmptyImageCallback()
            }
        } else {
            Box(
                modifier,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ImageNotSupported,
                    contentDescription = "Image not supported",
                    modifier = Modifier.fillMaxSize(0.7f)
                )
            }
        }
    }
}

@Composable
fun errorButton( onClick: () -> Unit,)
{
    Button( { onClick() } )
    {
        Text("Повторить еще раз..")
    }
}