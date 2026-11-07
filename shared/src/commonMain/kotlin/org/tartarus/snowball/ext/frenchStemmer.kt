// Converted from upstream's frenchStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from french.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class frenchStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var I_p2: Int = 0
    private var I_p1: Int = 0
    private var I_pV: Int = 0
    private fun r_elisions(): Boolean {
        bra = cursor
        lab0@ do {
            lab1@ do {
                if (!(in_grouping(g_elision_char, 99, 116))) {
                    break@lab1
                }
                break@lab0
            } while (false)
            if (!(eq_s("qu"))) {
                return false
            }
        } while (false)
        if (!(eq_s("'"))) {
            return false
        }
        ket = cursor
        if (cursor >= limit) {
            return false
        }
        slice_del()
        return true
    }
    private fun r_prelude(): Boolean {
        loop1@ while (true) {
            var v_1: Int = cursor
            lab0@ do {
                golab1@ while (true) {
                    var v_2: Int = cursor
                    lab2@ do {
                        lab3@ do {
                            var v_3: Int = cursor
                            lab4@ do {
                                if (!(in_grouping(g_v, 97, 251))) {
                                    break@lab4
                                }
                                bra = cursor
                                lab5@ do {
                                    var v_4: Int = cursor
                                    lab6@ do {
                                        if (!(eq_s("u"))) {
                                            break@lab6
                                        }
                                        ket = cursor
                                        if (!(in_grouping(g_v, 97, 251))) {
                                            break@lab6
                                        }
                                        slice_from("U")
                                        break@lab5
                                    } while (false)
                                    cursor = v_4
                                    lab7@ do {
                                        if (!(eq_s("i"))) {
                                            break@lab7
                                        }
                                        ket = cursor
                                        if (!(in_grouping(g_v, 97, 251))) {
                                            break@lab7
                                        }
                                        slice_from("I")
                                        break@lab5
                                    } while (false)
                                    cursor = v_4
                                    if (!(eq_s("y"))) {
                                        break@lab4
                                    }
                                    ket = cursor
                                    slice_from("Y")
                                } while (false)
                                break@lab3
                            } while (false)
                            cursor = v_3
                            lab8@ do {
                                bra = cursor
                                if (!(eq_s("\u00EB"))) {
                                    break@lab8
                                }
                                ket = cursor
                                slice_from("He")
                                break@lab3
                            } while (false)
                            cursor = v_3
                            lab9@ do {
                                bra = cursor
                                if (!(eq_s("\u00EF"))) {
                                    break@lab9
                                }
                                ket = cursor
                                slice_from("Hi")
                                break@lab3
                            } while (false)
                            cursor = v_3
                            lab10@ do {
                                bra = cursor
                                if (!(eq_s("y"))) {
                                    break@lab10
                                }
                                ket = cursor
                                if (!(in_grouping(g_v, 97, 251))) {
                                    break@lab10
                                }
                                slice_from("Y")
                                break@lab3
                            } while (false)
                            cursor = v_3
                            if (!(eq_s("q"))) {
                                break@lab2
                            }
                            bra = cursor
                            if (!(eq_s("u"))) {
                                break@lab2
                            }
                            ket = cursor
                            slice_from("U")
                        } while (false)
                        cursor = v_2
                        break@golab1
                    } while (false)
                    cursor = v_2
                    if (cursor >= limit) {
                        break@lab0
                    }
                    cursor++
                }
                continue@loop1
            } while (false)
            cursor = v_1
            break@loop1
        }
        return true
    }
    private fun r_mark_regions(): Boolean {
        var among_var: Int
        I_pV = limit
        I_p1 = limit
        I_p2 = limit
        var v_1: Int = cursor
        lab0@ do {
            lab1@ do {
                var v_2: Int = cursor
                lab2@ do {
                    if (!(in_grouping(g_v, 97, 251))) {
                        break@lab2
                    }
                    if (!(in_grouping(g_v, 97, 251))) {
                        break@lab2
                    }
                    if (cursor >= limit) {
                        break@lab2
                    }
                    cursor++
                    break@lab1
                } while (false)
                cursor = v_2
                lab3@ do {
                    among_var = find_among(a_0)
                    if (among_var == 0) {
                        break@lab3
                    }
                    sw2@ do {
                        when (among_var) {
                            1 -> {
                            if (!(in_grouping(g_v, 97, 251))) {
                                break@lab3
                            }
                            break@sw2
                            }
                        }
                    } while (false)
                    break@lab1
                } while (false)
                cursor = v_2
                if (cursor >= limit) {
                    break@lab0
                }
                cursor++
                if (!go_out_grouping(g_v, 97, 251)) {
                    break@lab0
                }
                cursor++
            } while (false)
            I_pV = cursor
        } while (false)
        cursor = v_1
        var v_3: Int = cursor
        lab4@ do {
            if (!go_out_grouping(g_v, 97, 251)) {
                break@lab4
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 251)) {
                break@lab4
            }
            cursor++
            I_p1 = cursor
            if (!go_out_grouping(g_v, 97, 251)) {
                break@lab4
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 251)) {
                break@lab4
            }
            cursor++
            I_p2 = cursor
        } while (false)
        cursor = v_3
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
                        slice_from("i")
                        break@sw4
                        }
                        2 -> {
                        slice_from("u")
                        break@sw4
                        }
                        3 -> {
                        slice_from("y")
                        break@sw4
                        }
                        4 -> {
                        slice_from("\u00EB")
                        break@sw4
                        }
                        5 -> {
                        slice_from("\u00EF")
                        break@sw4
                        }
                        6 -> {
                        slice_del()
                        break@sw4
                        }
                        7 -> {
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
    private fun r_R1(): Boolean {
        return I_p1 <= cursor
    }
    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }
    private fun r_standard_suffix(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_4)
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
                slice_del()
                var v_1: Int = limit - cursor
                lab0@ do {
                    ket = cursor
                    if (!(eq_s_b("ic"))) {
                        cursor = limit - v_1
                        break@lab0
                    }
                    bra = cursor
                    lab1@ do {
                        var v_2: Int = limit - cursor
                        lab2@ do {
                            if (!r_R2()) {
                                break@lab2
                            }
                            slice_del()
                            break@lab1
                        } while (false)
                        cursor = limit - v_2
                        slice_from("iqU")
                    } while (false)
                } while (false)
                break@sw5
                }
                3 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("log")
                break@sw5
                }
                4 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("u")
                break@sw5
                }
                5 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("ent")
                break@sw5
                }
                6 -> {
                if (!r_RV()) {
                    return false
                }
                slice_del()
                var v_3: Int = limit - cursor
                lab3@ do {
                    ket = cursor
                    among_var = find_among_b(a_2)
                    if (among_var == 0) {
                        cursor = limit - v_3
                        break@lab3
                    }
                    bra = cursor
                    sw6@ do {
                        when (among_var) {
                            1 -> {
                            if (!r_R2()) {
                                cursor = limit - v_3
                                break@lab3
                            }
                            slice_del()
                            ket = cursor
                            if (!(eq_s_b("at"))) {
                                cursor = limit - v_3
                                break@lab3
                            }
                            bra = cursor
                            if (!r_R2()) {
                                cursor = limit - v_3
                                break@lab3
                            }
                            slice_del()
                            break@sw6
                            }
                            2 -> {
                            lab4@ do {
                                var v_4: Int = limit - cursor
                                lab5@ do {
                                    if (!r_R2()) {
                                        break@lab5
                                    }
                                    slice_del()
                                    break@lab4
                                } while (false)
                                cursor = limit - v_4
                                if (!r_R1()) {
                                    cursor = limit - v_3
                                    break@lab3
                                }
                                slice_from("eux")
                            } while (false)
                            break@sw6
                            }
                            3 -> {
                            if (!r_R2()) {
                                cursor = limit - v_3
                                break@lab3
                            }
                            slice_del()
                            break@sw6
                            }
                            4 -> {
                            if (!r_RV()) {
                                cursor = limit - v_3
                                break@lab3
                            }
                            slice_from("i")
                            break@sw6
                            }
                        }
                    } while (false)
                } while (false)
                break@sw5
                }
                7 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_5: Int = limit - cursor
                lab6@ do {
                    ket = cursor
                    among_var = find_among_b(a_3)
                    if (among_var == 0) {
                        cursor = limit - v_5
                        break@lab6
                    }
                    bra = cursor
                    sw7@ do {
                        when (among_var) {
                            1 -> {
                            lab7@ do {
                                var v_6: Int = limit - cursor
                                lab8@ do {
                                    if (!r_R2()) {
                                        break@lab8
                                    }
                                    slice_del()
                                    break@lab7
                                } while (false)
                                cursor = limit - v_6
                                slice_from("abl")
                            } while (false)
                            break@sw7
                            }
                            2 -> {
                            lab9@ do {
                                var v_7: Int = limit - cursor
                                lab10@ do {
                                    if (!r_R2()) {
                                        break@lab10
                                    }
                                    slice_del()
                                    break@lab9
                                } while (false)
                                cursor = limit - v_7
                                slice_from("iqU")
                            } while (false)
                            break@sw7
                            }
                            3 -> {
                            if (!r_R2()) {
                                cursor = limit - v_5
                                break@lab6
                            }
                            slice_del()
                            break@sw7
                            }
                        }
                    } while (false)
                } while (false)
                break@sw5
                }
                8 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_8: Int = limit - cursor
                lab11@ do {
                    ket = cursor
                    if (!(eq_s_b("at"))) {
                        cursor = limit - v_8
                        break@lab11
                    }
                    bra = cursor
                    if (!r_R2()) {
                        cursor = limit - v_8
                        break@lab11
                    }
                    slice_del()
                    ket = cursor
                    if (!(eq_s_b("ic"))) {
                        cursor = limit - v_8
                        break@lab11
                    }
                    bra = cursor
                    lab12@ do {
                        var v_9: Int = limit - cursor
                        lab13@ do {
                            if (!r_R2()) {
                                break@lab13
                            }
                            slice_del()
                            break@lab12
                        } while (false)
                        cursor = limit - v_9
                        slice_from("iqU")
                    } while (false)
                } while (false)
                break@sw5
                }
                9 -> {
                slice_from("eau")
                break@sw5
                }
                10 -> {
                if (!r_R1()) {
                    return false
                }
                slice_from("al")
                break@sw5
                }
                11 -> {
                if (!(in_grouping_b(g_oux_ending, 98, 112))) {
                    return false
                }
                slice_from("ou")
                break@sw5
                }
                12 -> {
                lab14@ do {
                    var v_10: Int = limit - cursor
                    lab15@ do {
                        if (!r_R2()) {
                            break@lab15
                        }
                        slice_del()
                        break@lab14
                    } while (false)
                    cursor = limit - v_10
                    if (!r_R1()) {
                        return false
                    }
                    slice_from("eux")
                } while (false)
                break@sw5
                }
                13 -> {
                if (!r_R1()) {
                    return false
                }
                if (!(out_grouping_b(g_v, 97, 251))) {
                    return false
                }
                slice_del()
                break@sw5
                }
                14 -> {
                if (!r_RV()) {
                    return false
                }
                slice_from("ant")
                return false
                }
                15 -> {
                if (!r_RV()) {
                    return false
                }
                slice_from("ent")
                return false
                }
                16 -> {
                var v_11: Int = limit - cursor
                if (!(in_grouping_b(g_v, 97, 251))) {
                    return false
                }
                if (!r_RV()) {
                    return false
                }
                cursor = limit - v_11
                slice_del()
                return false
                }
            }
        } while (false)
        return true
    }
    private fun r_i_verb_suffix(): Boolean {
        if (cursor < I_pV) {
            return false
        }
        var v_1: Int = limit_backward
        limit_backward = I_pV
        ket = cursor
        if (find_among_b(a_5) == 0) {
            limit_backward = v_1
            return false
        }
        bra = cursor
        lab0@ do {
            if (!(eq_s_b("H"))) {
                break@lab0
            }
            limit_backward = v_1
            return false
        } while (false)
        if (!(out_grouping_b(g_v, 97, 251))) {
            limit_backward = v_1
            return false
        }
        slice_del()
        limit_backward = v_1
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
        among_var = find_among_b(a_7)
        if (among_var == 0) {
            limit_backward = v_1
            return false
        }
        bra = cursor
        limit_backward = v_1
        sw8@ do {
            when (among_var) {
                1 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                break@sw8
                }
                2 -> {
                slice_del()
                break@sw8
                }
                3 -> {
                var v_2: Int = limit - cursor
                lab0@ do {
                    if (!(eq_s_b("e"))) {
                        cursor = limit - v_2
                        break@lab0
                    }
                    if (!r_RV()) {
                        cursor = limit - v_2
                        break@lab0
                    }
                    bra = cursor
                } while (false)
                slice_del()
                break@sw8
                }
                4 -> {
                do {
                    var v_3: Int = limit - cursor
                    lab1@ do {
                        among_var = find_among_b(a_6)
                        if (among_var == 0) {
                            break@lab1
                        }
                        sw9@ do {
                            when (among_var) {
                                1 -> {
                                if (cursor <= limit_backward) {
                                    break@lab1
                                }
                                cursor--
                                if (cursor > limit_backward) {
                                    break@lab1
                                }
                                break@sw9
                                }
                            }
                        } while (false)
                        return false
                    } while (false)
                    cursor = limit - v_3
                } while (false)
                slice_del()
                break@sw8
                }
            }
        } while (false)
        return true
    }
    private fun r_residual_suffix(): Boolean {
        var among_var: Int
        var v_1: Int = limit - cursor
        lab0@ do {
            ket = cursor
            if (!(eq_s_b("s"))) {
                cursor = limit - v_1
                break@lab0
            }
            bra = cursor
            var v_2: Int = limit - cursor
            lab1@ do {
                lab2@ do {
                    if (!(eq_s_b("Hi"))) {
                        break@lab2
                    }
                    break@lab1
                } while (false)
                if (!(out_grouping_b(g_keep_with_s, 97, 232))) {
                    cursor = limit - v_1
                    break@lab0
                }
            } while (false)
            cursor = limit - v_2
            slice_del()
        } while (false)
        if (cursor < I_pV) {
            return false
        }
        var v_3: Int = limit_backward
        limit_backward = I_pV
        ket = cursor
        among_var = find_among_b(a_8)
        if (among_var == 0) {
            limit_backward = v_3
            return false
        }
        bra = cursor
        sw10@ do {
            when (among_var) {
                1 -> {
                if (!r_R2()) {
                    limit_backward = v_3
                    return false
                }
                lab3@ do {
                    lab4@ do {
                        if (!(eq_s_b("s"))) {
                            break@lab4
                        }
                        break@lab3
                    } while (false)
                    if (!(eq_s_b("t"))) {
                        limit_backward = v_3
                        return false
                    }
                } while (false)
                slice_del()
                break@sw10
                }
                2 -> {
                slice_from("i")
                break@sw10
                }
                3 -> {
                slice_del()
                break@sw10
                }
            }
        } while (false)
        limit_backward = v_3
        return true
    }
    private fun r_un_double(): Boolean {
        var v_1: Int = limit - cursor
        if (find_among_b(a_9) == 0) {
            return false
        }
        cursor = limit - v_1
        ket = cursor
        if (cursor <= limit_backward) {
            return false
        }
        cursor--
        bra = cursor
        slice_del()
        return true
    }
    private fun r_un_accent(): Boolean {
        do {
            var v_1: Int = 1
            loop11@ while (true) {
                lab0@ do {
                    if (!(out_grouping_b(g_v, 97, 251))) {
                        break@lab0
                    }
                    v_1--
                    continue@loop11
                } while (false)
                break@loop11
            }
            if (v_1 > 0) {
                return false
            }
        } while (false)
        ket = cursor
        lab1@ do {
            lab2@ do {
                if (!(eq_s_b("\u00E9"))) {
                    break@lab2
                }
                break@lab1
            } while (false)
            if (!(eq_s_b("\u00E8"))) {
                return false
            }
        } while (false)
        bra = cursor
        slice_from("e")
        return true
    }
    override fun stem(): Boolean {
        var v_1: Int = cursor
        r_elisions()
        cursor = v_1
        var v_2: Int = cursor
        r_prelude()
        cursor = v_2
        r_mark_regions()
        limit_backward = cursor
        cursor = limit
        var v_3: Int = limit - cursor
        lab0@ do {
            lab1@ do {
                var v_4: Int = limit - cursor
                lab2@ do {
                    var v_5: Int = limit - cursor
                    lab3@ do {
                        var v_6: Int = limit - cursor
                        lab4@ do {
                            if (!r_standard_suffix()) {
                                break@lab4
                            }
                            break@lab3
                        } while (false)
                        cursor = limit - v_6
                        lab5@ do {
                            if (!r_i_verb_suffix()) {
                                break@lab5
                            }
                            break@lab3
                        } while (false)
                        cursor = limit - v_6
                        if (!r_verb_suffix()) {
                            break@lab2
                        }
                    } while (false)
                    cursor = limit - v_5
                    var v_7: Int = limit - cursor
                    lab6@ do {
                        ket = cursor
                        lab7@ do {
                            var v_8: Int = limit - cursor
                            lab8@ do {
                                if (!(eq_s_b("Y"))) {
                                    break@lab8
                                }
                                bra = cursor
                                slice_from("i")
                                break@lab7
                            } while (false)
                            cursor = limit - v_8
                            if (!(eq_s_b("\u00E7"))) {
                                cursor = limit - v_7
                                break@lab6
                            }
                            bra = cursor
                            slice_from("c")
                        } while (false)
                    } while (false)
                    break@lab1
                } while (false)
                cursor = limit - v_4
                if (!r_residual_suffix()) {
                    break@lab0
                }
            } while (false)
        } while (false)
        cursor = limit - v_3
        var v_9: Int = limit - cursor
        r_un_double()
        cursor = limit - v_9
        var v_10: Int = limit - cursor
        r_un_accent()
        cursor = limit - v_10
        cursor = limit_backward
        var v_11: Int = cursor
        r_postlude()
        cursor = v_11
        return true
    }
    override fun equals(other: Any?): Boolean = other is frenchStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.frenchStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("col", -1, -1),
                Among("ni", -1, 1),
                Among("par", -1, -1),
                Among("tap", -1, -1),
            )
        private val a_1 =
            arrayOf(
                Among("", -1, 7),
                Among("H", 0, 6),
                Among("He", 1, 4),
                Among("Hi", 1, 5),
                Among("I", 0, 1),
                Among("U", 0, 2),
                Among("Y", 0, 3),
            )
        private val a_2 =
            arrayOf(
                Among("iqU", -1, 3),
                Among("abl", -1, 3),
                Among("I\u00E8r", -1, 4),
                Among("i\u00E8r", -1, 4),
                Among("eus", -1, 2),
                Among("iv", -1, 1),
            )
        private val a_3 =
            arrayOf(
                Among("ic", -1, 2),
                Among("abil", -1, 1),
                Among("iv", -1, 3),
            )
        private val a_4 =
            arrayOf(
                Among("iqUe", -1, 1),
                Among("atrice", -1, 2),
                Among("ance", -1, 1),
                Among("ence", -1, 5),
                Among("logie", -1, 3),
                Among("able", -1, 1),
                Among("isme", -1, 1),
                Among("euse", -1, 12),
                Among("iste", -1, 1),
                Among("ive", -1, 8),
                Among("if", -1, 8),
                Among("usion", -1, 4),
                Among("ation", -1, 2),
                Among("ution", -1, 4),
                Among("ateur", -1, 2),
                Among("iqUes", -1, 1),
                Among("atrices", -1, 2),
                Among("ances", -1, 1),
                Among("ences", -1, 5),
                Among("logies", -1, 3),
                Among("ables", -1, 1),
                Among("ismes", -1, 1),
                Among("euses", -1, 12),
                Among("istes", -1, 1),
                Among("ives", -1, 8),
                Among("ifs", -1, 8),
                Among("usions", -1, 4),
                Among("ations", -1, 2),
                Among("utions", -1, 4),
                Among("ateurs", -1, 2),
                Among("ments", -1, 16),
                Among("ements", 30, 6),
                Among("issements", 31, 13),
                Among("it\u00E9s", -1, 7),
                Among("ment", -1, 16),
                Among("ement", 34, 6),
                Among("issement", 35, 13),
                Among("amment", 34, 14),
                Among("emment", 34, 15),
                Among("aux", -1, 10),
                Among("eaux", 39, 9),
                Among("eux", -1, 1),
                Among("oux", -1, 11),
                Among("it\u00E9", -1, 7),
            )
        private val a_5 =
            arrayOf(
                Among("ira", -1, 1),
                Among("ie", -1, 1),
                Among("isse", -1, 1),
                Among("issante", -1, 1),
                Among("i", -1, 1),
                Among("irai", 4, 1),
                Among("ir", -1, 1),
                Among("iras", -1, 1),
                Among("ies", -1, 1),
                Among("\u00EEmes", -1, 1),
                Among("isses", -1, 1),
                Among("issantes", -1, 1),
                Among("\u00EEtes", -1, 1),
                Among("is", -1, 1),
                Among("irais", 13, 1),
                Among("issais", 13, 1),
                Among("irions", -1, 1),
                Among("issions", -1, 1),
                Among("irons", -1, 1),
                Among("issons", -1, 1),
                Among("issants", -1, 1),
                Among("it", -1, 1),
                Among("irait", 21, 1),
                Among("issait", 21, 1),
                Among("issant", -1, 1),
                Among("iraIent", -1, 1),
                Among("issaIent", -1, 1),
                Among("irent", -1, 1),
                Among("issent", -1, 1),
                Among("iront", -1, 1),
                Among("\u00EEt", -1, 1),
                Among("iriez", -1, 1),
                Among("issiez", -1, 1),
                Among("irez", -1, 1),
                Among("issez", -1, 1),
            )
        private val a_6 =
            arrayOf(
                Among("al", -1, 1),
                Among("\u00E9pl", -1, -1),
                Among("auv", -1, -1),
            )
        private val a_7 =
            arrayOf(
                Among("a", -1, 3),
                Among("era", 0, 2),
                Among("aise", -1, 4),
                Among("asse", -1, 3),
                Among("ante", -1, 3),
                Among("\u00E9e", -1, 2),
                Among("ai", -1, 3),
                Among("erai", 6, 2),
                Among("er", -1, 2),
                Among("as", -1, 3),
                Among("eras", 9, 2),
                Among("\u00E2mes", -1, 3),
                Among("aises", -1, 4),
                Among("asses", -1, 3),
                Among("antes", -1, 3),
                Among("\u00E2tes", -1, 3),
                Among("\u00E9es", -1, 2),
                Among("ais", -1, 4),
                Among("eais", 17, 2),
                Among("erais", 17, 2),
                Among("ions", -1, 1),
                Among("erions", 20, 2),
                Among("assions", 20, 3),
                Among("erons", -1, 2),
                Among("ants", -1, 3),
                Among("\u00E9s", -1, 2),
                Among("ait", -1, 3),
                Among("erait", 26, 2),
                Among("ant", -1, 3),
                Among("aIent", -1, 3),
                Among("eraIent", 29, 2),
                Among("\u00E8rent", -1, 2),
                Among("assent", -1, 3),
                Among("eront", -1, 2),
                Among("\u00E2t", -1, 3),
                Among("ez", -1, 2),
                Among("iez", 35, 2),
                Among("eriez", 36, 2),
                Among("assiez", 36, 3),
                Among("erez", 35, 2),
                Among("\u00E9", -1, 2),
            )
        private val a_8 =
            arrayOf(
                Among("e", -1, 3),
                Among("I\u00E8re", 0, 2),
                Among("i\u00E8re", 0, 2),
                Among("ion", -1, 1),
                Among("Ier", -1, 2),
                Among("ier", -1, 2),
            )
        private val a_9 =
            arrayOf(
                Among("ell", -1, -1),
                Among("eill", -1, -1),
                Among("enn", -1, -1),
                Among("onn", -1, -1),
                Among("ett", -1, -1),
            )
        private val g_v = charArrayOf('\u0011', '\u0041', '\u0010', '\u0001', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0080', '\u0082', '\u0067', '\u0008', '\u0005')
        private val g_oux_ending = charArrayOf('\u0041', '\u0055')
        private val g_elision_char = charArrayOf('\u0083', '\u000e', '\u0003')
        private val g_keep_with_s = charArrayOf('\u0001', '\u0041', '\u0014', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0080')
    }
}
