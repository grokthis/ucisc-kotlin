package com.grokthis.ucisc.compile

data class CompiledCode(val instructions: List<Int>, val tests: Map<Int, Int>) {
}