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

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ArtifactPushJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.replication.api.ArtifactPushClient
import com.tencent.bkrepo.replication.pojo.remote.request.ArtifactPushRequest
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 用于将新上传/更新的制品推送到远端仓库
 */
@Component
@EnableConfigurationProperties(ArtifactPushJobProperties::class)
class ArtifactPushJob(
    private val properties: ArtifactPushJobProperties,
    private val artifactPushClient: ArtifactPushClient
) : DefaultContextMongoDbJob<ArtifactPushJob.PackageVersionData>(properties) {
    private val types: List<String>
        get() = properties.repositoryTypes

    override fun start(): Boolean {
        return super.start()
    }

    override fun entityClass(): KClass<PackageVersionData> {
        return PackageVersionData::class
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        val fromDate = LocalDateTime.now().minusMinutes(1)
        return Query(
            Criteria.where(LAST_MODIFIED_DATE).gt(fromDate)
        )
    }

    override fun run(row: PackageVersionData, collectionName: String, context: JobContext) {
        with(row) {
            try {
                val result = mongoTemplate.find<Map<String, Any?>>(
                    Query(Criteria(ID).isEqualTo(row.packageId).and(TYPE).`in`(properties.repositoryTypes)),
                    PACKAGE_NAME
                )
                if (result.isEmpty()) return
                val packageInfo = mapToPackage(result[0])
                val event = buildArtifactPushRequest(row, packageInfo)
                logger.info(
                    "Preparing to send artifact ${event.packageName}|${event.packageVersion} push event" +
                        " in repo ${event.projectId}|${event.repoName}."
                )
                artifactPushClient.artifactPush(event)
            } catch (e: Exception) {
                throw JobExecuteException(
                    "Failed to send push request for artifact version $packageId|$name ${e.message}", e
                )
            }
        }
    }

    private fun buildArtifactPushRequest(
        versionInfo: PackageVersionData,
        packageInfo: PackageData
    ): ArtifactPushRequest {
        return ArtifactPushRequest(
            projectId = packageInfo.projectId,
            repoName = packageInfo.repoName,
            packageKey = packageInfo.key,
            packageName = packageInfo.name,
            packageType = packageInfo.type,
            packageVersion = versionInfo.name
        )
    }

    private fun mapToPackage(row: Map<String, Any?>): PackageData {
        return PackageData(row)
    }

    data class PackageVersionData(private val map: Map<String, Any?>) {
        val packageId: String by map
        val name: String by map
    }

    data class PackageData(private val map: Map<String, Any?>) {
        val repoName: String by map
        val projectId: String by map
        val name: String by map
        val key: String by map
        val type: String by map
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val COLLECTION_NAME = "package_version"
        private const val PACKAGE_NAME = "package"
    }

    override fun mapToEntity(row: Map<String, Any?>): PackageVersionData {
        return PackageVersionData(row)
    }
}
