package com.grokthis.ucisc.compile

import com.grokthis.ucisc.vm.ReferenceMachine
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ucisc [-c] <source.ucisc> [<source.ucisc> ...]")
        println("  -c :  Compile and dump hex")
        exitProcess(1)
    }

    var compile = false
    val codeList = args.map { arg ->
        if (arg == "-c") {
            compile = true
            null
        } else {
            readFile(arg)
        }
    }.filterNotNull()

    val code = codeList.joinToString("\n", "", "")
    val words =
        Compiler.compile(code)
    if (compile) {
        dumpHex(words)
    } else {
        run(words);
    }
}

fun readFile(fileName: String): String {
    val file = File(fileName)
    val reader = file.bufferedReader()
    val code = reader.readText()
    reader.close()
    return code
}

fun dumpHex(words: List<Int>) {
    var count = 0
    words.forEach { word ->
        System.out.printf("%04x ", word)
        count += 1
        if (count.rem(16) == 0) {
            println()
        }
    }
}

fun run(words: List<Int>) {
    val machine = ReferenceMachine(words)
    machine.run()
}