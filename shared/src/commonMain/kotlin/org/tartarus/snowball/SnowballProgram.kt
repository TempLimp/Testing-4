/*
 * Copyright (c) 2001, Dr Martin Porter
 * Copyright (c) 2004,2005, Richard Boulton
 * Copyright (c) 2013, Yoshiki Shibukawa
 * Copyright (c) 2006-2025, Olly Betts
 * All rights reserved.
 *
 * BSD-3-Clause; the full text is in iosApp/licenses/Snowball.txt (THIRD_PARTY_NOTICES.md lists it).
 */

@file:Suppress("FunctionName", "PropertyName")

package org.tartarus.snowball

// Upstream's SnowballProgram.java, SnowballStemmer.java and Among.java (Snowball 3.1.1) in Kotlin. Java's
// getCurrent()/setCurrent(String) become the `current` property (upstream's Kotlin already calls them that way);
// the char buffer is `buffer`. Among's MethodHandle form is left out: none of the seven stemmers uses it.

open class SnowballProgram protected constructor() {
    protected var buffer: CharArray = CharArray(0)
    protected var cursor: Int = 0
    protected var length: Int = 0
    protected var limit: Int = 0
    protected var limit_backward: Int = 0
    protected var bra: Int = 0
    protected var ket: Int = 0

    /** The current string. */
    var current: String
        get() = buffer.concatToString(0, length)
        set(value) {
            buffer = value.toCharArray()
            cursor = 0
            length = value.length
            limit = value.length
            limit_backward = 0
            bra = cursor
            ket = limit
        }

    protected fun in_grouping(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        if (cursor >= limit) return false
        var ch = buffer[cursor].code
        if (ch > max || ch < min) return false
        ch -= min
        if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) == 0) return false
        cursor++
        return true
    }

    protected fun go_in_grouping(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        while (cursor < limit) {
            var ch = buffer[cursor].code
            if (ch > max || ch < min) return true
            ch -= min
            if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) == 0) return true
            cursor++
        }
        return false
    }

    protected fun in_grouping_b(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        if (cursor <= limit_backward) return false
        var ch = buffer[cursor - 1].code
        if (ch > max || ch < min) return false
        ch -= min
        if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) == 0) return false
        cursor--
        return true
    }

    protected fun go_in_grouping_b(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        while (cursor > limit_backward) {
            var ch = buffer[cursor - 1].code
            if (ch > max || ch < min) return true
            ch -= min
            if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) == 0) return true
            cursor--
        }
        return false
    }

    protected fun out_grouping(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        if (cursor >= limit) return false
        var ch = buffer[cursor].code
        if (ch > max || ch < min) {
            cursor++
            return true
        }
        ch -= min
        if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) == 0) {
            cursor++
            return true
        }
        return false
    }

    protected fun go_out_grouping(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        while (cursor < limit) {
            var ch = buffer[cursor].code
            if (ch <= max && ch >= min) {
                ch -= min
                if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) != 0) return true
            }
            cursor++
        }
        return false
    }

    protected fun out_grouping_b(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        if (cursor <= limit_backward) return false
        var ch = buffer[cursor - 1].code
        if (ch > max || ch < min) {
            cursor--
            return true
        }
        ch -= min
        if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) == 0) {
            cursor--
            return true
        }
        return false
    }

    protected fun go_out_grouping_b(
        s: CharArray,
        min: Int,
        max: Int,
    ): Boolean {
        while (cursor > limit_backward) {
            var ch = buffer[cursor - 1].code
            if (ch <= max && ch >= min) {
                ch -= min
                if ((s[ch shr 3].code and (0x1 shl (ch and 0x7))) != 0) return true
            }
            cursor--
        }
        return false
    }

    protected fun eq_s(s: CharSequence): Boolean {
        if (limit - cursor < s.length) return false
        for (i in 0 until s.length) {
            if (buffer[cursor + i] != s[i]) return false
        }
        cursor += s.length
        return true
    }

    protected fun eq_s_b(s: CharSequence): Boolean {
        if (cursor - limit_backward < s.length) return false
        for (i in 0 until s.length) {
            if (buffer[cursor - s.length + i] != s[i]) return false
        }
        cursor -= s.length
        return true
    }

    protected fun find_among(v: Array<Among>): Int {
        var i = 0
        var j = v.size
        val c = cursor
        val l = limit
        var commonI = 0
        var commonJ = 0
        var firstKeyInspected = false
        while (true) {
            val k = i + ((j - i) shr 1)
            var diff = 0
            var common = if (commonI < commonJ) commonI else commonJ
            val w = v[k]
            var i2 = common
            while (i2 < w.s.size) {
                if (c + common == l) {
                    diff = -1
                    break
                }
                diff = buffer[c + common].code - w.s[i2].code
                if (diff != 0) break
                common++
                i2++
            }
            if (diff < 0) {
                j = k
                commonJ = common
            } else {
                i = k
                commonI = common
            }
            if (j - i <= 1) {
                if (i > 0) break
                if (j == i) break
                if (firstKeyInspected) break
                firstKeyInspected = true
            }
        }
        while (true) {
            val w = v[i]
            if (commonI >= w.s.size) {
                cursor = c + w.s.size
                return w.result
            }
            i = w.substring_i
            if (i < 0) return 0
        }
    }

    protected fun find_among_b(v: Array<Among>): Int {
        var i = 0
        var j = v.size
        val c = cursor
        val lb = limit_backward
        var commonI = 0
        var commonJ = 0
        var firstKeyInspected = false
        while (true) {
            val k = i + ((j - i) shr 1)
            var diff = 0
            var common = if (commonI < commonJ) commonI else commonJ
            val w = v[k]
            var i2 = w.s.size - 1 - common
            while (i2 >= 0) {
                if (c - common == lb) {
                    diff = -1
                    break
                }
                diff = buffer[c - 1 - common].code - w.s[i2].code
                if (diff != 0) break
                common++
                i2--
            }
            if (diff < 0) {
                j = k
                commonJ = common
            } else {
                i = k
                commonI = common
            }
            if (j - i <= 1) {
                if (i > 0) break
                if (j == i) break
                if (firstKeyInspected) break
                firstKeyInspected = true
            }
        }
        while (true) {
            val w = v[i]
            if (commonI >= w.s.size) {
                cursor = c - w.s.size
                return w.result
            }
            i = w.substring_i
            if (i < 0) return 0
        }
    }

    /** Replaces the chars between [cBra] and [cKet] in the buffer by [s]. */
    protected fun replace_s(
        cBra: Int,
        cKet: Int,
        s: CharSequence,
    ): Int {
        val adjustment = s.length - (cKet - cBra)
        val newLength = length + adjustment
        if (newLength > buffer.size) buffer = buffer.copyOf(newLength)
        if (adjustment != 0 && cKet < length) buffer.copyInto(buffer, cBra + s.length, cKet, length)
        for (i in 0 until s.length) buffer[cBra + i] = s[i]
        length += adjustment
        limit += adjustment
        if (cursor >= cKet) {
            cursor += adjustment
        } else if (cursor > cBra) {
            cursor = cBra
        }
        return adjustment
    }

    protected fun slice_from(s: CharSequence) {
        replace_s(bra, ket, s)
        ket = bra + s.length
    }

    protected fun slice_del() {
        slice_from("")
    }

    protected fun insert(
        cBra: Int,
        cKet: Int,
        s: CharSequence,
    ) {
        val adjustment = replace_s(cBra, cKet, s)
        if (cBra <= bra) bra += adjustment
        if (cBra <= ket) ket += adjustment
    }
}

abstract class SnowballStemmer : SnowballProgram() {
    abstract fun stem(): Boolean
}

class Among(
    s: String,
    /** Index to the longest matching substring. */
    val substring_i: Int,
    /** Result of the lookup. */
    val result: Int,
) {
    /** Search string. */
    val s: CharArray = s.toCharArray()
}
