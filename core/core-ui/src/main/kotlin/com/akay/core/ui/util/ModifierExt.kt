package com.akay.core.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.08f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        color = Color.White.copy(alpha = alpha),
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(cornerRadius)
    )

@Stable
fun Modifier.glassCardHigh(
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.16f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        color = Color.White.copy(alpha = alpha),
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.20f),
        shape = RoundedCornerShape(cornerRadius)
    )

@Stable
fun Modifier.shimmer(): Modifier = this
