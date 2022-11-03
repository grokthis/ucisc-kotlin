package com.grokthis.ucisc.compile

abstract class MemWords() {
    protected val labels: MutableMap<String, Int> = mutableMapOf()
    protected val publicLabels: MutableSet<String> = mutableSetOf()

    abstract fun computeWords(pc: Int, labels: Map<String, Int>): List<Int>
    abstract fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int
    abstract fun wordCount(): Int

    fun addLabel(name: String, argCount: Int = 0) {
        labels[name] = argCount
    }

    fun setPublic(name: String) {
        if (labels.containsKey(name)) {
            publicLabels.add(name);
        }
    }
}