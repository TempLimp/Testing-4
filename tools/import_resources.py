#!/usr/bin/env python3
"""Imports upstream's Android resources into this project's Compose Multiplatform resources.

    python3 tools/import_resources.py              # strings, vector drawables, iOS app icon SVG
    python3 tools/import_resources.py --self-test

Strings. aapt2 resolves escapes, quotes and whitespace when it compiles strings.xml; CMP's Gradle
plugin only unescapes \\n, \\t, \\uXXXX and \\\\ (PrepareComposeResources.handleSpecialCharacters
in 1.13.0-beta01). So every value is resolved the way aapt2 does it and written back escaped for
CMP: getString() then returns exactly what Android's getString() returns. Format specifiers are
kept verbatim; io.github.aedev.flow.ui.utils.stringResource formats them like Android does.
Keys a locale has but the default does not are dropped, because no code can reference them.

Locales. CMP reads the same qualifiers (values-xx, -rYY, b+lang+Script), except Android's legacy
`in` for Indonesian: iOS and JDK 17+ report `id`, so values-in becomes values-id.

Drawables. CMP's vector parser reads only #hex colours and ignores android:tint, so colour
references are resolved and a root tint is baked into the paths (tintMode src_in, the default).
Non-vector drawables (inset, selector, layer-list, animated-vector) are Android splash and widget
chrome; they are listed and skipped.

App icon. The adaptive icon's background and foreground are written as one SVG of the 72dp area
Android launchers show. Render it to the 1024 px PNG the asset catalog uses with any Chromium:
    msedge --headless --hide-scrollbars --force-device-scale-factor=1 --window-size=1024,1024 \\
        --screenshot=iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png build/AppIcon.svg
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "upstream", "app", "src", "main", "res")
OUT = os.path.join(ROOT, "shared", "src", "commonMain", "composeResources")
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Android's pre-ISO-639 codes; iOS and modern JDKs report the current ones.
LEGACY_LANGUAGES = {"in": "id", "iw": "he", "ji": "yi"}

ANDROID_COLORS = {"white": "#FFFFFFFF", "black": "#FF000000", "transparent": "#00000000"}


class ResourceError(Exception):
    pass


# ---------------------------------------------------------------- strings

def aapt2_string(raw):
    """The string aapt2 stores for this XML text (ResourceParser::FlattenXmlSubtree + StringBuilder)."""
    raw = raw.lstrip(" \t\n\r\f\v").rstrip(" \t\n\r\f\v")
    out = []
    quote = False
    last_space = False
    i = 0
    while i < len(raw):
        c = raw[i]
        i += 1
        if not quote and c in " \t\n\r\f\v":
            if not last_space:
                out.append(" ")
                last_space = True
            continue
        last_space = False
        if c == "\\":
            if i == len(raw):
                break
            e = raw[i]
            i += 1
            if e == "t":
                out.append("\t")
            elif e == "n":
                out.append("\n")
            elif e == "u":
                digits = raw[i:i + 4]
                if not re.fullmatch(r"[0-9a-fA-F]{4}", digits):
                    raise ResourceError("invalid unicode escape in %r" % raw)
                out.append(chr(int(digits, 16)))
                i += 4
            else:  # \# \@ \? \" \' \\ and any other character stand for themselves
                out.append(e)
        elif c == '"':
            quote = not quote
        elif c == "'" and not quote:
            raise ResourceError("unescaped apostrophe in %r" % raw)
        else:
            out.append(c)
    return "".join(out)


def cmp_text(value):
    """[value] as XML text that CMP's handleSpecialCharacters turns back into [value]."""
    value = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    return escape(value)


def element_text(element, path):
    if len(element):
        raise ResourceError("%s: <%s name=%r> has inline markup, which this importer does not handle"
                            % (path, element.tag, element.get("name")))
    return element.text or ""


def read_strings(path):
    """{(tag, name): value} in file order; value is a str, a list (string-array) or a dict (plurals)."""
    entries = {}
    for element in ET.parse(path).getroot():
        name = element.get("name")
        key = (element.tag, name)
        if key in entries:
            raise ResourceError("%s: duplicate %s %s" % (path, element.tag, name))
        if element.tag == "string":
            entries[key] = aapt2_string(element_text(element, path))
        elif element.tag == "string-array":
            entries[key] = [aapt2_string(element_text(item, path)) for item in element]
        elif element.tag == "plurals":
            entries[key] = {item.get("quantity"): aapt2_string(element_text(item, path)) for item in element}
        else:
            raise ResourceError("%s: unsupported <%s>" % (path, element.tag))
    return entries


def write_strings(path, entries):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for (tag, name), value in entries.items():
        if tag == "string":
            lines.append('    <string name="%s">%s</string>' % (name, cmp_text(value)))
        elif tag == "string-array":
            lines.append('    <string-array name="%s">' % name)
            lines += ["        <item>%s</item>" % cmp_text(item) for item in value]
            lines.append("    </string-array>")
        else:
            lines.append('    <plurals name="%s">' % name)
            lines += ['        <item quantity="%s">%s</item>' % (q, cmp_text(item)) for q, item in value.items()]
            lines.append("    </plurals>")
    lines.append("</resources>")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def cmp_values_dir(android_dir):
    qualifiers = android_dir.split("-")[1:]
    if qualifiers and qualifiers[0] in LEGACY_LANGUAGES:
        qualifiers[0] = LEGACY_LANGUAGES[qualifiers[0]]
    return "-".join(["values"] + qualifiers)


def import_strings():
    default = read_strings(os.path.join(RES, "values", "strings.xml"))
    report = []
    for android_dir in sorted(os.listdir(RES)):
        path = os.path.join(RES, android_dir, "strings.xml")
        if not android_dir.startswith("values") or not os.path.isfile(path):
            continue
        entries = default if android_dir == "values" else read_strings(path)
        extra = [name for (tag, name) in entries if (tag, name) not in default]
        kept = {key: value for key, value in entries.items() if key in default}
        target = cmp_values_dir(android_dir)
        write_strings(os.path.join(OUT, target, "strings.xml"), kept)
        report.append("%-18s -> %-18s %5d entries%s" % (
            android_dir, target, len(kept), ", dropped %d not in default: %s" % (len(extra), " ".join(extra)) if extra else ""))
    return report


# ---------------------------------------------------------------- drawables

def parse_color(value, colors):
    if value.startswith("@android:color/"):
        value = ANDROID_COLORS[value.split("/", 1)[1]]
    elif value.startswith("@color/"):
        value = colors[value.split("/", 1)[1]]
    if not re.fullmatch(r"#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", value):
        raise ResourceError("unsupported colour %r" % value)
    digits = value[1:]
    if len(digits) <= 4:
        digits = "".join(d * 2 for d in digits)
    if len(digits) == 6:
        digits = "FF" + digits
    return digits.upper()  # AARRGGBB


def read_colors():
    return {c.get("name"): c.text.strip() for c in ET.parse(os.path.join(RES, "values", "colors.xml")).getroot()}


def convert_vector(text, colors):
    """The vector with colour references resolved and a root src_in tint baked into its paths."""
    root = ET.fromstring(text)
    tint = root.get(ANDROID + "tint")
    if tint is not None and root.get(ANDROID + "tintMode", "src_in") != "src_in":
        raise ResourceError("unsupported tintMode")
    tint = parse_color(tint, colors) if tint is not None else None

    def recolor(match):
        attribute, value = match.group(1), match.group(2)
        if tint is None and value.startswith("#"):
            return match.group(0)
        argb = parse_color(value, colors)
        if tint is not None:
            alpha = round(int(tint[:2], 16) * int(argb[:2], 16) / 255)
            argb = "%02X%s" % (alpha, tint[2:])
        return 'android:%s="#%s"' % (attribute, argb)

    text = re.sub(r'android:(fillColor|strokeColor)="([^"]+)"', recolor, text)
    text = re.sub(r'(android:(?:width|height)="[\d.]+)dip"', r'\1dp"', text)  # Android's alias; CMP reads dp or px
    return re.sub(r'\s+android:tint(Mode)?="[^"]*"', "", text)


def import_drawables():
    colors = read_colors()
    target = os.path.join(OUT, "drawable")
    os.makedirs(target, exist_ok=True)
    copied, skipped = 0, []
    for name in sorted(os.listdir(os.path.join(RES, "drawable"))):
        with open(os.path.join(RES, "drawable", name), encoding="utf-8") as f:
            text = f.read()
        tag = ET.fromstring(text).tag
        if tag != "vector":
            skipped.append("%s (%s)" % (name, tag))
            continue
        with open(os.path.join(target, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(convert_vector(text, colors))
        copied += 1
    return ["drawables: %d vectors copied, skipped %d: %s" % (copied, len(skipped), ", ".join(skipped))]


# ---------------------------------------------------------------- app icon

def vector_to_svg_children(element, colors):
    """SVG markup for the paths and groups under [element] (fills, strokes and group transforms)."""
    parts = []
    for child in element:
        a = lambda k, d=None: child.get(ANDROID + k, d)
        if child.tag == "group":
            px, py = float(a("pivotX", 0)), float(a("pivotY", 0))
            transform = "translate(%s %s) rotate(%s) scale(%s %s) translate(%s %s)" % (
                float(a("translateX", 0)) + px, float(a("translateY", 0)) + py, float(a("rotation", 0)),
                float(a("scaleX", 1)), float(a("scaleY", 1)), -px, -py)
            parts.append('<g transform="%s">%s</g>' % (transform, vector_to_svg_children(child, colors)))
        elif child.tag == "path":
            attrs = ['d="%s"' % a("pathData")]
            for android_name, svg_name in (("fillColor", "fill"), ("strokeColor", "stroke")):
                value = a(android_name)
                if value is None:
                    attrs.append('%s="none"' % svg_name)
                    continue
                argb = parse_color(value, colors)
                attrs.append('%s="#%s" %s-opacity="%s"' % (svg_name, argb[2:], svg_name, int(argb[:2], 16) / 255))
            if a("strokeWidth"):
                attrs.append('stroke-width="%s"' % a("strokeWidth"))
            if a("fillType") == "evenOdd":
                attrs.append('fill-rule="evenodd"')
            parts.append("<path %s/>" % " ".join(attrs))
        else:
            raise ResourceError("icon: unsupported <%s>" % child.tag)
    return "".join(parts)


def adaptive_icon_svg(name="ic_launcher"):
    icon = ET.parse(os.path.join(RES, "mipmap-anydpi-v26", name + ".xml")).getroot()
    colors = read_colors()
    background = icon.find("background").get(ANDROID + "drawable")
    foreground = icon.find("foreground").get(ANDROID + "drawable")
    if not background.startswith("@color/") or not foreground.startswith("@drawable/"):
        raise ResourceError("icon: expected a colour background and a vector foreground")
    vector = ET.parse(os.path.join(RES, "drawable", foreground.split("/", 1)[1] + ".xml")).getroot()
    if (vector.get(ANDROID + "viewportWidth"), vector.get(ANDROID + "viewportHeight")) != ("108", "108"):
        raise ResourceError("icon: expected a 108x108 adaptive foreground")
    # Launchers show the centre 72 of the 108 dp layers; iOS masks a full 1024 px square.
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="18 18 72 72">'
            '<rect x="0" y="0" width="108" height="108" fill="#%s"/>%s</svg>\n'
            % (parse_color(background, colors)[2:], vector_to_svg_children(vector, colors)))


# ---------------------------------------------------------------- self-test

def self_test():
    cases = {
        "Plain": "Plain",
        "  padded\n  text  ": "padded text",
        "Don\\'t": "Don't",
        'Say \\"hi\\"': 'Say "hi"',
        '" · "': " · ",
        'No results for "%1$s"': "No results for %1$s",
        '"  keep  "  collapse  ': "  keep   collapse",
        "Line\\nbreak\\tTab": "Line\nbreak\tTab",
        "\\u00e9t\\u00E9": "été",
        "\\@home \\?attr \\# \\\\ \\q": "@home ?attr # \\ q",
        "100%% done": "100%% done",
        "trailing\\": "trailing",
    }
    for raw, expected in cases.items():
        actual = aapt2_string(raw)
        assert actual == expected, "aapt2_string(%r) = %r, expected %r" % (raw, actual, expected)
    for bad in ("it's", "\\u12"):
        try:
            aapt2_string(bad)
        except ResourceError:
            continue
        raise AssertionError("aapt2_string(%r) should fail" % bad)

    assert cmp_text('a\\b\nc\td <&> "q"') == 'a\\\\b\\nc\\td &lt;&amp;&gt; "q"'
    assert cmp_values_dir("values-in") == "values-id"
    assert cmp_values_dir("values-b+yue+Hant") == "values-b+yue+Hant"
    assert cmp_values_dir("values-pt-rBR") == "values-pt-rBR"
    assert cmp_values_dir("values") == "values"

    assert parse_color("#abc", {}) == "FFAABBCC"
    assert parse_color("@android:color/transparent", {}) == "00000000"
    assert parse_color("@color/bg", {"bg": "#80EB0000"}) == "80EB0000"
    tinted = convert_vector('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:tint="#6200EE">'
                            '<path android:fillColor="#80FFFFFF"/><path android:strokeColor="@android:color/white"/></vector>', {})
    assert 'android:fillColor="#806200EE"' in tinted and 'android:strokeColor="#FF6200EE"' in tinted, tinted
    assert "tint" not in tinted, tinted
    assert 'android:width="24.0dp"' in convert_vector('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
                                                      'android:width="24.0dip"/>', {})
    print("import_resources self-test: ok")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return
    for line in import_strings() + import_drawables():
        print(line)
    svg = os.path.join(ROOT, "build", "AppIcon.svg")
    os.makedirs(os.path.dirname(svg), exist_ok=True)
    with open(svg, "w", encoding="utf-8", newline="\n") as f:
        f.write(adaptive_icon_svg())
    print("app icon: wrote %s; render it as described in this script's docstring" % os.path.relpath(svg, ROOT))


if __name__ == "__main__":
    main()
