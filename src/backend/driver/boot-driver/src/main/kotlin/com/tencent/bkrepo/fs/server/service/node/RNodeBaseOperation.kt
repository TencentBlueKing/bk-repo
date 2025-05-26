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
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.fs.server.service.node

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.NodeInfo
import com.tencent.bkrepo.common.metadata.pojo.node.NodeListOption
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUpdateAccessDateRequest

/**
 * 节点CRUD基本操作接口
 */
interface RNodeBaseOperation {

    /**
     * 查询节点详情
     */
    suspend fun getNodeDetail(artifact: ArtifactInfo, repoType: String? = null): NodeDetail?

    /**
     * 分页查询节点
     */
    suspend fun listNodePage(artifact: ArtifactInfo, option: NodeListOption): Page<NodeInfo>

    /**
     * 判断节点是否存在
     */
    suspend fun checkExist(artifact: ArtifactInfo): Boolean

    /**
     * 创建节点，返回节点详情
     */
    suspend fun createNode(createRequest: NodeCreateRequest): NodeDetail

    /**
     * 创建链接节点，返回节点详情
     */
    suspend fun link(request: NodeLinkRequest): NodeDetail

    /**
     * 更新节点访问时间
     */
    suspend fun updateNodeAccessDate(updateAccessDateRequest: NodeUpdateAccessDateRequest)
}
