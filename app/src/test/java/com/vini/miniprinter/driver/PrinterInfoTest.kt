package com.vini.miniprinter.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterInfoTest {

    @Test
    fun parsesYhkReply() {
        val info = PrinterInfo.parse("HV=H1.0,SV=V1.01,VOLT=7980mv,DPI=384,\u0000")!!
        assertEquals("H1.0", info.hardware)
        assertEquals("V1.01", info.firmware)
        assertEquals(7980, info.millivolts)
        assertEquals(384, info.dots)
        assertEquals(75, info.batteryPercent)
    }

    @Test
    fun percentClampsAtEnds() {
        assertEquals(100, PrinterInfo.percentFor(8500))
        assertEquals(0, PrinterInfo.percentFor(6000))
    }

    @Test
    fun garbageIsNull() {
        assertNull(PrinterInfo.parse("\u0000"))
    }
}
