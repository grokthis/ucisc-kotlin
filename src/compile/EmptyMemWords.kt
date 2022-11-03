package com.grokthis.ucisc.compile

class EmptyMemWords: MemWords() {
    override fun computeWords(pc: Int, labels: Map<String, Int>): List<Int> {
        return emptyList()
    }

    override fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int {
        return pc
    }

    override fun wordCount(): Int {
        return 0
    }
}