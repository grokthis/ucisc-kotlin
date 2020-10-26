package com.grokthis.ucisc.vm

import com.grokthis.ucisc.compile.Assembler
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ucisc <file.uc>")
        exitProcess(1)
    }

    val codeList = args.map { readFile(it) }
    val code = codeList.joinToString("\n")

    val compiled = Assembler().compile(code)
    val machine = SerialBlinkyMachine(compiled.instructions)
    machine.run()
}

fun readFile(fileName: String): String {
    val file = File(fileName)
    val reader = file.bufferedReader()
    val code = reader.readText()
    reader.close()
    return code
}