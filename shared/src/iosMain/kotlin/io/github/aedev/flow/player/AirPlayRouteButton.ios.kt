@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVKit.AVRoutePickerView
import platform.UIKit.UIColor

@Composable
actual fun AirPlayRouteButton(
    modifier: Modifier,
    tint: Color,
    activeTint: Color,
) {
    UIKitView(
        factory = { AVRoutePickerView().apply { prioritizesVideoDevices = true } },
        modifier = modifier,
        update = { picker ->
            picker.tintColor = tint.toUIColor()
            picker.activeTintColor = activeTint.toUIColor()
        },
    )
}

private fun Color.toUIColor(): UIColor = UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
