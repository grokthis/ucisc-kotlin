package com.grokthis.ucisc.compile

enum class Op(val value: Int) {
    COPY(0),
    AND(1),
    OR(2),
    XOR(3),
    INV(4),
    SHL(5),
    SHR(6),
    SWAP(7),
    MSB(8),
    LSB(9),
    ADD(10),
    SUB(11),
    MULT(12),
    DIV(13)
}