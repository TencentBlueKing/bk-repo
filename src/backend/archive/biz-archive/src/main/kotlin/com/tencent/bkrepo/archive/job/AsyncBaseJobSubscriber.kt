package com.tencent.bkrepo.archive.job

import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

open class AsyncBaseJobSubscriber<T>(val executor: ThreadPoolExecutor) : BaseJobSubscriber<T>() {

    private val lock: ReentrantLock = ReentrantLock()
    private val executeComplete: Condition = lock.newCondition()
    private val committedTask: AtomicInteger = AtomicInteger(0)
    private val commitComplete: AtomicBoolean = AtomicBoolean(false)

    override fun hookOnNext(value: T) {
        committedTask.incrementAndGet()
        executor.execute {
            try {
                super.hookOnNext(value)
            } finally {
                val committed = committedTask.decrementAndGet()
                if (commitComplete.get() && committed == 0) {
                    lock.lock()
                    try {
                        executeComplete.signal()
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }
    }

    override fun hookOnComplete() {
        commitComplete.set(true)
        if (committedTask.get() != 0) {
            lock.lock()
            try {
                executeComplete.await()
            } finally {
                lock.unlock()
            }
        }
        super.hookOnComplete()
    }
}
