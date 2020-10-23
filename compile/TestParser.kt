package com.grokthis.ucisc.compile

class TestParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val args = line.split(Regex("\\s")).filter { it.isNotBlank() }
        if (args.isNotEmpty() && args.first() == "TEST") {
            val parsed = ParsedLine()
            parsed.tests[args[1]] = args[2].toInt().and(0xFFFF)
            return parsed
        }
        return null
    }
}