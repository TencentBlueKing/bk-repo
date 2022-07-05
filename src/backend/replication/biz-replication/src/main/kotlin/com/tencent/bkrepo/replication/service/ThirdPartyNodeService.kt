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

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.thirdparty.ThirdPartyInfo
import com.tencent.bkrepo.replication.pojo.thirdparty.request.ThirdPartyConfigUpdateRequest
import com.tencent.bkrepo.replication.pojo.thirdparty.request.ThirdPartyCreateRequest

interface ThirdPartyNodeService {
    /**
     * 根据[requests]创建第三方集群节点，创建成功后返回集群节点信息
     */
    fun thirdPartyCreate(requests: ThirdPartyCreateRequest): List<ClusterNodeInfo>

    /**
     * 根据[request]更新第三方集群节点
     */
    fun thirdPartyUpdate(request: ThirdPartyConfigUpdateRequest)

    /**
     * 根据[projectId] [repoName] [name]根据name以及关联项目仓库信息查询第三方集群详情
     */
    fun getByName(projectId: String, repoName: String, name: String): ThirdPartyInfo?

    /**
     * 根据[projectId] [repoName] [name]切换任务状态
     */
    fun toggleStatus(projectId: String, repoName: String, name: String)

    /**
     * 根据[projectId] [repoName] [name]根据name以及关联项目仓库信息删除第三方集群详情
     */
    fun deleteByName(projectId: String, repoName: String, name: String)
}
