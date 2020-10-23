package com.grokthis.ucisc.compile

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size == 0) {
        println("Usage: uciscc [-i] <source.ucisc> ...")
        println("  -i :  Use the instruction/micro assembler")
        exitProcess(1)
    }


    var useMicroAssembler = false
    val codeList = args.map {
        if (it == "-i") {
            useMicroAssembler = true
            null
        } else {
            val file = File(it)
            val reader = file.bufferedReader()
            val code = reader.readText()
            reader.close()
            code
        }
    }.filter { it != null }

    val code = codeList.joinToString("\n", "", "")

    val words = Assembler().compile(code).instructions
    val bytes = mutableListOf<Byte>()
    var count = 0
    words.forEach {
        val b1 = it.shr(8).and(0xFF)
        if (b1 < 16) {
            print("0" + b1.toString(16).toUpperCase())
        } else {
            print(b1.toString(16).toUpperCase())
        }
        bytes.add(b1.toByte())
        val b2 = it.and(0xFF)
        if (b2 < 16) {
            print("0" + b2.toString(16).toUpperCase())
        } else {
            print(b2.toString(16).toUpperCase())
        }
        bytes.add(b2.toByte())
        print(" ")
        count += 1
        if (count.rem(16) == 0) {
            println()
        }
    }
    val outFile = File("ucisc.out")
    val stream = outFile.outputStream().buffered()
    stream.write(bytes.toByteArray())
    stream.close()
}