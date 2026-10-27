package com.fluilet.app.presentation.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluilet.app.R

/**
 * Intro animada de Fluilet (~2.4 s). Al terminar —o al tocar "Comenzar"— llama a [onFinished] una sola vez.
 */
@Composable
fun FluiletSplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finish by rememberUpdatedState(onFinished)
    var done by remember { mutableStateOf(false) }
    val clock = remember { Animatable(0f) }

    fun complete() {
        if (!done) {
            done = true
            finish()
        }
    }

    LaunchedEffect(Unit) {
        clock.animateTo(SplashTimeline.TOTAL_MS.toFloat(), tween(SplashTimeline.TOTAL_MS.toInt(), easing = LinearEasing))
        complete()
    }
    val frame = SplashTimeline.frame(clock.value.toLong())

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Column(
                Modifier.align(Alignment.Center).semantics(mergeDescendants = true) {
                    contentDescription = "${FluiletBrand.NAME}. ${FluiletBrand.SLOGAN}"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FluiletIsotypeLogo(
                    Modifier.size(132.dp).graphicsLayer {
                        scaleX = frame.squareScale
                        scaleY = frame.squareScale
                    },
                    fProgress = frame.fProgress,
                    dotScale = frame.dotScale
                )
                Text(
                    FluiletBrand.NAME,
                    color = FluiletBrand.Purple,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.alpha(frame.nameAlpha).graphicsLayer { translationY = frame.nameOffset * 24.dp.toPx() }
                )
                Text(
                    FluiletBrand.SLOGAN,
                    color = FluiletBrand.SloganPurple,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.alpha(frame.sloganAlpha)
                )
                MintLine(Modifier.width(220.dp), progress = frame.lineProgress, thickness = 5.dp)
            }
            TextButton(onClick = ::complete, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.splash_start), style = MaterialTheme.typography.titleMedium, color = FluiletBrand.Purple)
            }
        }
    }
}
