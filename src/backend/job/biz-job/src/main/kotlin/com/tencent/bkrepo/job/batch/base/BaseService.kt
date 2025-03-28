package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.lock.service.LockOperation
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate

open class BaseService(
    private val redisTemplate: RedisTemplate<String, String>,
    private var lockOperation: LockOperation
) {

    fun refreshData(key: String, action: () -> Unit) {
        // 当没有 redis 的场景下需要所有保存在所有机器内存中
        // 有 redis 的场景只允许获取到锁的机器上进行刷下即可
        var lock: Any? = null
        if (redisEnableCheck(key)) {
            lock = getLock(key)
            if (lock == null) {
                logger.info("The other node is refreshing the data.")
                return
            }
        }
        action()
        removeLock(lock, key)
    }

    fun buildRedisKey(keyPrefix: String, key: String): String {
        return keyPrefix + key
    }


    private fun redisEnableCheck(key: String): Boolean {
        return try {
            redisTemplate.opsForValue().get(key)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getLock(key: String): Any? {
        val lock = lockOperation.getLock(key)
        return if (lockOperation.acquireLock(lockKey = key, lock = lock)) {
            logger.info("Lock for key $key has been acquired.")
            lock
        } else {
            null
        }
    }

    private fun removeLock(lock: Any?, key: String) {
        lock?.let {
            lockOperation.close(key, it)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BaseService::class.java)
    }
}