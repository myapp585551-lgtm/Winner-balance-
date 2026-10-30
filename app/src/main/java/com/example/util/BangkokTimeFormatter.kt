package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats all dates and times across the application strictly in Thailand's
 * IANA timezone ("Asia/Bangkok", UTC+07:00), independent of the visitor's device timezone.
 */
object BangkokTimeFormatter {
    const val THAILAND_TIMEZONE_ID = "Asia/Bangkok"

    private val bangkokTimeZone: TimeZone
        get() = TimeZone.getTimeZone(THAILAND_TIMEZONE_ID)

    fun formatDateTime(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a 'ICT'", Locale.ENGLISH).apply {
            timeZone = bangkokTimeZone
        }
        return formatter.format(Date(epochMillis))
    }

    fun formatCompactDateTime(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy • HH:mm 'ICT'", Locale.ENGLISH).apply {
            timeZone = bangkokTimeZone
        }
        return formatter.format(Date(epochMillis))
    }

    fun formatLiveHeaderClock(epochMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("EEE, dd MMM • HH:mm:ss 'ICT'", Locale.ENGLISH).apply {
            timeZone = bangkokTimeZone
        }
        return formatter.format(Date(epochMillis))
    }
}
