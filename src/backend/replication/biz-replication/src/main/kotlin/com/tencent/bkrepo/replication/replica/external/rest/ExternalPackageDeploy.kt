/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.external.rest

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.replica.base.ReplicaContext
import com.tencent.bkrepo.replication.replica.external.rest.base.DeployClient
import com.tencent.bkrepo.replication.replica.external.rest.helm.HelmDeployClient
import com.tencent.bkrepo.replication.replica.external.rest.oci.OciDeployClient
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion

/**
 * 包和节点的映射关系
 */
object ExternalPackageDeploy {

    private val clients = mutableMapOf<RepositoryType, DeployClient>()

    init {
        addClient(SpringContextUtils.getBean(HelmDeployClient::class.java))
        addClient(SpringContextUtils.getBean(OciDeployClient::class.java))
    }

    private fun addClient(client: DeployClient) {
        clients[client.type()] = client
        client.extraType()?.let { clients[client.extraType()!!] = client }
    }

    /**
     * @param packageSummary 包信息总览
     * @param packageVersion 版本信息
     * @return 返回
     */
    fun deploy(
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
        context: ReplicaContext
    ): Boolean {
        with(context) {
            val client = clients[localRepoType]
            check(client != null) { "client[$localRepoType] not found" }
            client.deployClient = httpClient
            client.clusterInfo = cluster
            return client.deployArtifact(
                name = packageSummary.name,
                version = packageVersion.name,
                projectId = packageSummary.projectId,
                repoName = packageSummary.repoName
            )
        }
    }
}
