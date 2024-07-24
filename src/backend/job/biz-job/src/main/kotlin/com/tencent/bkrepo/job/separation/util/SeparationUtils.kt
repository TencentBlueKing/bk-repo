/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.separation.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object SeparationUtils {

    private const val NODE_COLLECTION_PREFIX = "node_"

    fun getNodeCollectionName(projectId: String): String {
        return NODE_COLLECTION_PREFIX +
            HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT).toString()
    }

    /**
     * 创建线程池
     */
    fun buildThreadPoolExecutor(namePrefix: String, taskConcurrency: Int): ThreadPoolExecutor {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("$namePrefix-%d").build()
        return ThreadPoolExecutor(
            taskConcurrency, taskConcurrency, 60, TimeUnit.SECONDS,
            ArrayBlockingQueue(1024), namedThreadFactory, ThreadPoolExecutor.CallerRunsPolicy()
        )
    }

    /**
     * 获取对应时间当天的开始以及结束时间
     */
    fun findStartAndEndTimeOfDate(date: LocalDateTime): Pair<LocalDateTime, LocalDateTime> {
        val startOfDate = date.toLocalDate().atStartOfDay()
        val endOfDate = date.toLocalDate().minusDays(-1).atStartOfDay()
        return Pair(startOfDate, endOfDate)
    }
}