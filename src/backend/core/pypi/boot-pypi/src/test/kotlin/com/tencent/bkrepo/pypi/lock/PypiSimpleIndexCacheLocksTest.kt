package com.tencent.bkrepo.pypi.lock

import com.tencent.bkrepo.common.lock.service.LockOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PypiSimpleIndexCacheLocksTest {

    private lateinit var lockOperation: FakeLockOperation
    private lateinit var cacheLocks: PypiSimpleIndexCacheLocks

    @BeforeEach
    fun setup() {
        lockOperation = FakeLockOperation()
        cacheLocks = PypiSimpleIndexCacheLocks(
            lockOperation = lockOperation,
            readRetryTimes = 2,
            writeRetryTimes = 2,
            spinSleepTimeMs = 1L,
        )
    }

    @Test
    fun withReadLockReturnsBlockResultWhenLockAcquired() {
        lockOperation.acquireResults = listOf(true)

        val result = cacheLocks.withReadLock(PROJECT_ID, REPO_NAME, FULL_PATH) { "html" }

        assertEquals("html", result)
        assertEquals(listOf(EXPECTED_LOCK_KEY), lockOperation.closedKeys)
    }

    @Test
    fun withReadLockReturnsWhileWaitingResultWithoutAcquiringLock() {
        lockOperation.acquireResults = listOf(false, false)

        val result = cacheLocks.withReadLock(
            PROJECT_ID,
            REPO_NAME,
            FULL_PATH,
            whileWaiting = { "cached" },
        ) { error("should not run") }

        assertEquals("cached", result)
        assertTrue(lockOperation.closedKeys.isEmpty())
    }

    @Test
    fun withReadLockReturnsNullWhenLockTimesOut() {
        lockOperation.acquireResults = listOf(false, false)

        val result = cacheLocks.withReadLock(PROJECT_ID, REPO_NAME, FULL_PATH) { "html" }

        assertNull(result)
        assertTrue(lockOperation.closedKeys.isEmpty())
    }

    @Test
    fun withWriteLockReturnsTrueWhenLockAcquired() {
        lockOperation.spinLockResult = true
        var executed = false

        val result = cacheLocks.withWriteLock(PROJECT_ID, REPO_NAME, FULL_PATH) {
            executed = true
        }

        assertTrue(result)
        assertTrue(executed)
        assertEquals(listOf(EXPECTED_LOCK_KEY), lockOperation.closedKeys)
    }

    @Test
    fun withWriteLockReturnsFalseWhenLockTimesOut() {
        lockOperation.spinLockResult = false

        val result = cacheLocks.withWriteLock(PROJECT_ID, REPO_NAME, FULL_PATH) {
            error("should not run")
        }

        assertFalse(result)
        assertTrue(lockOperation.closedKeys.isEmpty())
    }

    @Test
    fun withWriteLockUsesPypiSimpleLockKey() {
        lockOperation.spinLockResult = true

        cacheLocks.withWriteLock(PROJECT_ID, REPO_NAME, FULL_PATH) {}

        assertEquals(EXPECTED_LOCK_KEY, lockOperation.lastSpinLockKey)
    }

    private class FakeLockOperation : LockOperation {
        var acquireResults: List<Boolean> = emptyList()
        var spinLockResult: Boolean = false
        val closedKeys = mutableListOf<String>()
        var lastSpinLockKey: String? = null
        private var acquireIndex = 0

        override fun getLock(lockKey: String): Any = lockKey

        override fun acquireLock(lockKey: String, lock: Any): Boolean {
            return acquireResults.getOrElse(acquireIndex++) { false }
        }

        override fun close(lockKey: String, lock: Any) {
            closedKeys.add(lockKey)
        }

        override fun getSpinLock(lockKey: String, lock: Any, retryTimes: Int, sleepTime: Long): Boolean {
            lastSpinLockKey = lockKey
            return spinLockResult
        }
    }

    companion object {
        private const val PROJECT_ID = "proj"
        private const val REPO_NAME = "pypi-local"
        private const val FULL_PATH = "/.pypi-simple-index/packages/requests.html"
        private const val EXPECTED_LOCK_KEY =
            "pypi:simple:lock:proj/pypi-local/.pypi-simple-index/packages/requests.html"
    }
}
