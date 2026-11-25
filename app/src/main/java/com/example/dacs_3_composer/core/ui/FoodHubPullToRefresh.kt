package com.example.dacs_3_composer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Lightweight pull-to-refresh using Compose nested scroll; no extra library required. */
@Composable
fun FoodHubPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val connection = remember(thresholdPx, refreshing, onRefresh) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!refreshing && source == NestedScrollSource.UserInput && available.y > 0f) {
                    pullDistance = (pullDistance + available.y * 0.55f).coerceAtMost(thresholdPx * 1.5f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!refreshing && pullDistance >= thresholdPx) onRefresh()
                pullDistance = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        if (refreshing || pullDistance > 0f) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = with(LocalDensity.current) { (if (refreshing) 36.dp.toPx() else pullDistance).toDp() })
                    .size(40.dp),
                shape = CircleShape,
                shadowElevation = 4.dp
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(10.dp),
                    strokeWidth = 2.dp,
                    progress = { if (refreshing) 1f else (pullDistance / thresholdPx).coerceIn(0f, 1f) }
                )
            }
        }
    }
}
