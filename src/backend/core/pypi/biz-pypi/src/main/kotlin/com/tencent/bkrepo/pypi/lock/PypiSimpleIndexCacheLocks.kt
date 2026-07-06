package com.tencent.bkrepo.pypi.lock

import com.tencent.bkrepo.common.lock.service.LockOperation
import org.springframework.stereotype.Component

@Component
class PypiSimpleIndexCacheLocks(
    private val lockOperation: LockOperation,
    private val readRetryTimes: Int = READ_RETRY_TIMES,
    private val writeRetryTimes: Int = WRITE_RETRY_TIMES,
    private val spinSleepTimeMs: Long = SPIN_SLEEP_TIME_MS,
) {

    fun <T> withReadLock(
        projectId: String,
        repoName: String,
        fullPath: String,
        whileWaiting: () -> T? = { null },
        block: () -> T,
    ): T? {
        val lockKey = lockKey(projectId, repoName, fullPath)
        val lock = lockOperation.getLock(lockKey)
        repeat(readRetryTimes) {
            whileWaiting()?.let { return it }
            if (lockOperation.acquireLock(lockKey, lock)) {
                try {
                    return block()
                } finally {
                    lockOperation.close(lockKey, lock)
                }
            }
            try {
                Thread.sleep(spinSleepTimeMs)
            } catch (_: InterruptedException) {
            }
        }
        return whileWaiting()
    }

    fun withWriteLock(
        projectId: String,
        repoName: String,
        fullPath: String,
        block: () -> Unit,
    ): Boolean {
        val lockKey = lockKey(projectId, repoName, fullPath)
        val lock = lockOperation.getLock(lockKey)
        if (!lockOperation.getSpinLock(lockKey, lock, writeRetryTimes, spinSleepTimeMs)) {
            return false
        }
        try {
            block()
            return true
        } finally {
            lockOperation.close(lockKey, lock)
        }
    }

    private fun lockKey(projectId: String, repoName: String, fullPath: String): String =
        "$LOCK_PREFIX$projectId/$repoName$fullPath"

    companion object {
        private const val LOCK_PREFIX = "pypi:simple:lock:"
        private const val READ_RETRY_TIMES = 50
        private const val WRITE_RETRY_TIMES = 25
        private const val SPIN_SLEEP_TIME_MS = 200L
        val READ_LOCK_TIMEOUT_SECONDS = (READ_RETRY_TIMES * SPIN_SLEEP_TIME_MS / 1000).toInt()
    }
}
