package com.lhzkml.jasmine.core.agent

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uniffi.jasmine_ffi.HostClock

/**
 * The one thing the core asks this side for: the clock.
 *
 * Conversations no longer cross the boundary in either direction — the core keeps them in its own
 * session files, under the directory this side hands over when it builds the handle. What is left
 * here is the zone, which is a fact only this side has.
 */
internal object DeviceClock : HostClock {

    override fun now(): String = CLOCK_FORMAT.format(ZonedDateTime.now())

    /**
     * A moment the core already recorded, in the device's zone.
     *
     * The core stores RFC 3339 stamps and prints them verbatim, so the zone has to be applied
     * here — which is exactly why the core asks rather than formats.
     */
    override fun format(timestamp: String): String =
        LIST_FORMAT.format(
            OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault()),
        )
}

/** Matches what the transcript shows next to a conversation in the list. */
private val LIST_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

/** Something like `2026-09-27 10:31:05 GMT+08:00`. */
private val CLOCK_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'GMT'xxx", Locale.US)
