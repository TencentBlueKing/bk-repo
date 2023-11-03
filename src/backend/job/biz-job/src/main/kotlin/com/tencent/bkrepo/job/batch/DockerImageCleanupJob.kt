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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.DockerImageCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.oci.api.OciClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 根据仓库配置的清理策略清理镜像仓库下的镜像
 */
@Component
@EnableConfigurationProperties(DockerImageCleanupJobProperties::class)
class DockerImageCleanupJob(
    private val properties: DockerImageCleanupJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val ociClient: OciClient
) : DefaultContextMongoDbJob<DockerImageCleanupJob.RepoData>(properties) {


    override fun entityClass(): Class<RepoData> {
        return RepoData::class.java
    }

    override fun collectionNames(): List<String> {
        return listOf(REPOSITORY_COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query(
            Criteria.where(TYPE).`in`(properties.repositoryTypes)
        )
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)


    override fun run(row: RepoData, collectionName: String, context: JobContext) {
        try {
            val config = row.configuration.readJsonString<RepositoryConfiguration>()
            val cleanupStrategy = config.getStringSetting(CLEAN_UP_STRATEGY)
                ?.readJsonString<CleanupStrategy>() ?: return
            if (properties.projectList.isNotEmpty() && !properties.projectList.contains(row.projectId)) return
            deletePackages(
                projectId = row.projectId,
                repoName = row.name,
                cleanupStrategy = cleanupStrategy
            )
        } catch (e: Exception) {
            throw JobExecuteException(
                "Failed to send  cleanup docker repository for " +
                    "repo ${row.projectId}|${row.name}, error: ${e.message}", e
            )
        }
    }



    private fun deletePackages(projectId: String, repoName: String, cleanupStrategy: CleanupStrategy) {
        val packageQuery = Query(Criteria(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(repoName))
        val packageList = mongoTemplate.find<PackageData>(
            packageQuery, PACKAGE_COLLECTION_NAME
        )
        if (packageList.isEmpty()) return
        packageList.forEach { pData ->
            val versionQuery = Query(Criteria(PACKAGE_ID).isEqualTo(pData.id))
            val versionList = mongoTemplate.find<PackageVersionData>(
                versionQuery, PACKAGE_VERSION_NAME
            )
            if (versionList.isEmpty()) return
            doPackageVersionCleanup(
                projectId = projectId,
                repoName = repoName,
                cleanupStrategy = cleanupStrategy,
                packageName = pData.name,
                versionList = versionList
            )
        }
    }


    private fun doPackageVersionCleanup(
        projectId: String, repoName: String,
        cleanupStrategy: CleanupStrategy, packageName: String,
        versionList: List<PackageVersionData>) {
        when (cleanupStrategy.cleanupType) {
            // 只保留距离当前时间天数以内的镜像
            CleanupStrategyEnum.RETENTION_DAYS.value -> {
                val cleanupDate = LocalDateTime.now().minusDays(cleanupStrategy.cleanupValue.toLong())
                versionList.forEach {
                    if (cleanupDate.isAfter(it.lastModifiedDate)) {
                        ociClient.deleteVersion(projectId, repoName, packageName, it.name)
                    }
                }
            }
            // 只保留设置的时间之后的镜像
            CleanupStrategyEnum.RETENTION_DATE.value -> {
                val cleanupDate = LocalDateTime.parse(cleanupStrategy.cleanupValue, DateTimeFormatter.ISO_DATE_TIME)
                versionList.forEach {
                    if (cleanupDate.isAfter(it.lastModifiedDate)) {
                        ociClient.deleteVersion(projectId, repoName, packageName, it.name)
                    }
                }
            }
            // 只保留设置个数的镜像，根据修改时间排序
            CleanupStrategyEnum.RETENTION_NUMS.value -> {
                if (versionList.size > cleanupStrategy.cleanupValue.toLong()) {
                    versionList.sortedByDescending { it.lastModifiedDate }
                        .subList(cleanupStrategy.cleanupValue.toInt(), versionList.size).forEach {
                        ociClient.deleteVersion(projectId, repoName, packageName, it.name)
                    }
                }
            }
            else -> return
        }
    }

    enum class CleanupStrategyEnum(val value: String) {
        RETENTION_DAYS("retentionDays"),
        RETENTION_DATE("retentionDate"),
        RETENTION_NUMS("retentionNums"),
    }


    data class PackageData(
        val id: String,
        val repoName: String,
        val projectId: String,
        val name: String,
        val key: String,
        val type: String
    )

    data class PackageVersionData(
        val name: String,
        val lastModifiedDate: LocalDateTime
    )

    data class RepoData(private val map: Map<String, Any?>) {
        val name: String by map
        val projectId: String by map
        val type: String by map
        val configuration: String by map
    }

    data class CleanupStrategy(
        val cleanupType: String,
        val cleanupValue: String
    )

    override fun mapToEntity(row: Map<String, Any?>): RepoData {
        return RepoData(row)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val REPOSITORY_COLLECTION_NAME = "repository"
        private const val PACKAGE_COLLECTION_NAME = "package"
        private const val PACKAGE_VERSION_NAME = "package_version"
        private const val PACKAGE_ID = "packageId"
        private const val CLEAN_UP_STRATEGY = "cleanupStrategy"
    }
}
