package com.grokthis.ucisc.compile

class Variable(
    val name: String,
    val declared: Int,
    var delta: Int = 0
) {
    val offset
        get() = declared + delta
}