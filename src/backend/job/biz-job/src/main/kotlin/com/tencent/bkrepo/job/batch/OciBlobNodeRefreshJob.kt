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

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.OciBlobNodeRefreshJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.oci.api.OciClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

/**
 * 用于将存储在blobs目录下的公共blob节点全部迁移到对应版本目录下，
 * 即从/{packageName}/blobs/xxx 到/{packageName}/blobs/{tag}/xxx
 */
@Component
@EnableConfigurationProperties(OciBlobNodeRefreshJobProperties::class)
class OciBlobNodeRefreshJob(
    private val properties: OciBlobNodeRefreshJobProperties,
    private val ociClient: OciClient
) : DefaultContextMongoDbJob<OciBlobNodeRefreshJob.PackageData>(properties) {
    private val types: List<String>
        get() = properties.repositoryTypes

    override fun start(): Boolean {
        return super.start()
    }

    override fun entityClass(): KClass<PackageData> {
        return PackageData::class
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query(
            Criteria.where(TYPE).`in`(properties.repositoryTypes)
        )
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun run(row: PackageData, collectionName: String, context: JobContext) {
        with(row) {
            try {
                val result = mongoTemplate.find<Map<String, Any?>>(
                    Query(Criteria(PACKAGE_ID).isEqualTo(row.id)),
                    PACKAGE_VERSION_NAME
                )
                if (result.isEmpty()) return
                var refreshStatus = true
                for (map in result) {
                    val version = map[NAME] as String? ?: continue
                    logger.info(
                        "Preparing to send blob refresh request for package ${row.name}|${version}" +
                            " in repo ${row.projectId}|${row.repoName}."
                    )
                    refreshStatus = refreshStatus && ociClient.blobPathRefresh(
                        projectId = row.projectId,
                        repoName = row.repoName,
                        packageName = row.name,
                        version = version
                    ).data ?: false
                }
                if (refreshStatus) {
                    // 当包下版本对应镜像的 blob节点都刷新完成后，删除旧路径下的 blob文件
                    logger.info(
                        "Will delete blobs folder of package ${row.name}" +
                            " in repo ${row.projectId}|${row.repoName}."
                    )
                    ociClient.deleteBlobsFolderAfterRefreshed(projectId, repoName, row.name)
                }
            } catch (e: Exception) {
                throw JobExecuteException(
                    "Failed to send blob refresh request for package ${row.name}" +
                        " in repo ${row.projectId}|${row.repoName}, error: ${e.message}", e
                )
            }
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

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val COLLECTION_NAME = "package"
        private const val PACKAGE_VERSION_NAME = "package_version"
        private const val METADATA_KEY = "blobPathRefreshed"
        private const val METADATA = "metadata"
        private const val PACKAGE_ID = "packageId"
        private const val NAME = "name"

    }

    override fun mapToEntity(row: Map<String, Any?>): PackageData {
        return PackageData(row)
    }
}
