package com.grokthis.ucisc.compile

import com.grokthis.ucisc.vm.ReferenceMachine
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ucisc [-r=<file>] [-t=<file>] [options] <source.ucisc> [<source.ucisc> ...]")
        println("  -c :  Compile and dump hex")
        println("  -d :  Start in debug mode")
        println("  -r :  Specify the UART Rx file")
        println("  -t :  Specify the UART Tx file")
        println("  -e :  Send EOT char after Rx file finishes")
        exitProcess(1)
    }

    var compile = false
    var debug = false
    var rxEOT = false
    var rx = "rx.data"
    var tx = "tx.data"
    val codeList = args.map { arg ->
        when {
            arg.startsWith("-t") -> {
                tx = arg.split("=").last()
                null
            }
            arg.startsWith("-r") -> {
                rx = arg.split("=").last()
                null
            }
            arg == "-c" -> {
                compile = true
                null
            }
            arg == "-d" -> {
                debug = true
                null
            }
            arg == "-e" -> {
                rxEOT = true
                null
            }
            else -> {
                readFile(arg)
            }
        }
    }.filterNotNull()

    val code = codeList.joinToString("\n", "", "")
    val words =
        Compiler.compile(code)
    if (compile) {
        dumpHex(words)
    } else {
        run(words, debug, rx, tx, rxEOT)
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

fun run(words: List<Int>, debug: Boolean, rx: String, tx: String, rxEot: Boolean) {
    val rxFile = File(rx)
    if (!rxFile.exists()) {
        rxFile.createNewFile()
    }
    val txFile = File(tx)
    if (!txFile.exists()) {
        txFile.createNewFile()
    }
    val machine = ReferenceMachine(words, rxFile, txFile, rxEot)
    machine.run(debug)
}