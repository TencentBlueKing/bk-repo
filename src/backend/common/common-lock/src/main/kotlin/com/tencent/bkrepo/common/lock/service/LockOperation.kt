/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.lock.service

import com.tencent.bkrepo.common.lock.config.RedisConfigProperties
import com.tencent.bkrepo.common.lock.pojo.LockType
import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 锁服务
 */
class LockOperation(
    private val redisOperation: RedisOperation,
    private val mongoOperation: MongoDistributedLock,
    private val redisProperties: RedisConfigProperties
) {
    /**
     * 判断是否配置了redis
     */
    private fun redisConnectionCheck(): Boolean {
        return (redisProperties.host.isNullOrBlank() && redisProperties.cluster == null)
    }

    /**
     * 自旋获取锁
     */
    private fun getSpinLock(
        lockKey: String,
        redisLock: RedisLock? = null,
        retryTimes: Int = RETRY_TIMES,
        sleepTime: Long = SPIN_SLEEP_TIME
    ): Boolean {
        logger.info("Will start to get lock to do some operations.")
        val type = if (redisLock == null) {
            logger.info("Will use mongodb lock to do some operations.")
            LockType.MONGODB
        } else {
            logger.info("Will use redis lock to do some operations.")
            LockType.REDIS
        }

        // 自旋获取锁
        loop@ for (i in 0 until retryTimes) {
            when (acquireLock(lockKey, type, redisLock)) {
                true -> return true
                else ->
                    try {
                        Thread.sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        continue@loop
                    }
            }
        }
        logger.info("Could not get lock after $retryTimes times...")
        return false
    }

    private fun getLockInfo(lockKey: String): RedisLock? {
        return if (!redisConnectionCheck()) {
            RedisLock(redisOperation, lockKey, EXPIRED_TIME_IN_SECONDS)
        } else {
            null
        }
    }

    private fun acquireLock(key: String, type: LockType, lock: RedisLock? = null): Boolean {
        return when (type) {
            LockType.REDIS -> {
                lock!!.tryLock()
            }
            LockType.MONGODB -> {
                mongoOperation.acquireLock(key, EXPIRED_TIME_IN_SECONDS)
            }
        }
    }

    /**
     * 释放锁
     */
    private fun close(
        lockKey: String,
        redisLock: RedisLock? = null
    ) {
        logger.info("Will start to release the key with index.yaml")
        if (redisLock == null) {
            logger.info("Start to unlock the mongodb key($lockKey)")
            mongoOperation.releaseLock(lockKey)
        } else {
            redisLock.unlock()
        }
    }

    fun <T> lockAction(lockKey: String, action: () -> T): T {
        val lock = getLockInfo(lockKey)
        return if (getSpinLock(lockKey, lock)) {
            logger.info("Lock for key $lockKey has been acquired.")
            try {
                action()
            } finally {
                close(lockKey, lock)
            }
        } else {
            action()
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LockOperation::class.java)
        /**
         * 定义Redis过期时间
         */
        private const val EXPIRED_TIME_IN_SECONDS: Long = 5 * 60 * 1000L
        private const val SPIN_SLEEP_TIME: Long = 30L
        private const val RETRY_TIMES: Int = 10000
    }
}
