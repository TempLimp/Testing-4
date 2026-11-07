// Converted from upstream's englishStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from english.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class englishStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var B_Y_found: Boolean = false
    private var I_p2: Int = 0
    private var I_p1: Int = 0
    private fun r_prelude(): Boolean {
        B_Y_found = false
        var v_1: Int = cursor
        lab0@ do {
            bra = cursor
            if (!(eq_s("'"))) {
                break@lab0
            }
            ket = cursor
            slice_del()
        } while (false)
        cursor = v_1
        var v_2: Int = cursor
        lab1@ do {
            bra = cursor
            if (!(eq_s("y"))) {
                break@lab1
            }
            ket = cursor
            slice_from("Y")
            B_Y_found = true
        } while (false)
        cursor = v_2
        var v_3: Int = cursor
        lab2@ do {
            loop1@ while (true) {
                var v_4: Int = cursor
                lab3@ do {
                    golab4@ while (true) {
                        var v_5: Int = cursor
                        lab5@ do {
                            if (!(in_grouping(g_v, 97, 121))) {
                                break@lab5
                            }
                            bra = cursor
                            if (!(eq_s("y"))) {
                                break@lab5
                            }
                            ket = cursor
                            cursor = v_5
                            break@golab4
                        } while (false)
                        cursor = v_5
                        if (cursor >= limit) {
                            break@lab3
                        }
                        cursor++
                    }
                    slice_from("Y")
                    B_Y_found = true
                    continue@loop1
                } while (false)
                cursor = v_4
                break@loop1
            }
        } while (false)
        cursor = v_3
        return true
    }
    private fun r_mark_regions(): Boolean {
        I_p1 = limit
        I_p2 = limit
        var v_1: Int = cursor
        lab0@ do {
            lab1@ do {
                var v_2: Int = cursor
                lab2@ do {
                    if (find_among(a_0) == 0) {
                        break@lab2
                    }
                    break@lab1
                } while (false)
                cursor = v_2
                if (!go_out_grouping(g_v, 97, 121)) {
                    break@lab0
                }
                cursor++
                if (!go_in_grouping(g_v, 97, 121)) {
                    break@lab0
                }
                cursor++
            } while (false)
            I_p1 = cursor
            if (!go_out_grouping(g_v, 97, 121)) {
                break@lab0
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 121)) {
                break@lab0
            }
            cursor++
            I_p2 = cursor
        } while (false)
        cursor = v_1
        return true
    }
    private fun r_shortv(): Boolean {
        lab0@ do {
            var v_1: Int = limit - cursor
            lab1@ do {
                if (!(out_grouping_b(g_v_WXY, 89, 121))) {
                    break@lab1
                }
                if (!(in_grouping_b(g_v, 97, 121))) {
                    break@lab1
                }
                if (!(out_grouping_b(g_v, 97, 121))) {
                    break@lab1
                }
                break@lab0
            } while (false)
            cursor = limit - v_1
            lab2@ do {
                if (!(out_grouping_b(g_v, 97, 121))) {
                    break@lab2
                }
                if (!(in_grouping_b(g_v, 97, 121))) {
                    break@lab2
                }
                if (cursor > limit_backward) {
                    break@lab2
                }
                break@lab0
            } while (false)
            cursor = limit - v_1
            if (!(eq_s_b("past"))) {
                return false
            }
        } while (false)
        return true
    }
    private fun r_R1(): Boolean {
        return I_p1 <= cursor
    }
    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }
    private fun r_Step_1a(): Boolean {
        var among_var: Int
        var v_1: Int = limit - cursor
        lab0@ do {
            ket = cursor
            if (find_among_b(a_1) == 0) {
                cursor = limit - v_1
                break@lab0
            }
            bra = cursor
            slice_del()
        } while (false)
        ket = cursor
        among_var = find_among_b(a_2)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw2@ do {
            when (among_var) {
                1 -> {
                slice_from("ss")
                break@sw2
                }
                2 -> {
                lab1@ do {
                    var v_2: Int = limit - cursor
                    lab2@ do {
                        do {
                            var c: Int = cursor - 2
                            if (c < limit_backward) {
                                break@lab2
                            }
                            cursor = c
                        } while (false)
                        slice_from("i")
                        break@lab1
                    } while (false)
                    cursor = limit - v_2
                    slice_from("ie")
                } while (false)
                break@sw2
                }
                3 -> {
                if (cursor <= limit_backward) {
                    return false
                }
                cursor--
                if (!go_out_grouping_b(g_v, 97, 121)) {
                    return false
                }
                cursor--
                slice_del()
                break@sw2
                }
            }
        } while (false)
        return true
    }
    private fun r_Step_1b(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_5)
        bra = cursor
        lab0@ do {
            var v_1: Int = limit - cursor
            lab1@ do {
                sw3@ do {
                    when (among_var) {
                        1 -> {
                        var v_2: Int = limit - cursor
                        lab2@ do {
                            if (!r_R1()) {
                                break@lab2
                            }
                            lab3@ do {
                                var v_3: Int = limit - cursor
                                lab4@ do {
                                    if (find_among_b(a_3) == 0) {
                                        break@lab4
                                    }
                                    if (cursor > limit_backward) {
                                        break@lab4
                                    }
                                    break@lab3
                                } while (false)
                                cursor = limit - v_3
                                slice_from("ee")
                            } while (false)
                        } while (false)
                        cursor = limit - v_2
                        break@sw3
                        }
                        2 -> {
                        break@lab1
                        }
                        3 -> {
                        among_var = find_among_b(a_4)
                        if (among_var == 0) {
                            break@lab1
                        }
                        sw4@ do {
                            when (among_var) {
                                1 -> {
                                var v_4: Int = limit - cursor
                                if (!(out_grouping_b(g_v, 97, 121))) {
                                    break@lab1
                                }
                                if (cursor > limit_backward) {
                                    break@lab1
                                }
                                cursor = limit - v_4
                                bra = cursor
                                slice_from("ie")
                                break@sw4
                                }
                                2 -> {
                                if (cursor > limit_backward) {
                                    break@lab1
                                }
                                break@sw4
                                }
                            }
                        } while (false)
                        break@sw3
                        }
                    }
                } while (false)
                break@lab0
            } while (false)
            cursor = limit - v_1
            var v_5: Int = limit - cursor
            if (!go_out_grouping_b(g_v, 97, 121)) {
                return false
            }
            cursor--
            cursor = limit - v_5
            slice_del()
            ket = cursor
            bra = cursor
            var v_6: Int = limit - cursor
            among_var = find_among_b(a_6)
            sw5@ do {
                when (among_var) {
                    1 -> {
                    slice_from("e")
                    return false
                    }
                    2 -> {
                    do {
                        var v_7: Int = limit - cursor
                        lab5@ do {
                            if (!(in_grouping_b(g_aeo, 97, 111))) {
                                break@lab5
                            }
                            if (cursor > limit_backward) {
                                break@lab5
                            }
                            return false
                        } while (false)
                        cursor = limit - v_7
                    } while (false)
                    break@sw5
                    }
                    3 -> {
                    if (cursor != I_p1) {
                        return false
                    }
                    var v_8: Int = limit - cursor
                    if (!r_shortv()) {
                        return false
                    }
                    cursor = limit - v_8
                    slice_from("e")
                    return false
                    }
                }
            } while (false)
            cursor = limit - v_6
            ket = cursor
            if (cursor <= limit_backward) {
                return false
            }
            cursor--
            bra = cursor
            slice_del()
        } while (false)
        return true
    }
    private fun r_Step_1c(): Boolean {
        ket = cursor
        lab0@ do {
            lab1@ do {
                if (!(eq_s_b("y"))) {
                    break@lab1
                }
                break@lab0
            } while (false)
            if (!(eq_s_b("Y"))) {
                return false
            }
        } while (false)
        bra = cursor
        if (!(out_grouping_b(g_v, 97, 121))) {
            return false
        }
        if (cursor <= limit_backward) {
            return false
        }
        slice_from("i")
        return true
    }
    private fun r_Step_2(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_7)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        if (!r_R1()) {
            return false
        }
        sw6@ do {
            when (among_var) {
                1 -> {
                slice_from("tion")
                break@sw6
                }
                2 -> {
                slice_from("ence")
                break@sw6
                }
                3 -> {
                slice_from("ance")
                break@sw6
                }
                4 -> {
                slice_from("able")
                break@sw6
                }
                5 -> {
                slice_from("ent")
                break@sw6
                }
                6 -> {
                slice_from("ize")
                break@sw6
                }
                7 -> {
                slice_from("ate")
                break@sw6
                }
                8 -> {
                slice_from("al")
                break@sw6
                }
                9 -> {
                slice_from("ful")
                break@sw6
                }
                10 -> {
                slice_from("ous")
                break@sw6
                }
                11 -> {
                slice_from("ive")
                break@sw6
                }
                12 -> {
                slice_from("ble")
                break@sw6
                }
                13 -> {
                slice_from("og")
                break@sw6
                }
                14 -> {
                if (!(eq_s_b("l"))) {
                    return false
                }
                slice_from("og")
                break@sw6
                }
                15 -> {
                slice_from("less")
                break@sw6
                }
                16 -> {
                if (!(in_grouping_b(g_valid_LI, 99, 116))) {
                    return false
                }
                slice_del()
                break@sw6
                }
            }
        } while (false)
        return true
    }
    private fun r_Step_3(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_8)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        if (!r_R1()) {
            return false
        }
        sw7@ do {
            when (among_var) {
                1 -> {
                slice_from("tion")
                break@sw7
                }
                2 -> {
                slice_from("ate")
                break@sw7
                }
                3 -> {
                slice_from("al")
                break@sw7
                }
                4 -> {
                slice_from("ic")
                break@sw7
                }
                5 -> {
                slice_del()
                break@sw7
                }
                6 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                break@sw7
                }
            }
        } while (false)
        return true
    }
    private fun r_Step_4(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_9)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        if (!r_R2()) {
            return false
        }
        sw8@ do {
            when (among_var) {
                1 -> {
                slice_del()
                break@sw8
                }
                2 -> {
                lab0@ do {
                    lab1@ do {
                        if (!(eq_s_b("s"))) {
                            break@lab1
                        }
                        break@lab0
                    } while (false)
                    if (!(eq_s_b("t"))) {
                        return false
                    }
                } while (false)
                slice_del()
                break@sw8
                }
            }
        } while (false)
        return true
    }
    private fun r_Step_5(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_10)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw9@ do {
            when (among_var) {
                1 -> {
                lab0@ do {
                    lab1@ do {
                        if (!r_R2()) {
                            break@lab1
                        }
                        break@lab0
                    } while (false)
                    if (!r_R1()) {
                        return false
                    }
                    do {
                        var v_1: Int = limit - cursor
                        lab2@ do {
                            if (!r_shortv()) {
                                break@lab2
                            }
                            return false
                        } while (false)
                        cursor = limit - v_1
                    } while (false)
                } while (false)
                slice_del()
                break@sw9
                }
                2 -> {
                if (!r_R2()) {
                    return false
                }
                if (!(eq_s_b("l"))) {
                    return false
                }
                slice_del()
                break@sw9
                }
            }
        } while (false)
        return true
    }
    private fun r_exception1(): Boolean {
        var among_var: Int
        bra = cursor
        among_var = find_among(a_11)
        if (among_var == 0) {
            return false
        }
        ket = cursor
        if (cursor < limit) {
            return false
        }
        sw10@ do {
            when (among_var) {
                1 -> {
                slice_from("ski")
                break@sw10
                }
                2 -> {
                slice_from("sky")
                break@sw10
                }
                3 -> {
                slice_from("idl")
                break@sw10
                }
                4 -> {
                slice_from("gentl")
                break@sw10
                }
                5 -> {
                slice_from("ugli")
                break@sw10
                }
                6 -> {
                slice_from("earli")
                break@sw10
                }
                7 -> {
                slice_from("onli")
                break@sw10
                }
                8 -> {
                slice_from("singl")
                break@sw10
                }
            }
        } while (false)
        return true
    }
    private fun r_postlude(): Boolean {
        if (!B_Y_found) {
            return false
        }
        loop11@ while (true) {
            var v_1: Int = cursor
            lab0@ do {
                golab1@ while (true) {
                    var v_2: Int = cursor
                    lab2@ do {
                        bra = cursor
                        if (!(eq_s("Y"))) {
                            break@lab2
                        }
                        ket = cursor
                        cursor = v_2
                        break@golab1
                    } while (false)
                    cursor = v_2
                    if (cursor >= limit) {
                        break@lab0
                    }
                    cursor++
                }
                slice_from("y")
                continue@loop11
            } while (false)
            cursor = v_1
            break@loop11
        }
        return true
    }
    override fun stem(): Boolean {
        lab0@ do {
            var v_1: Int = cursor
            lab1@ do {
                if (!r_exception1()) {
                    break@lab1
                }
                break@lab0
            } while (false)
            cursor = v_1
            lab2@ do {
                lab3@ do {
                    do {
                        var c: Int = cursor + 3
                        if (c > limit) {
                            break@lab3
                        }
                        cursor = c
                    } while (false)
                    break@lab2
                } while (false)
                break@lab0
            } while (false)
            cursor = v_1
            r_prelude()
            r_mark_regions()
            limit_backward = cursor
            cursor = limit
            var v_2: Int = limit - cursor
            r_Step_1a()
            cursor = limit - v_2
            var v_3: Int = limit - cursor
            r_Step_1b()
            cursor = limit - v_3
            var v_4: Int = limit - cursor
            r_Step_1c()
            cursor = limit - v_4
            var v_5: Int = limit - cursor
            r_Step_2()
            cursor = limit - v_5
            var v_6: Int = limit - cursor
            r_Step_3()
            cursor = limit - v_6
            var v_7: Int = limit - cursor
            r_Step_4()
            cursor = limit - v_7
            var v_8: Int = limit - cursor
            r_Step_5()
            cursor = limit - v_8
            cursor = limit_backward
            var v_9: Int = cursor
            r_postlude()
            cursor = v_9
        } while (false)
        return true
    }
    override fun equals(other: Any?): Boolean = other is englishStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.englishStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("arsen", -1, -1),
                Among("commun", -1, -1),
                Among("emerg", -1, -1),
                Among("gener", -1, -1),
                Among("inter", -1, -1),
                Among("later", -1, -1),
                Among("organ", -1, -1),
                Among("past", -1, -1),
                Among("univers", -1, -1),
            )
        private val a_1 =
            arrayOf(
                Among("'", -1, 1),
                Among("'s'", 0, 1),
                Among("'s", -1, 1),
            )
        private val a_2 =
            arrayOf(
                Among("ied", -1, 2),
                Among("s", -1, 3),
                Among("ies", 1, 2),
                Among("sses", 1, 1),
                Among("ss", 1, -1),
                Among("us", 1, -1),
            )
        private val a_3 =
            arrayOf(
                Among("succ", -1, 1),
                Among("proc", -1, 1),
                Among("exc", -1, 1),
            )
        private val a_4 =
            arrayOf(
                Among("even", -1, 2),
                Among("cann", -1, 2),
                Among("inn", -1, 2),
                Among("earr", -1, 2),
                Among("herr", -1, 2),
                Among("out", -1, 2),
                Among("y", -1, 1),
            )
        private val a_5 =
            arrayOf(
                Among("", -1, -1),
                Among("ed", 0, 2),
                Among("eed", 1, 1),
                Among("ing", 0, 3),
                Among("edly", 0, 2),
                Among("eedly", 4, 1),
                Among("ingly", 0, 2),
            )
        private val a_6 =
            arrayOf(
                Among("", -1, 3),
                Among("bb", 0, 2),
                Among("dd", 0, 2),
                Among("ff", 0, 2),
                Among("gg", 0, 2),
                Among("bl", 0, 1),
                Among("mm", 0, 2),
                Among("nn", 0, 2),
                Among("pp", 0, 2),
                Among("rr", 0, 2),
                Among("at", 0, 1),
                Among("tt", 0, 2),
                Among("iz", 0, 1),
            )
        private val a_7 =
            arrayOf(
                Among("anci", -1, 3),
                Among("enci", -1, 2),
                Among("ogi", -1, 14),
                Among("li", -1, 16),
                Among("bli", 3, 12),
                Among("abli", 4, 4),
                Among("alli", 3, 8),
                Among("fulli", 3, 9),
                Among("lessli", 3, 15),
                Among("ousli", 3, 10),
                Among("entli", 3, 5),
                Among("aliti", -1, 8),
                Among("biliti", -1, 12),
                Among("iviti", -1, 11),
                Among("tional", -1, 1),
                Among("ational", 14, 7),
                Among("alism", -1, 8),
                Among("ation", -1, 7),
                Among("ization", 17, 6),
                Among("izer", -1, 6),
                Among("ator", -1, 7),
                Among("iveness", -1, 11),
                Among("fulness", -1, 9),
                Among("ousness", -1, 10),
                Among("ogist", -1, 13),
            )
        private val a_8 =
            arrayOf(
                Among("icate", -1, 4),
                Among("ative", -1, 6),
                Among("alize", -1, 3),
                Among("iciti", -1, 4),
                Among("ical", -1, 4),
                Among("tional", -1, 1),
                Among("ational", 5, 2),
                Among("ful", -1, 5),
                Among("ness", -1, 5),
            )
        private val a_9 =
            arrayOf(
                Among("ic", -1, 1),
                Among("ance", -1, 1),
                Among("ence", -1, 1),
                Among("able", -1, 1),
                Among("ible", -1, 1),
                Among("ate", -1, 1),
                Among("ive", -1, 1),
                Among("ize", -1, 1),
                Among("iti", -1, 1),
                Among("al", -1, 1),
                Among("ism", -1, 1),
                Among("ion", -1, 2),
                Among("er", -1, 1),
                Among("ous", -1, 1),
                Among("ant", -1, 1),
                Among("ent", -1, 1),
                Among("ment", 15, 1),
                Among("ement", 16, 1),
            )
        private val a_10 =
            arrayOf(
                Among("e", -1, 1),
                Among("l", -1, 2),
            )
        private val a_11 =
            arrayOf(
                Among("andes", -1, -1),
                Among("atlas", -1, -1),
                Among("bias", -1, -1),
                Among("cosmos", -1, -1),
                Among("early", -1, 6),
                Among("gently", -1, 4),
                Among("howe", -1, -1),
                Among("idly", -1, 3),
                Among("news", -1, -1),
                Among("only", -1, 7),
                Among("singly", -1, 8),
                Among("skies", -1, 2),
                Among("skis", -1, 1),
                Among("sky", -1, -1),
                Among("ugly", -1, 5),
            )
        private val g_aeo = charArrayOf('\u0011', '\u0040')
        private val g_v = charArrayOf('\u0011', '\u0041', '\u0010', '\u0001')
        private val g_v_WXY = charArrayOf('\u0001', '\u0011', '\u0041', '\u00d0', '\u0001')
        private val g_valid_LI = charArrayOf('\u0037', '\u008d', '\u0002')
    }
}
