package app.callrecorder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FileNamesTest {
    private val tz = ZoneId.of("America/Toronto")
    private fun t(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, tz).toInstant().toEpochMilli()

    @Test fun appStyle() {
        val i = FileNames.parse("phone_20261111-180119__15550102030.m4a", zone = tz)
        assertEquals(Source.IMPORTED, i.source)
        assertEquals("15550102030", i.number)
        assertNull(i.name)
        assertEquals(t(2026, 11, 11, 18, 1, 19), i.startedAt)
        assertEquals("phone", i.app)
    }

    @Test fun appStyleWhatsAppWithName() {
        val i = FileNames.parse("whatsapp_20261111-193733_Jane Doe_.m4a", zone = tz)
        assertEquals(Source.IMPORTED, i.source)
        assertEquals("Jane Doe", i.name)
        assertEquals("whatsapp", i.app)
    }

    @Test fun samsungNumber() {
        val i = FileNames.parse("Call +15550104040_261105_191703.m4a", zone = tz)
        assertEquals(Source.SAMSUNG, i.source)
        assertEquals("+15550104040", i.number)
        assertEquals(t(2026, 11, 5, 19, 17, 3), i.startedAt)
    }

    @Test fun samsungLabel() {
        val i = FileNames.parse("Call Suspected Spam_261031_145851.m4a", zone = tz)
        assertEquals("Suspected Spam", i.name)
        assertNull(i.number)
    }

    @Test fun ownNamed() {
        val i = FileNames.parse("CallRec_Jane Doe_261111_193703.m4a", zone = tz)
        assertEquals(Source.OWN, i.source)
        assertEquals("Jane Doe", i.name)
        assertEquals(t(2026, 11, 11, 19, 37, 3), i.startedAt)
    }

    @Test fun ownOldName() {
        val i = FileNames.parse("CallRec_261111_193354.m4a", zone = tz)
        assertEquals(Source.OWN, i.source)
        assertNull(i.name); assertNull(i.number)
    }

    @Test fun unknown() {
        assertEquals(Source.OTHER, FileNames.parse("random voice memo.m4a", zone = tz).source)
        assertEquals(Source.IMPORTED, FileNames.parse("odd.m4a", hasSidecar = true, zone = tz).source)
    }

    @Test fun readableRoundTrip() {
        val at = t(2026, 11, 11, 20, 21, 0)
        val name = FileNames.readable("Jane Doe", at, tz)
        assertEquals("Jane Doe – 2026-11-11 8.21 PM.m4a", name)
        val i = FileNames.parse(name, zone = tz)
        assertEquals(Source.OWN, i.source)
        assertEquals("Jane Doe", i.name)
        assertEquals(at, i.startedAt)
    }

    @Test fun readableNumberAndInternet() {
        val at = t(2026, 1, 5, 9, 3, 0)
        assertEquals("+1 555-010-2030 – 2026-01-05 9.03 AM.m4a", FileNames.readable("+1 555-010-2030", at, tz))
        assertEquals("+1 555-010-2030", FileNames.parse("+1 555-010-2030 – 2026-01-05 9.03 AM.m4a", zone = tz).number)
        val net = FileNames.parse("Internet call – 2026-01-05 9.03 AM (1).m4a", zone = tz)
        assertEquals("internet", net.app); assertEquals(null, net.name)
        assertEquals("Bad name – 2026-01-05 9.03 AM.m4a", FileNames.readable("Bad/ name:", at, tz))
    }

    @Test fun whatsAppName() {
        val at = t(2026, 11, 11, 19, 37, 0)
        val name = FileNames.readable("Jane Doe", at, tz, app = "WhatsApp")
        assertEquals("Jane Doe (WhatsApp) – 2026-11-11 7.37 PM.m4a", name)
        val i = FileNames.parse(name, zone = tz)
        assertEquals("Jane Doe", i.name); assertEquals("whatsapp", i.app); assertEquals(at, i.startedAt)
        assertEquals("Ameen (Tech)", FileNames.parse("Ameen (Tech) – 2026-11-11 7.37 PM.m4a", zone = tz).name)
    }
}
