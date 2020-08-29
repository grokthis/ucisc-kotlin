package com.grokthis.ucisc.vm

class Instruction(val word: Int, private val memory: BankedMemory) {
    private val source = word.and(0x0380).shr(7)
    private val destination = word.and(0x1C00).shr(10)
    private val effect = word.and(0x6000).shr(13)
    private val aluCode = word.and(0x000F)
    private val increment = word.and(0x0040)
    private val processor: Processor
    init {
        if (memory.device is Processor) {
            processor = memory.device
        } else {
            throw IllegalArgumentException("BankedMemory device must be a processor")
        }
    }

    fun execute(): Int {
        val result = computeResult()
        if (shouldStore()) {
            storeResult(result.value, result.address)
        }

        val halt = destination == 0 && source == 0 && processor.next == processor.pc
        processor.pc = processor.next + if (halt) 1 else 0
        processor.next = processor.pc + 1
        return when {
            halt && isCopy() -> 2 // copy is usually understood as debug
            halt -> 1 // alu is usually understood as halt
            else -> 0
        }
    }

    data class Result(val value: Int, val address: Int = 0)

    private fun push() = !incrementIsImmediate() && increment > 0
    private fun incrementIsImmediate() = signed() && destination !in 1..3
    private fun signed() = source !in 1..3
    private fun isCopy() = word.and(0x8000) == 0
    private fun halfWidth() = isCopy() && source in 1..3 && destination in 1..3

    private fun computeResult(updateFlags: Boolean = true): Result {
        val immediateValues = extractImmediateValues()
        val sourceValue = sourceValue(immediateValues.first())
        val destinationImmediate = if (immediateValues.size > 1) immediateValues.last() else 0
        return when {
            isCopy() -> Result(sourceValue, destinationAddress(destinationImmediate))
            else -> {
                val result = destinationValue(destinationImmediate)
                Result(compute(sourceValue, result.value, updateFlags), result.address)
            }
        }
    }

    private fun shouldStore(): Boolean {
        val flags = processor.flags
        val zero = flags.and(0x0002) != 0
        return when (effect) {
            3 -> true
            0 -> zero
            1 -> !zero
            2 -> flags.and(0x0004) != 0
            else -> false
        }
    }

    private fun storeResult(value: Int, address: Int) {
        when (destination) {
            0 -> processor.next = value
            in 1..3 -> {
                if (push()) {
                    val regVal = (processor.getRegister(destination) - 1).and(0xFFFF)
                    processor.setRegister(destination, regVal)
                }
                memory.writeMem(processor.id, address, value)
            }
            4 -> memory.bankMask = value
            in 5..7 -> processor.setRegister(destination - 4, value)
        }
    }

    private fun extractImmediateValues(): Array<Int> {
        var immediateMask: Int
        var immediateShift = 0
        if (isCopy()) {
            immediateMask = 0x003F
        } else {
            immediateMask = 0x0030
            immediateShift = 4
        }

        val signMask: Int
        if (incrementIsImmediate()) {
            signMask = 0x0040
            immediateMask = immediateMask.or(signMask)
        } else {
            signMask = 0x0020
        }

        return when {
            (signed() && (word.and(signMask) != 0)) -> {
                // Sign extend and shift immediate
                arrayOf(word.and(immediateMask).inv().and(immediateMask).inv().shr(immediateShift))
            }
            halfWidth() -> {
                arrayOf(
                        word.and(immediateMask).shr(immediateShift + 3),
                        word.and(immediateMask.shr(3)).shr(immediateShift)
                )
            }
            else -> {
                arrayOf(word.and(immediateMask).shr(immediateShift))
            }
        }
    }

    private fun sourceValue(immediate: Int): Int {
        return when (source) {
            0 -> (processor.pc + immediate).and(0xFFFF)
            in 1..3 -> memory.readMem(processor.id, processor.getRegister(source) + immediate)
            4 -> immediate
            in 5..7 -> processor.getRegister(source - 4) + immediate
            else -> 0
        }
    }

    private fun destinationValue(immediate: Int): Result {
        return when (destination) {
            0 -> Result((processor.pc + immediate).and(0xFFFF))
            in 1..3 -> {
                val destinationAddress = processor.getRegister(destination) + immediate
                val pushedAddress = if (push()) (destinationAddress - 1).and(0xFFFF) else destinationAddress
                Result(memory.readMem(processor.id, destinationAddress), pushedAddress)
            }
            4 -> Result(memory.bankMask)
            in 5..7 -> Result(processor.getRegister(destination - 4) + immediate)
            else -> Result(0)
        }
    }

    private fun destinationAddress(immediate: Int): Int {
        return when (destination) {
            in 1..3 -> {
                val destinationAddress = (processor.getRegister(destination) + immediate).and(0xFFFF)
                if (push()) (destinationAddress - 1).and(0xFFFF) else destinationAddress
            }
            else -> 0
        }
    }

    private fun signedModeOn() = processor.flags.and(0x0010) != 0

    private fun compute(sourceValue: Int, destinationValue: Int, updateFlags: Boolean): Int {
        var overflow = false
        var overflowReg = 0
        val computed = when(aluCode) {
            0 -> sourceValue.inv().and(0xFFFF)
            1 -> sourceValue.and(destinationValue).and(0xFFFF)
            2 -> sourceValue.or(destinationValue).and(0xFFFF)
            3 -> sourceValue.xor(destinationValue).and(0xFFFF)
            4 -> (sourceValue * -1).and(0xFFFF)
            5 -> {
                val value = destinationValue.shl(sourceValue)
                overflowReg = value.and(0xFFFF0000.toInt()).shr(16).and(0xFFFF)
                value.and(0xFFFF)
            }
            6 -> {
                overflowReg = if (sourceValue > 16) {
                    destinationValue
                } else {
                    destinationValue.shl(16 - sourceValue)
                }
                if (signedModeOn() && destinationValue.and(0x8000) != 0) {
                    destinationValue.or(0xFFFF0000.toInt()).shr(sourceValue).and(0xFFFF)
                } else {
                    destinationValue.and(0xFFFF).shr(sourceValue).and(0xFFFF)
                }
            }
            7 -> sourceValue.and(0x00FF).shl(8).or(sourceValue.and(0xFF00).shr(8))
            8 -> sourceValue.and(0xFF00)
            9 -> sourceValue.and(0x00FF)
            in 10..11 -> {
                // add (opcode: 10) and subtract (opcode: 11)
                val arg1 = wordToSignedInt(destinationValue)
                var arg2 = wordToSignedInt(sourceValue)

                if (aluCode == 11) arg2 *= -1
                val value = arg1 + arg2
                if (signedModeOn()) {
                    val signBit = 0x8000
                    overflow = sourceValue.and(signBit) == arg1.and(signBit) &&
                            value.and(signBit) != sourceValue.and(signBit)
                    overflowReg = value.shr(16).and(0xFFFF)
                } else {
                    overflow = value.and(0x10000) != 0
                    overflowReg = if (overflow) 1 else 0
                }
                value.and(0xFFFF)
            }
            12 -> {
                // multiply
                val arg1 = wordToSignedInt(destinationValue)
                val arg2 = wordToSignedInt(sourceValue)
                val value = arg1 * arg2
                overflowReg = value.shr(16).and(0xFFFF)
                overflow = overflowReg != 0xFFFF && overflowReg != 0x0000
                value.and(0xFFFF)
            }
            13 -> {
                // divide
                val arg1 = wordToSignedInt(destinationValue)
                val arg2 = wordToSignedInt(sourceValue)
                val value = arg1 / arg2
                overflowReg = arg1 - (value * arg2)
                value.and(0xFFFF)
            }
            14 -> {
                // page start for sourceValue as address
                sourceValue.and(0xFFC)
            }
            15 -> {
                // overflow + sourceValue
                sourceValue + processor.overflow
            }
            else -> {
                throw IllegalArgumentException("Invalid ALU Code: $aluCode")
            }
        }

        val zero = computed == 0
        val negative = computed.and(0x8000) != 0
        if (updateFlags) {
            processor.flags =
                    (if (overflow) 1 else 0).
                    or(if (zero) 2 else 0).
                    or(if (negative) 4 else 0).
                    or(processor.flags.and(0xFFF0))
            processor.overflow = overflowReg
        }
        return computed
    }

    private fun wordToSignedInt(value: Int): Int {
        return if (signedModeOn() && value.and(0x8000) != 0) {
            value.or(0xFFFF0000.toInt())
        } else {
            value
        }
    }
}