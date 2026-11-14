package com.bmwcockpit.app.obd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdFrameParserTest {
    private fun b(vararg x: Int) = ByteArray(x.size) { x[it].toByte() }

    @Test fun singleFrameWithSpacesAndPadding() {
        val m = ObdFrameParser.parse(listOf("7E8 04 41 0C 1A F8 AA AA AA"), ProtocolFamily.CAN_11).single()
        assertEquals("7E8", m.ecu)
        assertTrue(m.complete)
        assertArrayEquals(b(0x41, 0x0C, 0x1A, 0xF8), m.data)
    }

    @Test fun singleFrameWithoutSpaces() {
        val m = ObdFrameParser.parse(listOf("7E803410D32"), ProtocolFamily.CAN_11).single()
        assertArrayEquals(b(0x41, 0x0D, 0x32), m.data)
    }

    @Test fun twoEcusAreKeptSeparate() {
        val lines = listOf("7E8 06 41 00 BE 3F A8 13", "7E9 06 41 00 98 18 80 11")
        val msgs = ObdFrameParser.parse(lines, ProtocolFamily.CAN_11)
        assertEquals(listOf("7E8", "7E9"), msgs.map { it.ecu })
        assertEquals(0xBE.toByte(), msgs[0].data[2])
        assertEquals(0x98.toByte(), msgs[1].data[2])
        // filtering by the selected ECU never mixes in the transmission ECU
        assertEquals(1, ObdFrameParser.parseFrom(lines, ProtocolFamily.CAN_11, "7E8").size)
    }

    @Test fun multiFrameIsReassembled() {
        // VIN-style response (service 09 PID 02), 0x14 = 20 bytes
        val lines = listOf(
            "7E8 10 14 49 02 01 57 42 41",
            "7E8 21 38 45 31 43 35 30 46",
            "7E8 22 4B 31 32 33 34 35 36",
        )
        val m = ObdFrameParser.parse(lines, ProtocolFamily.CAN_11).single()
        assertTrue(m.complete)
        assertEquals(20, m.data.size)
        assertEquals(0x49.toByte(), m.data[0])
        assertEquals(0x36.toByte(), m.data[19])
    }

    @Test fun multiFrameMissingConsecutiveIsIncomplete() {
        val lines = listOf("7E8 10 14 49 02 01 57 42 41", "7E8 22 4B 31 32 33 34 35 36")
        val m = ObdFrameParser.parse(lines, ProtocolFamily.CAN_11).single()
        assertFalse(m.complete)
    }

    @Test fun interleavedMultiFrameFromTwoEcus() {
        val lines = listOf(
            "7E8 10 0A 43 04 01 71 03 02",
            "7E9 03 43 01 07 00",
            "7E8 21 04 20 01 33 00 00 00",
        )
        val msgs = ObdFrameParser.parse(lines, ProtocolFamily.CAN_11)
        val ecm = msgs.first { it.ecu == "7E8" }
        assertTrue(ecm.complete)
        assertEquals(10, ecm.data.size)
        assertEquals("7E9", msgs.first { it.ecu == "7E9" }.ecu)
    }

    @Test fun truncatedSingleFrameIsIncomplete() {
        val m = ObdFrameParser.parse(listOf("7E8 04 41 0C 1A"), ProtocolFamily.CAN_11).single()
        assertFalse(m.complete)
    }

    @Test fun can29Bit() {
        val m = ObdFrameParser.parse(listOf("18DAF110 03 41 0D 32"), ProtocolFamily.CAN_29).single()
        assertEquals("18DAF110", m.ecu)
        assertArrayEquals(b(0x41, 0x0D, 0x32), m.data)
    }

    @Test fun legacyDropsHeaderAndChecksum() {
        val m = ObdFrameParser.parse(listOf("48 6B 10 41 0C 1A F8 C4"), ProtocolFamily.LEGACY).single()
        assertEquals("10", m.ecu)
        assertArrayEquals(b(0x41, 0x0C, 0x1A, 0xF8), m.data)
    }

    @Test fun nonHexLinesIgnored() {
        assertTrue(ObdFrameParser.parse(listOf("NO DATA", "SEARCHING...", "OK"), ProtocolFamily.CAN_11).isEmpty())
    }

    @Test fun protocolFamily() {
        assertEquals(ProtocolFamily.CAN_11, ObdFrameParser.familyFromProtocolNumber("A6"))
        assertEquals(ProtocolFamily.CAN_11, ObdFrameParser.familyFromProtocolNumber("6"))
        assertEquals(ProtocolFamily.CAN_29, ObdFrameParser.familyFromProtocolNumber("7"))
        assertEquals(ProtocolFamily.LEGACY, ObdFrameParser.familyFromProtocolNumber("A3"))
    }
}
