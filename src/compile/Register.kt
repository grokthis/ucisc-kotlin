package com.grokthis.ucisc.compile

enum class Register(val value: Int) {
    PC(0),
    VAL(4),
    BANKING(4),
    R1(5),
    R2(6),
    R3(7),
    FLAGS(8),
    INTERRUPT(12),
    R4(13),
    R5(14),
    R6(15);

    val hasMemRef
        get() = value in 5..7 || value in 13..15

    val memRef
       get() = if (hasMemRef) value - 4 else value

    val validSrc
       get() = this != BANKING

    val validDest
        get() = this != VAL
}