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
import com.tencent.bkrepo.common.artifact.path.PathUtils.toPath
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PATH
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.config.properties.ArtifactCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.oci.api.OciClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import org.bson.types.ObjectId
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * 根据仓库配置的清理策略清理对应仓库下的制品
 */
@Component
@EnableConfigurationProperties(ArtifactCleanupJobProperties::class)
class ArtifactCleanupJob(
    private val properties: ArtifactCleanupJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val ociClient: OciClient,
    private val nodeClient: NodeClient
) : DefaultContextMongoDbJob<ArtifactCleanupJob.RepoData>(properties) {


    override fun entityClass(): Class<RepoData> {
        return RepoData::class.java
    }

    override fun collectionNames(): List<String> {
        return listOf(REPOSITORY_COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)


    override fun run(row: RepoData, collectionName: String, context: JobContext) {
        try {
            val config = row.configuration.readJsonString<RepositoryConfiguration>()
            val cleanupStrategyMap = config.getSetting<Map<String, Any>>(CLEAN_UP_STRATEGY) ?: return
            val cleanupStrategy = toCleanupStrategy(cleanupStrategyMap) ?: return
            if (!filterConfig(row.projectId, cleanupStrategy)) return
            logger.info("Will clean the artifacts in repo ${row.projectId}|${row.name}")
            when (row.type) {
                RepositoryType.GENERIC.name -> {
                    // 清理generic制品
                    deleteNodes(row.projectId, row.name, cleanupStrategy)
                }
                RepositoryType.DOCKER.name, RepositoryType.OCI.name -> {
                    // 清理镜像制品
                    deletePackages(
                        projectId = row.projectId,
                        repoName = row.name,
                        cleanupStrategy = cleanupStrategy
                    )
                }
                else -> return
            }
        } catch (e: Exception) {
            throw JobExecuteException(
                "Failed to send  cleanup docker repository for " +
                    "repo ${row.projectId}|${row.name}, error: ${e.message}", e
            )
        }
    }

    private fun toCleanupStrategy(map: Map<String, Any>): CleanupStrategy? {
        val cleanupStrategy = CleanupStrategy(
            enable = map[CleanupStrategy::enable.name] as? Boolean ?: false,
            cleanupType = map[CleanupStrategy::cleanupType.name] as? String,
            cleanupValue = map[CleanupStrategy::cleanupValue.name] as? String,
            cleanTargets = map[CleanupStrategy::cleanTargets.name] as? List<String>,
            )
        if (cleanupStrategy.cleanupValue.isNullOrEmpty() || cleanupStrategy.cleanupValue.isNullOrEmpty())
            return null
        return cleanupStrategy
    }

    private fun filterConfig(projectId: String, cleanupStrategy: CleanupStrategy): Boolean {
        if (properties.projectList.isNotEmpty() && !properties.projectList.contains(projectId)) return false
        if (properties.repoList.isNotEmpty() && !properties.repoList.contains(projectId)) return false
        return cleanupStrategy.enable
    }


    private fun deleteNodes(projectId: String, repoName: String, cleanupStrategy: CleanupStrategy) {
        val cleanupDate = when (cleanupStrategy.cleanupType) {
            // 只保留距离当前时间天数以内的制品
            CleanupStrategyEnum.RETENTION_DAYS.value -> {
                LocalDateTime.now().minusDays(cleanupStrategy.cleanupValue!!.toLong())
            }
            // 只保留设置的时间之后的制品
            CleanupStrategyEnum.RETENTION_DATE.value -> {
                LocalDateTime.parse(cleanupStrategy.cleanupValue, DateTimeFormatter.ISO_DATE_TIME)
            }
            else -> return
        }
        doNodeCleanup(projectId, repoName, cleanupDate, cleanupStrategy.cleanTargets)
    }

    private fun doNodeCleanup(
        projectId: String, repoName: String, cleanupDate: LocalDateTime,
        cleanupFolders: List<String>? = null
    ) {
        val pageSize = BATCH_SIZE
        var querySize: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        val nodeCollectionName = COLLECTION_NODE_PREFIX +
            MongoShardingUtils.shardingSequence(projectId, SHARDING_COUNT)

        do {
            val query = Query(
                Criteria.where(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(repoName)
                    .and(FOLDER).isEqualTo(false).and(DELETED_DATE).isEqualTo(null)
                    .and(LAST_MODIFIED_DATE).lt(cleanupDate).and(ID).gt(lastId)
                    .apply {
                        if (!cleanupFolders.isNullOrEmpty()) {
                            this.and(PATH).`in`(cleanupFolders.map { Pattern.compile("^${toPath(it)}") })
                        }
                    }
            ).limit(BATCH_SIZE)
                .with(Sort.by(ID).ascending())

            val data = mongoTemplate.find<NodeData>(
                query,
                nodeCollectionName
            )
            if (data.isEmpty()) {
                break
            }
            nodeClient.deleteNodes(NodesDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPaths = data.map { it.fullPath },
                operator = SYSTEM_USER
            ))
            querySize = data.size
            lastId = ObjectId(data.last().id)
        } while (querySize == pageSize)
    }


    private fun deletePackages(projectId: String, repoName: String, cleanupStrategy: CleanupStrategy) {
        val packageQuery = Query(
            Criteria(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(repoName).apply {
                if (!cleanupStrategy.cleanTargets.isNullOrEmpty()) {
                    this.and(PACKAGE_NAME).`in`(cleanupStrategy.cleanTargets)
                }
            }
        )
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
            // 只保留距离当前时间天数以内的制品
            CleanupStrategyEnum.RETENTION_DAYS.value -> {
                val cleanupDate = LocalDateTime.now().minusDays(cleanupStrategy.cleanupValue!!.toLong())
                versionList.forEach {
                    if (cleanupDate.isAfter(it.lastModifiedDate)) {
                        ociClient.deleteVersion(projectId, repoName, packageName, it.name)
                    }
                }
            }
            // 只保留设置的时间之后的制品
            CleanupStrategyEnum.RETENTION_DATE.value -> {
                val cleanupDate = LocalDateTime.parse(cleanupStrategy.cleanupValue, DateTimeFormatter.ISO_DATE_TIME)
                versionList.forEach {
                    if (cleanupDate.isAfter(it.lastModifiedDate)) {
                        ociClient.deleteVersion(projectId, repoName, packageName, it.name)
                    }
                }
            }
            // 只保留设置个数的制品，根据修改时间排序
            CleanupStrategyEnum.RETENTION_NUMS.value -> {
                if (versionList.size > cleanupStrategy.cleanupValue!!.toInt()) {
                    versionList.sortedByDescending { it.lastModifiedDate }
                        .subList(cleanupStrategy.cleanupValue.toInt(), versionList.size).forEach {
                        ociClient.deleteVersion(projectId, repoName, packageName, it.name)
                    }
                }
            }
            else -> return
        }
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

    data class NodeData(
        val id: String,
        val fullPath: String,
   )

    data class RepoData(private val map: Map<String, Any?>) {
        val name: String by map
        val projectId: String by map
        val type: String by map
        val configuration: String by map
    }

    data class CleanupStrategy(
        // 清理策略类型
        val cleanupType: String? = null,
        // 清理策略类型对应的实际值
        val cleanupValue: String? = null,
        // 指定路径或者package
        val cleanTargets: List<String>? = null,
        // 是否启用
        val enable: Boolean = false
    )


    enum class CleanupStrategyEnum(val value: String) {
        RETENTION_DAYS("retentionDays"),
        RETENTION_DATE("retentionDate"),
        RETENTION_NUMS("retentionNums"),
    }

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
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val PACKAGE_NAME = "name"
        private const val BATCH_SIZE = 1000

    }
}
