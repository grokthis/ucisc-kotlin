package com.grokthis.ucisc.compile

class DefParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val defRegex = Regex("def\\s+(?<name>[a-zA-Z0-9@.?]+)\\s+as\\s+(?<value>-?[0-9]+)")
        val match = defRegex.matchEntire(line)
        if (match != null) {
            val name = match.groups["name"]!!.value
            val value = match.groups["value"]!!.value
            val parsed = ParsedLine()
            parsed.defs[name] = value.toInt()
            return parsed
        }
        return null
    }
}