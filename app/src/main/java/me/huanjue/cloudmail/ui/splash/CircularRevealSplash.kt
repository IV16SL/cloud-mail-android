package me.huanjue.cloudmail.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.huanjue.cloudmail.R
import kotlin.math.hypot

/**
 * 圆形扩散开屏：从中心图标位置向外扩散圆，揭开主界面。
 * @param onFinished 动画完成后回调
 */
@Composable
fun CircularRevealSplash(onFinished: () -> Unit) {
    var startExit by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (startExit) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "reveal"
    )
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val bgColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        delay(600) // 开屏展示 0.6 秒
        startExit = true
        delay(550)
        onFinished()
    }

    if (progress < 1f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
        ) {
            // 背景 + 扩散圆挖洞
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(bgColor)
                if (viewSize != IntSize.Zero && progress > 0f) {
                    val maxRadius = hypot(
                        viewSize.width / 2f,
                        viewSize.height / 2f
                    )
                    // 从中心扩散，用 Clear 模式挖洞透出下层主界面
                    drawCircle(
                        color = Color.Transparent,
                        radius = maxRadius * progress,
                        center = Offset(
                            viewSize.width / 2f,
                            viewSize.height / 2f
                        ),
                        blendMode = BlendMode.Clear
                    )
                }
            }
            // 中心图标：扩散开始后快速缩小淡出
            if (progress < 0.4f) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val iconScale = 1f - (progress / 0.4f) * 0.3f
                    val iconAlpha = 1f - (progress / 0.4f)
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .size((96 * iconScale).dp)
                            .clip(CircleShape),
                        alpha = iconAlpha.coerceIn(0f, 1f)
                    )
                }
            }
        }
    }
}
