package com.grokthis.ucisc.compile

interface Parser {
    fun parse(line: String, scope: Scope): Scope

    fun matches(line: String): Boolean
}