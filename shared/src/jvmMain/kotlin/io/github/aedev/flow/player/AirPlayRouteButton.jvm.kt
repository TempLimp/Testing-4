package io.github.aedev.flow.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// The jvm target runs tests only: it has no AirPlay.
@Composable
actual fun AirPlayRouteButton(
    modifier: Modifier,
    tint: Color,
    activeTint: Color,
) {}
