package com.grokthis.ucisc.compile

class DefParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val defRegex = Regex("def\\s+(<?name>[a-zA-Z0-9@.?]+)\\s+(?<value>[0-9]+)")
        val match = defRegex.matchEntire(line)
        if (match != null) {
            val name = match.groups["name"].toString()
            val value = match.groups["name"].toString()
            val parsed = ParsedLine()
            parsed.defs[name] = value.toInt()
            return parsed
        }
        return null
    }
}