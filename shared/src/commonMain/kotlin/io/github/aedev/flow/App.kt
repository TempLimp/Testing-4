package io.github.aedev.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.app_name
import org.jetbrains.compose.resources.stringResource

// shortcut: placeholder root with the default M3 theme, replaced by the ported Flow theme in S02 and FlowApp in S13.
@Composable
fun App() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
