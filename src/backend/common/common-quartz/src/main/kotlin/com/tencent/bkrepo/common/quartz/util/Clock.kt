package com.tencent.bkrepo.common.quartz.util

import java.util.Date

abstract class Clock {
    /**
     * Return current time in millis.
     */
    abstract fun millis(): Long

    /**
     * Return current Date.
     */
    abstract fun now(): Date

    companion object {
        /**
         * Default implementation that returns system time.
         */
        val SYSTEM_CLOCK: Clock = object : Clock() {
            override fun millis(): Long {
                return System.currentTimeMillis()
            }

            override fun now(): Date {
                return Date()
            }
        }
    }
}
