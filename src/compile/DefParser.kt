package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class DefParser: Parser {
    private val defRegex =
        Regex("def +(?<name>[a-zA-Z0-9_\\-]+)/(?<reg>[a-zA-Z0-9]+) *(?<src><.+)?")

    override fun parse(line: String, scope: Scope): Scope {
        val match = defRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expecting valid def: def <name>/<register> [source]"
            )

        val name = match.groups["name"]!!.value
        val registerName = match.groups["reg"]!!.value.toUpperCase()
        val register = try {
            Register.valueOf(registerName)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid register name: $registerName, expecting r[1-6]."
            )
        }
        scope.defineRegister(name, register)
        val source = match.groups["src"]?.value
        if (source != null) {
            val argument = Argument(register, 0, true)
            scope.addWords(Statement(argument, false, Source.parse(source, scope)))
        }
        return scope
    }

    override fun matches(line: String): Boolean {
        return line.startsWith("def")
    }
}