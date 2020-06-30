package com.tencent.bkrepo.common.quartz.cluster

class Scheduler(
    val name: String,
    val instanceId: String,
    val lastCheckinTime: Long,
    val checkinInterval: Long
) {

    /**
     * Return true if scheduler is defunct for given time.
     * @param time    time to compare with
     * @return
     */
    fun isDefunct(time: Long): Boolean {
        return expectedCheckinTime() < time
    }

    private fun expectedCheckinTime(): Long {
        return lastCheckinTime + checkinInterval + TIME_EPSILON
    }

    companion object {
        const val TIME_EPSILON = 7500L
    }
}
