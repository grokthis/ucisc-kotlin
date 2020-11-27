package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class LabelParser: Parser {
    private val labelRegex = Regex("(?<label>[a-zA-Z0-9_\\-]+):")

    override fun parse(line: String, scope: Scope): Scope {
        val match = labelRegex.matchEntire(line)
            ?: throw IllegalArgumentException("Expected valid label")

        val label = match.groups["label"]!!.value
        scope.lastWords().addLabel(label)
        return scope
    }

    override fun matches(line: String): Boolean {
        return line.matches(labelRegex)
    }
}