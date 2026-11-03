"""Writes the tiny TrueType font CustomFontStoreTest imports: one empty glyph, made here so no font licence applies.

Usage: python tools/make_test_font.py shared/src/commonTest/resources/test-font.ttf
"""
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen


def build(path: str) -> None:
    fb = FontBuilder(unitsPerEm=1000, isTTF=True)
    fb.setupGlyphOrder([".notdef", "space"])
    fb.setupCharacterMap({0x20: "space"})
    empty = TTGlyphPen(None).glyph()
    fb.setupGlyf({".notdef": empty, "space": empty})
    fb.setupHorizontalMetrics({".notdef": (500, 0), "space": (250, 0)})
    fb.setupHorizontalHeader(ascent=800, descent=-200)
    fb.setupNameTable({"familyName": "Flow Test", "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=800, sTypoDescender=-200, usWinAscent=800, usWinDescent=200)
    fb.setupPost()
    fb.save(path)


if __name__ == "__main__":
    build(sys.argv[1])
