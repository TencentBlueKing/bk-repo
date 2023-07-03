/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.helm.api.HelmClient
import com.tencent.bkrepo.job.CATEGORY
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultRepoJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.RepoRefreshJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.oci.api.OciClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

/**
 * 用于remote类型或者composite类型仓库定时从远程代理刷新信息
 */
@Component
@EnableConfigurationProperties(RepoRefreshJobProperties::class)
class RepoRefreshJob(
    private val properties: RepoRefreshJobProperties,
    private val helmClient: HelmClient,
    private val ociClient: OciClient
) : DefaultRepoJob(properties) {

    private val types: List<String>
        get() = properties.repositorytypes

    private val categories: List<String>
        get() = properties.repositoryCategories

    override fun start(): Boolean {
        return super.start()
    }

    override fun buildQuery(): Query {
        return Query(
            Criteria.where(TYPE).`in`(properties.repositorytypes)
                .and(CATEGORY).`in`(properties.repositoryCategories)
        )
    }

    override fun run(row: ProxyRepoData, collectionName: String, context: JobContext) {
        try {
            val config = row.configuration.readJsonString<RepositoryConfiguration>()
            if (!checkConfigType(config)) return
            logger.info("Refresh request will be sent in repo ${row.projectId}|${row.name}")
            when (row.type) {
                RepositoryType.HELM.name -> {
                    helmClient.refreshIndexYamlAndPackage(row.projectId, row.name)
                }
                RepositoryType.OCI.name, RepositoryType.DOCKER.name -> {
                    ociClient.getPackagesFromThirdPartyRepo(row.projectId, row.name)
                }
                else -> throw UnsupportedOperationException()
            }
        } catch (e: Exception) {
            throw JobExecuteException("Failed to send refresh request for repo ${row.projectId}|${row.name}.", e)
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
