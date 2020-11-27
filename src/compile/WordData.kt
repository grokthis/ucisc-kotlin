package com.grokthis.ucisc.compile

class WordData(private val data: List<Int>): Words() {
    override fun words(pc: Int, labels: Map<String, Int>): List<Int> {
        return data
    }

    override fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int {
        val nextPC = pc + data.size
        this.labels.forEach { (label, _) ->
            labels[label] = nextPC
        }
        return nextPC
    }

    override fun wordCount(): Int {
        return data.size
    }
}