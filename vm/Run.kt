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

    val serial = SerialConnection(100)
    val processor = Processor(1, 16)
    processor.connected[2] = serial
    val compiled = Assembler().compile(code)
    processor.load(compiled.instructions)
    serial.rxData = readFile("ucisc/compile.uc")
    processor.run()
}

fun readFile(fileName: String): String {
    val file = File(fileName)
    val reader = file.bufferedReader()
    val code = reader.readText()
    reader.close()
    return code
}