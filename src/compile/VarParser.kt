package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException
import java.lang.NumberFormatException

class VarParser: Parser {
    private val varRegex =
        Regex("var +(?<reg>[a-zA-Z0-9_\\-]+)\\.(?<name>[a-zA-Z0-9_\\-]+)/(?<offset>(%[0-9a-fA-F]+)|([0-9]+)) *(?<push>push)? *(?<src><.+)?")

    override fun parse(line: String, scope: Scope): Scope {
        val match = varRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expecting valid var: var <register>.<name>/<offset> [source]"
            )

        val registerName = match.groups["reg"]!!.value
        val name = match.groups["name"]!!.value
        val offset = match.groups["offset"]!!.value
        val push = match.groups["push"]?.value
        val source = match.groups["src"]?.value

        val register = scope.findRegister(registerName)
        val offsetValue =
            if (offset.startsWith('%')) {
                try {
                    offset.substring(1, offset.length).toInt(16);
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid hex value: $offset");
                }
            } else {
                offset.toInt()
            }

        val parsedSource: Source? = if (source != null) {
            Source.parse(source, scope)
        } else {
            null
        }
        if (source != null && push != null) {
            scope.updateDelta(register, 1)
        }
        scope.defineVariable(register, name, offsetValue)

        if (parsedSource != null) {
            val argument = Argument(register, offsetValue, false)
            scope.addWords(Statement(argument, push != null, parsedSource))
        }
        return scope
    }

    override fun matches(line: String): Boolean {
        return line.startsWith("var")
    }
}