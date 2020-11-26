package com.grokthis.ucisc.vm

/**
 * Implements the uCISC ALU operations. From the documentation:
 *
 * Signedness: The processor can be put into signed or unsigned mode. Arithmetic and
 * right shift operations respect this mode.
 *
 * #### Bit operations
 *
 * Carry and overflow flags are 0
 *
 * Format: CODE - Name and description (instruction)
 *
 * 00 - Copy (copy)
 * 01 - And (and)
 * 02 - Or (or)
 * 03 - Xor (xor)
 * 04 - Invert (inv)
 *
 * 05 - Shift left, zero extend (shl)
 * 06 - Shift right, respect signed mode (shr)
 *
 * #### Byte operations
 *
 * Carry and overflow flags are 0
 *
 * 07 - Swap MSB and LSB bytes (swap)
 * 08 - MSB only: A & 0xFF00 (msb)
 * 09 - LSB only: A & 0x00FF (lsb)
 *
 * #### Arithmetic operations
 *
 * Carry and overflow flags are set appropriately. Overflow means the
 * result is too big to correctly represent in the result.
 *
 * 0A - Add, respect signed mode (add)
 * 0B - Subtract, respect signed mode (sub)
 * 0C - Multiply, respect signed mode, carry is zero (mult)
 * 0D - Multiply, return MSW, respect signed mode, carry is zero (mult)
 * 0E - Add with carry in, respect signed mode (addc)
 * 0F - Subtract with carry in, respect signed mode (subc)
 */
class Alu {
    data class AluResult(val value: Int, val flags: Int)

    fun compute(op: Int, srcValue: Int, dstValue: Int, flags: Int): AluResult {
        var overflow = false
        var carryOut = false
        val signed = signedModeOn(flags)
        val computed = when (op) {
            0 -> srcValue
            1 -> srcValue.and(dstValue).and(0xFFFF)
            2 -> srcValue.or(dstValue).and(0xFFFF)
            3 -> srcValue.xor(dstValue).and(0xFFFF)
            4 -> srcValue.inv().and(0xFFFF)
            5 -> dstValue.shl(srcValue).and(0xFFFF)
            6 -> {
                if (signed && dstValue.and(0x8000) != 0) {
                    dstValue.or(0xFFFF0000.toInt()).shr(srcValue).and(0xFFFF)
                } else {
                    dstValue.and(0xFFFF).shr(srcValue).and(0xFFFF)
                }
            }
            7 -> srcValue
                .and(0x00FF).shl(8).or(
                    srcValue.and(0xFF00).shr(8)
                )
            8 -> srcValue.and(0xFF00)
            9 -> srcValue.and(0x00FF)
            in 10..11, in 14..15 -> {
                val arg1 = wordToSignedInt(dstValue, signed)
                var arg2 = wordToSignedInt(srcValue, signed)
                if (op == 11 || op == 15) {
                    arg2 *= -1
                }
                val carry = if (op > 11) carryIn(flags) else 0
                val value = arg1 + arg2 + carry
                overflow = if (signed) {
                    val signBit = 0x8000
                    srcValue.and(signBit) == arg1.and(signBit) &&
                        value.and(signBit) != srcValue.and(signBit)
                } else {
                    value.and(0x10000) != 0
                }
                carryOut = value.and(0x10000) !=
                    (arg1.and(0x10000) + arg2.and(0x10000)).and(0x10000)
                value.and(0xFFFF)
            }
            12 -> {
                val arg1 = wordToSignedInt(dstValue, signed)
                val arg2 = wordToSignedInt(srcValue, signed)
                val value = arg1 * arg2
                overflow = value > 0xFFFF
                value.and(0xFFFF)
            }
            13 -> {
                val arg1 = wordToSignedInt(dstValue, signed)
                val arg2 = wordToSignedInt(srcValue, signed)
                val value = arg1 * arg2
                value.shr(16).and(0xFFFF)
            }
            else -> {
                throw IllegalArgumentException("Invalid ALU Code: $op")
            }
        }

        val zero = computed == 0
        val negative = computed.and(0x8000) != 0
        val newFlags =
            (if (overflow) 8 else 0)
                .or(if (carryOut) 4 else 0)
                .or(if (negative) 2 else 0)
                .or(if (zero) 1 else 0)
                .or(flags.and(0xFFF0))
        return AluResult(computed, if (op == 0) flags else newFlags)
    }

    private fun signedModeOn(flags: Int): Boolean {
        return flags.and(0x0100) > 0
    }

    private fun carryIn(flags: Int): Int {
        return if (flags.and(0x4) > 0) 1 else 0
    }

    private fun wordToSignedInt(value: Int, signed: Boolean): Int {
        return if (signed && value.and(0x8000) != 0) {
            value.or(0xFFFF0000.toInt())
        } else {
            value
        }
    }
}