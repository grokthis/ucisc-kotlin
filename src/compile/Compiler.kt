package com.grokthis.ucisc.compile

import kotlin.system.exitProcess

class Compiler {
    companion object {
        fun compile(code: String): List<Int> {
            var scope = Scope()
            code.split("\n").forEachIndexed { lineIndex, line ->
                try {
                    val cleanLine = line.replace(Regex("#.*"), "").trim()
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
            return try {
                scope.resolveLabels(0, labels)
                scope.computeWords(0, labels)
            } catch (e: IllegalArgumentException) {
                System.err.println("Error: ${e.message}")
                exitProcess(1)
            }
        }
    }
}