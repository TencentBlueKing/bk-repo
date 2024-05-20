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

package com.tencent.bkrepo.job.batch.task.bkbase

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.stream.constant.BinderType
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.RepoDetailReport2BkbaseJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import com.tencent.bkrepo.job.pojo.project.TRepoMetrics
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/**
 * 上报仓库详情到bkbase
 */
@Component
@EnableConfigurationProperties(RepoDetailReport2BkbaseJobProperties::class)
class RepoDetailReport2BkbaseJob(
    val properties: RepoDetailReport2BkbaseJobProperties,
    val messageSupplier: MessageSupplier
) : DefaultContextMongoDbJob<TProjectMetrics>(properties) {
    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME_PROJECT_METRICS)
    }

    override fun buildQuery(): Query {
        return Query(
            where(TProjectMetrics::createdDate).`is`(LocalDate.now().atStartOfDay())
        )
    }

    override fun run(row: TProjectMetrics, collectionName: String, context: JobContext) {
        val repoDetails = calculateRepoStorage(row)
        repoDetails.forEach {
            messageSupplier.delegateToSupplier(it, topic = TOPIC, binderType = BinderType.KAFKA)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): TProjectMetrics {
        return TProjectMetrics(
            projectId = row[TProjectMetrics::projectId.name].toString(),
            nodeNum = row[TProjectMetrics::nodeNum.name].toString().toLongOrNull() ?: 0,
            capSize = row[TProjectMetrics::capSize.name].toString().toLongOrNull() ?: 0,
            repoMetrics = row[TProjectMetrics::repoMetrics.name]?.toJsonString()
                ?.readJsonString<List<TRepoMetrics>>() ?: emptyList(),
            createdDate = TimeUtils.parseMongoDateTimeStr(row[TProjectMetrics::createdDate.name].toString()),
            active = row[TProjectMetrics::active.name].toString().toBoolean(),
            projectStatus = row[TProjectMetrics::projectStatus.name]?.toString()?.toBoolean()
        )
    }

    override fun entityClass(): KClass<TProjectMetrics> {
        return TProjectMetrics::class
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    private fun calculateRepoStorage(
        current: TProjectMetrics
    ): List<RepoDetail> {
        val result = mutableListOf<RepoDetail>()
        val reportDate = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        current.repoMetrics.forEach {
            try {
                val query = Query(
                    where(RepoInfo::projectId).isEqualTo(current.projectId)
                        .and(RepoInfo::name.name).isEqualTo(it.repoName)
                )
                val repo = mongoTemplate.find(query, RepoInfo::class.java, COLLECTION_NAME_REPOSITORY).firstOrNull()
                    ?: throw RepoNotFoundException("${current.projectId}/${it.repoName}")
                result.add(
                    RepoDetail(
                        projectId = current.projectId,
                        repoName = repo.name,
                        repoUsage = it.size,
                        nodeNum = it.num,
                        repoType = it.type,
                        createdDate = repo.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                        createdBy = repo.createdBy,
                        quota = repo.quota,
                        reportDate = reportDate
                    )
                )
            } catch (e: RepoNotFoundException) {
                logger.warn("repo ${current.projectId}|${it.repoName} not exist")
            }
        }
        return result
    }

    data class RepoDetail(
        var projectId: String,
        var repoName: String,
        var repoUsage: Long,
        var nodeNum: Long,
        var repoType: String,
        var createdDate: String,
        var createdBy: String,
        var cleanupStrategy: String? = null,
        var quota: Long? = null,
        var reportDate: LocalDateTime,
    )

    data class RepoInfo(
        var projectId: String,
        var name: String,
        var createdDate: LocalDateTime,
        var createdBy: String,
        var quota: Long? = null
    )

    companion object {
        private val logger = LoggerFactory.getLogger(RepoDetailReport2BkbaseJob::class.java)
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
        private const val COLLECTION_NAME_REPOSITORY = "repository"
        private const val TOPIC = "bkbase-bkrepo-repo-detail"
    }
}
