// Converted from upstream's portugueseStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from portuguese.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class portugueseStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var I_p2: Int = 0
    private var I_p1: Int = 0
    private var I_pV: Int = 0
    private fun r_prelude(): Boolean {
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
                        slice_from("a~")
                        break@sw2
                        }
                        2 -> {
                        slice_from("o~")
                        break@sw2
                        }
                        3 -> {
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
    private fun r_mark_regions(): Boolean {
        I_pV = limit
        I_p1 = limit
        I_p2 = limit
        var v_1: Int = cursor
        lab0@ do {
            lab1@ do {
                var v_2: Int = cursor
                lab2@ do {
                    if (!(in_grouping(g_v, 97, 250))) {
                        break@lab2
                    }
                    lab3@ do {
                        var v_3: Int = cursor
                        lab4@ do {
                            if (!(out_grouping(g_v, 97, 250))) {
                                break@lab4
                            }
                            if (!go_out_grouping(g_v, 97, 250)) {
                                break@lab4
                            }
                            cursor++
                            break@lab3
                        } while (false)
                        cursor = v_3
                        if (!(in_grouping(g_v, 97, 250))) {
                            break@lab2
                        }
                        if (!go_in_grouping(g_v, 97, 250)) {
                            break@lab2
                        }
                        cursor++
                    } while (false)
                    break@lab1
                } while (false)
                cursor = v_2
                if (!(out_grouping(g_v, 97, 250))) {
                    break@lab0
                }
                lab5@ do {
                    var v_4: Int = cursor
                    lab6@ do {
                        if (!(out_grouping(g_v, 97, 250))) {
                            break@lab6
                        }
                        if (!go_out_grouping(g_v, 97, 250)) {
                            break@lab6
                        }
                        cursor++
                        break@lab5
                    } while (false)
                    cursor = v_4
                    if (!(in_grouping(g_v, 97, 250))) {
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
            if (!go_out_grouping(g_v, 97, 250)) {
                break@lab7
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 250)) {
                break@lab7
            }
            cursor++
            I_p1 = cursor
            if (!go_out_grouping(g_v, 97, 250)) {
                break@lab7
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 250)) {
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
        loop3@ while (true) {
            var v_1: Int = cursor
            lab0@ do {
                bra = cursor
                among_var = find_among(a_1)
                ket = cursor
                sw4@ do {
                    when (among_var) {
                        1 -> {
                        slice_from("\u00E3")
                        break@sw4
                        }
                        2 -> {
                        slice_from("\u00F5")
                        break@sw4
                        }
                        3 -> {
                        if (cursor >= limit) {
                            break@lab0
                        }
                        cursor++
                        break@sw4
                        }
                    }
                } while (false)
                continue@loop3
            } while (false)
            cursor = v_1
            break@loop3
        }
        return true
    }
    private fun r_RV(): Boolean {
        return I_pV <= cursor
    }
    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }
    private fun r_standard_suffix(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_5)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw5@ do {
            when (among_var) {
                1 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                break@sw5
                }
                2 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("log")
                break@sw5
                }
                3 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("u")
                break@sw5
                }
                4 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("ente")
                break@sw5
                }
                5 -> {
                if (I_p1 > cursor) {
                    return false
                }
                slice_del()
                var v_1: Int = limit - cursor
                lab0@ do {
                    ket = cursor
                    among_var = find_among_b(a_2)
                    if (among_var == 0) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    slice_del()
                    sw6@ do {
                        when (among_var) {
                            1 -> {
                            ket = cursor
                            if (!(eq_s_b("at"))) {
                                cursor = limit - v_1
                                break@lab0
                            }
                            bra = cursor
                            if (!r_R2()) {
                                cursor = limit - v_1
                                break@lab0
                            }
                            slice_del()
                            break@sw6
                            }
                        }
                    } while (false)
                } while (false)
                break@sw5
                }
                6 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_2: Int = limit - cursor
                lab1@ do {
                    ket = cursor
                    if (find_among_b(a_3) == 0) {
                        cursor = limit - v_2
                        break@lab1
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_2
                        break@lab1
                    }
                    slice_del()
                } while (false)
                break@sw5
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
                break@sw5
                }
                8 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_4: Int = limit - cursor
                lab3@ do {
                    ket = cursor
                    if (!(eq_s_b("at"))) {
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
                break@sw5
                }
                9 -> {
                if (!r_RV()) {
                    return false
                }
                if (!(eq_s_b("e"))) {
                    return false
                }
                slice_from("ir")
                break@sw5
                }
            }
        } while (false)
        return true
    }
    private fun r_verb_suffix(): Boolean {
        if (cursor < I_pV) {
            return false
        }
        var v_1: Int = limit_backward
        limit_backward = I_pV
        ket = cursor
        if (find_among_b(a_6) == 0) {
            limit_backward = v_1
            return false
        }
        bra = cursor
        slice_del()
        limit_backward = v_1
        return true
    }
    private fun r_residual_suffix(): Boolean {
        ket = cursor
        if (find_among_b(a_7) == 0) {
            return false
        }
        bra = cursor
        if (!r_RV()) {
            return false
        }
        slice_del()
        return true
    }
    private fun r_residual_form(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_8)
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
                ket = cursor
                lab0@ do {
                    var v_1: Int = limit - cursor
                    lab1@ do {
                        if (!(eq_s_b("u"))) {
                            break@lab1
                        }
                        bra = cursor
                        var v_2: Int = limit - cursor
                        if (!(eq_s_b("g"))) {
                            break@lab1
                        }
                        cursor = limit - v_2
                        break@lab0
                    } while (false)
                    cursor = limit - v_1
                    if (!(eq_s_b("i"))) {
                        return false
                    }
                    bra = cursor
                    var v_3: Int = limit - cursor
                    if (!(eq_s_b("c"))) {
                        return false
                    }
                    cursor = limit - v_3
                } while (false)
                if (!r_RV()) {
                    return false
                }
                slice_del()
                break@sw7
                }
                2 -> {
                slice_from("c")
                break@sw7
                }
            }
        } while (false)
        return true
    }
    override fun stem(): Boolean {
        var v_1: Int = cursor
        r_prelude()
        cursor = v_1
        r_mark_regions()
        limit_backward = cursor
        cursor = limit
        var v_2: Int = limit - cursor
        lab0@ do {
            lab1@ do {
                var v_3: Int = limit - cursor
                lab2@ do {
                    var v_4: Int = limit - cursor
                    lab3@ do {
                        var v_5: Int = limit - cursor
                        lab4@ do {
                            if (!r_standard_suffix()) {
                                break@lab4
                            }
                            break@lab3
                        } while (false)
                        cursor = limit - v_5
                        if (!r_verb_suffix()) {
                            break@lab2
                        }
                    } while (false)
                    cursor = limit - v_4
                    var v_6: Int = limit - cursor
                    lab5@ do {
                        ket = cursor
                        if (!(eq_s_b("i"))) {
                            break@lab5
                        }
                        bra = cursor
                        var v_7: Int = limit - cursor
                        if (!(eq_s_b("c"))) {
                            break@lab5
                        }
                        cursor = limit - v_7
                        if (!r_RV()) {
                            break@lab5
                        }
                        slice_del()
                    } while (false)
                    cursor = limit - v_6
                    break@lab1
                } while (false)
                cursor = limit - v_3
                if (!r_residual_suffix()) {
                    break@lab0
                }
            } while (false)
        } while (false)
        cursor = limit - v_2
        var v_8: Int = limit - cursor
        r_residual_form()
        cursor = limit - v_8
        cursor = limit_backward
        var v_9: Int = cursor
        r_postlude()
        cursor = v_9
        return true
    }
    override fun equals(other: Any?): Boolean = other is portugueseStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.portugueseStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("", -1, 3),
                Among("\u00E3", 0, 1),
                Among("\u00F5", 0, 2),
            )
        private val a_1 =
            arrayOf(
                Among("", -1, 3),
                Among("a~", 0, 1),
                Among("o~", 0, 2),
            )
        private val a_2 =
            arrayOf(
                Among("ic", -1, -1),
                Among("ad", -1, -1),
                Among("os", -1, -1),
                Among("iv", -1, 1),
            )
        private val a_3 =
            arrayOf(
                Among("ante", -1, 1),
                Among("avel", -1, 1),
                Among("\u00EDvel", -1, 1),
            )
        private val a_4 =
            arrayOf(
                Among("ic", -1, 1),
                Among("abil", -1, 1),
                Among("iv", -1, 1),
            )
        private val a_5 =
            arrayOf(
                Among("ica", -1, 1),
                Among("\u00E2ncia", -1, 1),
                Among("\u00EAncia", -1, 4),
                Among("logia", -1, 2),
                Among("ira", -1, 9),
                Among("adora", -1, 1),
                Among("osa", -1, 1),
                Among("ista", -1, 1),
                Among("iva", -1, 8),
                Among("eza", -1, 1),
                Among("idade", -1, 7),
                Among("ante", -1, 1),
                Among("mente", -1, 6),
                Among("amente", 12, 5),
                Among("\u00E1vel", -1, 1),
                Among("\u00EDvel", -1, 1),
                Among("ico", -1, 1),
                Among("ismo", -1, 1),
                Among("oso", -1, 1),
                Among("amento", -1, 1),
                Among("imento", -1, 1),
                Among("ivo", -1, 8),
                Among("a\u00E7a~o", -1, 1),
                Among("u\u00E7a~o", -1, 3),
                Among("ador", -1, 1),
                Among("icas", -1, 1),
                Among("\u00EAncias", -1, 4),
                Among("logias", -1, 2),
                Among("iras", -1, 9),
                Among("adoras", -1, 1),
                Among("osas", -1, 1),
                Among("istas", -1, 1),
                Among("ivas", -1, 8),
                Among("ezas", -1, 1),
                Among("idades", -1, 7),
                Among("adores", -1, 1),
                Among("antes", -1, 1),
                Among("a\u00E7o~es", -1, 1),
                Among("u\u00E7o~es", -1, 3),
                Among("icos", -1, 1),
                Among("ismos", -1, 1),
                Among("osos", -1, 1),
                Among("amentos", -1, 1),
                Among("imentos", -1, 1),
                Among("ivos", -1, 8),
            )
        private val a_6 =
            arrayOf(
                Among("ada", -1, 1),
                Among("ida", -1, 1),
                Among("ia", -1, 1),
                Among("aria", 2, 1),
                Among("eria", 2, 1),
                Among("iria", 2, 1),
                Among("ara", -1, 1),
                Among("era", -1, 1),
                Among("ira", -1, 1),
                Among("ava", -1, 1),
                Among("asse", -1, 1),
                Among("esse", -1, 1),
                Among("isse", -1, 1),
                Among("aste", -1, 1),
                Among("este", -1, 1),
                Among("iste", -1, 1),
                Among("ei", -1, 1),
                Among("arei", 16, 1),
                Among("erei", 16, 1),
                Among("irei", 16, 1),
                Among("am", -1, 1),
                Among("iam", 20, 1),
                Among("ariam", 21, 1),
                Among("eriam", 21, 1),
                Among("iriam", 21, 1),
                Among("aram", 20, 1),
                Among("eram", 20, 1),
                Among("iram", 20, 1),
                Among("avam", 20, 1),
                Among("em", -1, 1),
                Among("arem", 29, 1),
                Among("erem", 29, 1),
                Among("irem", 29, 1),
                Among("assem", 29, 1),
                Among("essem", 29, 1),
                Among("issem", 29, 1),
                Among("ado", -1, 1),
                Among("ido", -1, 1),
                Among("ando", -1, 1),
                Among("endo", -1, 1),
                Among("indo", -1, 1),
                Among("ara~o", -1, 1),
                Among("era~o", -1, 1),
                Among("ira~o", -1, 1),
                Among("ar", -1, 1),
                Among("er", -1, 1),
                Among("ir", -1, 1),
                Among("as", -1, 1),
                Among("adas", 47, 1),
                Among("idas", 47, 1),
                Among("ias", 47, 1),
                Among("arias", 50, 1),
                Among("erias", 50, 1),
                Among("irias", 50, 1),
                Among("aras", 47, 1),
                Among("eras", 47, 1),
                Among("iras", 47, 1),
                Among("avas", 47, 1),
                Among("es", -1, 1),
                Among("ardes", 58, 1),
                Among("erdes", 58, 1),
                Among("irdes", 58, 1),
                Among("ares", 58, 1),
                Among("eres", 58, 1),
                Among("ires", 58, 1),
                Among("asses", 58, 1),
                Among("esses", 58, 1),
                Among("isses", 58, 1),
                Among("astes", 58, 1),
                Among("estes", 58, 1),
                Among("istes", 58, 1),
                Among("is", -1, 1),
                Among("ais", 71, 1),
                Among("eis", 71, 1),
                Among("areis", 73, 1),
                Among("ereis", 73, 1),
                Among("ireis", 73, 1),
                Among("\u00E1reis", 73, 1),
                Among("\u00E9reis", 73, 1),
                Among("\u00EDreis", 73, 1),
                Among("\u00E1sseis", 73, 1),
                Among("\u00E9sseis", 73, 1),
                Among("\u00EDsseis", 73, 1),
                Among("\u00E1veis", 73, 1),
                Among("\u00EDeis", 73, 1),
                Among("ar\u00EDeis", 84, 1),
                Among("er\u00EDeis", 84, 1),
                Among("ir\u00EDeis", 84, 1),
                Among("ados", -1, 1),
                Among("idos", -1, 1),
                Among("amos", -1, 1),
                Among("\u00E1ramos", 90, 1),
                Among("\u00E9ramos", 90, 1),
                Among("\u00EDramos", 90, 1),
                Among("\u00E1vamos", 90, 1),
                Among("\u00EDamos", 90, 1),
                Among("ar\u00EDamos", 95, 1),
                Among("er\u00EDamos", 95, 1),
                Among("ir\u00EDamos", 95, 1),
                Among("emos", -1, 1),
                Among("aremos", 99, 1),
                Among("eremos", 99, 1),
                Among("iremos", 99, 1),
                Among("\u00E1ssemos", 99, 1),
                Among("\u00EAssemos", 99, 1),
                Among("\u00EDssemos", 99, 1),
                Among("imos", -1, 1),
                Among("armos", -1, 1),
                Among("ermos", -1, 1),
                Among("irmos", -1, 1),
                Among("\u00E1mos", -1, 1),
                Among("ar\u00E1s", -1, 1),
                Among("er\u00E1s", -1, 1),
                Among("ir\u00E1s", -1, 1),
                Among("eu", -1, 1),
                Among("iu", -1, 1),
                Among("ou", -1, 1),
                Among("ar\u00E1", -1, 1),
                Among("er\u00E1", -1, 1),
                Among("ir\u00E1", -1, 1),
            )
        private val a_7 =
            arrayOf(
                Among("a", -1, 1),
                Among("i", -1, 1),
                Among("o", -1, 1),
                Among("os", -1, 1),
                Among("\u00E1", -1, 1),
                Among("\u00ED", -1, 1),
                Among("\u00F3", -1, 1),
            )
        private val a_8 =
            arrayOf(
                Among("e", -1, 1),
                Among("\u00E7", -1, 2),
                Among("\u00E9", -1, 1),
                Among("\u00EA", -1, 1),
            )
        private val g_v = charArrayOf('\u0011', '\u0041', '\u0010', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0003', '\u0013', '\u000c', '\u0002')
    }
}
