package com.bmwcockpit.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidDecoderTest {
    private fun b(vararg x: Int) = ByteArray(x.size) { x[it].toByte() }

    @Test fun rpm() = assertEquals(1726f, PidDecoder.decode(Pid.RPM, b(0x41, 0x0C, 0x1A, 0xF8))!!, 0.01f)
    @Test fun speed() = assertEquals(50f, PidDecoder.decode(Pid.SPEED, b(0x41, 0x0D, 0x32))!!, 0f)
    @Test fun coolant() = assertEquals(90f, PidDecoder.decode(Pid.COOLANT, b(0x41, 0x05, 130))!!, 0f)
    @Test fun iatNegative() = assertEquals(-10f, PidDecoder.decode(Pid.IAT, b(0x41, 0x0F, 30))!!, 0f)
    @Test fun map() = assertEquals(245f, PidDecoder.decode(Pid.MAP, b(0x41, 0x0B, 245))!!, 0f)
    @Test fun maf() = assertEquals(185.5f, PidDecoder.decode(Pid.MAF, b(0x41, 0x10, 0x48, 0x76))!!, 0.01f)
    @Test fun load() = assertEquals(100f, PidDecoder.decode(Pid.ENGINE_LOAD, b(0x41, 0x04, 255))!!, 0.01f)
    @Test fun throttle() = assertEquals(50.2f, PidDecoder.decode(Pid.THROTTLE, b(0x41, 0x11, 128))!!, 0.1f)
    @Test fun moduleVoltage() = assertEquals(14.2f, PidDecoder.decode(Pid.MODULE_VOLTAGE, b(0x41, 0x42, 0x37, 0x78))!!, 0.001f)
    @Test fun lambda() = assertEquals(1.0f, PidDecoder.decode(Pid.LAMBDA, b(0x41, 0x44, 0x80, 0x00))!!, 0.001f)
    @Test fun oilTemp() = assertEquals(105f, PidDecoder.decode(Pid.OIL_TEMP, b(0x41, 0x5C, 145))!!, 0f)

    @Test fun incompletePayloadIsNull() = assertNull(PidDecoder.decode(Pid.RPM, b(0x41, 0x0C, 0x1A)))
    @Test fun wrongPidIsNull() = assertNull(PidDecoder.decode(Pid.RPM, b(0x41, 0x0D, 0x1A, 0xF8)))
    @Test fun wrongServiceIsNull() = assertNull(PidDecoder.decode(Pid.SPEED, b(0x7F, 0x01, 0x12)))

    @Test fun supportedBitmap() {
        // BE 3F A8 13 – classic example
        val s = PidDecoder.supportedPids(0x00, b(0x41, 0x00, 0xBE, 0x3F, 0xA8, 0x13))!!
        assertTrue(0x01 in s); assertTrue(0x0C in s); assertTrue(0x0D in s); assertTrue(0x20 in s)
        assertTrue(0x02 !in s)
        assertEquals(setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20), s)
    }

    @Test fun supportedBitmapRejectsOtherBase() = assertNull(PidDecoder.supportedPids(0x20, b(0x41, 0x00, 0xBE, 0x3F, 0xA8, 0x13)))

    @Test fun monitorStatus() {
        assertEquals(Pair(true, 2), PidDecoder.monitorStatus(b(0x41, 0x01, 0x82, 0x07, 0xE5, 0x00)))
        assertEquals(Pair(false, 0), PidDecoder.monitorStatus(b(0x41, 0x01, 0x00, 0x07, 0xE5, 0x00)))
    }

    @Test fun atrv() {
        assertEquals(12.6f, PidDecoder.adapterVoltage(listOf("12.6V"))!!, 0.001f)
        assertEquals(14.1f, PidDecoder.adapterVoltage(listOf("14,1 V"))!!, 0.001f)
        assertNull(PidDecoder.adapterVoltage(listOf("?")))
    }
}
