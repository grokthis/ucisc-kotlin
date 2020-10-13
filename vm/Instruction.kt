package com.grokthis.ucisc.vm

import java.lang.IllegalStateException

class Instruction(instruction: Int, private val processor: Processor) {
    private val word = instruction.and(0xFFFF0000.toInt()).shr(16).and(0xFFFF)
    val source = word.and(0xF000).shr(12)
    val destination = word.and(0x0F00).shr(8)
    val increment = word.and(0x0080).shr(7)
    val effect = word.and(0x0070).shr(4)
    val aluCode = word.and(0x000F)
    val immediate: Int
    val offset: Int

    init {
        if (isDestinationMem()) {
        val sign = if (!isSourceMem() && instruction.and(0x0800) > 0) {
                0xF000 // Sign extend negative value
            } else {
                0x0000
            }
            immediate = instruction.and(0x0FFF).or(sign)
            offset = instruction.and(0xF000).shr(12)
        } else {
            immediate = instruction.and(0xFFFF)
            offset = 0
        }
    }

    fun execute(): Int {
        val result = computeResult()
        val store = shouldStore()
        if (store) storeResult(result, finalizeDestinationAddress())

        val halt = store && destination == 0 && source == 0 && result == processor.pc
        processor.pc = processor.next
        processor.next = processor.pc + 2

        return if (halt) 1 else 0
    }

    private fun isDestinationMem() = destination in 1..3 || destination in 9..11
    private fun isSourceMem() = source in 1..3 || source in 5..7
    private fun isPop() = increment == 1 && !isDestinationMem()

    fun computeResult(debug: Boolean = false): Int {
        val sourceValue = sourceValue(debug)
        val result = destinationValue(debug)
        return compute(sourceValue, result, !debug).and(0xFFFF)
    }

    fun shouldStore(): Boolean {
        val flags = processor.flags
        val zero = flags.and(0x0001) != 0
        val negative = flags.and(0x0002) != 0

        return when (effect) {
            0 -> zero
            1 -> !zero 2 -> negative
            3 -> false
            4 -> true
            5 -> flags.and(0x0008) != 0
            6 -> flags.and(0x0010) != 0
            7 -> flags.and(0x0020) != 0
            else -> false
        }
    }

    private fun storeResult(value: Int, address: Int) {
        when (destination) {
            0 -> processor.next = value.and(0xFFFF)
            in 1..3 -> processor.writeMem(processor.id, address, value)
            4 -> processor.flags = value.and(0xFFFF)
            in 5..7 -> processor.setRegister(destination - 4, value)
            8 -> processor.flags = value.and(0xFFFF)
            in 9..11 -> processor.writeBanked(address, value)
            12 -> processor.interruptHandler = value.and(0xFFFF)
            in 13..15 -> processor.setRegister(destination - 8, value)
        }
        if (isPop()) {
            val regNum: Int = when (source) {
                in 1..3 -> source
                in 5..7 -> source - 4
                in 9..11 -> source - 4
                in 13..15 -> source - 8
                else -> throw IllegalStateException("Illegal register value") // Should never be true
            }
            processor.setRegister(regNum, (processor.getRegister(regNum) + 1).and(0xFFFF))
        }
    }


    fun sourceValue(debug: Boolean): Int {
        return when (source) {
            0 -> (processor.pc + immediate).and(0xFFFF)
            in 1..3 -> processor.readMem(processor.id, processor.getRegister(source) + immediate)
            4 -> immediate
            in 5..7 -> processor.getRegister(source - 4) + immediate
            8 -> processor.flags
            in 9..11 -> processor.readBanked(processor.getRegister(source - 4) + immediate, debug)
            12 -> processor.interruptHandler
            in 13..15 -> processor.getRegister(source - 8) + immediate
            else -> 0
        }
    }

    fun destinationValue(debug: Boolean): Int {
        return when (destination) {
            0 -> processor.pc
            in 1..3 -> processor.readMem(processor.id, processor.getRegister(destination) + offset)
            4 -> processor.flags
            in 5..7 -> processor.getRegister(destination - 4)
            8 -> processor.flags
            in 9..11 -> processor.readBanked(processor.getRegister(destination - 4) + offset, debug)
            12 -> processor.interruptHandler
            in 13..15 -> processor.getRegister(destination - 8)
            else -> 0
        }
    }

    private fun finalizeDestinationAddress(): Int {
       return when (destination) {
           in 1..3 -> {
               val regValue = processor.getRegister(destination)
               var destinationAddress = regValue + offset
               if (increment == 1) {
                   destinationAddress = (destinationAddress - 1).and(0xFFFF)
                   processor.setRegister(destination, regValue - 1)
               }
               destinationAddress
           }
           in 9..11 -> {
               val regValue = processor.getRegister(destination - 4)
               var destinationAddress = regValue + offset
               if (increment == 1) {
                   destinationAddress -= 1
                   processor.setRegister(destination - 4, regValue - 1)
               }
               destinationAddress
           }
           else -> 0
       }
    }

    private fun signedModeOn() = processor.flags.and(0x0010) != 0

    private fun compute(sourceValue: Int, destinationValue: Int, updateFlags: Boolean = true): Int {
        var doFlags = updateFlags
        var overflow = false
        var overflowReg = 0
        var error = false
        val computed = when (aluCode) {
            0 -> {
                doFlags = false
                sourceValue
            }
            1 -> sourceValue.and(destinationValue).and(0xFFFF)
            2 -> sourceValue.or(destinationValue).and(0xFFFF)
            3 -> sourceValue.xor(destinationValue).and(0xFFFF)
            4 -> sourceValue.inv().and(0xFFFF)
            5 -> {
                val value = destinationValue.shl(sourceValue)
                overflowReg = value.and(0xFFFF0000.toInt()).shr(16).and(0xFFFF)
                value.and(0xFFFF)
            } 6 -> {
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
                if (arg2 == 0) {
                    error = true
                    arg1
                } else {
                    val value = arg1 / arg2
                    overflowReg = arg1 - (value * arg2)
                    value.and(0xFFFF)
                }
            }
            14 -> {
                // page start for sourceValue as address
                sourceValue.and(processor.overflow)
            }
            15 -> {
                // overflow + sourceValue
                overflowReg = sourceValue.and(destinationValue)
                overflowReg
            }
            else -> {
                throw IllegalArgumentException("Invalid ALU Code: $aluCode")
            }
        }

        if (doFlags) {
            val zero = computed == 0
            val negative = computed.and(0x8000) != 0
            val carry = overflowReg.and(0x1) > 0
            processor.flags =
                (if (error) 0x10 else 0).or(if (overflow) 8 else 0).or(if (carry) 4 else 0).or(if (negative) 2
                else 0)
                    .or(if (zero) 1 else
                        0).or(processor.flags.and(0xFFF0))
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

    override fun toString(): String {
        return "$aluCode.op $source.arg $immediate.imm $destination.arg $offset.imm $increment.inc $effect.eff"
    }
}