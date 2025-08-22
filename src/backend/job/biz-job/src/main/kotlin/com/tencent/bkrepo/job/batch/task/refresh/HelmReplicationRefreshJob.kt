/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.refresh

import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.helm.api.HelmClient
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.HelmReplicationRefreshJobProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 当没有事件的场景下， 同步helm仓库后用于index文件刷新
 */
@Component
class HelmReplicationRefreshJob(
    private val properties: HelmReplicationRefreshJobProperties,
    private val helmClient: HelmClient
) : DefaultContextMongoDbJob<HelmReplicationRefreshJob.Package>(properties) {


    override fun entityClass(): KClass<Package> {
        return Package::class
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        val fromDate = LocalDateTime.now().minusMinutes(1)
        return Query(
            Criteria.where(TYPE).`is`(RepositoryType.HELM.name)
                .and(LAST_MODIFIED_DATE).gte(fromDate)
        )
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun run(row: Package, collectionName: String, context: JobContext) {
        with(row) {
            try {
                val query = Query(Criteria(PACKAGE_ID).isEqualTo(id).and(NAME).isEqualTo(latest))
                val versionData = mongoTemplate.findOne(
                    query, PackageVersionData::class.java, PACKAGE_VERSION_NAME
                ) ?: return
                if (!filterReplicationVersion(versionData)) return
                logger.info(
                    "Preparing to send $row replication event " +
                        "for repo ${row.projectId}|${row.repoName}."
                )
                helmClient.refreshIndexForReplication(
                    projectId = projectId,
                    repoName = repoName,
                    packageKey = key,
                    packageName = row.name,
                    packageVersion = versionData.name
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to send $row replication event " +
                        "for repo ${row.projectId}|${row.repoName}, error: ${e.message}", e
                )
            }
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): Package {
        return Package(row)
    }

    private fun filterReplicationVersion(versionData: PackageVersionData): Boolean {
        with(versionData) {
            if (metadata.isEmpty()) return false
            val sourceType = metadata.firstOrNull { it[METADATA_KEY] == SOURCE_TYPE }
                ?.get(METADATA_VALUE) as? String ?: return false
            return sourceType == ArtifactChannel.REPLICATION.name
        }
    }

    data class Package(
        val id: String,
        var projectId: String,
        var repoName: String,
        var key: String,
        var name: String,
        var latest: String?,
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Package::id.name].toString(),
            map[Package::projectId.name].toString(),
            map[Package::repoName.name].toString(),
            map[Package::key.name].toString(),
            map[Package::name.name].toString(),
            map[Package::latest.name]?.toString(),
        )
    }

    data class PackageVersionData(
        var name: String,
        val metadata: List<Map<String, Any>>,
    )

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME = "package"
        private const val PACKAGE_VERSION_NAME = "package_version"
        private const val PACKAGE_ID = "packageId"
        private const val METADATA_KEY = "key"
        private const val METADATA_VALUE = "value"
    }
}
