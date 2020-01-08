package com.tencent.bkrepo.common.storage.filesystem.cleanup

/**
 *
 * @author: carrypan
 * @date: 2020/1/6
 */
data class CleanupResult(var count: Long = 0, var size: Long = 0) {
    operator fun plus(increment: CleanupResult): CleanupResult {
        return CleanupResult(count + increment.count, size + increment.size)
    }
}
