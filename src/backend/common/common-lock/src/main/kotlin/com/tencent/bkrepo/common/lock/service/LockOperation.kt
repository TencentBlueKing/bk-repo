/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface LockOperation {

    /**
     * 获取锁信息
     */
    fun getLock(lockKey: String): Any

    /**
     * 自旋获取锁
     */

    fun getSpinLock(
        lockKey: String,
        lock: Any,
        retryTimes: Int = RETRY_TIMES,
        sleepTime: Long = SPIN_SLEEP_TIME
    ): Boolean {
        logger.info("Will start to get lock to do some operations.")
        // 自旋获取锁
        for (i in 0 until retryTimes) {
            when (acquireLock(lockKey, lock)) {
                true -> return true
                else ->
                    try {
                        Thread.sleep(sleepTime)
                    } catch (ignore: InterruptedException) {
                    }
            }
        }
        logger.info("Could not get lock after $retryTimes times...")
        return false
    }

    /**
     * 获取锁
     */
    fun acquireLock(lockKey: String, lock: Any): Boolean

    /**
     * 释放锁
     */
    fun close(lockKey: String, lock: Any)

    /**
     * 获取锁并执行[action]，返回[action]的执行结果，获取锁失败时抛出异常
     */
    fun <T> doWithLock(lockKey: String, action: () -> T): T {
        val lock = getLock(lockKey)
        var locked = false
        try {
            locked = getSpinLock(lockKey, lock, Int.MAX_VALUE)
            logger.info("get lock[$lockKey]: $locked")
            if (locked) {
                return action()
            }
        } finally {
            if (locked) {
                close(lockKey, lock)
            }
        }
        logger.error("get lock[$lockKey] failed")
        throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(LockOperation::class.java)
        /**
         * 定义Redis过期时间
         */
        const val SPIN_SLEEP_TIME: Long = 30L
        const val RETRY_TIMES: Int = 10000
    }
}
