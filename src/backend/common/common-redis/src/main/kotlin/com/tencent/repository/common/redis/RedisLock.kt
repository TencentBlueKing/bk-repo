package com.tencent.repository.common.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStringCommands
import org.springframework.data.redis.connection.ReturnType
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.types.Expiration
import java.util.*

class RedisLock(
        private val redisOperation: RedisOperation,
        private val lockKey: String,
        private val lockValue: String = UUID.randomUUID().toString(),
        private val expiredTimeInSeconds: Long = defaultLockExpired
) : AutoCloseable {
    companion object {

        private val logger = LoggerFactory.getLogger(RedisLock::class.java)

        private const val defaultLockExpired = 30L

        private const val UNLOCK_LUA =
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
    }

    /**
     * 一直等待到获取到锁
     *
     */
    fun lock() {
        while (true) {
            // 如果存在且value一致，则返回true
            val redisValue = redisOperation.get(lockKey)
            if (redisValue != null && redisValue == lockValue) {
                return
            }
            logger.info("Start to lock($lockKey) of value($lockValue) for $expiredTimeInSeconds sec")
            val result = set(lockKey, lockValue, expiredTimeInSeconds)
            logger.info("Get the lock result($result)")
            if (result) {
                return
            }
            Thread.sleep(100)
        }
    }

    /**
     * 尝试获取锁 立即返回
     *
     * @return 是否成功获得锁
     */
    fun tryLock(): Boolean {
        // 如果存在且value一致，则返回true
        val redisValue = redisOperation.get(lockKey)
        if (redisValue != null && redisValue == lockValue) {
            return true
        }
        // 不存在则添加 且设置过期时间（单位ms）
        logger.info("Start to lock($lockKey) of value($lockValue) for $expiredTimeInSeconds sec")
        val result = set(lockKey, lockValue, expiredTimeInSeconds)
        logger.info("Get the lock result($result)")
        return result
    }

    /**
     * redis 加锁操作
     *
     * @param key 锁的Key
     * @param value 锁里面的值
     * @param seconds 过去时间（秒）
     * @return
     */
    private fun set(key: String, value: String, seconds: Long): Boolean {
        return redisOperation.execute(RedisCallback { connection ->
            connection.set(key.toByteArray(), value.toByteArray(), Expiration.seconds(seconds), RedisStringCommands.SetOption.ifPresent())
        }) ?: false
    }

    /**
     * 解锁
     * <p>
     * 可以通过以下修改，让这个锁实现更健壮：
     * <p>
     * 不使用固定的字符串作为键的值，而是设置一个不可猜测（non-guessable）的长随机字符串，作为口令串（token）。
     * 不使用 DEL 命令来释放锁，而是发送一个 Lua 脚本，这个脚本只在客户端传入的值和键的口令串相匹配时，才对键进行删除。
     * 这两个改动可以防止持有过期锁的客户端误删现有锁的情况出现。
     */
    fun unlock(): Boolean {
        // 只有加锁成功并且锁还有效才去释放锁
        // 如果存在且value一致，则返回true
        val redisValue = redisOperation.get(lockKey)
        var result = false
        if (redisValue == null) {
            logger.info("It's already unlock")
            return true
        }
        if (redisValue == lockValue) {
            logger.info("It's owner lock.")
            result = true
        }
        if (redisValue != lockValue) {
            logger.info("It's not allowed to unlock")
            return false
        }
        if (result) {
            result = redisOperation.execute(RedisCallback { connection ->
                val queryResult = connection.eval<Int>(UNLOCK_LUA.toByteArray(), ReturnType.INTEGER, 1, lockKey.toByteArray())
                queryResult == 1
            }) ?: false
        }
        return result
    }

    override fun close() {
        unlock()
    }
}
