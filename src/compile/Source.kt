package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class Source(
    val argument: Argument,
    val opcode: Op,
    val effect: Effect,
    val pop: Boolean
) {
    companion object {
        private val srcRegex =
            Regex("<(?<eff>[\\-~0!noei])\\?? (?<op>[a-z]+) (?<arg>&?[a-zA-Z0-9\\-_/.]+) *(?<inc>pop)?")

        fun parse(line: String, scope: Scope): Source {
            val match = srcRegex.matchEntire(line)
                ?: throw IllegalArgumentException(
                    "Expected valid source: <effect> [&]<register>[.<variable>][/<offset>] [pop]"
                )

            val effStr = match.groups["eff"]!!.value
            val effect: Effect = when(effStr) {
                "0" -> Effect.ZERO
                "!" -> Effect.NOTZERO
                "n" -> Effect.NEGATIVE
                "p" -> Effect.POSITIVE
                "~" -> Effect.FLAGS
                "-" -> Effect.STORE
                "o" -> Effect.OVERFLOW
                "i" -> Effect.INTERRUPTED
                else -> {
                    throw IllegalArgumentException(
                        "Invalid effect: <$effStr"
                    )
                }
            }

            val opStr = match.groups["op"]!!.value
            val op: Op =
                try {
                    Op.valueOf(opStr.toUpperCase())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid op code: $opStr")
                }

            val isInc = match.groups["inc"] != null
            val argString = match.groups["arg"]!!.value
            val argument = Argument.parse(argString, scope)

            return Source(argument, op, effect, isInc)
        }
    }
}