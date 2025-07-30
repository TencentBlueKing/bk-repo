/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest

/**
 * 联邦仓库同步接口
 */
interface FederationRepositoryService {


    /**
     * 创建联邦仓库并分发配置
     */
    fun createFederationRepository(request: FederatedRepositoryCreateRequest): String

    /**
     * 在联邦集群上创建对应集群信息以及任务存储联邦仓库配置
     */
    fun saveFederationRepositoryConfig(request: FederatedRepositoryConfigRequest)

    /**
     * 根据项目id和仓库名称和federationId查询相关联的联邦仓库配置
     */
    fun listFederationRepository(
        projectId: String, repoName: String, federationId: String? = null,
    ): List<FederatedRepositoryInfo>

    /**
     * 删除联邦仓库配置（本地+remote）
     */
    fun deleteFederationRepositoryConfig(projectId: String, repoName: String, federationId: String)

    /**
     * 删除本地联邦仓库配置
     */
    fun deleteLocalFederationRepositoryConfig(projectId: String, repoName: String, federationId: String)

    /**
     * 根据项目仓库获取当前集群名
     */
    fun getCurrentClusterName(projectId: String, repoName: String, federationId: String): String?

    /**
     * 执行联邦仓库fullSync
    */
    fun fullSyncFederationRepository(projectId: String, repoName: String, federationId: String)


}