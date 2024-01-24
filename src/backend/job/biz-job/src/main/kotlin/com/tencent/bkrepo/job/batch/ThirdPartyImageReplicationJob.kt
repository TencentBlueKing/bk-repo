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
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ThirdPartyImageReplicationJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.oci.api.OciClient
import com.tencent.bkrepo.oci.pojo.third.OciReplicationRecordInfo
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * 生成
 */
@Component
@EnableConfigurationProperties(ThirdPartyImageReplicationJobProperties::class)
class ThirdPartyImageReplicationJob(
    private val properties: ThirdPartyImageReplicationJobProperties,
    private val ociClient: OciClient
) : DefaultContextMongoDbJob<ThirdPartyImageReplicationJob.OciReplicationRecordInfoData>(properties) {

    override fun start(): Boolean {
        return super.start()
    }

    override fun entityClass(): KClass<OciReplicationRecordInfoData> {
        return OciReplicationRecordInfoData::class
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query()
    }


    override fun run(row: OciReplicationRecordInfoData, collectionName: String, context: JobContext) {
        with(row) {
            try {
                logger.info(
                    "Preparing to create package for image $packageName with version $packageVersion" +
                        " in repo $projectId|$repoName."
                )
                ociClient.packageCreate(OciReplicationRecordInfo(
                    projectId = projectId,
                    repoName = repoName,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    manifestPath = manifestPath,
                ))
            } catch (e: Exception) {
                throw JobExecuteException(
                    "Failed to send request for image version image $packageName with version $packageVersion " +
                        "in repo $projectId|$repoName", e
                )
            }
        }
    }

    data class OciReplicationRecordInfoData(private val map: Map<String, Any?>) {
        val projectId: String by map
        val repoName: String by map
        val packageName: String by map
        val packageVersion: String by map
        val manifestPath: String by map
    }

    override fun mapToEntity(row: Map<String, Any?>): OciReplicationRecordInfoData {
        return OciReplicationRecordInfoData(row)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val COLLECTION_NAME = "oci_replication_record"
    }
}
