// Converted from upstream's germanStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from german.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class germanStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var I_p2: Int = 0
    private var I_p1: Int = 0
    private fun r_prelude(): Boolean {
        var among_var: Int
        var v_1: Int = cursor
        loop1@ while (true) {
            var v_2: Int = cursor
            lab0@ do {
                golab1@ while (true) {
                    var v_3: Int = cursor
                    lab2@ do {
                        if (!(in_grouping(g_v, 97, 252))) {
                            break@lab2
                        }
                        bra = cursor
                        lab3@ do {
                            var v_4: Int = cursor
                            lab4@ do {
                                if (!(eq_s("u"))) {
                                    break@lab4
                                }
                                ket = cursor
                                if (!(in_grouping(g_v, 97, 252))) {
                                    break@lab4
                                }
                                slice_from("U")
                                break@lab3
                            } while (false)
                            cursor = v_4
                            if (!(eq_s("y"))) {
                                break@lab2
                            }
                            ket = cursor
                            if (!(in_grouping(g_v, 97, 252))) {
                                break@lab2
                            }
                            slice_from("Y")
                        } while (false)
                        cursor = v_3
                        break@golab1
                    } while (false)
                    cursor = v_3
                    if (cursor >= limit) {
                        break@lab0
                    }
                    cursor++
                }
                continue@loop1
            } while (false)
            cursor = v_2
            break@loop1
        }
        cursor = v_1
        loop2@ while (true) {
            var v_5: Int = cursor
            lab5@ do {
                bra = cursor
                among_var = find_among(a_0)
                ket = cursor
                sw3@ do {
                    when (among_var) {
                        1 -> {
                        slice_from("ss")
                        break@sw3
                        }
                        2 -> {
                        slice_from("\u00E4")
                        break@sw3
                        }
                        3 -> {
                        slice_from("\u00F6")
                        break@sw3
                        }
                        4 -> {
                        slice_from("\u00FC")
                        break@sw3
                        }
                        5 -> {
                        if (cursor >= limit) {
                            break@lab5
                        }
                        cursor++
                        break@sw3
                        }
                    }
                } while (false)
                continue@loop2
            } while (false)
            cursor = v_5
            break@loop2
        }
        return true
    }
    private fun r_mark_regions(): Boolean {
        var I_x: Int
        I_p1 = limit
        I_p2 = limit
        var v_1: Int = cursor
        do {
            var c: Int = cursor + 3
            if (c > limit) {
                return false
            }
            cursor = c
        } while (false)
        I_x = cursor
        cursor = v_1
        if (!go_out_grouping(g_v, 97, 252)) {
            return false
        }
        cursor++
        if (!go_in_grouping(g_v, 97, 252)) {
            return false
        }
        cursor++
        I_p1 = cursor
        lab0@ do {
            if (I_p1 >= I_x) {
                break@lab0
            }
            I_p1 = I_x
        } while (false)
        if (!go_out_grouping(g_v, 97, 252)) {
            return false
        }
        cursor++
        if (!go_in_grouping(g_v, 97, 252)) {
            return false
        }
        cursor++
        I_p2 = cursor
        return true
    }
    private fun r_postlude(): Boolean {
        var among_var: Int
        loop4@ while (true) {
            var v_1: Int = cursor
            lab0@ do {
                bra = cursor
                among_var = find_among(a_1)
                ket = cursor
                sw5@ do {
                    when (among_var) {
                        1 -> {
                        slice_from("y")
                        break@sw5
                        }
                        2 -> {
                        slice_from("u")
                        break@sw5
                        }
                        3 -> {
                        slice_from("a")
                        break@sw5
                        }
                        4 -> {
                        slice_from("o")
                        break@sw5
                        }
                        5 -> {
                        if (cursor >= limit) {
                            break@lab0
                        }
                        cursor++
                        break@sw5
                        }
                    }
                } while (false)
                continue@loop4
            } while (false)
            cursor = v_1
            break@loop4
        }
        return true
    }
    private fun r_R1(): Boolean {
        return I_p1 <= cursor
    }
    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }
    private fun r_standard_suffix(): Boolean {
        var among_var: Int
        var v_1: Int = limit - cursor
        lab0@ do {
            ket = cursor
            among_var = find_among_b(a_2)
            if (among_var == 0) {
                break@lab0
            }
            bra = cursor
            if (!r_R1()) {
                break@lab0
            }
            sw6@ do {
                when (among_var) {
                    1 -> {
                    lab1@ do {
                        if (!(eq_s_b("syst"))) {
                            break@lab1
                        }
                        break@lab0
                    } while (false)
                    slice_del()
                    break@sw6
                    }
                    2 -> {
                    slice_del()
                    break@sw6
                    }
                    3 -> {
                    slice_del()
                    var v_2: Int = limit - cursor
                    lab2@ do {
                        ket = cursor
                        if (!(eq_s_b("s"))) {
                            cursor = limit - v_2
                            break@lab2
                        }
                        bra = cursor
                        if (!(eq_s_b("nis"))) {
                            cursor = limit - v_2
                            break@lab2
                        }
                        slice_del()
                    } while (false)
                    break@sw6
                    }
                    4 -> {
                    if (!(in_grouping_b(g_s_ending, 98, 116))) {
                        break@lab0
                    }
                    slice_del()
                    break@sw6
                    }
                    5 -> {
                    slice_from("l")
                    break@sw6
                    }
                }
            } while (false)
        } while (false)
        cursor = limit - v_1
        var v_3: Int = limit - cursor
        lab3@ do {
            ket = cursor
            among_var = find_among_b(a_4)
            if (among_var == 0) {
                break@lab3
            }
            bra = cursor
            if (!r_R1()) {
                break@lab3
            }
            sw7@ do {
                when (among_var) {
                    1 -> {
                    slice_del()
                    break@sw7
                    }
                    2 -> {
                    if (!(in_grouping_b(g_st_ending, 98, 116))) {
                        break@lab3
                    }
                    do {
                        var c: Int = cursor - 3
                        if (c < limit_backward) {
                            break@lab3
                        }
                        cursor = c
                    } while (false)
                    slice_del()
                    break@sw7
                    }
                    3 -> {
                    var v_4: Int = limit - cursor
                    if (!(in_grouping_b(g_et_ending, 85, 228))) {
                        break@lab3
                    }
                    cursor = limit - v_4
                    do {
                        var v_5: Int = limit - cursor
                        lab4@ do {
                            if (find_among_b(a_3) == 0) {
                                break@lab4
                            }
                            break@lab3
                        } while (false)
                        cursor = limit - v_5
                    } while (false)
                    slice_del()
                    break@sw7
                    }
                }
            } while (false)
        } while (false)
        cursor = limit - v_3
        var v_6: Int = limit - cursor
        lab5@ do {
            ket = cursor
            among_var = find_among_b(a_6)
            if (among_var == 0) {
                break@lab5
            }
            bra = cursor
            if (!r_R2()) {
                break@lab5
            }
            sw8@ do {
                when (among_var) {
                    1 -> {
                    slice_del()
                    var v_7: Int = limit - cursor
                    lab6@ do {
                        ket = cursor
                        if (!(eq_s_b("ig"))) {
                            cursor = limit - v_7
                            break@lab6
                        }
                        bra = cursor
                        lab7@ do {
                            if (!(eq_s_b("e"))) {
                                break@lab7
                            }
                            cursor = limit - v_7
                            break@lab6
                        } while (false)
                        if (!r_R2()) {
                            cursor = limit - v_7
                            break@lab6
                        }
                        slice_del()
                    } while (false)
                    break@sw8
                    }
                    2 -> {
                    lab8@ do {
                        if (!(eq_s_b("e"))) {
                            break@lab8
                        }
                        break@lab5
                    } while (false)
                    slice_del()
                    break@sw8
                    }
                    3 -> {
                    slice_del()
                    var v_8: Int = limit - cursor
                    lab9@ do {
                        ket = cursor
                        lab10@ do {
                            lab11@ do {
                                if (!(eq_s_b("er"))) {
                                    break@lab11
                                }
                                break@lab10
                            } while (false)
                            if (!(eq_s_b("en"))) {
                                cursor = limit - v_8
                                break@lab9
                            }
                        } while (false)
                        bra = cursor
                        if (!r_R1()) {
                            cursor = limit - v_8
                            break@lab9
                        }
                        slice_del()
                    } while (false)
                    break@sw8
                    }
                    4 -> {
                    slice_del()
                    var v_9: Int = limit - cursor
                    lab12@ do {
                        ket = cursor
                        if (find_among_b(a_5) == 0) {
                            cursor = limit - v_9
                            break@lab12
                        }
                        bra = cursor
                        if (!r_R2()) {
                            cursor = limit - v_9
                            break@lab12
                        }
                        slice_del()
                    } while (false)
                    break@sw8
                    }
                }
            } while (false)
        } while (false)
        cursor = limit - v_6
        var v_10: Int = limit - cursor
        lab13@ do {
            ket = cursor
            if (find_among_b(a_7) == 0) {
                break@lab13
            }
            bra = cursor
            if (cursor <= limit_backward) {
                break@lab13
            }
            cursor--
            if (cursor <= limit_backward) {
                break@lab13
            }
            slice_del()
        } while (false)
        cursor = limit - v_10
        return true
    }
    override fun stem(): Boolean {
        var v_1: Int = cursor
        r_prelude()
        cursor = v_1
        var v_2: Int = cursor
        r_mark_regions()
        cursor = v_2
        limit_backward = cursor
        cursor = limit
        r_standard_suffix()
        cursor = limit_backward
        var v_3: Int = cursor
        r_postlude()
        cursor = v_3
        return true
    }
    override fun equals(other: Any?): Boolean = other is germanStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.germanStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("", -1, 5),
                Among("ae", 0, 2),
                Among("oe", 0, 3),
                Among("qu", 0, -1),
                Among("ue", 0, 4),
                Among("\u00DF", 0, 1),
            )
        private val a_1 =
            arrayOf(
                Among("", -1, 5),
                Among("U", 0, 2),
                Among("Y", 0, 1),
                Among("\u00E4", 0, 3),
                Among("\u00F6", 0, 4),
                Among("\u00FC", 0, 2),
            )
        private val a_2 =
            arrayOf(
                Among("e", -1, 3),
                Among("em", -1, 1),
                Among("en", -1, 3),
                Among("erinnen", 2, 2),
                Among("erin", -1, 2),
                Among("ln", -1, 5),
                Among("ern", -1, 2),
                Among("er", -1, 2),
                Among("s", -1, 4),
                Among("es", 8, 3),
                Among("lns", 8, 5),
            )
        private val a_3 =
            arrayOf(
                Among("tick", -1, -1),
                Among("plan", -1, -1),
                Among("geordn", -1, -1),
                Among("intern", -1, -1),
                Among("tr", -1, -1),
            )
        private val a_4 =
            arrayOf(
                Among("en", -1, 1),
                Among("er", -1, 1),
                Among("et", -1, 3),
                Among("st", -1, 2),
                Among("est", 3, 1),
            )
        private val a_5 =
            arrayOf(
                Among("ig", -1, 1),
                Among("lich", -1, 1),
            )
        private val a_6 =
            arrayOf(
                Among("end", -1, 1),
                Among("ig", -1, 2),
                Among("ung", -1, 1),
                Among("lich", -1, 3),
                Among("isch", -1, 2),
                Among("ik", -1, 2),
                Among("heit", -1, 3),
                Among("keit", -1, 4),
            )
        private val a_7 =
            arrayOf(
                Among("'", -1, 1),
                Among("'sch", -1, 1),
                Among("'s", -1, 1),
            )
        private val g_v = charArrayOf('\u0011', '\u0041', '\u0010', '\u0001', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0008', '\u0000', '\u0020', '\u0008')
        private val g_et_ending = charArrayOf('\u0001', '\u0080', '\u00c6', '\u00e3', '\u0020', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0080')
        private val g_s_ending = charArrayOf('\u0075', '\u001e', '\u0005')
        private val g_st_ending = charArrayOf('\u0075', '\u001e', '\u0004')
    }
}
