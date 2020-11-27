package com.grokthis.ucisc.vm

class Instruction(msWord: Int, lsWord: Int) {
    private val word = msWord.and(0xFFFF)
    val source = word.shr(12)
    val destination = word.and(0x0F00).shr(8)
    private val increment = word.and(0x0080).shr(7)
    val effect = word.and(0x0070).shr(4)
    val aluCode = word.and(0x000F)
    val immediate: Int
    val offset: Int

    init {
        if (dstMem) {
        val sign = if (lsWord.and(0x0800) == 0 || srcMem) {
                0x0000
            } else {
                0xF000 // Sign extend negative value
            }
            immediate = lsWord.and(0x0FFF).or(sign)
            offset = lsWord.and(0xF000).shr(12)
        } else {
            immediate = lsWord.and(0xFFFF)
            offset = 0
        }
    }

    val dstMem
        get() = destination in 1..3 || destination in 9..11
    val srcMem
        get() = source in 1..3 || source in 9..11
    val pop
        get() = increment == 1 && srcMem && !dstMem
    val push
        get() = increment == 1 && dstMem

    fun shouldStore(flags: Int): Boolean {
        val zero = flags.and(0x0001) != 0
        val negative = flags.and(0x0002) != 0

        return when (effect) {
            4 -> true
            7 -> false
            0 -> zero
            1 -> !zero
            2 -> negative
            3 -> !negative && !zero
            5 -> flags.and(0x0008) != 0
            6 -> flags.and(0x0010) != 0
            else -> false
        }
    }

    override fun toString(): String {
        val op = when (aluCode) {
            0 -> "copy"
            1 -> "and"
            2 -> "or"
            3 -> "xor"
            4 -> "inv"
            5 -> "shl"
            6 -> "shr"
            7 -> "swap"
            8 -> "msb"
            9 -> "lsb"
            10 -> "add"
            11 -> "sub"
            12 -> "mul"
            13 -> "mulu"
            14 -> "addc"
            15 -> "subc"
            else -> "????"
        }
        val src = when (source) {
            0 -> "pc"
            1 -> "r1"
            2 -> "r2"
            3 -> "r3"
            4 -> "val"
            5 -> "&r1"
            6 -> "&r2"
            7 -> "&r3"
            8 -> "flag"
            9 -> "r4"
            10 -> "r5"
            11 -> "r6"
            12 -> "int"
            13 -> "&r4"
            14 -> "&r5"
            15 -> "&r6"
            else -> "????"
        }
        val dst = when (destination) {
            0 -> "pc"
            1 -> "r1"
            2 -> "r2"
            3 -> "r3"
            4 -> "bank"
            5 -> "&r1"
            6 -> "&r2"
            7 -> "&r3"
            8 -> "flag"
            9 -> "r4"
            10 -> "r5"
            11 -> "r6"
            12 -> "int"
            13 -> "&r4"
            14 -> "&r5"
            15 -> "&r6"
            else -> "????"
        }
        val push = when {
            dstMem && increment == 1 -> " push"
            else -> ""
        }
        val pop = when {
            !dstMem && srcMem && increment == 1 -> " pop"
            else -> ""
        }
        val eff = when (effect) {
            0 -> "0?"
            1 -> "!?"
            2 -> "n?"
            3 -> "p?"
            4 -> "-"
            5 -> "o?"
            6 -> "i?"
            7 -> "~"
            else -> "x"
        }
        return "$dst/$offset$push <$eff $op $src/$immediate$pop"
    }
}