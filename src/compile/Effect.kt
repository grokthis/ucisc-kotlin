package com.grokthis.ucisc.compile

enum class Effect(val value: Int) {
    ZERO(0),
    NOTZERO(1),
    NEGATIVE(2),
    FLAGS(3),
    STORE(4),
    OVERFLOW(5),
    ERROR(6),
    INTERRUPTED(7)
}