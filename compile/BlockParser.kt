package com.grokthis.ucisc.compile

class BlockParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val parsed = ParsedLine()
        when {
            line.startsWith("{") -> {
                parsed.labels.add("loop")
            }
            line.startsWith("}") -> {
                parsed.labels.add("break")
            }
            else -> {
                return null
            }
        }

        if (rootParser != null) {
            val subLine = rootParser.parse(line.substring(1).trim(), rootParser)
            if (subLine != null) {
                parsed.labels.addAll(subLine.labels)
                parsed.data.addAll(subLine.data)
                parsed.instructions.addAll(subLine.instructions)
                parsed.variables.addAll(subLine.variables)
            }
        }
        return parsed
    }
}