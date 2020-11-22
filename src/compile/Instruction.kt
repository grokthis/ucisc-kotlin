package com.grokthis.ucisc.compile

class Instruction {
    private var msw: Int = 0
    private var lsw: Int = 0
    var immediateLabel: String = ""
    var offsetLabel: String = ""
    var address: Int = 0
    var parseError: Boolean = false

    fun words(): List<Int> {
        return listOf(msw, lsw)
    }

    fun setInstruction(op: String, src: String, imm: String, dest: String, offset: String, inc: String, eff: String) {
        setOpCode(op)
        setSource(src)
        setImmediate(imm)
        setDestination(dest)
        setOffset(offset)
        setIncrement(inc)
        setEffect(eff)
    }

    fun hasOffset(): Boolean {
        val dest = msw.and(0x0F00).shr(8)
        return dest in 1..3 || dest in 9..11
    }

    fun getImmediate(): Int {
        return if (hasOffset()) {
            lsw.and(0x0FFF)
        } else {
            lsw.and(0xFFFF)
        }
    }

    fun setImmediate(imm: Int) {
        lsw = if (hasOffset()) {
            lsw.and(0xF000).or(imm.and(0x0FFF))
        } else {
            lsw.and(0x0000).or(imm.and(0xFFFF))
        }
    }

    fun setImmediate(imm: String) {
        val number = imm.toIntOrNull()
        if (number == null) {
            immediateLabel = imm
        } else {
            setImmediate(number)
        }
    }

    fun setOffset(offset: Int) {
        if (hasOffset()) {
            lsw = lsw.and(0x0FFF).or(offset.and(0xF).shl(12))
        }
    }

    fun getOffset(): Int {
        return if (hasOffset()) {
            lsw.and(0xF000).shr(12)
        } else {
            0
        }
    }

    fun setOffset(offset: String) {
        val number = offset.toIntOrNull()
        if (number == null) {
            offsetLabel = offset
        } else {
            setOffset(number)
        }
    }

    fun setOpCode(opCode: Int) {
        msw = msw.and(0xFFF0).or(opCode.and(0xF))
    }

    fun getOpCode(): Int {
        return msw.and(0x000F)
    }

    fun setOpCode(opCode: String) {
        when (opCode) {
            "copy", "0", "0.op" -> setOpCode(0)
            "and", "1", "1.op" -> setOpCode(1)
            "or", "2", "2.op" -> setOpCode(2)
            "xor", "3", "3.op" -> setOpCode(3)
            "inv", "4", "4.op" -> setOpCode(4)
            "shl", "5", "5.op" -> setOpCode(5)
            "shr", "6", "6.op" -> setOpCode(6)
            "swap", "7", "7.op" -> setOpCode(7)
            "msb", "8", "8.op" -> setOpCode(8)
            "lsb", "9", "9.op" -> setOpCode(9)
            "add", "10", "10.op", "A.op" -> setOpCode(10)
            "sub", "11", "11.op", "B.op" -> setOpCode(11)
            "mult", "12", "12.op", "C.op" -> setOpCode(12)
            "div", "13", "13.op", "D.op" -> setOpCode(13)
            "oflow", "14", "14.op", "E.op" -> setOpCode(14)
            "woflow", "15", "15.op", "F.op" -> setOpCode(15)
            else -> parseError = true
        }
    }

    fun setSource(source: Int) {
        msw = msw.and(0x0FFF).or(source.and(0xF).shl(12))
    }

    fun getSource(): Int {
        return msw.and(0xF000).shr(12)
    }

    fun setSource(source: String) {
        setSource(mapArg(source))
    }

    fun setDestination(destination: Int) {
        msw = msw.and(0xF0FF).or(destination.and(0xF).shl(8))
        if (listOf(1, 2, 3, 9, 10, 11).contains(destination)) {
            // Erase offset
            lsw = lsw.and(0x0FFF)
        }
    }

    fun getDestination(): Int {
        return msw.and(0x0F00).shr(8)
    }

    fun setDestination(destination: String) {
        setDestination(mapArg(destination))
    }

    fun mapArg(arg: String): Int {
        return when (arg) {
            "pc", "0.reg", "0" -> 0
            "stack", "r1", "1.mem", "1" -> 1
            "2.mem", "r2", "2" -> 2
            "3.mem", "r3", "3" -> 3
            "val", "4.val", "banking", "4.reg", "4" -> 4
            "&stack", "&r1", "1.reg", "5" -> 5
            "&r2", "2.reg", "6" -> 6
            "&r3", "3.reg", "7" -> 7
            "flags", "8" -> 8
            "rb1", "5.mem", "9" -> 9
            "rb2", "6.mem", "10" -> 10
            "rb3", "7.mem", "11" -> 11
            "int", "12.reg", "12" -> 12
            "&rb1", "13.reg", "13" -> 13
            "&rb2", "14.reg", "14" -> 14
            "&rb3", "15.reg", "15" -> 15
            else -> {
                parseError = true
                0
            }
        }
    }

    fun setEffect(effect: Int) {
        msw = msw.and(0xFF8F).or(effect.and(0x7).shl(4))
    }

    fun getEffect(): Int {
        return msw.and(0x0070).shr(4)
    }

    fun setEffect(effect: String) {
        val effect = parseEffect(effect)
        if (effect < 0) {
            parseError = true
        } else {
            setEffect(effect)
        }
    }

    fun parseEffect(effect: String): Int {
        return when (effect) {
            "zero?", "0.eff", "0" -> 0
            "!zero?", "1.eff", "1" -> 1
            "negative?", "2.eff", "2" -> 2
            "flags", "3.eff", "3" -> 3
            "store", "4.eff", "4" -> 4
            "oflow?", "5.eff", "5" -> 5
            "error?", "6.eff", "6" -> 6
            "int?", "7.eff", "7" -> 7
            else -> -1
        }
    }

    fun setIncrement(inc: Int) {
        msw = msw.and(0xFF7F).or(inc.and(0x1).shl(7))
    }

    fun getIncrement(): Int {
        return msw.and(0x0080).shr(7)
    }

    fun hasIncrement(): Boolean {
        return hasOffset() || getSource() in 1..3 || getSource() in 9..11
    }

    fun isPush(): Boolean {
        return getIncrement() == 1 && (getDestination() in 1..3 || getDestination() in 9..11)
    }

    fun isPop(): Boolean {
        return getIncrement() == 1 && !isPush()
    }

    fun setIncrement(inc: String) {
        if (hasIncrement()) {
            when (inc) {
                "push", "pop", "1" -> setIncrement(1)
                "none", "0", "-" -> setIncrement(0)
                else -> parseError = true
            }
        }
    }
}