package com.grokthis.ucisc.compile

import com.grokthis.ucisc.vm.SerialBlinkyMachine
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
    var scope = Scope()
    code.split("\n").forEachIndexed { lineIndex, line ->
        try {
            var cleanLine = line.replace(Regex("#.*"), "").trim()
            if (cleanLine.isNotEmpty()) {
                scope = scope.parseLine(cleanLine)
            }
        } catch (e: IllegalArgumentException) {
            System.err.println("Error on line ${lineIndex + 1}: ${e.message}")
            exitProcess(1)
        }
    }
    if (scope.parent != null) {
        System.err.println("Found unclosed block at the end of the file")
    }
    val labels = mutableMapOf<String, Int>()
    try {
        scope.resolveLabels(0, labels)
        val words = scope.words(0, labels)
        if (compile) {
            dumpHex(words)
        } else {
            run(words);
        }
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
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
    val machine = SerialBlinkyMachine(words)
    machine.run()
}