package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException
import java.lang.NumberFormatException

class Argument(
    val register: Register,
    var offset: Int,
    val addr: Boolean,
    val immLabel: String? = null
) {

    fun resolveLabel(pc: Int, labels: Map<String, Int>) {
        if (immLabel != null) {
            val absoluteValue = labels[immLabel]
                ?: throw IllegalArgumentException(
                    "Unable to resolve label $immLabel"
                )
            offset = if (register == Register.PC) {
                absoluteValue - pc
            } else {
                absoluteValue
            }
        }
    }

    companion object {
        private val regex =
            Regex("(?<addrOf>&)?(?<reg>[a-zA-Z0-9_\\-]+)(\\.(?<var>[a-zA-Z0-9]+))?(/(?<offset>-?[a-zA-Z0-9]+))?")

        fun parse(str: String, scope: Scope): Argument {
            val match = regex.matchEntire(str)
                ?: throw IllegalArgumentException(
                    "Expected argument: [&]<register>[.<variable>][/<offset>]."
                )

            val registerName = match.groups["reg"]!!.value
            val register: Register = scope.findRegister(registerName)

            val varName = match.groups["var"]?.value
            var offset: Int = baseOffset(scope, varName, register)

            val offsetString = match.groups["offset"]?.value
            var immLabel: String? = null
            if (offsetString != null) {
                try {
                    offset += offsetString.toInt()
                } catch (e: NumberFormatException) {
                    immLabel = offsetString
                }
            }

            val isAddr = match.groups["addrOf"] != null
            return Argument(register, offset, isAddr, immLabel)
        }

        private fun offsetFromString(offsetString: String?): Int {
            return if (offsetString != null) {
                try {
                    offsetString.toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException(
                        "Invalid offset: $offsetString, expecting valid number"
                    )
                }
            } else {
                0
            }
        }

        private fun baseOffset(scope: Scope, varName: String?, register: Register): Int {
            return if (varName != null) {
                val base = scope.findVariable(register, varName)
                val delta = scope.findDelta(register, varName)
                base + delta
            } else {
                0
            }
        }
    }
}