package io.github.aedev.flow

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.app_name
import io.github.aedev.flow.ui.theme.FlowTheme
import io.github.aedev.flow.ui.theme.ThemeCatalog
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.utils.stringResource

// shortcut: theme-switcher demo standing in for the app root (FlowApp); S13 replaces the whole root.
@Composable
fun App() {
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    FlowTheme(themeMode = themeMode) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = WindowInsets.safeDrawing.asPaddingValues()) {
                item {
                    ListItem(headlineContent = { Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge) })
                }
                items(listOf(ThemeMode.SYSTEM) + ThemeCatalog.palettes.map { it.mode }) { mode ->
                    ListItem(
                        headlineContent = { Text(stringResource(ThemeCatalog.nameRes(mode))) },
                        leadingContent = { RadioButton(selected = mode == themeMode, onClick = null) },
                        modifier = Modifier.selectable(selected = mode == themeMode, role = Role.RadioButton) { themeMode = mode },
                    )
                }
            }
        }
    }
}
