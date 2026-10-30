#!/usr/bin/env python3
"""Rewrites upstream Kotlin for this project. Later sessions run it on each file they port.

    python3 tools/codemod.py FILE.kt...     # rewrites in place, prints what still needs a hand
    python3 tools/codemod.py --self-test

Rewritten:
  R.string/plurals/array/drawable/font.x   -> Res.<type>.x, plus the Res and accessor imports
  @StringRes/@PluralsRes/@DrawableRes/@ArrayRes ... : Int  -> StringResource/... (props, params, funs)
  androidx.compose.ui.res.stringResource / pluralStringResource -> io.github.aedev.flow.ui.utils
      (they format %d, %s, %.1f and %% like Android; CMP's own only handle %1$s and %1$d)
  painterResource / vectorResource / stringArrayResource -> org.jetbrains.compose.resources
  ImageVector.vectorResource(...) -> vectorResource(...); a leading `id = ` argument name is dropped
  hiltViewModel -> koinViewModel; @HiltViewModel and @Inject on constructors are removed
Reported, not rewritten: anything that needs judgement (Context, getString outside Compose,
android.*/java.* imports, R types CMP has no equivalent for, Koin registration).
"""

import re
import sys

RES_PACKAGE = "io.github.aedev.flow.resources"
RES_TYPES = ("string", "plurals", "array", "drawable", "font")
ANNOTATION_TYPES = {
    "StringRes": "StringResource",
    "PluralsRes": "PluralStringResource",
    "DrawableRes": "DrawableResource",
    "ArrayRes": "StringArrayResource",
}
IMPORT_REWRITES = {
    "androidx.compose.ui.res.stringResource": "io.github.aedev.flow.ui.utils.stringResource",
    "androidx.compose.ui.res.pluralStringResource": "io.github.aedev.flow.ui.utils.pluralStringResource",
    "androidx.compose.ui.res.painterResource": "org.jetbrains.compose.resources.painterResource",
    "androidx.compose.ui.res.vectorResource": "org.jetbrains.compose.resources.vectorResource",
    "androidx.compose.ui.res.stringArrayResource": "org.jetbrains.compose.resources.stringArrayResource",
    "androidx.hilt.navigation.compose.hiltViewModel": "org.koin.compose.viewmodel.koinViewModel",
    "androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel": "org.koin.compose.viewmodel.koinViewModel",
}
IMPORT_REMOVALS = {"io.github.aedev.flow.R", "dagger.hilt.android.lifecycle.HiltViewModel"} | {
    "androidx.annotation." + a for a in ANNOTATION_TYPES}
# (pattern, advice) for lines left as they are.
REPORTS = [
    (r"(?<![\w.])R\.(?!string\.|plurals\.|array\.|drawable\.|font\.)\w+\.", "R type with no CMP equivalent (mipmap, raw, xml, id, color, dimen, layout, style)"),
    (r"\bandroid\.R\.", "Android framework resource"),
    (r"\b(getString|getQuantityString|getText)\(\s*Res\.", "resource read outside Compose: use the suspend io.github.aedev.flow.ui.utils.getString / getPluralString"),
    (r"\bLocalContext\b|\bContext\b", "Android Context"),
    (r"^import (android|java|javax)\.", "JVM/Android import"),
    (r"\bBuild\.VERSION\b", "Android SDK check"),
    (r"@(StringRes|PluralsRes|DrawableRes|ArrayRes)\b", "resource annotation the type rewrite could not place"),
    (r"@ApplicationContext|@AndroidEntryPoint|@HiltAndroidApp|@Module\b|@InstallIn|EntryPoint", "Hilt wiring: move to a Koin module"),
]


def rewrite(source):
    """Returns (rewritten source, list of follow-up notes with line numbers)."""
    text = source
    accessors = set()
    imports_added = set()

    text = re.sub(r"\bio\.github\.aedev\.flow\.R\.", "R.", text)

    def res(match):
        accessors.add(match.group(2))
        return "Res.%s.%s" % (match.group(1), match.group(2))

    text = re.sub(r"(?<![\w.])R\.(%s)\.(\w+)" % "|".join(RES_TYPES), res, text)

    text = re.sub(r"\bImageVector\.vectorResource\(", "vectorResource(", text)
    if "vectorResource(" in text and "import androidx.compose.ui.res.vectorResource" not in text:
        imports_added.add("org.jetbrains.compose.resources.vectorResource")
    text = re.sub(r"\b(painterResource|vectorResource|stringArrayResource)\(\s*id\s*=\s*", r"\1(", text)

    for annotation, cmp_type in ANNOTATION_TYPES.items():
        before = text
        # An annotation on its own line joins its declaration, so the rewrites below see one line.
        text = re.sub(r"^([ \t]*)@%s[ \t]*\n[ \t]*" % annotation, r"\1@%s " % annotation, text, flags=re.M)
        # Properties and parameters: `@StringRes val title: Int?` -> `val title: StringResource?`
        text = re.sub(r"@%s\s+((?:(?:val|var)\s+)?\w+\s*:\s*)Int\b" % annotation, r"\1%s" % cmp_type, text)
        # Functions whose signature has no default values before the return type.
        text = re.sub(r"@%s\s+((?:[\w@]+\s+)*fun\b[^{=]*?\))\s*:\s*Int\b" % annotation, r"\1: %s" % cmp_type, text)
        if text != before:
            imports_added.add("org.jetbrains.compose.resources." + cmp_type)

    text = re.sub(r"(?<![\w.])hiltViewModel\b", "koinViewModel", text)  # imports are mapped in fix_imports
    text = re.sub(r"^[ \t]*@HiltViewModel[ \t]*\n", "", text, flags=re.M)
    text = re.sub(r"^[ \t]*@Inject[ \t]*\n([ \t]*constructor\b)", r"\1", text, flags=re.M)
    text = re.sub(r"@Inject\s+constructor\b", "constructor", text)

    if accessors:
        imports_added.add(RES_PACKAGE + ".Res")
        imports_added.update(RES_PACKAGE + "." + name for name in accessors)
    text = fix_imports(text, imports_added)

    notes = []
    for number, line in enumerate(text.split("\n"), 1):
        for pattern, advice in REPORTS:
            if re.search(pattern, line):
                notes.append("%d: %s: %s" % (number, advice, line.strip()))
    if "koinViewModel" in text and "koinViewModel" not in source:
        notes.append("koinViewModel: register each ViewModel it obtains in a Koin module (viewModelOf(::X))")
    if re.search(r"^[ \t]*@HiltViewModel", source, flags=re.M):
        notes.append("ViewModel lost @HiltViewModel: register it in a Koin module")
    return text, notes


def import_key(path):
    """ktlint's default layout: everything else, then java, javax, kotlin, then aliases."""
    if " as " in path:
        group = 4
    elif re.match(r"(java|javax|kotlin)\.", path):
        group = ("java", "javax", "kotlin").index(path.split(".")[0]) + 1
    else:
        group = 0
    return group, path


def fix_imports(text, added):
    lines = text.split("\n")
    positions = [i for i, line in enumerate(lines) if line.startswith("import ")]
    current = {lines[i][len("import "):].strip() for i in positions}
    imports = {IMPORT_REWRITES.get(path, path) for path in current if path not in IMPORT_REMOVALS}
    imports |= added
    if "@Inject" not in text:
        imports.discard("javax.inject.Inject")
    if imports == current:
        return text
    block = ["import " + path for path in sorted(imports, key=import_key)]
    if positions:
        first = positions[0]
        lines = [line for i, line in enumerate(lines) if i not in set(positions)]
        lines[first:first] = block
    else:
        package = next(i for i, line in enumerate(lines) if line.startswith("package "))
        lines[package + 1:package + 1] = [""] + block
    return "\n".join(lines)


def self_test():
    before = '''package io.github.aedev.flow.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import javax.inject.Inject

data class Entry(
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int? = null,
)

@StringRes
fun nameRes(mode: Mode): Int =
    when (mode) {
        Mode.A -> R.string.theme_name_a
        else -> io.github.aedev.flow.R.string.theme_name_b
    }

@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val repo: Repo,
    ) : ViewModel()

@Composable
fun Row(vm: ThemeViewModel = hiltViewModel()) {
    Icon(ImageVector.vectorResource(id = R.drawable.ic_shorts), stringResource(R.string.shorts, 3))
    val other = android.R.drawable.stat_sys_download
    Text(context.getString(R.string.shorts))
}
'''
    expected = '''package io.github.aedev.flow.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.ic_shorts
import io.github.aedev.flow.resources.shorts
import io.github.aedev.flow.resources.theme_name_a
import io.github.aedev.flow.resources.theme_name_b
import io.github.aedev.flow.ui.utils.stringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

data class Entry(
    val nameRes: StringResource,
    val iconRes: DrawableResource? = null,
)

fun nameRes(mode: Mode): StringResource =
    when (mode) {
        Mode.A -> Res.string.theme_name_a
        else -> Res.string.theme_name_b
    }

class ThemeViewModel
    constructor(
        private val repo: Repo,
    ) : ViewModel()

@Composable
fun Row(vm: ThemeViewModel = koinViewModel()) {
    Icon(vectorResource(Res.drawable.ic_shorts), stringResource(Res.string.shorts, 3))
    val other = android.R.drawable.stat_sys_download
    Text(context.getString(Res.string.shorts))
}
'''
    actual, notes = rewrite(before)
    assert actual == expected, "rewrite mismatch:\n" + actual
    joined = "\n".join(notes)
    for fragment in ("Android framework resource", "resource read outside Compose", "register it in a Koin module"):
        assert fragment in joined, "missing note %r in:\n%s" % (fragment, joined)
    assert rewrite(expected)[0] == expected, "rewrite is not idempotent"
    print("codemod self-test: ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as f:
            source = f.read()
        text, notes = rewrite(source)
        if text != source:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
        for note in notes:
            print("%s:%s" % (path, note))


if __name__ == "__main__":
    main()
