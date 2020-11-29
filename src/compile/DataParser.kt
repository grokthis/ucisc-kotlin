package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class DataParser: Parser {
    private val dataRegex = Regex("((?<label>[a-zA-Z0-9_\\-]+):)? *(\"(?<str>.*)\")|(% *(?<data>[0-9a-fA-F ]*))")

    override fun parse(line: String, scope: Scope): Scope {
        val match = dataRegex.matchEntire(line)
            ?: throw IllegalArgumentException("Expected valid label")

        if (match.groups["label"] != null) {
            val label = match.groups["label"]!!.value
            scope.lastWords().addLabel(label)
        }

        val words = mutableListOf<Int>()
        if (match.groups["str"] != null) {
            var strLiteral = match.groups["str"]!!.value
            strLiteral = strLiteral.replace("\"\"", "\"")
            strLiteral = strLiteral.replace("\\n", "\n")
            val bytes = strLiteral.toByteArray(Charsets.UTF_8)
            bytes.forEach { byte ->
                val word = byte.toInt()
                words.add(word)
            }
            words.add(0) // Null terminate
        } else if (match.groups["data"] != null) {
            val dataLiteral = match.groups["data"]!!.value
            val hexLine = dataLiteral.replace(" ", "")
            for (i in 0 until hexLine.length / 4) {
                val hexValue = hexLine.substring(i * 4, minOf(i * 4 + 4, hexLine.length))
                val word = hexValue.toIntOrNull(16)
                if (word == null) {
                    throw IllegalArgumentException(
                        "Unable to parse data as hex: $hexValue"
                    )
                } else {
                    words.add(word)
                }
            }
        }
        scope.addWords(WordData(words))
        return scope
    }

    override fun matches(line: String): Boolean {
        return line.matches(Regex("((?<label>[a-zA-Z0-9_\\-]+):)? *(\"|%).*"))
    }
}