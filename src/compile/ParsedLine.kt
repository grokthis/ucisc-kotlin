package com.grokthis.ucisc.compile

class ParsedLine {
    val instructions: MutableList<Instruction> = mutableListOf()
    val labels: MutableList<String> = mutableListOf()
    val variables: MutableList<Variable> = mutableListOf()
    val data: MutableList<Int> = mutableListOf()
    val defs: MutableMap<String, Int> = mutableMapOf()
    val tests: MutableMap<String, Int> = mutableMapOf()
}