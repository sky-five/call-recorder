package app.callrecorder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordRulesTest {
    private val everyone = RecordRules.Rules(AppSettings.RecordOnly.EVERYONE, internetCalls = true, minSeconds = 0)
    private fun call(sec: Long = 60, internet: Boolean = false, excluded: Boolean = false, contact: Boolean = true) =
        RecordRules.Call(sec, internet, excluded, contact)

    @Test fun keepsByDefault() = assertNull(RecordRules.dropReason(call(), everyone))

    @Test fun dropsExcludedFirst() =
        assertEquals("on the don't-record list", RecordRules.dropReason(call(excluded = true), everyone))

    @Test fun internetToggle() {
        assertNull(RecordRules.dropReason(call(internet = true), everyone))
        assertEquals("internet calls are off", RecordRules.dropReason(call(internet = true), everyone.copy(internetCalls = false)))
    }

    @Test fun minimumLength() {
        val r = everyone.copy(minSeconds = 10)
        assertEquals("shorter than 10s", RecordRules.dropReason(call(sec = 4), r))
        assertNull(RecordRules.dropReason(call(sec = 10), r))
    }

    @Test fun contactsOnlyAndUnknownOnly() {
        val contacts = everyone.copy(recordOnly = AppSettings.RecordOnly.CONTACTS)
        val unknown = everyone.copy(recordOnly = AppSettings.RecordOnly.UNKNOWN)
        assertEquals("not a contact", RecordRules.dropReason(call(contact = false), contacts))
        assertNull(RecordRules.dropReason(call(contact = true), contacts))
        assertEquals("is a contact", RecordRules.dropReason(call(contact = true), unknown))
        assertNull(RecordRules.dropReason(call(contact = false), unknown))
    }

    @Test fun numberKeyMatchesAcrossFormats() {
        assertEquals(AppSettings.key("+1 (555) 010-2030"), AppSettings.key("5550102030"))
    }
}
