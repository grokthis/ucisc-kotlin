package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class StatementParser: Parser<Statement> {
    private val dstRegex =
        Regex("(?<arg>&?[a-zA-Z0-9\\-_/.]+) *(?<inc>push)? *(?<src><.+)")

    override fun parse(line: String, scope: Scope): Statement {
        val match = dstRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expecting valid destination: [&]<register>[.<variable>][/<offset>] [push] [source]"
            )

        val argString = match.groups["arg"]!!.value
        val argument = Argument.parse(argString, scope)

        if (argument.addr && argument.offset != 0) {
            throw IllegalArgumentException(
                "Offset must be zero for address destinations"
            )
        } else if (!argument.addr && argument.offset !in 0..15) {
            throw IllegalArgumentException(
                "Offset out of bounds: ${argument.offset} - it must be in 0..15"
            )
        }

        val isInc = match.groups["inc"] != null
        val source: Source = SourceParser().parse(match.groups["src"]!!.value, scope)
        val statement = Statement(argument, isInc, source)
        scope.addWords(statement)

        if (statement.push) {
            scope.updateDelta(statement.argument.register, 1)
        } else if (statement.source.pop) {
            scope.updateDelta(statement.source.argument.register, -1)
        }
        return statement
    }

    override fun matches(line: String): Boolean {
        return line.matches(dstRegex)
    }
}