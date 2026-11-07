// Converted from upstream's italianStemmer.java by tools/snowball_to_kotlin.py. Do not edit.
// Generated from italian.sbl by Snowball 3.1.1 - https://snowballstem.org/ (BSD-3-Clause: iosApp/licenses/Snowball.txt)
@file:Suppress("ktlint", "FunctionName", "LocalVariableName", "PropertyName", "ClassName", "NAME_SHADOWING", "UNUSED_VARIABLE", "UNUSED_VALUE", "CanBeVal", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNREACHABLE_CODE", "UNUSED_PARAMETER")

package org.tartarus.snowball.ext

import org.tartarus.snowball.Among

class italianStemmer : org.tartarus.snowball.SnowballStemmer() {
    private var I_p2: Int = 0
    private var I_p1: Int = 0
    private var I_pV: Int = 0
    private fun r_elisions(): Boolean {
        bra = cursor
        if (find_among(a_0) == 0) {
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
        var among_var: Int
        var v_1: Int = cursor
        loop1@ while (true) {
            var v_2: Int = cursor
            lab0@ do {
                bra = cursor
                among_var = find_among(a_1)
                ket = cursor
                sw2@ do {
                    when (among_var) {
                        1 -> {
                        slice_from("\u00E0")
                        break@sw2
                        }
                        2 -> {
                        slice_from("\u00E8")
                        break@sw2
                        }
                        3 -> {
                        slice_from("\u00EC")
                        break@sw2
                        }
                        4 -> {
                        slice_from("\u00F2")
                        break@sw2
                        }
                        5 -> {
                        slice_from("\u00F9")
                        break@sw2
                        }
                        6 -> {
                        slice_from("qU")
                        break@sw2
                        }
                        7 -> {
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
            cursor = v_2
            break@loop1
        }
        cursor = v_1
        loop3@ while (true) {
            var v_3: Int = cursor
            lab1@ do {
                golab2@ while (true) {
                    var v_4: Int = cursor
                    lab3@ do {
                        if (!(in_grouping(g_v, 97, 249))) {
                            break@lab3
                        }
                        bra = cursor
                        lab4@ do {
                            var v_5: Int = cursor
                            lab5@ do {
                                if (!(eq_s("u"))) {
                                    break@lab5
                                }
                                ket = cursor
                                if (!(in_grouping(g_v, 97, 249))) {
                                    break@lab5
                                }
                                slice_from("U")
                                break@lab4
                            } while (false)
                            cursor = v_5
                            if (!(eq_s("i"))) {
                                break@lab3
                            }
                            ket = cursor
                            if (!(in_grouping(g_v, 97, 249))) {
                                break@lab3
                            }
                            slice_from("I")
                        } while (false)
                        cursor = v_4
                        break@golab2
                    } while (false)
                    cursor = v_4
                    if (cursor >= limit) {
                        break@lab1
                    }
                    cursor++
                }
                continue@loop3
            } while (false)
            cursor = v_3
            break@loop3
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
                    if (!(in_grouping(g_v, 97, 249))) {
                        break@lab2
                    }
                    lab3@ do {
                        var v_3: Int = cursor
                        lab4@ do {
                            if (!(out_grouping(g_v, 97, 249))) {
                                break@lab4
                            }
                            if (!go_out_grouping(g_v, 97, 249)) {
                                break@lab4
                            }
                            cursor++
                            break@lab3
                        } while (false)
                        cursor = v_3
                        if (!(in_grouping(g_v, 97, 249))) {
                            break@lab2
                        }
                        if (!go_in_grouping(g_v, 97, 249)) {
                            break@lab2
                        }
                        cursor++
                    } while (false)
                    break@lab1
                } while (false)
                cursor = v_2
                lab5@ do {
                    if (!(eq_s("divan"))) {
                        break@lab5
                    }
                    break@lab1
                } while (false)
                cursor = v_2
                if (!(out_grouping(g_v, 97, 249))) {
                    break@lab0
                }
                lab6@ do {
                    var v_4: Int = cursor
                    lab7@ do {
                        if (!(out_grouping(g_v, 97, 249))) {
                            break@lab7
                        }
                        if (!go_out_grouping(g_v, 97, 249)) {
                            break@lab7
                        }
                        cursor++
                        break@lab6
                    } while (false)
                    cursor = v_4
                    if (!(in_grouping(g_v, 97, 249))) {
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
        lab8@ do {
            if (!go_out_grouping(g_v, 97, 249)) {
                break@lab8
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 249)) {
                break@lab8
            }
            cursor++
            I_p1 = cursor
            if (!go_out_grouping(g_v, 97, 249)) {
                break@lab8
            }
            cursor++
            if (!go_in_grouping(g_v, 97, 249)) {
                break@lab8
            }
            cursor++
            I_p2 = cursor
        } while (false)
        cursor = v_5
        return true
    }
    private fun r_postlude(): Boolean {
        var among_var: Int
        loop4@ while (true) {
            var v_1: Int = cursor
            lab0@ do {
                bra = cursor
                among_var = find_among(a_2)
                ket = cursor
                sw5@ do {
                    when (among_var) {
                        1 -> {
                        slice_from("i")
                        break@sw5
                        }
                        2 -> {
                        slice_from("u")
                        break@sw5
                        }
                        3 -> {
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
    private fun r_RV(): Boolean {
        return I_pV <= cursor
    }
    private fun r_R2(): Boolean {
        return I_p2 <= cursor
    }
    private fun r_attached_pronoun(): Boolean {
        var among_var: Int
        ket = cursor
        if (find_among_b(a_3) == 0) {
            return false
        }
        bra = cursor
        among_var = find_among_b(a_4)
        if (among_var == 0) {
            return false
        }
        if (!r_RV()) {
            return false
        }
        sw6@ do {
            when (among_var) {
                1 -> {
                slice_del()
                break@sw6
                }
                2 -> {
                slice_from("e")
                break@sw6
                }
            }
        } while (false)
        return true
    }
    private fun r_standard_suffix(): Boolean {
        var among_var: Int
        ket = cursor
        among_var = find_among_b(a_7)
        if (among_var == 0) {
            return false
        }
        bra = cursor
        sw7@ do {
            when (among_var) {
                1 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                break@sw7
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
                break@sw7
                }
                3 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("log")
                break@sw7
                }
                4 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("u")
                break@sw7
                }
                5 -> {
                if (!r_R2()) {
                    return false
                }
                slice_from("ente")
                break@sw7
                }
                6 -> {
                if (!r_RV()) {
                    return false
                }
                slice_del()
                break@sw7
                }
                7 -> {
                if (I_p1 > cursor) {
                    return false
                }
                slice_del()
                var v_2: Int = limit - cursor
                lab1@ do {
                    ket = cursor
                    among_var = find_among_b(a_5)
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
                    sw8@ do {
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
                            break@sw8
                            }
                        }
                    } while (false)
                } while (false)
                break@sw7
                }
                8 -> {
                if (!r_R2()) {
                    return false
                }
                slice_del()
                var v_3: Int = limit - cursor
                lab2@ do {
                    ket = cursor
                    if (find_among_b(a_6) == 0) {
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
                break@sw7
                }
                9 -> {
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
                    ket = cursor
                    if (!(eq_s_b("ic"))) {
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
                break@sw7
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
        if (find_among_b(a_8) == 0) {
            limit_backward = v_1
            return false
        }
        bra = cursor
        slice_del()
        limit_backward = v_1
        return true
    }
    private fun r_vowel_suffix(): Boolean {
        var v_1: Int = limit - cursor
        lab0@ do {
            ket = cursor
            if (!(in_grouping_b(g_AEIO, 97, 242))) {
                cursor = limit - v_1
                break@lab0
            }
            bra = cursor
            if (!r_RV()) {
                cursor = limit - v_1
                break@lab0
            }
            slice_del()
            ket = cursor
            if (!(eq_s_b("i"))) {
                cursor = limit - v_1
                break@lab0
            }
            bra = cursor
            if (!r_RV()) {
                cursor = limit - v_1
                break@lab0
            }
            slice_del()
        } while (false)
        var v_2: Int = limit - cursor
        lab1@ do {
            ket = cursor
            if (!(eq_s_b("h"))) {
                cursor = limit - v_2
                break@lab1
            }
            bra = cursor
            if (!(in_grouping_b(g_CG, 99, 103))) {
                cursor = limit - v_2
                break@lab1
            }
            if (!r_RV()) {
                cursor = limit - v_2
                break@lab1
            }
            slice_del()
        } while (false)
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
        r_attached_pronoun()
        cursor = limit - v_3
        var v_4: Int = limit - cursor
        lab0@ do {
            lab1@ do {
                var v_5: Int = limit - cursor
                lab2@ do {
                    if (!r_standard_suffix()) {
                        break@lab2
                    }
                    break@lab1
                } while (false)
                cursor = limit - v_5
                if (!r_verb_suffix()) {
                    break@lab0
                }
            } while (false)
        } while (false)
        cursor = limit - v_4
        var v_6: Int = limit - cursor
        r_vowel_suffix()
        cursor = limit - v_6
        cursor = limit_backward
        var v_7: Int = cursor
        r_postlude()
        cursor = v_7
        return true
    }
    override fun equals(other: Any?): Boolean = other is italianStemmer
    override fun hashCode(): Int = "org.tartarus.snowball.ext.italianStemmer".hashCode()

    private companion object {
        private val a_0 =
            arrayOf(
                Among("all'", -1, -1),
                Among("d'", -1, -1),
                Among("dall'", -1, -1),
                Among("dell'", -1, -1),
                Among("gl'", -1, -1),
                Among("l'", -1, -1),
                Among("m'", -1, -1),
                Among("nell'", -1, -1),
                Among("quell'", -1, -1),
                Among("quest'", -1, -1),
                Among("s'", -1, -1),
                Among("sull'", -1, -1),
                Among("t'", -1, -1),
                Among("tutt'", -1, -1),
                Among("un'", -1, -1),
                Among("v'", -1, -1),
            )
        private val a_1 =
            arrayOf(
                Among("", -1, 7),
                Among("qu", 0, 6),
                Among("\u00E1", 0, 1),
                Among("\u00E9", 0, 2),
                Among("\u00ED", 0, 3),
                Among("\u00F3", 0, 4),
                Among("\u00FA", 0, 5),
            )
        private val a_2 =
            arrayOf(
                Among("", -1, 3),
                Among("I", 0, 1),
                Among("U", 0, 2),
            )
        private val a_3 =
            arrayOf(
                Among("la", -1, -1),
                Among("cela", 0, -1),
                Among("gliela", 0, -1),
                Among("mela", 0, -1),
                Among("tela", 0, -1),
                Among("vela", 0, -1),
                Among("le", -1, -1),
                Among("cele", 6, -1),
                Among("gliele", 6, -1),
                Among("mele", 6, -1),
                Among("tele", 6, -1),
                Among("vele", 6, -1),
                Among("ne", -1, -1),
                Among("cene", 12, -1),
                Among("gliene", 12, -1),
                Among("mene", 12, -1),
                Among("sene", 12, -1),
                Among("tene", 12, -1),
                Among("vene", 12, -1),
                Among("ci", -1, -1),
                Among("li", -1, -1),
                Among("celi", 20, -1),
                Among("glieli", 20, -1),
                Among("meli", 20, -1),
                Among("teli", 20, -1),
                Among("veli", 20, -1),
                Among("gli", 20, -1),
                Among("mi", -1, -1),
                Among("si", -1, -1),
                Among("ti", -1, -1),
                Among("vi", -1, -1),
                Among("lo", -1, -1),
                Among("celo", 31, -1),
                Among("glielo", 31, -1),
                Among("melo", 31, -1),
                Among("telo", 31, -1),
                Among("velo", 31, -1),
            )
        private val a_4 =
            arrayOf(
                Among("ando", -1, 1),
                Among("endo", -1, 1),
                Among("ar", -1, 2),
                Among("er", -1, 2),
                Among("ir", -1, 2),
            )
        private val a_5 =
            arrayOf(
                Among("ic", -1, -1),
                Among("abil", -1, -1),
                Among("os", -1, -1),
                Among("iv", -1, 1),
            )
        private val a_6 =
            arrayOf(
                Among("ic", -1, 1),
                Among("abil", -1, 1),
                Among("iv", -1, 1),
            )
        private val a_7 =
            arrayOf(
                Among("ica", -1, 1),
                Among("logia", -1, 3),
                Among("osa", -1, 1),
                Among("ista", -1, 1),
                Among("iva", -1, 9),
                Among("anza", -1, 1),
                Among("enza", -1, 5),
                Among("ice", -1, 1),
                Among("atrice", 7, 1),
                Among("iche", -1, 1),
                Among("logie", -1, 3),
                Among("abile", -1, 1),
                Among("ibile", -1, 1),
                Among("usione", -1, 4),
                Among("azione", -1, 2),
                Among("uzione", -1, 4),
                Among("atore", -1, 2),
                Among("ose", -1, 1),
                Among("ante", -1, 1),
                Among("mente", -1, 1),
                Among("amente", 19, 7),
                Among("iste", -1, 1),
                Among("ive", -1, 9),
                Among("anze", -1, 1),
                Among("enze", -1, 5),
                Among("ici", -1, 1),
                Among("atrici", 25, 1),
                Among("ichi", -1, 1),
                Among("abili", -1, 1),
                Among("ibili", -1, 1),
                Among("ismi", -1, 1),
                Among("usioni", -1, 4),
                Among("azioni", -1, 2),
                Among("uzioni", -1, 4),
                Among("atori", -1, 2),
                Among("osi", -1, 1),
                Among("anti", -1, 1),
                Among("amenti", -1, 6),
                Among("imenti", -1, 6),
                Among("isti", -1, 1),
                Among("ivi", -1, 9),
                Among("ico", -1, 1),
                Among("ismo", -1, 1),
                Among("oso", -1, 1),
                Among("amento", -1, 6),
                Among("imento", -1, 6),
                Among("ivo", -1, 9),
                Among("it\u00E0", -1, 8),
                Among("ist\u00E0", -1, 1),
                Among("ist\u00E8", -1, 1),
                Among("ist\u00EC", -1, 1),
            )
        private val a_8 =
            arrayOf(
                Among("isca", -1, 1),
                Among("enda", -1, 1),
                Among("ata", -1, 1),
                Among("ita", -1, 1),
                Among("uta", -1, 1),
                Among("ava", -1, 1),
                Among("eva", -1, 1),
                Among("iva", -1, 1),
                Among("erebbe", -1, 1),
                Among("irebbe", -1, 1),
                Among("isce", -1, 1),
                Among("ende", -1, 1),
                Among("are", -1, 1),
                Among("ere", -1, 1),
                Among("ire", -1, 1),
                Among("asse", -1, 1),
                Among("ate", -1, 1),
                Among("avate", 16, 1),
                Among("evate", 16, 1),
                Among("ivate", 16, 1),
                Among("ete", -1, 1),
                Among("erete", 20, 1),
                Among("irete", 20, 1),
                Among("ite", -1, 1),
                Among("ereste", -1, 1),
                Among("ireste", -1, 1),
                Among("ute", -1, 1),
                Among("erai", -1, 1),
                Among("irai", -1, 1),
                Among("isci", -1, 1),
                Among("endi", -1, 1),
                Among("erei", -1, 1),
                Among("irei", -1, 1),
                Among("assi", -1, 1),
                Among("ati", -1, 1),
                Among("iti", -1, 1),
                Among("eresti", -1, 1),
                Among("iresti", -1, 1),
                Among("uti", -1, 1),
                Among("avi", -1, 1),
                Among("evi", -1, 1),
                Among("ivi", -1, 1),
                Among("isco", -1, 1),
                Among("ando", -1, 1),
                Among("endo", -1, 1),
                Among("Yamo", -1, 1),
                Among("iamo", -1, 1),
                Among("avamo", -1, 1),
                Among("evamo", -1, 1),
                Among("ivamo", -1, 1),
                Among("eremo", -1, 1),
                Among("iremo", -1, 1),
                Among("assimo", -1, 1),
                Among("ammo", -1, 1),
                Among("emmo", -1, 1),
                Among("eremmo", 54, 1),
                Among("iremmo", 54, 1),
                Among("immo", -1, 1),
                Among("ano", -1, 1),
                Among("iscano", 58, 1),
                Among("avano", 58, 1),
                Among("evano", 58, 1),
                Among("ivano", 58, 1),
                Among("eranno", -1, 1),
                Among("iranno", -1, 1),
                Among("ono", -1, 1),
                Among("iscono", 65, 1),
                Among("arono", 65, 1),
                Among("erono", 65, 1),
                Among("irono", 65, 1),
                Among("erebbero", -1, 1),
                Among("irebbero", -1, 1),
                Among("assero", -1, 1),
                Among("essero", -1, 1),
                Among("issero", -1, 1),
                Among("ato", -1, 1),
                Among("ito", -1, 1),
                Among("uto", -1, 1),
                Among("avo", -1, 1),
                Among("evo", -1, 1),
                Among("ivo", -1, 1),
                Among("ar", -1, 1),
                Among("ir", -1, 1),
                Among("er\u00E0", -1, 1),
                Among("ir\u00E0", -1, 1),
                Among("er\u00F2", -1, 1),
                Among("ir\u00F2", -1, 1),
            )
        private val g_v = charArrayOf('\u0011', '\u0041', '\u0010', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0080', '\u0080', '\u0008', '\u0002', '\u0001')
        private val g_AEIO = charArrayOf('\u0011', '\u0041', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0000', '\u0080', '\u0080', '\u0008', '\u0002')
        private val g_CG = charArrayOf('\u0011')
    }
}
