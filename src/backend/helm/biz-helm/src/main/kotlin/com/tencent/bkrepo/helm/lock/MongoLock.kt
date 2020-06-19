package com.tencent.bkrepo.helm.lock

import com.tencent.bkrepo.helm.dao.MongoLockDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.HashMap

@Component
class MongoLock {

    @Autowired
    private lateinit var mongoLockDao: MongoLockDao

    /**
     * 获得锁
     *
     * @param key
     * @param expire
     * @return
     */
    fun getLock(key: String, expire: Long = 30 * 1000L): Boolean {
        val mongoLocks = mongoLockDao.getByKey(key)
        // 判断该锁是否被获得,锁已经被其他请求获得，直接返回
        if (mongoLocks.isNotEmpty() && mongoLocks[0].expire >= System.currentTimeMillis()) {
            return false
        }
        // 释放过期的锁
        if (mongoLocks.isNotEmpty() && mongoLocks[0].expire < System.currentTimeMillis()) {
            releaseLockExpire(key, System.currentTimeMillis())
        }
        // (在高并发前提下)在当前请求已经获得锁的前提下，还可能有其他请求尝试去获得锁，此时会导致当前锁的过期时间被延长，由于延长时间在毫秒级，可以忽略。
        val mapResult = mongoLockDao.incrByWithExpire(key, 1, System.currentTimeMillis() + expire)
        // 如果结果是1，代表当前请求获得锁
        if (mapResult["value"] as Int == 1) {
            logger.info("Get lock success with key [$key] for [${expire/1000}] sec")
            return true
            // 如果结果>1，表示当前请求在获取锁的过程中，锁已被其他请求获得。
        } else if (mapResult["value"] as Int > 1) {
            logger.info("Get lock failed with key [$key].")
            return false
        }
        return false
    }

    /**
     * 释放锁
     *
     * @param key
     */
    fun releaseLock(key: String) {
        val condition = HashMap<String, Any>()
        condition["key"] = key
        mongoLockDao.remove(condition)
    }

    /**
     * 释放过期锁
     *
     * @param key
     * @param expireTime
     */
    private fun releaseLockExpire(key: String, expireTime: Long) {
        mongoLockDao.removeExpire(key, expireTime)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MongoLock::class.java)
    }
}
