package com.grokthis.ucisc.compile

class Instruction {
    private var msw: Int = 0
    private var lsw: Int = 0

    fun words(): List<Int> {
        return listOf(msw, lsw)
    }

    fun setImmediate(imm: Int) {
        lsw = if (hasOffset()) {
            lsw.and(0xF000).or(imm.and(0x0FFF))
        } else {
            lsw.and(0x0000).or(imm.and(0xFFFF))
        }
    }

    fun setOffset(offset: Int) {
        if (hasOffset()) {
            lsw = lsw.and(0x0FFF).or(offset.and(0xF).shl(12))
        }
    }

    fun setOpCode(opCode: Int) {
        msw = msw.and(0xFFF0).or(opCode.and(0xF))
    }

    fun setSource(source: Int) {
        msw = msw.and(0x0FFF).or(source.and(0xF).shl(12))
    }

    fun setDestination(destination: Int) {
        msw = msw.and(0xF0FF).or(destination.and(0xF).shl(8))
        if (listOf(1, 2, 3, 9, 10, 11).contains(destination)) {
            // Erase offset
            lsw = lsw.and(0x0FFF)
        }
    }

    fun setEffect(effect: Int) {
        msw = msw.and(0xFF8F).or(effect.and(0x7).shl(4))
    }

    fun setIncrement(inc: Int) {
        msw = msw.and(0xFF7F).or(inc.and(0x1).shl(7))
    }

    private fun hasOffset(): Boolean {
        val dest = msw.and(0x0F00).shr(8)
        return dest in 1..3 || dest in 9..11
    }
}