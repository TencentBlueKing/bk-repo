/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.artifact.remote

import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.redis.RedisOperation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PreDestroy

@Component
class RemoteArtifactCacheLocks(private val redisOperation: RedisOperation) {
    /**
     * 加锁避免同一文件被缓存多次
     */
    private val locks: ConcurrentHashMap<String, RedisLock> = ConcurrentHashMap()

    /**
     * 获取指定制品的锁
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 制品路径
     *
     * @return first 为制品对应的锁， second 为true时表示锁是新创建的 false表示已存在的锁
     */
    fun create(projectId: String, repoName: String, fullPath: String): Pair<RedisLock, Boolean> {
        val lockKey = lockKey(projectId, repoName, fullPath)
        val existedLock = locks[lockKey]
            ?: locks.putIfAbsent(lockKey, RedisLock(redisOperation, lockKey, LOCK_EXPIRED_TIME_IN_SECONDS))
        return Pair(locks[lockKey]!!, existedLock == null)
    }

    /**
     * 需要先调用[create]方法创建锁，再调用[tryLock]，否则此方法永远发挥false
     */
    fun tryLock(projectId: String, repoName: String, fullPath: String): Boolean {
        return locks[lockKey(projectId, repoName, fullPath)]?.tryLock() ?: false
    }

    /**
     * 未取得锁时，仅移除锁
     */
    fun remove(projectId: String, repoName: String, fullPath: String) {
        locks.remove(lockKey(projectId, repoName, fullPath))
    }

    /**
     * 释放锁
     */
    fun release(projectId: String, repoName: String, fullPath: String) {
        locks.remove(lockKey(projectId, repoName, fullPath))?.unlock()
    }

    @PreDestroy
    fun preDestroy() {
        logger.info("[${locks.size}] artifact is caching async, try to release lock before shutdown")
        locks.values.forEach { it.unlock() }
    }

    private fun lockKey(projectId: String, repoName: String, fullPath: String) =
        "remote:cache:lock:$projectId/$repoName$fullPath"

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteArtifactCacheLocks::class.java)
        const val LOCK_EXPIRED_TIME_IN_SECONDS = 24L * 60L * 60L
    }
}
