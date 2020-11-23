package com.grokthis.ucisc.compile

abstract class Words() {
    abstract fun words(pc: Int, labels: Map<String, Int>): List<Int>
    abstract fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int
    abstract fun addLabel(name: String)
    abstract fun wordCount(): Int
}