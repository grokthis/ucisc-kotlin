package com.grokthis.ucisc.vm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import kotlin.random.Random

internal class CopyInstructionTest {
    private val processor = Processor(17, 8)
    private val memory = BankedMemory(device = processor, bankIndex = 0)

    @BeforeEach
    fun setUp() {
        for(i in 0..255) memory.writeMem(processor.id, i, i)
    }

    private fun setupInstruction(
            fromReg: Int, fromVal: Int,
            toReg: Int, toVal: Int? = null,
            push: Boolean = false,
            immediate: Int = 0,
            condition: Int = 3,
            target: BankedMemory? = null
    ): Instruction {
        val processor = target?.device as Processor? ?: processor
        when (fromReg) {
            0 -> processor.pc = fromVal
            in 1..3 -> processor.setRegister(fromReg, fromVal)
            in 5..7 -> processor.setRegister(fromReg - 4, fromVal)
        }
        toVal?.let {
            when (toReg) {
                0 -> processor.pc = toVal
                in 1..3 -> processor.setRegister(toReg, toVal)
                4 -> memory.bankMask = toVal
                in 5..7 -> processor.setRegister(toReg - 4, toVal)
            }
        }

        val pushFlag = if (push) 1.shl(6) else 0
        val encodedImm = immediate.and(0x7F)
        val base = condition.shl(13)
        val word = base.or(fromReg.shl(7)).or(toReg.shl(10)).or(pushFlag).or(encodedImm)
        return Instruction(word, target ?: memory)
    }

    @Test
    fun `copy in place leaves value unchanged`() {
        for(i in 10..20) {
            val fromReg = 1
            val toReg = i.rem(3) + 1
            setupInstruction(fromReg, i, toReg, i).execute()

            assertEquals(i, memory.readMem(17, i, true))
            assertEquals(i, memory.readMem(17, processor.getRegister(fromReg), true))
            assertEquals(i, memory.readMem(17, processor.getRegister(toReg), true))
        }
    }

    @Test
    fun `copy new values updates value`() {
        for(i in 10..20) {
            val fromReg = i.rem(3) + 1
            val toReg = (fromReg + 1).rem(3) + 1
            setupInstruction(fromReg, i + 100, toReg, i).execute()

            assertEquals(i + 100, memory.readMem(17, i, true))
            assertEquals(i + 100, memory.readMem(17, processor.getRegister(fromReg), true))
            assertEquals(i + 100, memory.readMem(17, processor.getRegister(toReg), true))
        }
    }

    @Test
    fun `copy outside of mem space affects local mem`() {
        for(i in 0..9) {
            val fromReg = 1
            val toReg = 2
            setupInstruction(fromReg, i + 256 + 10, toReg, i).execute()

            assertEquals(i + 10, memory.readMem(17, i, true))
            assertEquals(i + 10, memory.readMem(17, processor.getRegister(fromReg), true))
            assertEquals(i + 10, memory.readMem(17, processor.getRegister(toReg), true))
        }
    }

    @Test
    fun `pushing writes to previous address`() {
        for(i in 0..9) {
            val fromReg = 1
            val toReg = 2
            setupInstruction(fromReg, i, toReg, i, true).execute()

            assertEquals(i, memory.readMem(17, i, true))
            assertEquals(i, memory.readMem(17, (i - 1).and(0xFFFF), true))
            assertEquals(i + 1, memory.readMem(17, i + 1, true))
            assertEquals(i, memory.readMem(17, processor.getRegister(fromReg), true))
            assertEquals(i, memory.readMem(17, processor.getRegister(toReg), true))
            assertEquals((i - 1).and(0xFFFF), processor.getRegister(toReg))
        }
    }

    @Test
    fun `writing from register to mem`() {
        for(i in 0..9) {
            val fromReg = 7
            val toReg = 2
            setupInstruction(fromReg, 0x8888, toReg, i).execute()

            assertEquals(0x8888, memory.readMem(17, i, true))
        }
    }

    @Test
    fun `writing from mem to reg`() {
        for(i in 0..9) {
            val fromReg = 1
            val toReg = 6
            setupInstruction(fromReg, i, toReg, 0).execute()

            assertEquals(i, memory.readMem(17, i, true))
            assertEquals(i, processor.getRegister(toReg - 4))
        }
    }

    @Test
    fun `reading from mem with immediate`() {
        for(i in 0..9) {
            val fromReg = 1
            val toReg = 6
            setupInstruction(fromReg, i, toReg, 0, immediate = 10).execute()

            assertEquals((i + 10).rem(256), processor.getRegister(toReg - 4))
        }
    }

    @Test
    fun `reading from mem and writing to mem with immediates`() {
        for(i in 0..9) {
            val fromReg = 1
            val toReg = 2
            val fromImmediate = 7
            val toImmediate = 3
            val immediate = fromImmediate.and(0x7).shl(3).or(toImmediate.and(0x7))
            setupInstruction(fromReg, i, toReg, i, immediate = immediate).execute()

            assertEquals(i + 7, memory.readMem(17, i + 3, true))
        }
    }


    @Test
    fun `negative immediate for register`() {
        setupInstruction(0, 100, 0, 100, immediate = -64).execute()
        assertEquals(36, processor.pc)

        setupInstruction(0, 100, 0, 100, immediate = 63).execute()
        assertEquals(163, processor.pc)
    }

    @Test
    fun `copy from register to register`() {
        for (fromReg in 5..7) {
            for (toReg in 5..7) {
                setupInstruction(fromReg, 0x9F9F, toReg, immediate = 0x10).execute()
                assertEquals(0x9FAF, processor.getRegister(toReg - 4))
                if (toReg != fromReg) {
                    assertEquals(0x9F9F, processor.getRegister(fromReg - 4))
                }
            }
        }
    }

    @Test
    fun `respects zero conditional`() {
        processor.flags = 0x10 // signed, no zero flag
        setupInstruction(0, 100, 0, immediate = -64, condition = 0).execute()
        assertEquals(101, processor.pc)

        processor.flags = 0x00 // unsigned, no zero flag
        setupInstruction(0, 100, 0, immediate = -64, condition = 0).execute()
        assertEquals(101, processor.pc)

        processor.flags = 0x12 // signed, zero flag set
        setupInstruction(0, 100, 0, immediate = -64, condition = 0).execute()
        assertEquals(36, processor.pc)

        processor.flags = 0x02 // unsigned, zero flag set
        setupInstruction(0, 100, 0, immediate = -64, condition = 0).execute()
        assertEquals(36, processor.pc)
    }

    @Test
    fun `respects not zero conditional`() {
        processor.flags = 0x10 // signed, no zero flag
        setupInstruction(0, 100, 0, immediate = -64, condition = 1).execute()
        assertEquals(36, processor.pc)

        processor.flags = 0x00 // unsigned, no zero flag
        setupInstruction(0, 100, 0, immediate = -64, condition = 1).execute()
        assertEquals(36, processor.pc)

        processor.flags = 0x12 // signed, zero flag set
        setupInstruction(0, 100, 0, immediate = -64, condition = 1).execute()
        assertEquals(101, processor.pc)

        processor.flags = 0x02 // unsigned, zero flag set
        setupInstruction(0, 100, 0, immediate = -64, condition = 1).execute()
        assertEquals(101, processor.pc)
    }

    @Test
    fun `respects negative conditional`() {
        processor.flags = 0x10 // signed, no negative flag
        setupInstruction(0, 100, 0, immediate = -64, condition = 2).execute()
        assertEquals(101, processor.pc)

        processor.flags = 0x00 // unsigned, no negative flag
        setupInstruction(0, 100, 0, immediate = -64, condition = 2).execute()
        assertEquals(101, processor.pc)

        processor.flags = 0x14 // signed, negative flag set
        setupInstruction(0, 100, 0, immediate = -64, condition = 2).execute()
        assertEquals(36, processor.pc)

        processor.flags = 0x04 // unsigned, negative flag set
        setupInstruction(0, 100, 0, immediate = -64, condition = 2).execute()
        assertEquals(36, processor.pc)
    }

    @Test
    fun `copy leaves register state as expected`() {
        for (toOffset in 0..7) {
            val fromReg = 1
            val toReg = 2
            val fromImmediate = 1 // read from address 100
            val immediate = fromImmediate.and(0x7).shl(3).or(toOffset.and(0x7))

            val random = Random.nextInt(0x0000, 0x10000)
            memory.writeMem(17, 100, random)
            setupInstruction(fromReg, 99, toReg, 100, immediate = immediate).execute()

            // Registers should be unchanged after copy
            assertEquals(99, processor.getRegister(1))
            assertEquals(100, processor.getRegister(2))

            // Make sure the memory values read the random number correctly
            if (toOffset != 0) {
                // We write 100 back over the source to make sure we are reading the right memory address
                memory.writeMem(17, 100, 100)
                assertEquals(100, memory.readMem(17, 100, true))
            }
            assertEquals(random, memory.readMem(17, 100 + toOffset, true))
        }
    }

   @Test
    fun `copy from self processor bank control section`() {
        processor.setRegister(3, 103)
        processor.flags = 0xFFFF
        memory.deviceStatus = 0x42
        memory.bankMask = 1
        memory.mappedBlock = 1
        val controlValues = mapOf(
            0 to 17, // Device ID
            1 to 0x0001, // Bank block (MSB) | Device Type (LSB)
            2 to 0, // Init device ID
            3 to 1, // Mapped device block address
            4 to 0x42, // Device status
            5 to 0, // Interrupt handler address
            6 to 0, // Maximum local memory block
            7 to 0, // Next interrupt device
            8 to 100, // pc
            9 to 9, // 1.reg - we use 1.reg as memory pointer
            10 to 9, // 2.reg - we use 2.reg as result, this copies result from previous iteration to itself
            11 to 103, // 3.reg
            12 to 0xFFFF, // flags
            13 to 0x0001 // banking control
        )

        controlValues.forEach { (address, value) ->
            processor.pc = 100
            setupInstruction(1, address, 6).execute()
            assertEquals(value, processor.getRegister(2))
        }
    }

    @Test
    fun `interrupt handler saves and restores value`() {
        memory.bankMask = 1
        setupInstruction(5, 0xF0F0, 2, 5).execute()
        assertEquals(0xF0F0, memory.interruptHandler)
        setupInstruction(1, 5, 6).execute()
        assertEquals(0xF0F0, processor.getRegister(2))
    }

    @Test
    fun `maximum local memory block is correct`() {
        for (width in 8..16) {
            val processor = Processor(16, width)
            val memory = BankedMemory(device = processor, bankIndex = 0)
            memory.bankMask = 1
            setupInstruction(1, 6, 6, target = memory).execute()
            val expectedBank = (-1).shl(width).inv().and(0xFF00)
            assertEquals(expectedBank, processor.getRegister(2))
        }
    }

    @Test
    @Disabled
    fun `copy to self processor bank control section`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `copy from bank control section`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `copy to bank control section`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `copy to bank memory block`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `copy from bank memory block`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `copy to control is ignored when not init device`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `copy to banked block is ignored when not init device`() {
        assert(false)
    }

    @Test
    fun `flags not modified after copy`() {
        processor.flags = 0x13
        setupInstruction(5, 100, 6, 10, immediate = -64).execute()
        assertEquals(36, processor.getRegister(2))
        assertEquals(0x13, processor.flags)

        processor.flags = 0x03
        setupInstruction(5, 100, 6, 10, immediate = -64).execute()
        assertEquals(36, processor.getRegister(2))
        assertEquals(0x03, processor.flags)

        processor.flags = 0x13
        setupInstruction(1, 100, 2, 10).execute()
        assertEquals(100, memory.readMem(17, 10, true))
        assertEquals(0x13, processor.flags)

        processor.flags = 0x03
        setupInstruction(1, 100, 2, 10).execute()
        assertEquals(100, memory.readMem(17, 10, true))
        assertEquals(0x03, processor.flags)
    }

    @Test
    fun `overflow not modified after copy`() {
        processor.overflow = 0xFFFF
        setupInstruction(1, 100, 2, 10).execute()
        assertEquals(100, memory.readMem(17, 10, true))
        assertEquals(0xFFFF, processor.overflow)

        processor.overflow = 0x4242
        setupInstruction(1, 100, 2, 10).execute()
        assertEquals(100, memory.readMem(17, 10, true))
        assertEquals(0x4242, processor.overflow)
    }

    @Test
    fun `pc progresses after execution`() {
        processor.pc = 255
        setupInstruction(1, 1, 2, 1).execute()
        // progress normally after mem to mem copy
        assertEquals(256, processor.pc)
        setupInstruction(5, 1, 6).execute()
        // progress normally after reg to reg copy
        assertEquals(257, processor.pc)
        setupInstruction(5, 0xF00, 0).execute()
        // after jump, exactly match the jump value
        assertEquals(0xF00, processor.pc)
        setupInstruction(5, 1, 6).execute()
        // continue incrementing after jump
        assertEquals(0xF01, processor.pc)
    }

    @Test
    @Disabled
    fun `banked memory works past end of local mem space`() {
        assert(false)
    }

    @Test
    fun `write to ROM section is ignored`() {
        memory.setRom(arrayOf(0x1000, 0x1001, 0x1002, 0x1003), 8)

        // 0 to 3 should have their values initialized as expected
        for (i in 0..3) {
            assertEquals(0x1000 + i, memory.readMem(17, i, true))
        }

        // 0 to 7 are in ROM section, whether value is explicitly set or not
        for (i in 0..7) {
            val before = memory.readMem(17, i, true)
            setupInstruction(6, 0xFFFF, 1, i).execute()
            assertEquals(before, memory.readMem(17, i, true))
        }

        // Address 8 is past the ROM section, it should work normally
        setupInstruction(6, 0xFFFF, 1, 8).execute()
        assertEquals(0xFFFF, memory.readMem(17, 8, true))
    }

    @Test
    @Disabled
    fun `bank index does not need to match device id`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `reads from the correct device when there are multiple`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `init device is auto claimed`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `init device can not be written to unless from init device`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `changing mapped bank changes read location`() {
        assert(false)
    }

    @Test
    @Disabled
    fun `changing mapped bank changes write location`() {
        assert(false)
    }
}