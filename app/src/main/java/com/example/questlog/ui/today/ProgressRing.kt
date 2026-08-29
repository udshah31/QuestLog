package com.example.questlog.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.reducedMotion

/**
 * A thin arc that sweeps clockwise from 12 o'clock to [fraction] of a full turn, on a
 * faint full-circle track. Animates once on first composition unless reduced motion is on.
 * [content] is centred inside the ring.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val target = fraction.coerceIn(0f, 1f)
    val reduce = reducedMotion()
    val swept = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target, reduce) {
        if (reduce) swept.snapTo(target)
        else swept.animateTo(target, tween(700, easing = FastOutSlowInEasing))
    }
    val c = QuestLogTheme.colors
    Box(
        modifier
            .size(56.dp)
            .drawBehind {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                val inset = strokeWidth.toPx() / 2f
                val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
                drawArc(
                    color = c.rule, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = stroke,
                )
                drawArc(
                    color = c.earned, startAngle = -90f, sweepAngle = 360f * swept.value, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = stroke,
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Preview
@Composable
private fun RingPreview() {
    QuestLogTheme {
        Row(
            modifier = Modifier
                .background(QuestLogTheme.colors.ground)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(0f, 0.5f, 0.85f, 1f).forEach { f ->
                ProgressRing(fraction = f) {
                    Text(
                        "6d", style = QuestType.serifNumeral,
                        color = QuestLogTheme.colors.earned,
                    )
                }
            }
        }
    }
}
