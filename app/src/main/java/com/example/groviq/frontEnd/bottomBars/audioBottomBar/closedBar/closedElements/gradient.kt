package com.example.groviq.frontEnd.bottomBars.audioBottomBar.closedBar.closedElements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Constraints
import com.example.groviq.frontEnd.grooviqUI

@Composable
fun grooviqUI.elements.closedElements.gradientLoaderBar(constraints: Constraints, baseColor: Color, gradientShift : Float, gradientAlpha : Float )
{
    val highlightColor = MaterialTheme.colorScheme.primary

    val boxWidth = constraints.maxWidth.toFloat()
    val gradientWidth = boxWidth
    val alphaFactor = 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.5f - gradientAlpha * alphaFactor),
                        highlightColor.copy(alpha = gradientAlpha * alphaFactor),
                        baseColor.copy(alpha = 0.5f - gradientAlpha * alphaFactor)
                    ),
                    startX  = gradientShift * (boxWidth + gradientWidth) - gradientWidth,
                    endX    = gradientShift * (boxWidth + gradientWidth),
                )
            )
    )
}