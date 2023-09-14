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

package com.tencent.bkrepo.replication.replica.repository.remote

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.replica.repository.remote.base.PushClient
import com.tencent.bkrepo.replication.replica.repository.remote.type.helm.HelmArtifactPushClient
import com.tencent.bkrepo.replication.replica.repository.remote.type.oci.OciArtifactPushClient
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion

/**
 * 制品和对应仓库推送实现的映射关系
 */
object ArtifactPushMappings {

    private val clients = mutableMapOf<RepositoryType, PushClient>()

    init {
        addClient(SpringContextUtils.getBean(HelmArtifactPushClient::class.java))
        addClient(SpringContextUtils.getBean(OciArtifactPushClient::class.java))
    }

    private fun addClient(client: PushClient) {
        clients[client.type()] = client
        client.extraType()?.let { clients[client.extraType()!!] = client }
    }

    /**
     * @param packageSummary 包信息总览
     * @param packageVersion 版本信息
     * @return 返回 成功与否
     */
    fun push(
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
        context: ReplicaContext
    ): Boolean {
        with(context) {
            val client = clients[localRepoType]
            check(client != null) { "client[$localRepoType] not found" }
            return client.pushArtifact(
                name = packageSummary.name,
                version = packageVersion.name,
                projectId = packageSummary.projectId,
                repoName = packageSummary.repoName,
                context = context
            )
        }
    }
}
