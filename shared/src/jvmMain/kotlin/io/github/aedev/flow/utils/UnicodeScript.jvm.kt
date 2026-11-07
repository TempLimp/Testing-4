package io.github.aedev.flow.utils

import com.ibm.icu.lang.UScript
import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

internal actual fun icuScriptCode(codePoint: Int): Int = UScript.getScript(codePoint)

internal actual fun icuWordBreaks(text: String): List<String> {
    val breaks = BreakIterator.getWordInstance(ULocale.ROOT)
    breaks.setText(text)
    val pieces = ArrayList<String>()
    var start = breaks.first()
    var end = breaks.next()
    while (end != BreakIterator.DONE) {
        pieces += text.substring(start, end)
        start = end
        end = breaks.next()
    }
    return pieces
}
