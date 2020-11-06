package com.grokthis.ucisc.compile

class LabelParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val regex = Regex("^(?<name>[a-zA-Z0-9.?/@$]+):.*")
        val match = regex.matchEntire(line) ?: return null

        val name = match.groups["name"]?.value ?: return null
        val parsed = ParsedLine()
        parsed.labels.add(name)

        if (rootParser != null && line.length > name.length) {
            val subLine = rootParser.parse(line.substring(name.length + 1).trim(), rootParser)
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