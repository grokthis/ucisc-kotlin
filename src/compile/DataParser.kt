package com.grokthis.ucisc.compile

class DataParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        if (!line.startsWith("%")) {
            return null
        }
        val data = line.substring(1).split(Regex("\\s")).filter { it.isNotBlank() }
        val result = mutableListOf<Int>()

        val words = data.flatMap { element ->
            val strMatch = Regex("\"(?<str>([^\"]*|\\\\\")*)\"").matchEntire(element)
            if (strMatch != null) {
                val string = strMatch.groups["str"].toString()
                    .replace("\\\"", "\"")
                    .replace("\\\n", "\n")
                val bytes = string.toByteArray(Charsets.UTF_16BE)
                val result = mutableListOf<Int>()
                for (i in 0 .. bytes.size / 2) {
                    val word = bytes[i].toInt().and(0xF).shl(8).or(bytes[i + 1].toInt().and(0xF))
                    result.add(word)
                }
                result
            } else {
                val result = mutableListOf<Int>()
                for (i in 0 .. element.length / 4) {
                    val word = element.substring(i, i + 4).toIntOrNull(16)
                    if (word == null) {
                        println("Unable to parse data as hex fully: $element")
                    } else {
                        result.add(word)
                    }
                }
                result
            }
        }
        val parsed = ParsedLine()
        parsed.data.addAll(words)
        return parsed
    }
}