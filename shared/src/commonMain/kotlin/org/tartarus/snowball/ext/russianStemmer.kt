// Converted from upstream's russianStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from russian.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class russianStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var I_p2: Int = 0
    private var I_pV: Int = 0
    private fun r_mark_regions(): Boolean {
        I_pV = limit
        I_p2 = limit
        var v_1: Int = cursor
        lab0@ do {
            if (!go_out_grouping(g_v, 1072, 1103)) {
                break@lab0
            }
            cursor++
            I_pV = cursor
            if (!go_in_grouping(g_v, 1072, 1103)) {
                break@lab0
            }
            cursor++
            if (!go_out_grouping(g_v, 1072, 1103)) {
                break@lab0
            }
            cursor++
            if (!go_in_grouping(g_v, 1072, 1103)) {
                break@lab0
            }
            cursor++
            I_p2 = cursor
        } while (false)
        cursor = v_1
        return true
    }
    private fun r_perfective_gerund(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_0)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw1@ do {
            when (among_var) {
                1 -> {
                lab0@ do {
                    lab1@ do {
                        if (!(eq_s_b("\u0430"))) {
                            break@lab1
                        }
                        break@lab0
                    } while (false)
                    if (!(eq_s_b("\u044F"))) {
                        return false
                    }
                } while (false)
                slice_del()
                break@sw1
                }
                2 -> {
                slice_del()
                break@sw1
                }
            }
        } while (false)
        return true
    }
    private fun r_adjective(): Boolean {
        ket = cursor
        if (find_among_b(a_1) == 0) {
            return false
        }
        bra = cursor
        slice_del()
        return true
    }
    private fun r_adjectival(): Boolean {
        var among_var: Int
        if (!r_adjective()) {
            return false
        }
        var v_1: Int = limit - cursor
        lab0@ do {
            ket = cursor
            among_var = find_among_b(a_2)
            if (among_var == 0) {
                cursor = limit - v_1
                break@lab0
            }
            bra = cursor
            sw2@ do {
                when (among_var) {
                    1 -> {
                    lab1@ do {
                        lab2@ do {
                            if (!(eq_s_b("\u0430"))) {
                                break@lab2
                            }
                            break@lab1
                        } while (false)
                        if (!(eq_s_b("\u044F"))) {
                            cursor = limit - v_1
                            break@lab0
                        }
                    } while (false)
                    slice_del()
                    break@sw2
                    }
                    2 -> {
                    slice_del()
                    break@sw2
                    }
                }
            } while (false)
        } while (false)
        return true
    }
    private fun r_reflexive(): Boolean {
        ket = cursor
        if (find_among_b(a_3) == 0) {
            return false
        }
        bra = cursor
        slice_del()
        return true
    }
    private fun r_verb(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_4)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw3@ do {
            when (among_var) {
                1 -> {
                lab0@ do {
                    lab1@ do {
                        if (!(eq_s_b("\u0430"))) {
                            break@lab1
                        }
                        break@lab0
                    } while (false)
                    if (!(eq_s_b("\u044F"))) {
                        return false
                    }
                } while (false)
                slice_del()
                break@sw3
                }
                2 -> {
                slice_del()
                break@sw3
                }
            }
        } while (false)
        return true
    }
    private fun r_noun(): Boolean {
        ket = cursor
        if (find_among_b(a_5) == 0) {
            return false
        }
        bra = cursor
        slice_del()
        return true
    }
    private fun r_derivational(): Boolean {
        ket = cursor
        if (find_among_b(a_6) == 0) {
            return false
        }
        bra = cursor
        if (I_p2 > cursor) {
            return false
        }
        slice_del()
        return true
    }
    private fun r_tidy_up(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_7)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw4@ do {
            when (among_var) {
                1 -> {
                slice_del()
                ket = cursor
                if (!(eq_s_b("\u043D"))) {
                    return false
                }
                bra = cursor
                if (!(eq_s_b("\u043D"))) {
                    return false
                }
                slice_del()
                break@sw4
                }
                2 -> {
                if (!(eq_s_b("\u043D"))) {
                    return false
                }
                slice_del()
                break@sw4
                }
                3 -> {
                slice_del()
                break@sw4
                }
            }
        } while (false)
        return true
    }
    override fun stem(): Boolean {
        var v_1: Int = cursor
        lab0@ do {
            loop5@ while (true) {
                var v_2: Int = cursor
                lab1@ do {
                    golab2@ while (true) {
                        var v_3: Int = cursor
                        lab3@ do {
                            bra = cursor
                            if (!(eq_s("\u0451"))) {
                                break@lab3
                            }
                            ket = cursor
                            cursor = v_3
                            break@golab2
                        } while (false)
                        cursor = v_3
                        if (cursor >= limit) {
                            break@lab1
                        }
                        cursor++
                    }
                    slice_from("\u0435")
                    continue@loop5
                } while (false)
                cursor = v_2
                break@loop5
            }
        } while (false)
        cursor = v_1
        r_mark_regions()
        limit_backward = cursor
        cursor = limit
        if (cursor < I_pV) {
            return false
        }
        var v_4: Int = limit_backward
        limit_backward = I_pV
        var v_5: Int = limit - cursor
        lab4@ do {
            lab5@ do {
                var v_6: Int = limit - cursor
                lab6@ do {
                    if (!r_perfective_gerund()) {
                        break@lab6
                    }
                    break@lab5
                } while (false)
                cursor = limit - v_6
                var v_7: Int = limit - cursor
                lab7@ do {
                    if (!r_reflexive()) {
                        cursor = limit - v_7
                        break@lab7
                    }
                } while (false)
                lab8@ do {
                    var v_8: Int = limit - cursor
                    lab9@ do {
                        if (!r_adjectival()) {
                            break@lab9
                        }
                        break@lab8
                    } while (false)
                    cursor = limit - v_8
                    lab10@ do {
                        if (!r_verb()) {
                            break@lab10
                        }
                        break@lab8
                    } while (false)
                    cursor = limit - v_8
                    if (!r_noun()) {
                        break@lab4
                    }
                } while (false)
            } while (false)
        } while (false)
        cursor = limit - v_5
        var v_9: Int = limit - cursor
        lab11@ do {
            ket = cursor
            if (!(eq_s_b("\u0438"))) {
                cursor = limit - v_9
                break@lab11
            }
            bra = cursor
            slice_del()
        } while (false)
        var v_10: Int = limit - cursor
        r_derivational()
        cursor = limit - v_10
        var v_11: Int = limit - cursor
        r_tidy_up()
        cursor = limit - v_11
        limit_backward = v_4
        cursor = limit_backward
        return true
    }
    override fun equals(other: Any?): Boolean = other is russianStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.russianStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("\u0432", -1, 1),
                Among("\u0438\u0432", 0, 2),
                Among("\u044B\u0432", 0, 2),
                Among("\u0432\u0448\u0438", -1, 1),
                Among("\u0438\u0432\u0448\u0438", 3, 2),
                Among("\u044B\u0432\u0448\u0438", 3, 2),
                Among("\u0432\u0448\u0438\u0441\u044C", -1, 1),
                Among("\u0438\u0432\u0448\u0438\u0441\u044C", 6, 2),
                Among("\u044B\u0432\u0448\u0438\u0441\u044C", 6, 2),
            )
        private val a_1 =
            arrayOf(
                Among("\u0435\u0435", -1, 1),
                Among("\u0438\u0435", -1, 1),
                Among("\u043E\u0435", -1, 1),
                Among("\u044B\u0435", -1, 1),
                Among("\u0438\u043C\u0438", -1, 1),
                Among("\u044B\u043C\u0438", -1, 1),
                Among("\u0435\u0439", -1, 1),
                Among("\u0438\u0439", -1, 1),
                Among("\u043E\u0439", -1, 1),
                Among("\u044B\u0439", -1, 1),
                Among("\u0435\u043C", -1, 1),
                Among("\u0438\u043C", -1, 1),
                Among("\u043E\u043C", -1, 1),
                Among("\u044B\u043C", -1, 1),
                Among("\u0435\u0433\u043E", -1, 1),
                Among("\u043E\u0433\u043E", -1, 1),
                Among("\u0435\u043C\u0443", -1, 1),
                Among("\u043E\u043C\u0443", -1, 1),
                Among("\u0438\u0445", -1, 1),
                Among("\u044B\u0445", -1, 1),
                Among("\u0435\u044E", -1, 1),
                Among("\u043E\u044E", -1, 1),
                Among("\u0443\u044E", -1, 1),
                Among("\u044E\u044E", -1, 1),
                Among("\u0430\u044F", -1, 1),
                Among("\u044F\u044F", -1, 1),
            )
        private val a_2 =
            arrayOf(
                Among("\u0435\u043C", -1, 1),
                Among("\u043D\u043D", -1, 1),
                Among("\u0432\u0448", -1, 1),
                Among("\u0438\u0432\u0448", 2, 2),
                Among("\u044B\u0432\u0448", 2, 2),
                Among("\u0449", -1, 1),
                Among("\u044E\u0449", 5, 1),
                Among("\u0443\u044E\u0449", 6, 2),
            )
        private val a_3 =
            arrayOf(
                Among("\u0441\u044C", -1, 1),
                Among("\u0441\u044F", -1, 1),
            )
        private val a_4 =
            arrayOf(
                Among("\u043B\u0430", -1, 1),
                Among("\u0438\u043B\u0430", 0, 2),
                Among("\u044B\u043B\u0430", 0, 2),
                Among("\u043D\u0430", -1, 1),
                Among("\u0435\u043D\u0430", 3, 2),
                Among("\u0435\u0442\u0435", -1, 1),
                Among("\u0438\u0442\u0435", -1, 2),
                Among("\u0439\u0442\u0435", -1, 1),
                Among("\u0435\u0439\u0442\u0435", 7, 2),
                Among("\u0443\u0439\u0442\u0435", 7, 2),
                Among("\u043B\u0438", -1, 1),
                Among("\u0438\u043B\u0438", 10, 2),
                Among("\u044B\u043B\u0438", 10, 2),
                Among("\u0439", -1, 1),
                Among("\u0435\u0439", 13, 2),
                Among("\u0443\u0439", 13, 2),
                Among("\u043B", -1, 1),
                Among("\u0438\u043B", 16, 2),
                Among("\u044B\u043B", 16, 2),
                Among("\u0435\u043C", -1, 1),
                Among("\u0438\u043C", -1, 2),
                Among("\u044B\u043C", -1, 2),
                Among("\u043D", -1, 1),
                Among("\u0435\u043D", 22, 2),
                Among("\u043B\u043E", -1, 1),
                Among("\u0438\u043B\u043E", 24, 2),
                Among("\u044B\u043B\u043E", 24, 2),
                Among("\u043D\u043E", -1, 1),
                Among("\u0435\u043D\u043E", 27, 2),
                Among("\u043D\u043D\u043E", 27, 1),
                Among("\u0435\u0442", -1, 1),
                Among("\u0443\u0435\u0442", 30, 2),
                Among("\u0438\u0442", -1, 2),
                Among("\u044B\u0442", -1, 2),
                Among("\u044E\u0442", -1, 1),
                Among("\u0443\u044E\u0442", 34, 2),
                Among("\u044F\u0442", -1, 2),
                Among("\u043D\u044B", -1, 1),
                Among("\u0435\u043D\u044B", 37, 2),
                Among("\u0442\u044C", -1, 1),
                Among("\u0438\u0442\u044C", 39, 2),
                Among("\u044B\u0442\u044C", 39, 2),
                Among("\u0435\u0448\u044C", -1, 1),
                Among("\u0438\u0448\u044C", -1, 2),
                Among("\u044E", -1, 2),
                Among("\u0443\u044E", 44, 2),
            )
        private val a_5 =
            arrayOf(
                Among("\u0430", -1, 1),
                Among("\u0435\u0432", -1, 1),
                Among("\u043E\u0432", -1, 1),
                Among("\u0435", -1, 1),
                Among("\u0438\u0435", 3, 1),
                Among("\u044C\u0435", 3, 1),
                Among("\u0438", -1, 1),
                Among("\u0435\u0438", 6, 1),
                Among("\u0438\u0438", 6, 1),
                Among("\u0430\u043C\u0438", 6, 1),
                Among("\u044F\u043C\u0438", 6, 1),
                Among("\u0438\u044F\u043C\u0438", 10, 1),
                Among("\u0439", -1, 1),
                Among("\u0435\u0439", 12, 1),
                Among("\u0438\u0435\u0439", 13, 1),
                Among("\u0438\u0439", 12, 1),
                Among("\u043E\u0439", 12, 1),
                Among("\u0430\u043C", -1, 1),
                Among("\u0435\u043C", -1, 1),
                Among("\u0438\u0435\u043C", 18, 1),
                Among("\u043E\u043C", -1, 1),
                Among("\u044F\u043C", -1, 1),
                Among("\u0438\u044F\u043C", 21, 1),
                Among("\u043E", -1, 1),
                Among("\u0443", -1, 1),
                Among("\u0430\u0445", -1, 1),
                Among("\u044F\u0445", -1, 1),
                Among("\u0438\u044F\u0445", 26, 1),
                Among("\u044B", -1, 1),
                Among("\u044C", -1, 1),
                Among("\u044E", -1, 1),
                Among("\u0438\u044E", 30, 1),
                Among("\u044C\u044E", 30, 1),
                Among("\u044F", -1, 1),
                Among("\u0438\u044F", 33, 1),
                Among("\u044C\u044F", 33, 1),
            )
        private val a_6 =
            arrayOf(
                Among("\u043E\u0441\u0442", -1, 1),
                Among("\u043E\u0441\u0442\u044C", -1, 1),
            )
        private val a_7 =
            arrayOf(
                Among("\u0435\u0439\u0448\u0435", -1, 1),
                Among("\u043D", -1, 2),
                Among("\u0435\u0439\u0448", -1, 1),
                Among("\u044C", -1, 3),
            )
        private val g_v = charArrayOf('\u0021', '\u0041', '\u0008', '\u00e8')
    }
}
