// Converted from upstream's spanishStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from spanish.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class spanishStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var I_p2: Int = 0
    private var I_p1: Int = 0
    private var I_pV: Int = 0
    private fun r_mark_regions(): Boolean {
        I_pV = limit
        I_p1 = limit
        I_p2 = limit
        var v_1: Int = cursor
        lab0@ do {
            lab1@ do {
                var v_2: Int = cursor
                lab2@ do {
                    if (!(in_grouping(g_v, 97, 252))) {
                        break@lab2
                    }
                    lab3@ do {
                        var v_3: Int = cursor
                        lab4@ do {
                            if (!(out_grouping(g_v, 97, 252))) {
                                break@lab4
                            }
                            if (!go_out_grouping(g_v, 97, 252)) {
                                break@lab4
                            }
                            cursor++
                            break@lab3
                        } while (false)
                        cursor = v_3
                        if (!(in_grouping(g_v, 97, 252))) {
                            break@lab2
                        }
                        if (!go_in_grouping(g_v, 97, 252)) {
                            break@lab2
                        }
                        cursor++
                    } while (false)
                    break@lab1
                } while (false)
                cursor = v_2
                if (!(out_grouping(g_v, 97, 252))) {
                    break@lab0
                }
                lab5@ do {
                    var v_4: Int = cursor
                    lab6@ do {
                        if (!(out_grouping(g_v, 97, 252))) {
                            break@lab6
                        }
                        if (!go_out_grouping(g_v, 97, 252)) {
                            break@lab6
                        }
                        cursor++
                        break@lab5
                    } while (false)
                    cursor = v_4
                    if (!(in_grouping(g_v, 97, 252))) {
                        break@lab0
                    }
                    if (cursor >= limit) {
                        break@lab0
                    }
                    cursor++
                } while (false)
            } while (false)
            I_pV = cursor
        } while (false)
        cursor = v_1
        var v_5: Int = cursor
        lab7@ do {
            if (!go_out_grouping(g_v, 97, 252)) {
                break@lab7
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 252)) {
                break@lab7
            }
            cursor++
            I_p1 = cursor
            if (!go_out_grouping(g_v, 97, 252)) {
                break@lab7
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 252)) {
                break@lab7
            }
            cursor++
            I_p2 = cursor
        } while (false)
        cursor = v_5
        return true
    }
    private fun r_postlude(): Boolean {
        var among_var: Int
        loop1@ while (true) {
            var v_1: Int = cursor
            lab0@ do {
                bra = cursor
                among_var = find_among(a_0)
                ket = cursor
                sw2@ do {
                    when (among_var) {
                        1 -> {
                        slice_from("a")
                        break@sw2
                        }
                        2 -> {
                        slice_from("e")
                        break@sw2
                        }
                        3 -> {
                        slice_from("i")
                        break@sw2
                        }
                        4 -> {
                        slice_from("o")
                        break@sw2
                        }
                        5 -> {
                        slice_from("u")
                        break@sw2
                        }
                        6 -> {
                        if (cursor >= limit) {
                            break@lab0
                        }
                        cursor++
                        break@sw2
                        }
                    }
                } while (false)
                continue@loop1
            } while (false)
            cursor = v_1
            break@loop1
        }
        return true
    }
    private fun r_RV(): Boolean {
        return I_pV <= cursor
    }
    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }
    private fun r_attached_pronoun(): Boolean {
        var among_var: Int
        ket = cursor
        if (find_among_b(a_1) == 0) {
            return false
        }
        bra = cursor
        among_var = find_among_b(a_2)
        if (among_var == 0) {
            return false
        }
        if (!r_RV()) {
            return false
        }
        sw3@ do {
            when (among_var) {
                1 -> {
                bra = cursor
                slice_from("iendo")
                break@sw3
                }
                2 -> {
                bra = cursor
                slice_from("ando")
                break@sw3
                }
                3 -> {
                bra = cursor
                slice_from("ar")
                break@sw3
                }
                4 -> {
                bra = cursor
                slice_from("er")
                break@sw3
                }
                5 -> {
                bra = cursor
                slice_from("ir")
                break@sw3
                }
                6 -> {
                slice_del()
                break@sw3
                }
                7 -> {
                if (!(eq_s_b("u"))) {
                    return false
                }
                slice_del()
                break@sw3
                }
            }
        } while (false)
        return true
    }
    private fun r_standard_suffix(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_6)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw4@ do {
            when (among_var) {
                1 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                break@sw4
                }
                2 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_1: Int = limit - cursor
                lab0@ do {
                    ket = cursor
                    if (!(eq_s_b("ic"))) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    slice_del()
                } while (false)
                break@sw4
                }
                3 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("log")
                break@sw4
                }
                4 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("u")
                break@sw4
                }
                5 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("ente")
                break@sw4
                }
                6 -> {
                if (I_p1 > cursor) {
                    return false
                }
                slice_del()
                var v_2: Int = limit - cursor
                lab1@ do {
                    ket = cursor
                    among_var = find_among_b(a_3)
                    if (among_var == 0) {
                        cursor = limit - v_2
                        break@lab1
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_2
                        break@lab1
                    }
                    slice_del()
                    sw5@ do {
                        when (among_var) {
                            1 -> {
                            ket = cursor
                            if (!(eq_s_b("at"))) {
                                cursor = limit - v_2
                                break@lab1
                            }
                            bra = cursor
                            if (!r_R2()) {
                                cursor = limit - v_2
                                break@lab1
                            }
                            slice_del()
                            break@sw5
                            }
                        }
                    } while (false)
                } while (false)
                break@sw4
                }
                7 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_3: Int = limit - cursor
                lab2@ do {
                    ket = cursor
                    if (find_among_b(a_4) == 0) {
                        cursor = limit - v_3
                        break@lab2
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_3
                        break@lab2
                    }
                    slice_del()
                } while (false)
                break@sw4
                }
                8 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_4: Int = limit - cursor
                lab3@ do {
                    ket = cursor
                    if (find_among_b(a_5) == 0) {
                        cursor = limit - v_4
                        break@lab3
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_4
                        break@lab3
                    }
                    slice_del()
                } while (false)
                break@sw4
                }
                9 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_5: Int = limit - cursor
                lab4@ do {
                    ket = cursor
                    if (!(eq_s_b("at"))) {
                        cursor = limit - v_5
                        break@lab4
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_5
                        break@lab4
                    }
                    slice_del()
                } while (false)
                break@sw4
                }
            }
        } while (false)
        return true
    }
    private fun r_y_verb_suffix(): Boolean {
        if (cursor < I_pV) {
            return false
        }
        var v_1: Int = limit_backward
        limit_backward = I_pV
        ket = cursor
        if (find_among_b(a_7) == 0) {
            limit_backward = v_1
            return false
        }
        bra = cursor
        limit_backward = v_1
        if (!(eq_s_b("u"))) {
            return false
        }
        slice_del()
        return true
    }
    private fun r_verb_suffix(): Boolean {
        var among_var: Int
        if (cursor < I_pV) {
            return false
        }
        var v_1: Int = limit_backward
        limit_backward = I_pV
        ket = cursor
        among_var = find_among_b(a_8)
        if (among_var == 0) {
            limit_backward = v_1
            return false
        }
        bra = cursor
        limit_backward = v_1
        sw6@ do {
            when (among_var) {
                1 -> {
                var v_2: Int = limit - cursor
                lab0@ do {
                    if (!(eq_s_b("u"))) {
                        cursor = limit - v_2
                        break@lab0
                    }
                    var v_3: Int = limit - cursor
                    if (!(eq_s_b("g"))) {
                        cursor = limit - v_2
                        break@lab0
                    }
                    cursor = limit - v_3
                } while (false)
                bra = cursor
                slice_del()
                break@sw6
                }
                2 -> {
                slice_del()
                break@sw6
                }
            }
        } while (false)
        return true
    }
    private fun r_residual_suffix(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_9)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw7@ do {
            when (among_var) {
                1 -> {
                if (!r_RV()) {
                    return false
                }
                slice_del()
                break@sw7
                }
                2 -> {
                if (!r_RV()) {
                    return false
                }
                slice_del()
                var v_1: Int = limit - cursor
                lab0@ do {
                    ket = cursor
                    if (!(eq_s_b("u"))) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    bra = cursor
                    var v_2: Int = limit - cursor
                    if (!(eq_s_b("g"))) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    cursor = limit - v_2
                    if (!r_RV()) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    slice_del()
                } while (false)
                break@sw7
                }
            }
        } while (false)
        return true
    }
    override fun stem(): Boolean {
        r_mark_regions()
        limit_backward = cursor
        cursor = limit
        var v_1: Int = limit - cursor
        r_attached_pronoun()
        cursor = limit - v_1
        var v_2: Int = limit - cursor
        lab0@ do {
            lab1@ do {
                var v_3: Int = limit - cursor
                lab2@ do {
                    if (!r_standard_suffix()) {
                        break@lab2
                    }
                    break@lab1
                } while (false)
                cursor = limit - v_3
                lab3@ do {
                    if (!r_y_verb_suffix()) {
                        break@lab3
                    }
                    break@lab1
                } while (false)
                cursor = limit - v_3
                if (!r_verb_suffix()) {
                    break@lab0
                }
            } while (false)
        } while (false)
        cursor = limit - v_2
        var v_4: Int = limit - cursor
        r_residual_suffix()
        cursor = limit - v_4
        cursor = limit_backward
        var v_5: Int = cursor
        r_postlude()
        cursor = v_5
        return true
    }
    override fun equals(other: Any?): Boolean = other is spanishStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.spanishStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("", -1, 6),
                Among("\u00E1", 0, 1),
                Among("\u00E9", 0, 2),
                Among("\u00ED", 0, 3),
                Among("\u00F3", 0, 4),
                Among("\u00FA", 0, 5),
            )
        private val a_1 =
            arrayOf(
                Among("la", -1, -1),
                Among("sela", 0, -1),
                Among("le", -1, -1),
                Among("me", -1, -1),
                Among("se", -1, -1),
                Among("lo", -1, -1),
                Among("selo", 5, -1),
                Among("las", -1, -1),
                Among("selas", 7, -1),
                Among("les", -1, -1),
                Among("los", -1, -1),
                Among("selos", 10, -1),
                Among("nos", -1, -1),
            )
        private val a_2 =
            arrayOf(
                Among("ando", -1, 6),
                Among("iendo", -1, 6),
                Among("yendo", -1, 7),
                Among("\u00E1ndo", -1, 2),
                Among("i\u00E9ndo", -1, 1),
                Among("ar", -1, 6),
                Among("er", -1, 6),
                Among("ir", -1, 6),
                Among("\u00E1r", -1, 3),
                Among("\u00E9r", -1, 4),
                Among("\u00EDr", -1, 5),
            )
        private val a_3 =
            arrayOf(
                Among("ic", -1, -1),
                Among("ad", -1, -1),
                Among("os", -1, -1),
                Among("iv", -1, 1),
            )
        private val a_4 =
            arrayOf(
                Among("able", -1, 1),
                Among("ible", -1, 1),
                Among("ante", -1, 1),
            )
        private val a_5 =
            arrayOf(
                Among("ic", -1, 1),
                Among("abil", -1, 1),
                Among("iv", -1, 1),
            )
        private val a_6 =
            arrayOf(
                Among("ica", -1, 1),
                Among("ancia", -1, 2),
                Among("encia", -1, 5),
                Among("adora", -1, 2),
                Among("osa", -1, 1),
                Among("ista", -1, 1),
                Among("iva", -1, 9),
                Among("anza", -1, 1),
                Among("log\u00EDa", -1, 3),
                Among("idad", -1, 8),
                Among("able", -1, 1),
                Among("ible", -1, 1),
                Among("ante", -1, 2),
                Among("mente", -1, 7),
                Among("amente", 13, 6),
                Among("acion", -1, 2),
                Among("ucion", -1, 4),
                Among("aci\u00F3n", -1, 2),
                Among("uci\u00F3n", -1, 4),
                Among("ico", -1, 1),
                Among("ismo", -1, 1),
                Among("oso", -1, 1),
                Among("amiento", -1, 1),
                Among("imiento", -1, 1),
                Among("ivo", -1, 9),
                Among("ador", -1, 2),
                Among("icas", -1, 1),
                Among("ancias", -1, 2),
                Among("encias", -1, 5),
                Among("adoras", -1, 2),
                Among("osas", -1, 1),
                Among("istas", -1, 1),
                Among("ivas", -1, 9),
                Among("anzas", -1, 1),
                Among("log\u00EDas", -1, 3),
                Among("idades", -1, 8),
                Among("ables", -1, 1),
                Among("ibles", -1, 1),
                Among("aciones", -1, 2),
                Among("uciones", -1, 4),
                Among("adores", -1, 2),
                Among("antes", -1, 2),
                Among("icos", -1, 1),
                Among("ismos", -1, 1),
                Among("osos", -1, 1),
                Among("amientos", -1, 1),
                Among("imientos", -1, 1),
                Among("ivos", -1, 9),
            )
        private val a_7 =
            arrayOf(
                Among("ya", -1, 1),
                Among("ye", -1, 1),
                Among("yan", -1, 1),
                Among("yen", -1, 1),
                Among("yeron", -1, 1),
                Among("yendo", -1, 1),
                Among("yo", -1, 1),
                Among("yas", -1, 1),
                Among("yes", -1, 1),
                Among("yais", -1, 1),
                Among("yamos", -1, 1),
                Among("y\u00F3", -1, 1),
            )
        private val a_8 =
            arrayOf(
                Among("aba", -1, 2),
                Among("ada", -1, 2),
                Among("ida", -1, 2),
                Among("ara", -1, 2),
                Among("iera", -1, 2),
                Among("\u00EDa", -1, 2),
                Among("ar\u00EDa", 5, 2),
                Among("er\u00EDa", 5, 2),
                Among("ir\u00EDa", 5, 2),
                Among("ad", -1, 2),
                Among("ed", -1, 2),
                Among("id", -1, 2),
                Among("ase", -1, 2),
                Among("iese", -1, 2),
                Among("aste", -1, 2),
                Among("iste", -1, 2),
                Among("an", -1, 2),
                Among("aban", 16, 2),
                Among("aran", 16, 2),
                Among("ieran", 16, 2),
                Among("\u00EDan", 16, 2),
                Among("ar\u00EDan", 20, 2),
                Among("er\u00EDan", 20, 2),
                Among("ir\u00EDan", 20, 2),
                Among("en", -1, 1),
                Among("asen", 24, 2),
                Among("iesen", 24, 2),
                Among("aron", -1, 2),
                Among("ieron", -1, 2),
                Among("ar\u00E1n", -1, 2),
                Among("er\u00E1n", -1, 2),
                Among("ir\u00E1n", -1, 2),
                Among("ado", -1, 2),
                Among("ido", -1, 2),
                Among("ando", -1, 2),
                Among("iendo", -1, 2),
                Among("ar", -1, 2),
                Among("er", -1, 2),
                Among("ir", -1, 2),
                Among("as", -1, 2),
                Among("abas", 39, 2),
                Among("adas", 39, 2),
                Among("idas", 39, 2),
                Among("aras", 39, 2),
                Among("ieras", 39, 2),
                Among("\u00EDas", 39, 2),
                Among("ar\u00EDas", 45, 2),
                Among("er\u00EDas", 45, 2),
                Among("ir\u00EDas", 45, 2),
                Among("es", -1, 1),
                Among("ases", 49, 2),
                Among("ieses", 49, 2),
                Among("abais", -1, 2),
                Among("arais", -1, 2),
                Among("ierais", -1, 2),
                Among("\u00EDais", -1, 2),
                Among("ar\u00EDais", 55, 2),
                Among("er\u00EDais", 55, 2),
                Among("ir\u00EDais", 55, 2),
                Among("aseis", -1, 2),
                Among("ieseis", -1, 2),
                Among("asteis", -1, 2),
                Among("isteis", -1, 2),
                Among("\u00E1is", -1, 2),
                Among("\u00E9is", -1, 1),
                Among("ar\u00E9is", 64, 2),
                Among("er\u00E9is", 64, 2),
                Among("ir\u00E9is", 64, 2),
                Among("ados", -1, 2),
                Among("idos", -1, 2),
                Among("amos", -1, 2),
                Among("\u00E1bamos", 70, 2),
                Among("\u00E1ramos", 70, 2),
                Among("i\u00E9ramos", 70, 2),
                Among("\u00EDamos", 70, 2),
                Among("ar\u00EDamos", 74, 2),
                Among("er\u00EDamos", 74, 2),
                Among("ir\u00EDamos", 74, 2),
                Among("emos", -1, 1),
                Among("aremos", 78, 2),
                Among("eremos", 78, 2),
                Among("iremos", 78, 2),
                Among("\u00E1semos", 78, 2),
                Among("i\u00E9semos", 78, 2),
                Among("imos", -1, 2),
                Among("ar\u00E1s", -1, 2),
                Among("er\u00E1s", -1, 2),
                Among("ir\u00E1s", -1, 2),
                Among("\u00EDs", -1, 2),
                Among("ar\u00E1", -1, 2),
                Among("er\u00E1", -1, 2),
                Among("ir\u00E1", -1, 2),
                Among("ar\u00E9", -1, 2),
                Among("er\u00E9", -1, 2),
                Among("ir\u00E9", -1, 2),
                Among("i\u00F3", -1, 2),
            )
        private val a_9 =
            arrayOf(
                Among("a", -1, 1),
                Among("e", -1, 2),
                Among("o", -1, 1),
                Among("os", -1, 1),
                Among("\u00E1", -1, 1),
                Among("\u00E9", -1, 2),
                Among("\u00ED", -1, 1),
                Among("\u00F3", -1, 1),
            )
        private val g_v = charArrayOf('\u0011', '\u0041', '\u0010', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0001', '\u0011', '\u0004', '\u000a')
    }
}
