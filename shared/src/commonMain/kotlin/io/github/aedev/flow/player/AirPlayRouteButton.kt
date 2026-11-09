package io.github.aedev.flow.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * iOS's AirPlay route picker (AVRoutePickerView): the system's own button and device list, video receivers first. The
 * player screen (S14) shows it beside upstream's cast button (S21). While AirPlay is the route only AVPlayer's formats
 * play (PlatformCodecSupport.airPlayActive), and a video on the bundled engine reloads on AVPlayer when it starts.
 */
@Composable
expect fun AirPlayRouteButton(
    modifier: Modifier = Modifier,
    tint: Color,
    activeTint: Color,
)
