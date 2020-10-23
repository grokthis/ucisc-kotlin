package com.grokthis.ucisc.compile

class AggregateParser(val children: List<Parser>): Parser() {
    companion object Default {
        fun get(): AggregateParser {
            val parsers = mutableListOf<Parser>(
                TestParser(),
                DefParser(),
                DataParser(),
                LabelParser(),
                BlockParser(),
                InstructionParser()
            )
            return AggregateParser(parsers)
        }
    }

    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        children.forEach { parser ->
            val parsed = parser.parse(line, rootParser)
            if (parsed != null) return parsed
        }
        return null
    }
}