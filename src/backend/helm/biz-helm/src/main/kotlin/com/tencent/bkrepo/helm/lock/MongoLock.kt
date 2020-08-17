package com.tencent.bkrepo.helm.lock

import com.tencent.bkrepo.helm.dao.MongoLockDao
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MongoLock {

    @Autowired
    private lateinit var mongoLockDao: MongoLockDao

    fun tryLock(lockKey: String, lockValue: String): Boolean {
        // 如果存在且value一致，则返回true
        val mongoValue = mongoLockDao.getByKey(lockKey, lockValue)
        if (mongoValue != null && mongoValue.requestId == lockValue) {
            return true
        }
        // 不存在则添加 且设置过期时间（单位ms）
        logger.info("Start to lock [$lockKey] of value[$lockValue] for $expiredTimeInSeconds sec")
        val result = mongoLockDao.incrByWithExpire(lockKey, lockValue, expiredTimeInSeconds)
        logger.info("Get the lock [$lockKey] result($result)")
        return result
    }

    /**
     * 释放锁
     * @param lockKey
     * @param lockValue
     */
    fun releaseLock(lockKey: String, lockValue: String): Boolean {
        val mongoValue = mongoLockDao.getByKey(lockKey, lockValue)
        var result = false
        if (mongoValue == null) {
            logger.info("the lock key ($lockKey) already unlock")
            return true
        }
        if (mongoValue.requestId == lockValue) {
            logger.info("It's owen unlock")
            result = true
        }
        if (mongoValue.requestId != lockValue) {
            logger.info("the lock ($lockKey) is not allowed to unlock")
            return false
        }
        if (result) {
            result = mongoLockDao.releaseLock(lockKey, lockValue)
            logger.info("release lock ($lockKey) success!")
        }
        return result
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MongoLock::class.java)
        const val expiredTimeInSeconds = 30L
    }
}
