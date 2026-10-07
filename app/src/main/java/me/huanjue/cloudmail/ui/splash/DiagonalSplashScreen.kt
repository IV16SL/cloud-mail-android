package me.huanjue.cloudmail.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.huanjue.cloudmail.R

/**
 * 对角线切分开屏：上下两半沿对角线切开，分别向上/向下划出。
 * @param onFinished 动画完成后回调
 */
@Composable
fun DiagonalSplashScreen(onFinished: () -> Unit) {
    var startExit by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (startExit) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "splashExit"
    )

    LaunchedEffect(Unit) {
        delay(1000) // 开屏展示 1 秒
        startExit = true
        delay(750) // 等动画播完
        onFinished()
    }

    if (progress < 1f) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 上半：对角线切分，向上划出
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -size.height * progress
                    }
                    .clip(DiagonalTopShape())
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary)
                            .graphicsLayer {
                                // 图标跟着上半一起走，但要抵消位移保持视觉居中直到切开
                                translationY = size.height * progress * 0.5f
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }
            // 下半：对角线切分，向下划出
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = size.height * progress
                    }
                    .clip(DiagonalBottomShape())
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** 对角线切分的上半形状：左上到右下的斜线 */
private class DiagonalTopShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val slant = size.height * 0.12f // 斜线倾斜量
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.5f + slant)
            lineTo(0f, size.height * 0.5f - slant)
            close()
        }
        return Outline.Generic(path)
    }
}

/** 对角线切分的下半形状 */
private class DiagonalBottomShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val slant = size.height * 0.12f
        val path = Path().apply {
            moveTo(0f, size.height * 0.5f - slant)
            lineTo(size.width, size.height * 0.5f + slant)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}
