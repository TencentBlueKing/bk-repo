package com.tencent.bkrepo.job.executor

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * 任务信息
 * */
data class IdentityTaskInfo(
    @Volatile
    var complete: Boolean = false,
    var count: AtomicLong = AtomicLong(),
    var doneCount: AtomicLong = AtomicLong(),
    val monitor: ReentrantLock = ReentrantLock(),
    val flag: Condition = monitor.newCondition()
) {
    fun await(duration: Duration): Boolean {
        try {
            monitor.lock()
            return flag.await(duration.seconds, TimeUnit.SECONDS)
        } finally {
            monitor.unlock()
        }
    }

    fun signalAll() {
        try {
            monitor.lock()
            flag.signalAll()
        } finally {
            monitor.unlock()
        }
    }
}
