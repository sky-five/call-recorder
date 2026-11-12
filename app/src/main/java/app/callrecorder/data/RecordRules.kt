package app.callrecorder.data

/**
 * Decides, once a call has ended and we know who it was, whether to keep the recording.
 * (Android doesn't reveal the number when a call starts, so rules are applied right after it ends
 * and an unwanted recording is deleted immediately — it never appears in the list.)
 */
object RecordRules {
    data class Call(val seconds: Long, val internet: Boolean, val excluded: Boolean, val isContact: Boolean)
    data class Rules(val recordOnly: AppSettings.RecordOnly, val internetCalls: Boolean, val minSeconds: Int)

    /** Returns why the recording should be dropped, or null to keep it. */
    fun dropReason(c: Call, r: Rules): String? = when {
        c.excluded -> "on the don't-record list"
        c.internet && !r.internetCalls -> "internet calls are off"
        r.minSeconds > 0 && c.seconds < r.minSeconds -> "shorter than ${r.minSeconds}s"
        r.recordOnly == AppSettings.RecordOnly.CONTACTS && !c.isContact -> "not a contact"
        r.recordOnly == AppSettings.RecordOnly.UNKNOWN && c.isContact -> "is a contact"
        else -> null
    }
}
