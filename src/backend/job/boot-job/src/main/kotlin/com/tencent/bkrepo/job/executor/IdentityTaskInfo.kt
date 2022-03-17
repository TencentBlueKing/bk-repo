package com.tencent.bkrepo.job.executor

import com.google.common.util.concurrent.RateLimiter
import com.tencent.bkrepo.common.api.util.HumanReadable
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    // 目前任务数
    var count: AtomicInteger = AtomicInteger(),
    // 已做任务数
    var doneCount: AtomicInteger = AtomicInteger(),
    val monitor: ReentrantLock = ReentrantLock(),
    val flag: Condition = monitor.newCondition(),
    val permitsPerSecond: Double
) {
    private var rateLimiter: RateLimiter? = null
    var waitTime: AtomicInteger = AtomicInteger()
    var executeTime: AtomicInteger = AtomicInteger()

    init {
        if (permitsPerSecond > 0) {
            rateLimiter = RateLimiter.create(permitsPerSecond)
        }
    }

    /**
     * 等待
     * */
    fun await(duration: Duration): Boolean {
        try {
            monitor.lock()
            return flag.await(duration.seconds, TimeUnit.SECONDS)
        } finally {
            monitor.unlock()
        }
    }

    /**
     * 通知所有等待线程
     * */
    fun signalAll() {
        try {
            monitor.lock()
            flag.signalAll()
        } finally {
            monitor.unlock()
        }
    }

    /**
     * 获取许可证
     * */
    fun acquire() {
        rateLimiter?.acquire()
    }

    /**
     * 平均任务等待时间
     * */
    fun avgWaitTime(): Int {
        if (doneCount.get() <= 0) {
            return 0
        }
        return waitTime.get() / doneCount.get()
    }

    /**
     * 平均任务执行时长
     * */
    fun avgExecuteTime(): Int {
        if (doneCount.get() <= 0) {
            return 0
        }
        return executeTime.get() / doneCount.get()
    }

    override fun toString(): String {
        val avgWaitTime = Duration.ofMillis(avgWaitTime().toLong()).toNanos()
        val avgExecuteTime = Duration.ofMillis(avgExecuteTime().toLong()).toNanos()
        return "Task[$id] state: complete[$complete]," +
            "remain[$count],done[$doneCount],tps[$permitsPerSecond]," +
            "avgWaitTime ${HumanReadable.time(avgWaitTime)}," +
            "avgExecuteTime ${HumanReadable.time(avgExecuteTime)}"
    }
}
