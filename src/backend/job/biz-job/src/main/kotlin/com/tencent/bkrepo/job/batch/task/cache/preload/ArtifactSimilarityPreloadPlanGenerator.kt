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

import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanGenerateParam
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanGenerator
import com.tencent.bkrepo.common.mongo.dao.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.AiProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.EmbeddingModel
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStore
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStoreProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.SearchRequest
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.VectorStore
import io.milvus.client.MilvusClient
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class ArtifactSimilarityPreloadPlanGenerator(
    private val embeddingModel: EmbeddingModel,
    private val milvusClient: MilvusClient,
    private val aiProperties: AiProperties,
    private val preloadProperties: ArtifactPreloadProperties,
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
            val preloadHourOfDay = preloadProperties.preloadHourOfDay.sorted().ifEmpty { return null }

            // 查询相似路径，没有相似路径时不执行预加载
            val projectPath = "/$projectId/$repoName$fullPath"
            val searchReq = SearchRequest(
                query = projectPath,
                topK = 10,
                similarityThreshold = aiProperties.defaultSimilarityThreshold
            )
            val docs = createVectorStore(0L).similaritySearch(searchReq).ifEmpty {
                createVectorStore(1L).similaritySearch(searchReq)
            }
            if (docs.isEmpty()) {
                logger.info("no similarity path found for [$projectPath]")
                return null
            }

            val now = LocalDateTime.now()
            val preloadHour = preloadHourOfDay.firstOrNull { it > now.hour }
                ?: (preloadHourOfDay.first { (it + 24) > now.hour } + 24)
            val preloadTimestamp = now
                // 设置预加载时间
                .plusHours((preloadHour - now.hour).toLong())
                .withMinute(0)
                // 减去随机时间，避免同时多文件触发加载
                .minusSeconds(Random.nextLong(0, preloadProperties.maxRandomSeconds))
                // 转化为毫秒时间戳
                .atZone(ZoneId.systemDefault())
                .toEpochSecond() * 1000
            logger.info(
                "similarity path[${docs.first().content}] found for [$projectPath], will preload on $preloadTimestamp"
            )
            return preloadTimestamp
        }
    }

    private fun createVectorStore(minusMonth: Long): VectorStore {
        // 根据上个月的历史访问数据进行预测
        val seq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(minusMonth), 1)
        val collectionName = "${aiProperties.collectionPrefix}$seq"
        val config = MilvusVectorStoreProperties(
            databaseName = aiProperties.databaseName,
            collectionName = collectionName,
            embeddingDimension = embeddingModel.dimensions(),
        )
        return MilvusVectorStore(config, milvusClient, embeddingModel)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactSimilarityPreloadPlanGenerator::class.java)
    }
}
