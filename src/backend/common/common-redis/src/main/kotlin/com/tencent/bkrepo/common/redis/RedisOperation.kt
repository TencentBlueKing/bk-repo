package com.tencent.bkrepo.common.redis

import java.util.concurrent.TimeUnit
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.RedisTemplate

/**
 * deng
 * 2019-04-19
 */
class RedisOperation(private val redisTemplate: RedisTemplate<String, String>) {

    // max expire time is 30 days
    private val maxExpireTime = TimeUnit.DAYS.toSeconds(30)

    fun get(key: String): String? {
        return redisTemplate.opsForValue().get(key)
    }

    fun getAndSet(key: String, defaultValue: String, expiredInSecond: Long? = null): String? {
        val value = redisTemplate.opsForValue().getAndSet(key, defaultValue)
        if (value == null) {
            redisTemplate.expire(key, expiredInSecond ?: maxExpireTime, TimeUnit.SECONDS)
        }
        return value
    }

    fun set(key: String, value: String, expiredInSecond: Long? = null, expired: Boolean? = true) {
        return if (expired == false) {
            redisTemplate.opsForValue().set(key, value)
        } else {
            redisTemplate.opsForValue().set(key, value, expiredInSecond ?: maxExpireTime, TimeUnit.SECONDS)
        }
    }

    fun delete(key: String) {
        redisTemplate.delete(key)
    }

    fun delete(keys: Collection<String>) {
        redisTemplate.delete(keys)
    }

    fun hasKey(key: String): Boolean {
        return redisTemplate.hasKey(key)
    }

    fun keys(pattern: String): Set<String> {
        return redisTemplate.keys(pattern)
    }

    fun addSetValue(key: String, item: String) {
        redisTemplate.opsForSet().add(key, item)
    }

    fun removeSetMember(key: String, item: String) {
        redisTemplate.opsForSet().remove(key, item)
    }

    fun isMember(key: String, item: String): Boolean {
        return redisTemplate.opsForSet().isMember(key, item) ?: false
    }

    fun getSetMembers(key: String): Set<String>? {
        return redisTemplate.opsForSet().members(key)
    }

    /**
     * @param key key
     * @param hashKey hash key
     * @param values values
     */
    fun hset(key: String, hashKey: String, values: String) {
        redisTemplate.opsForHash<String, String>().put(key, hashKey, values)
    }

    fun hget(key: String, hashKey: String): String? {
        return redisTemplate.opsForHash<String, String>().get(key, hashKey)
    }

    fun hdelete(key: String, hashKey: String) {
        redisTemplate.opsForHash<String, String>().delete(key, hashKey)
    }

    fun hhaskey(key: String, hashKey: String): Boolean {
        return redisTemplate.opsForHash<String, String>().hasKey(key, hashKey)
    }

    fun hsize(key: String): Long {
        return redisTemplate.opsForHash<String, String>().size(key)
    }

    fun hvalues(key: String): MutableList<String>? {
        return redisTemplate.opsForHash<String, String>().values(key)
    }

    fun <T> execute(action: RedisCallback<T>): T? {
        return redisTemplate.execute(action)
    }
}
