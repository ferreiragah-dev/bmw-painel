package com.bmwcockpit.app
import org.junit.Test
import org.junit.Assert.*

class ObdTest {
    @Test fun engineAndMissingValues(){
        val f=Obd.frame(mapOf(12 to listOf(0x41,0xA0),13 to listOf(112),11 to listOf(220),51 to listOf(100),66 to listOf(0x37,0x78)),123)
        assertEquals(4200.0,f[Sensor.RPM].value!!,0.0);assertEquals(1.20,f[Sensor.BOOST].value!!,.001)
        assertEquals(14.2,f[Sensor.VOLTS].value!!,.001);assertEquals(Source.ESTIMATE,f[Sensor.BOOST].source)
        assertNull(f.gear);assertEquals(Source.MISSING,f[Sensor.OIL_P].source)
        assertNull(Obd.frame(mapOf(11 to listOf(180)),1)[Sensor.BOOST].value)
    }
    @Test fun supportMask(){assertEquals(setOf(1,12,32),Obd.supported(0,listOf(65,0,128,16,0,1)))}
    @Test fun extendedCanHeader(){assertEquals(listOf(65,13,96),Obd.replies("18 DA F1 10 03 41 0D 60")["18DAF110"])}
    @Test fun ecuIsolation(){
        val r=Obd.replies("7E8 04 41 0C 41 A0\n7E9 03 41 0D 60")
        assertEquals(listOf(65,12,65,160),r["7E8"]);assertEquals(listOf(65,13,96),r["7E9"])
    }
    @Test fun multiframeAndDtc(){
        val response="7E8 10 0A 43 04 03 02 01 71\n7E8 21 00 01 C0 02 00 00 00"
        assertEquals(listOf("P0302","P0171","P0001","U0002"),Obd.parseDtcs(Obd.replies(response).getValue("7E8")))
        assertTrue(Obd.replies(response.replace("21 00","22 00")).isEmpty())
        assertTrue(Obd.parseDtcs(listOf(67,0)).isEmpty())
    }
    @Test(expected=java.io.IOException::class) fun truncatedIsNotClear(){Obd.parseDtcs(listOf(67,1))}
    @Test fun demoMoves(){val a=demoFrame(0.0,1);val b=demoFrame(4.0,2);assertNotEquals(a[Sensor.SPEED],b[Sensor.SPEED])
        assertTrue(a.values.values.all{it.source==Source.DEMO});assertNotNull(a.gear)}
}
