package com.grokthis.ucisc.vm

import com.grokthis.ucisc.compile.MicroAssembler
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.exp
import kotlin.test.assertEquals

class UciscInstructionTests {

    @TestFactory
    fun instructionTests(): Collection<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        File("ucisc/test").walk().forEach {
            if (it.name.endsWith("uc.test")) {
                val testName = it.name
                // Micro assembly code
                val reader = it.bufferedReader()
                val code = reader.readText()
                reader.close()
                val compiled = MicroAssembler().compile(code)
                compiled.tests.entries.forEach {
                    val startOffset = it.key
                    val expected = it.value
                    tests.add(dynamicTest("$testName - $startOffset") {
                        val processor = createProcessor(compiled.instructions)
                        processor.pc = startOffset
                        val result = processor.run()
                        assertEquals(expected, result)
                    })
                }
            }
        }
        return tests
    }

    fun createProcessor(instructions: List<Int>): Processor {
        val writer = TerminalWriter(100)
        // val reader = TerminalReader(101)
        val processor = Processor(1, 16)
        processor.connected[16] = writer
        //processor.connected[17] = reader
        processor.load(instructions)
        return processor
    }
}