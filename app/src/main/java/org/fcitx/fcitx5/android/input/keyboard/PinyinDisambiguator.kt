/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 WhiteFrost Suretype
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * Pinyin-aware disambiguation for Suretype dual-letter keys.
 *
 * @deprecated Replaced by Rime speller/algebra xlit-based disambiguation (Phase 3).
 *             The Kotlin-side buffer approach caused engine state desync.
 *             See DEVELOPMENT.md Section 4 (Failure Analysis) and Section 5 (Phase 3 Plan).
 *             This file is retained for reference (pinyin syllable data, completionCount logic).
 */
@Deprecated(
    message = "Replaced by Rime algebra xlit disambiguation",
    replaceWith = ReplaceWith("Rime schema rime_frost_suretype.schema.yaml")
)
object PinyinDisambiguator {

    // Complete list of valid Modern Standard Chinese pinyin syllables (without tones)
    // Reference: GB/T 16159-2012
    private val validSyllables: Set<String> = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian",
        "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai",
        "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou",
        "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci",
        "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di",
        "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan",
        "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong",
        "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong",
        "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong",
        "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong",
        "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia",
        "lian", "liang", "liao", "lie", "lin", "ling", "liu", "long", "lou",
        "lu", "luan", "lun", "luo", "lv", "lve",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi",
        "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni",
        "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou",
        "nu", "nuan", "nuo", "nv", "nve",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian",
        "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong",
        "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru",
        "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai",
        "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou",
        "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si",
        "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "tei", "teng", "ti", "tian",
        "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong",
        "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong",
        "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha",
        "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi",
        "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui",
        "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    // All valid prefixes of pinyin syllables (for partial input matching)
    private val validPrefixes: Set<String> by lazy {
        validSyllables.flatMap { s ->
            (1..s.length).map { s.take(it) }
        }.toSet()
    }

    // Maximum length of a pinyin syllable
    private val maxSyllableLen = validSyllables.maxOf { it.length }

    /**
     * Check if [input] could be a valid complete or partial pinyin sequence.
     * Accepts sequences like "wo", "xiang", "xi'a" (with syllable separator).
     */
    fun isValidPinyinSequence(input: String): Boolean {
        if (input.isEmpty()) return true
        val lower = input.lowercase()
        // Try to split into valid syllables
        return canParse(lower, 0)
    }

    private fun canParse(s: String, start: Int): Boolean {
        if (start >= s.length) return true
        val remaining = s.length - start
        val maxLook = minOf(maxSyllableLen, remaining)
        // Try to find a valid syllable starting at 'start'
        for (len in maxLook downTo 1) {
            val candidate = s.substring(start, start + len)
            if (candidate in validSyllables) {
                // Check if the rest can be parsed too
                if (canParse(s, start + len)) return true
            }
        }
        // No valid syllable - but the remainder could be a valid prefix
        return remaining <= maxSyllableLen && s.substring(start) in validPrefixes
    }

    /**
     * Approximate frequency of letters as pinyin syllable initials in common Chinese text.
     * Used as tiebreaker when both letters are valid pinyin prefixes.
     * Higher = more common as a syllable starter.
     */
    private val initialFrequency: Map<Char, Int> = mapOf(
        'y' to 100, 'j' to 95, 'x' to 88, 'z' to 83, 'w' to 80,
        's' to 77, 'l' to 73, 'd' to 70, 'g' to 62, 'b' to 60,
        'h' to 57, 'q' to 54, 't' to 50, 'f' to 47, 'c' to 44,
        'm' to 40, 'n' to 38, 'r' to 35, 'k' to 32, 'p' to 30
    )

    private fun freq(c: Char): Int = initialFrequency[c.lowercaseChar()] ?: 0

    /** Number of complete valid syllables that start with the given prefix. */
    private fun completionCount(prefix: String): Int {
        return validSyllables.count { it.startsWith(prefix) }
    }

    /**
     * Given a full pinyin input sequence, extract the active syllable prefix
     * (the part being currently typed). Considers all possible syllable splits
     * and returns the longest possible current prefix that could form a syllable.
     */
    private fun currentSyllablePrefix(input: String): String {
        if (input.isEmpty()) return ""
        // Try: the entire input as a single prefix (most common for mid-syllable)
        if (input.length <= maxSyllableLen && input in validPrefixes) {
            return input
        }
        // Try parsing from start: greedily consume complete syllables
        var pos = 0
        while (pos < input.length) {
            val remaining = input.length - pos
            var found = false
            for (len in minOf(maxSyllableLen, remaining) downTo 1) {
                val candidate = input.substring(pos, pos + len)
                if (candidate in validSyllables) {
                    pos += len
                    found = true
                    break
                }
            }
            if (!found) break
        }
        val remainder = input.substring(pos)
        // Check if remainder could be a prefix
        return if (remainder.length <= maxSyllableLen && remainder in validPrefixes) remainder
        else input // fallback: treat whole input as prefix
    }

    /**
     * Disambiguate between two letters for a Suretype key tap.
     *
     * When only one letter forms a valid pinyin sequence, it wins.
     * When both are valid, the more common pinyin initial wins (frequency tiebreaker).
     * When neither is valid, the primary letter is used (and buffer resets).
     *
     * @param primary   the primary (top/left) letter
     * @param secondary the secondary (bottom/right) letter
     * @param currentBuffer the current pinyin input buffer (lowercase, empty = new syllable)
     * @param isChineseMode whether we're in Chinese input mode
     * @return the chosen letter
     */
    fun disambiguate(
        primary: Char,
        secondary: Char,
        currentBuffer: String,
        isChineseMode: Boolean = true
    ): Char {
        if (!isChineseMode) return primary
        if (!primary.isLetter() || !secondary.isLetter()) return primary
        if (primary == secondary) return primary

        val p = primary.lowercaseChar()
        val s = secondary.lowercaseChar()

        val withPrimary = currentBuffer + p
        val withSecondary = currentBuffer + s

        val primaryValid = isValidPinyinSequence(withPrimary)
        val secondaryValid = isValidPinyinSequence(withSecondary)

        return when {
            primaryValid && !secondaryValid -> primary
            !primaryValid && secondaryValid -> secondary
            primaryValid && secondaryValid -> {
                // Both valid — use frequency + completion count as tiebreaker
                if (currentBuffer.isEmpty()) {
                    // New syllable: prefer the more common pinyin initial
                    if (freq(s) > freq(p)) secondary else primary
                } else {
                    // Mid-syllable: prefer the letter that leads to more valid completions
                    // Count completions for just the current syllable prefix
                    val pPrefix = currentSyllablePrefix(withPrimary)
                    val sPrefix = currentSyllablePrefix(withSecondary)
                    val pCount = completionCount(pPrefix)
                    val sCount = completionCount(sPrefix)
                    if (sCount > pCount) secondary else primary
                }
            }
            else -> primary // neither valid, default to primary
        }
    }

    /**
     * Generate all possible letter combinations from a sequence of ambiguous key pairs.
     *
     * Each key pair represents a Suretype key with two possible letters. This method
     * computes the Cartesian product: for N keys, it produces 2^N combinations.
     *
     * Example: [(Q,W), (E,R)] → ["qe", "qr", "we", "wr"]
     *
     * @param keys ordered list of ambiguous key pairs from the tap sequence
     * @return all possible 2^N lowercase letter strings
     */
    fun expandCombinations(keys: List<Pair<Char, Char>>): List<String> {
        if (keys.isEmpty()) return emptyList()
        var result = listOf("")
        for ((a, b) in keys) {
            result = result.flatMap { prefix ->
                listOf(prefix + a.lowercaseChar(), prefix + b.lowercaseChar())
            }
        }
        return result
    }

    /**
     * Resolve a sequence of ambiguous key taps into ranked candidate strings.
     *
     * **Chinese mode**: Expands all combinations via [expandCombinations], filters
     * against the valid pinyin syllable dictionary via [isValidPinyinSequence],
     * then ranks surviving candidates by [completionCount] on the active syllable
     * prefix (descending). Returns an ordered list where the best candidate is first.
     *
     * **English mode**: Returns a single-item list containing only the primary
     * (top/left) letter from each key pair concatenated in order.
     *
     * @param keys ordered list of ambiguous key pairs from the tap sequence
     * @param isChinese whether we are in Chinese (pinyin) input mode
     * @return ranked list of candidate strings (best first), may be empty if no
     *         valid Chinese combination exists and we fall back
     */
    fun resolveSequence(keys: List<Pair<Char, Char>>, isChinese: Boolean): List<String> {
        if (keys.isEmpty()) return emptyList()

        if (!isChinese) {
            // English mode: just use primary letters
            return listOf(keys.map { it.first.lowercaseChar() }.joinToString(""))
        }

        val allCombos = expandCombinations(keys)
        val validCombos = allCombos.filter { isValidPinyinSequence(it) }

        if (validCombos.isEmpty()) {
            // No valid pinyin combination found — fall back to primary letters
            return listOf(keys.map { it.first.lowercaseChar() }.joinToString(""))
        }

        // Rank by completion count on the current syllable prefix
        return validCombos.sortedByDescending { combo ->
            completionCount(currentSyllablePrefix(combo))
        }
    }
}
