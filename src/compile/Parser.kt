package com.grokthis.ucisc.compile

interface Parser<T> {
    fun parse(line: String, scope: Scope): T

    fun matches(line: String): Boolean
}