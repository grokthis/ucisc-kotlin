package com.grokthis.ucisc.compile

abstract class Words() {
    protected val labels: MutableList<String> = mutableListOf()

    abstract fun words(pc: Int, labels: Map<String, Int>): List<Int>
    abstract fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int
    abstract fun wordCount(): Int

    fun addLabel(name: String) {
        labels.add(name)
    }
}