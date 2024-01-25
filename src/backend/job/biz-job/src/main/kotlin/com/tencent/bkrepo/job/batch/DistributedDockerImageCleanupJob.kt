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

import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.constant.SCAN_STATUS
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.DistributedDockerImageCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.oci.api.OciClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清理镜像仓库下存储的已经分发的镜像
 */
@Component
@EnableConfigurationProperties(DistributedDockerImageCleanupJobProperties::class)
class DistributedDockerImageCleanupJob(
    private val properties: DistributedDockerImageCleanupJobProperties,
    private val ociClient: OciClient
) : DefaultContextMongoDbJob<DistributedDockerImageCleanupJob.PackageData>(properties) {


    override fun entityClass(): KClass<PackageData> {
        return PackageData::class
    }

    override fun collectionNames(): List<String> {
        return listOf(PACKAGE_COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query(
            Criteria.where(TYPE).`in`(properties.repositoryTypes).
                and(REPO_NAME).isEqualTo(DISTRIBUTION_IMAGE_REPO)
        )
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(3)


    override fun run(row: PackageData, collectionName: String, context: JobContext) {
        try {
            val query = Query(Criteria(PACKAGE_ID).isEqualTo(row.id))
            val versionList = mongoTemplate.find<PackageVersionData>(
                query, PACKAGE_VERSION_NAME
            )
            if (versionList.isEmpty()) return
            for (versionInfo in versionList) {
                if (!filterDistributedAndScannedImage(versionInfo)) continue
                logger.info(
                    "Preparing to send image delete request for package ${row.name}|${versionInfo.name}" +
                        " in repo ${row.projectId}|${row.repoName}."
                )
                ociClient.deleteVersion(row.projectId, row.repoName, row.name, versionInfo.name)
            }
        } catch (e: Exception) {
            throw JobExecuteException(
                "Failed to send  image delete request for package ${row.name}" +
                    " in repo ${row.projectId}|${row.repoName}, error: ${e.message}", e
            )
        }
    }


    /**
     * 如果未开启扫描， 标记为分发并且分发已经结束的镜像直接删除
     * 如果开启扫描， 删除已扫描完的被标记为分发已结束的镜像
     */
    private fun filterDistributedAndScannedImage(versionData: PackageVersionData): Boolean {
        with(versionData) {
            // 避免误删，只删除最后更新时间是前一天的镜像
            if (versionData.lastModifiedDate.isAfter(LocalDateTime.now().minusDays(properties.keepDays))) return false
            if (metadata.isEmpty()) return false
            val enableDistribution = metadata.firstOrNull { it[METADATA_KEY] == DISTRIBUTION_METADATA_KEY }
                ?.get(METADATA_VALUE) as? Boolean ?: return false
            // 如果未标记为开启镜像分发，则保留
            if (!enableDistribution)  return false
            val enableImageScan =  metadata.firstOrNull { it[METADATA_KEY] == IMAGE_SCAN_METADATA_KEY }
                ?.get(METADATA_VALUE) as? Boolean
            val scanStatus =  metadata.firstOrNull { it[METADATA_KEY] == SCAN_STATUS }
                ?.get(METADATA_VALUE) as? String?
            val distributionStatus = metadata.firstOrNull { it[METADATA_KEY] == DISTRIBUTION_STATUS_METADATA_KEY }
                ?.get(METADATA_VALUE) as? String?
            // 镜像分发未结束，不进行删除
            if (distributionStatus.isNullOrEmpty() || distributionStatus != DISTRIBUTION_FINISH_STATUS) return false
            // 不开启镜像扫描，删除
            if ((enableImageScan == null || !enableImageScan)) return true
            if (!scanStatus.isNullOrEmpty()  && scanStatus !in SCAN_RUNNING_STATUS) return true
            return false
        }
    }

    data class PackageData(private val map: Map<String, Any?>) {
        val id: String by map
        val repoName: String by map
        val projectId: String by map
        val name: String by map
        val key: String by map
        val type: String by map
    }

    data class PackageVersionData(
        val name: String,
        val metadata: List<Map<String, Any>>,
        val lastModifiedDate: LocalDateTime
    )



    override fun mapToEntity(row: Map<String, Any?>): PackageData {
        return PackageData(row)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val PACKAGE_COLLECTION_NAME = "package"
        private const val PACKAGE_VERSION_NAME = "package_version"
        private const val DISTRIBUTION_METADATA_KEY = "enableDistribution"
        private const val IMAGE_SCAN_METADATA_KEY = "enableImageScan"
        private const val DISTRIBUTION_STATUS_METADATA_KEY = "distributionStatus"
        private const val METADATA_KEY = "key"
        private const val METADATA_VALUE = "value"
        private const val PACKAGE_ID = "packageId"
        private val SCAN_RUNNING_STATUS = listOf("INIT", "RUNNING")
        private const val DISTRIBUTION_FINISH_STATUS = "finished"
        private const val DISTRIBUTION_IMAGE_REPO = "image"

    }
}