/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.ratelimiter.stream

import com.tencent.bkrepo.common.artifact.stream.DelegateInputStream
import com.tencent.bkrepo.common.ratelimiter.service.evict.DownloadEvictRateLimiterService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream

/**
 * 可驱逐的下载流包装器
 *
 * 每次 read 时通过 [DownloadEvictRateLimiterService.findEvictRule] 动态查找驱逐规则，
 * 规则由框架定时刷新机制维护，支持热更新，无需重启。
 *
 * ResourceLimit 字段含义：
 *   limit    — 最大存活秒数，超过则强制驱逐
 *   capacity — 最小保障秒数（可选），在此时间内不驱逐，默认 0
 *
 * 驱逐时抛出 IOException，Tomcat 关闭连接后 afterCompletion 自动归还计数器。
 */
class EvictableInputStream(
    delegate: InputStream,
    private val evictContext: EvictContext,
    private val evictService: DownloadEvictRateLimiterService,
) : DelegateInputStream(delegate) {

    private val startTime = System.currentTimeMillis()

    override fun read(): Int {
        checkEvict()
        return super.read()
    }

    override fun read(byteArray: ByteArray): Int {
        checkEvict()
        return super.read(byteArray)
    }

    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        checkEvict()
        return super.read(byteArray, off, len)
    }

    private fun checkEvict() {
        val resLimitInfo = evictService.findEvictRule(
            userId = evictContext.userId,
            clientIp = evictContext.clientIp,
            projectId = evictContext.projectId,
            repoName = evictContext.repoName,
        ) ?: return

        val aliveSeconds = (System.currentTimeMillis() - startTime) / 1000
        val minGuaranteeSeconds = resLimitInfo.resourceLimit.capacity

        // 最小保障期内不驱逐
        if (minGuaranteeSeconds != null && aliveSeconds <= minGuaranteeSeconds) return

        if (aliveSeconds > resLimitInfo.resourceLimit.limit) {
            val msg = "timeout(${aliveSeconds}s > ${resLimitInfo.resourceLimit.limit}s)"
            logger.info("Evicting connection: ${evictContext.desc()}, reason=$msg, rule=${resLimitInfo.resource}")
            throw IOException("Connection evicted: ${evictContext.desc()}, $msg")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EvictableInputStream::class.java)
    }
}

/**
 * 驱逐上下文，持有请求时的身份信息（只在流创建时采集一次，规则动态读取）
 */
data class EvictContext(
    val userId: String,
    val clientIp: String,
    val projectId: String?,
    val repoName: String?,
) {
    fun desc() = "user=$userId, ip=$clientIp, project=$projectId, repo=$repoName"
}
