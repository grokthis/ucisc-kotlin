package com.grokthis.ucisc.compile

class Variable(val target: Int, val name: String, val offset: Int) {
    fun matches(instruction: Instruction): Boolean {
        return instruction.getDestination() == target && instruction.isPush()
    }
}