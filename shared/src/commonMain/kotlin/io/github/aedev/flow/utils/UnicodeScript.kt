package io.github.aedev.flow.utils

/**
 * `java.lang.Character.UnicodeScript` for the scripts the app asks about; every other script reads [OTHER].
 * Android answers it from ICU, and so does the port: ICU4J on jvm, the system's ICU (libicucore) on iOS.
 */
enum class UnicodeScript {
    LATIN,
    CYRILLIC,
    ARABIC,
    DEVANAGARI,
    HAN,
    HIRAGANA,
    KATAKANA,
    HANGUL,
    THAI,
    LAO,
    KHMER,
    MYANMAR,
    OTHER,
    ;

    companion object {
        fun of(codePoint: Int): UnicodeScript = fromIcu(icuScriptCode(codePoint))

        /** ICU's `UScriptCode` values (uscript.h; stable since ICU 2.2). */
        internal fun fromIcu(code: Int): UnicodeScript =
            when (code) {
                2 -> ARABIC
                8 -> CYRILLIC
                10 -> DEVANAGARI
                17 -> HAN
                18 -> HANGUL
                20 -> HIRAGANA
                22 -> KATAKANA
                23 -> KHMER
                24 -> LAO
                25 -> LATIN
                28 -> MYANMAR
                38 -> THAI
                else -> OTHER
            }
    }
}

/** ICU's `uscript_getScript`: the `UScriptCode` of [codePoint]. */
internal expect fun icuScriptCode(codePoint: Int): Int

/**
 * ICU's word break iterator over [text] with the root locale: consecutive pieces, punctuation and spaces
 * included, scripts written without spaces split by ICU's dictionaries (Android's `java.text.BreakIterator`).
 */
internal expect fun icuWordBreaks(text: String): List<String>
