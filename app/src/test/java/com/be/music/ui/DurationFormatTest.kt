package com.be.music.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test
    fun formatDuration_omitsHours_whenDurationIsUnderOneHour() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59_999))
        assertEquals("3:45", formatDuration(225_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun formatDuration_includesHours_whenDurationIsOneHourOrMore() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1:02:33", formatDuration(3_753_000))
        assertEquals("12:30:00", formatDuration(45_000_000))
    }

    @Test
    fun formatDurationSeconds_formatsSecondsTheSameWay() {
        assertEquals("0:00", formatDurationSeconds(0))
        assertEquals("3:45", formatDurationSeconds(225))
        assertEquals("1:02:33", formatDurationSeconds(3_753))
    }

    @Test
    fun formatDurationSeconds_clampsNegativeValues() {
        assertEquals("0:00", formatDurationSeconds(-1))
    }
}
