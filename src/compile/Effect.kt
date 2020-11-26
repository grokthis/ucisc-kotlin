package com.grokthis.ucisc.compile

enum class Effect(val value: Int) {
    ZERO(0),
    NOTZERO(1),
    NEGATIVE(2),
    POSITIVE(3),
    STORE(4),
    OVERFLOW(5),
    INTERRUPTED(6),
    FLAGS(7)
}