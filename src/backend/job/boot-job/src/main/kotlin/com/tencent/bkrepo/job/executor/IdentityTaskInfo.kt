package com.tencent.bkrepo.job.executor

import com.google.common.util.concurrent.RateLimiter
import com.tencent.bkrepo.common.api.util.HumanReadable
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * 任务信息
 * */
@Suppress("UnstableApiUsage")
data class IdentityTaskInfo(
    val id: String,
    @Volatile
    var complete: Boolean = false,
    var count: AtomicLong = AtomicLong(),
    var doneCount: AtomicLong = AtomicLong(),
    val monitor: ReentrantLock = ReentrantLock(),
    val flag: Condition = monitor.newCondition(),
    val permitsPerSecond: Double
) {
    private var rateLimiter: RateLimiter? = null
    var waitTime: AtomicLong = AtomicLong()
    var executeTime: AtomicLong = AtomicLong()

    init {
        if (permitsPerSecond > 0) {
            rateLimiter = RateLimiter.create(permitsPerSecond)
        }
    }

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

    fun acquire() {
        rateLimiter?.acquire()
    }

    override fun toString(): String {
        var avgWaitTime = 0L
        var avgExecuteTime = 0L
        if (doneCount.get() > 0) {
            avgWaitTime = waitTime.get() / doneCount.get()
            avgExecuteTime = executeTime.get() / doneCount.get()
        }
        return "Task[$id] state: complete[$complete]," +
            "remain[$count],done[$doneCount],tps[$permitsPerSecond]," +
            "avgWaitTime ${HumanReadable.time(Duration.ofMillis(avgWaitTime).toNanos())}," +
            "avgExecuteTime ${HumanReadable.time(Duration.ofMillis(avgExecuteTime).toNanos())}"
    }
}
