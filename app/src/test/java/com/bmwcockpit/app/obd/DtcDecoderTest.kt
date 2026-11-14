package com.bmwcockpit.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcDecoderTest {
    private fun can(vararg lines: String) = ObdFrameParser.parse(lines.toList(), ProtocolFamily.CAN_11)

    @Test fun codeNotation() {
        assertEquals("P0171", DtcDecoder.code(0x01, 0x71))
        assertEquals("P0302", DtcDecoder.code(0x03, 0x02))
        assertEquals("C0035", DtcDecoder.code(0x40, 0x35))
        assertEquals("B1234", DtcDecoder.code(0x92, 0x34))
        assertEquals("U0100", DtcDecoder.code(0xC1, 0x00))
        assertEquals("P2187", DtcDecoder.code(0x21, 0x87))
    }

    @Test fun canTwoCodes() {
        val r = DtcDecoder.decode(0x03, can("7E8 06 43 02 03 02 01 71"), ProtocolFamily.CAN_11, ElmStatus.OK)
        r as DtcResult.Codes
        assertEquals(listOf("P0302", "P0171"), r.codes.map { it.code })
        assertEquals("Falha de ignição, cilindro 2", r.codes[0].description)
    }

    @Test fun canExplicitZeroIsNoCodes() {
        val r = DtcDecoder.decode(0x03, can("7E8 02 43 00"), ProtocolFamily.CAN_11, ElmStatus.OK)
        assertEquals(DtcResult.NoCodes, r)
    }

    @Test fun countMismatchIsIncompleteNotEmpty() {
        // ECU says 3 codes but only one pair arrived
        val r = DtcDecoder.decode(0x03, can("7E8 04 43 03 03 02"), ProtocolFamily.CAN_11, ElmStatus.OK)
        assertTrue(r is DtcResult.Incomplete)
        assertEquals("P0302", (r as DtcResult.Incomplete).partial.single().code)
    }

    @Test fun brokenMultiFrameIsIncomplete() {
        val r = DtcDecoder.decode(0x03, can("7E8 10 0A 43 04 01 71 03 02"), ProtocolFamily.CAN_11, ElmStatus.OK)
        assertTrue(r is DtcResult.Incomplete)
    }

    @Test fun multiFrameFourCodes() {
        val r = DtcDecoder.decode(0x03, can("7E8 10 0A 43 04 01 71 03 02", "7E8 21 04 20 01 33 00 00 00"), ProtocolFamily.CAN_11, ElmStatus.OK)
        assertEquals(listOf("P0171", "P0302", "P0420", "P0133"), (r as DtcResult.Codes).codes.map { it.code })
    }

    @Test fun noDataIsNotAbsence() {
        val r = DtcDecoder.decode(0x03, emptyList(), ProtocolFamily.CAN_11, ElmStatus.NO_DATA)
        assertTrue(r is DtcResult.NoResponse)
    }

    @Test fun timeoutIsNotAbsence() {
        assertTrue(DtcDecoder.decode(0x03, emptyList(), ProtocolFamily.CAN_11, ElmStatus.TIMEOUT) is DtcResult.NoResponse)
    }

    @Test fun negativeResponseIsUnsupported() {
        val r = DtcDecoder.decode(0x0A, can("7E8 03 7F 0A 11"), ProtocolFamily.CAN_11, ElmStatus.OK)
        assertTrue(r is DtcResult.Unsupported)
    }

    @Test fun legacyFramesWithPadding() {
        val msgs = ObdFrameParser.parse(listOf("48 6B 10 43 01 71 03 02 00 00 C4"), ProtocolFamily.LEGACY)
        val r = DtcDecoder.decode(0x03, msgs, ProtocolFamily.LEGACY, ElmStatus.OK)
        assertEquals(listOf("P0171", "P0302"), (r as DtcResult.Codes).codes.map { it.code })
    }

    @Test fun unknownCodeHasNullDescription() {
        val r = DtcDecoder.decode(0x03, can("7E8 04 43 01 2A BC"), ProtocolFamily.CAN_11, ElmStatus.OK) as DtcResult.Codes
        assertEquals(null, r.codes.single().description)
    }
}
