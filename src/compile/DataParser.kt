package com.grokthis.ucisc.compile

class DataParser: Parser() {
    val strMatch = Regex("\"(?<str>.*)")

    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        if (!line.startsWith("%") && !line.startsWith("\"")) {
            return null
        }
        val words = mutableListOf<Int>()
        if (line.startsWith("\"")) {
            val strLiteral = line.substring(1)
            val bytes = strLiteral.toByteArray(Charsets.UTF_8)
            bytes.forEach { byte ->
                val word = byte.toInt()
                words.add(word)
            }
            words.add(0, words.size)
        } else {
            val hexLine = line.replace(" ", "")
            for (i in 0 .. hexLine.length / 4) {
                val hexValue = hexLine.substring(i * 4, minOf(i * 4 + 4, hexLine.length))
                val word = hexValue.toIntOrNull(16)
                if (word == null) {
                    println("Unable to parse data as hex fully: $hexValue")
                } else {
                    words.add(word)
                }
            }
        }
        val parsed = ParsedLine()
        parsed.data.addAll(words)
        return parsed
    }
}