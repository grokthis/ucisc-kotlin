package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class LabelParser: Parser<String> {
    private val labelRegex = Regex("(?<label>[a-zA-Z0-9_\\-]+):")

    override fun parse(line: String, scope: Scope): String {
        val match = labelRegex.matchEntire(line)
            ?: throw IllegalArgumentException("Expected valid label")

        val label = match.groups["label"]!!.value
        scope.lastWords().addLabel(label)
        return label
    }

    override fun matches(line: String): Boolean {
        return line.matches(labelRegex)
    }
}