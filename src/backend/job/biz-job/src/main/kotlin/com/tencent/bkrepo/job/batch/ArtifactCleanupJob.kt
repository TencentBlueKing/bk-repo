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

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ArtifactCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeCleanRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/**
 * 根据仓库配置的清理策略清理对应仓库下的制品
 */
@Component
@EnableConfigurationProperties(ArtifactCleanupJobProperties::class)
class ArtifactCleanupJob(
    private val properties: ArtifactCleanupJobProperties,
    private val nodeClient: NodeClient,
    private val discoveryClient: DiscoveryClient,
    private val serviceAuthManager: ServiceAuthManager
    ) : DefaultContextMongoDbJob<ArtifactCleanupJob.RepoData>(properties) {

    private val restTemplate = RestTemplate()

    @Value("\${service.prefix:}")
    private val servicePrefix: String = ""

    override fun entityClass(): KClass<RepoData> {
        return RepoData::class
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
                RepositoryType.DOCKER.name,
                RepositoryType.OCI.name,
                RepositoryType.HELM.name
                -> {
                    // 清理镜像制品
                    deletePackages(
                        projectId = row.projectId,
                        repoName = row.name,
                        cleanupStrategy = cleanupStrategy,
                        repoType = row.type
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
        val folders = if (cleanupFolders.isNullOrEmpty()) {
            listOf(PathUtils.ROOT)
        } else {
            cleanupFolders
        }
        folders.forEach {
            try {
                nodeClient.cleanNodes((NodeCleanRequest(
                    projectId = projectId,
                    repoName = repoName,
                    path = PathUtils.toPath(it),
                    date = cleanupDate,
                    operator = SYSTEM_USER)))
            } catch (e: NullPointerException) {
                logger.warn("Request of clean nodes $it in repo $projectId|$repoName is timeout!")
            }
        }
    }


    private fun deletePackages(
        projectId: String, repoName: String,
        cleanupStrategy: CleanupStrategy,
        repoType: String
    ) {
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
                versionList = versionList,
                repoType = repoType
            )
        }
    }


    private fun doPackageVersionCleanup(
        projectId: String, repoName: String,
        cleanupStrategy: CleanupStrategy, packageName: String,
        versionList: List<PackageVersionData>, repoType: String
    ) {
        when (cleanupStrategy.cleanupType) {
            // 只保留距离当前时间天数以内的制品
            CleanupStrategyEnum.RETENTION_DAYS.value -> {
                val cleanupDate = LocalDateTime.now().minusDays(cleanupStrategy.cleanupValue!!.toLong())
                versionList.forEach {
                    if (cleanupDate.isAfter(it.lastModifiedDate)) {
                        deleteVersion(projectId, repoName, packageName, it.name, repoType)
                    }
                }
            }
            // 只保留设置的时间之后的制品
            CleanupStrategyEnum.RETENTION_DATE.value -> {
                val cleanupDate = LocalDateTime.parse(cleanupStrategy.cleanupValue, DateTimeFormatter.ISO_DATE_TIME)
                versionList.forEach {
                    if (cleanupDate.isAfter(it.lastModifiedDate)) {
                        deleteVersion(projectId, repoName, packageName, it.name, repoType)
                    }
                }
            }
            // 只保留设置个数的制品，根据修改时间排序
            CleanupStrategyEnum.RETENTION_NUMS.value -> {
                if (versionList.size > cleanupStrategy.cleanupValue!!.toInt()) {
                    versionList.sortedByDescending { it.lastModifiedDate }
                        .subList(cleanupStrategy.cleanupValue.toInt(), versionList.size).forEach {
                        deleteVersion(projectId, repoName, packageName, it.name, repoType)
                    }
                }
            }
            else -> return
        }
    }


    private fun deleteVersion(
        projectId: String,
        repoName: String,
        packageName: String,
        version: String,
        repoType: String,
        ) {
        val (urlPath, serviceInstance) = when (repoType) {
            RepositoryType.DOCKER.name, RepositoryType.OCI.name -> {
                val dockerServiceId = buildServiceName(RepositoryType.DOCKER.name.toLowerCase())
                val instance = discoveryClient.getInstances(dockerServiceId).firstOrNull()
                Pair(
                    "/service/third/version/delete/$projectId/$repoName",
                    instance
                )
            }
            RepositoryType.HELM.name -> {
                val dockerServiceId = buildServiceName(RepositoryType.HELM.name.toLowerCase())
                val instance = discoveryClient.getInstances(dockerServiceId).firstOrNull()
                Pair(
                    "/service/index/version/delete/$projectId/$repoName",
                    instance
                )
            }
            else -> {
                logger.warn("Unsupported repository type for artifactCleanupJob!")
                return
            }
        }
        if (serviceInstance == null) {
            logger.warn("Cloud not find serviceInstance for package $packageName in repo $projectId|$repoName")
            return
        }
        val target = serviceInstance.uri
        val url = "$target$urlPath?packageName=$packageName&version=$version"
        try {
            val headers = HttpHeaders()
            headers.add(MS_AUTH_HEADER_SECURITY_TOKEN, serviceAuthManager.getSecurityToken())
            headers.add(MS_AUTH_HEADER_UID, SYSTEM_USER)
            val httpEntity = HttpEntity<Any>(headers)
            val response = restTemplate.exchange(url, HttpMethod.DELETE, httpEntity, Response::class.java)
            if (response.statusCode != HttpStatus.OK) throw RuntimeException("response code is ${response.statusCode}")
        } catch (e: Exception) {
            logger.warn("Request of clean package $packageName in repo $projectId|$repoName error: ${e.message}")
        }
    }

    private fun buildServiceName(name: String): String {
        return "$servicePrefix$name"
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
        private const val PACKAGE_NAME = "name"
    }
}
