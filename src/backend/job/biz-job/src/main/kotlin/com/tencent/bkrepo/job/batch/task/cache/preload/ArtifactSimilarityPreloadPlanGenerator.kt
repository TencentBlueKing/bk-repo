/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.cache.preload

import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanGenerateParam
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanGenerator
import com.tencent.bkrepo.job.METADATA_KEY_ACCESS_TIMESTAMP
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.AiProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.SearchRequest
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.VectorStore
import java.time.LocalDateTime
import kotlin.random.Random

class ArtifactSimilarityPreloadPlanGenerator(
    private val vectorStore: VectorStore,
    private val aiProperties: AiProperties,
) : ArtifactPreloadPlanGenerator {
    override fun generate(param: ArtifactPreloadPlanGenerateParam): ArtifactPreloadPlan? {
        with(param) {
            val executeTime = calculateExecuteTime(param) ?: return null
            val now = LocalDateTime.now()
            return ArtifactPreloadPlan(
                id = null,
                createdDate = now,
                lastModifiedDate = now,
                strategyId = strategy.id!!,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                sha256 = sha256,
                size = size,
                credentialsKey = credentialsKey,
                executeTime = executeTime
            )
        }
    }

    /**
     * 根据历史访问记录计算预加载时间
     */
    private fun calculateExecuteTime(param: ArtifactPreloadPlanGenerateParam): Long? {
        with(param) {
            // 查询相似路径
            val searchReq = SearchRequest(
                query = "/$projectId/$repoName$fullPath",
                topK = 10,
                similarityThreshold = aiProperties.defaultSimilarityThreshold
            )
            val docs = vectorStore.similaritySearch(searchReq)
            if (docs.isEmpty()) {
                return null
            }

            // 获取相似路径的历史访问时间
            val accessTimestamps = docs
                .flatMap { it.metadata[METADATA_KEY_ACCESS_TIMESTAMP].toString().split(",") }
                .map { it.toLong() }
                .sorted()

            if (accessTimestamps.isNotEmpty()) {
                // TODO 根据历史访问时间预测下次访问时间
                return System.currentTimeMillis() + Random.nextLong(0, RANDOM_SECONDS) * 1000L
            }

            // 没有相似路径时不执行预加载
            return null
        }
    }

    companion object {
        /**
         * 随机时间，避免同时加载过多文件
         */
        private const val RANDOM_SECONDS = 600L
    }
}
