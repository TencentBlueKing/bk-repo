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

package com.tencent.bkrepo.common.operate.service.service

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.RateLimiter
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.operate.api.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.operate.api.pojo.ProjectUsageStatistics
import com.tencent.bkrepo.common.operate.api.pojo.ProjectUsageStatisticsListOption
import com.tencent.bkrepo.common.operate.service.config.ProjectUsageStatisticsProperties
import com.tencent.bkrepo.common.operate.service.dao.ProjectUsageStatisticsDao
import com.tencent.bkrepo.common.operate.service.model.TProjectUsageStatistics
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder
import javax.annotation.PreDestroy

open class ProjectUsageStatisticsServiceImpl(
    private val properties: ProjectUsageStatisticsProperties,
    private val projectUsageStatisticsDao: ProjectUsageStatisticsDao,
) : ProjectUsageStatisticsService {

    /**
     * 对flush操作进行限流，避免造成数据库高负载
     */
    private val rateLimiter = RateLimiter.create(properties.flushRateLimit)

    private val executor = ThreadPoolExecutor(
        4,
        8,
        60,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(10000),
        ThreadFactoryBuilder().setNameFormat("update-project-statistics-%d").build(),
        RejectedExecutionHandler { r, e ->
            if (!e.isShutdown) {
                logger.warn("caller runs update project statistics")
                r.run()
            }
        }
    )

    private val cache: LoadingCache<String, ProjectUsageStatisticsAdder> = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .concurrencyLevel(1)
        .expireAfterWrite(30L, TimeUnit.MINUTES)
        .removalListener<String, ProjectUsageStatisticsAdder> {
            executor.execute(
                Runnable {
                    rateLimiter.acquire()
                    synchronized(it.value!!) {
                        flush(it.key!!, it.value!!)
                    }
                }.trace()
            )
        }
        .build(CacheLoader.from { _ -> ProjectUsageStatisticsAdder() })

    override fun inc(projectId: String, reqCount: Long, receivedBytes: Long, responseBytes: Long) {
        if (!properties.enabled) {
            return
        }

        // 不统计指定前缀的项目
        properties.ignoredProjectIdPrefix.forEach {
            if (projectId.startsWith(it)) {
                return
            }
        }

        while (true) {
            val adder = cache.get(projectId)
            var added = false
            // 加锁避免inc与flush操作同一个adder对象导致丢失部分计数
            synchronized(adder) {
                if (!adder.flushed) {
                    adder.reqCount.add(reqCount)
                    adder.receivedBytes.add(receivedBytes)
                    adder.responseBytes.add(responseBytes)
                    added = true
                }
            }
            if (added) {
                break
            }
            logger.warn("adder of project[$projectId] was flushed, try to get new adder")
        }
    }

    override fun page(options: ProjectUsageStatisticsListOption): Page<ProjectUsageStatistics> {
        val page = projectUsageStatisticsDao.find(options)
        return Pages.buildPage(page.records.map { it.convert() }, page.pageNumber, page.pageSize)
    }

    override fun delete(start: Long?, end: Long) {
        projectUsageStatisticsDao.delete(start, end)
    }

    @PreDestroy
    open fun destroy() {
        if (!properties.enabled) {
            return
        }

        logger.info("${cache.size()} project will update usage statistics")
        cache.invalidateAll()
        executor.shutdown()
        val waitMinutes = 3L
        if (executor.awaitTermination(waitMinutes, TimeUnit.MINUTES)) {
            logger.info("${cache.size()} project update usage statistics finished")
        } else {
            logger.error(
                "cache flush executor termination timeout after $waitMinutes minutes, " +
                        "cache size[${cache.size()}, " +
                        "executor active[${executor.activeCount}, queue[${executor.queue.size}]]]"
            )
        }
    }

    @Scheduled(cron = "0 55 23 * * ?")
    open fun flush() {
        if (properties.enabled) {
            logger.info("try to flush all projects usage statistics")
            cache.invalidateAll()
        }
    }

    private fun flush(projectId: String, adder: ProjectUsageStatisticsAdder) {
        val start = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        projectUsageStatisticsDao.inc(
            projectId,
            adder.reqCount.toLong(),
            adder.receivedBytes.toLong(),
            adder.responseBytes.toLong(),
            start
        )
        adder.flushed = true
    }

    private fun TProjectUsageStatistics.convert(): ProjectUsageStatistics = ProjectUsageStatistics(
        projectId = projectId,
        reqCount = reqCount,
        receiveBytes = receiveBytes,
        responseBytes = responseByte,
    )

    private data class ProjectUsageStatisticsAdder(
        val reqCount: LongAdder = LongAdder(),
        val receivedBytes: LongAdder = LongAdder(),
        val responseBytes: LongAdder = LongAdder(),
        /**
         * 是否已经写入缓存
         */
        @Volatile
        var flushed: Boolean = false
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectUsageStatisticsServiceImpl::class.java)
    }
}
