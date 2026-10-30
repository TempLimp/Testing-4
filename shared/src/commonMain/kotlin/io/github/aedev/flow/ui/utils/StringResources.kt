package io.github.aedev.flow.ui.utils

import androidx.compose.runtime.Composable
import io.github.aedev.flow.utils.formatResourceString
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as cmpGetPluralString
import org.jetbrains.compose.resources.getString as cmpGetString
import org.jetbrains.compose.resources.pluralStringResource as cmpPluralStringResource
import org.jetbrains.compose.resources.stringResource as cmpStringResource

// Android's string accessors, with its parameter names so ported call sites compile unchanged.
// Arguments go through formatResourceString, because CMP's own overloads substitute only %n$s and %n$d.

@Composable
fun stringResource(id: StringResource): String = cmpStringResource(id)

@Composable
fun stringResource(
    id: StringResource,
    vararg formatArgs: Any,
): String = formatResourceString(cmpStringResource(id), formatArgs)

@Composable
fun pluralStringResource(
    id: PluralStringResource,
    count: Int,
): String = cmpPluralStringResource(id, count)

@Composable
fun pluralStringResource(
    id: PluralStringResource,
    count: Int,
    vararg formatArgs: Any,
): String = formatResourceString(cmpPluralStringResource(id, count), formatArgs)

/** `context.getString(id, args)` for code outside composition. */
suspend fun getString(
    resource: StringResource,
    vararg formatArgs: Any,
): String = formatResourceString(cmpGetString(resource), formatArgs)

/** `resources.getQuantityString(id, quantity, args)` for code outside composition. */
suspend fun getPluralString(
    resource: PluralStringResource,
    quantity: Int,
    vararg formatArgs: Any,
): String = formatResourceString(cmpGetPluralString(resource, quantity), formatArgs)
