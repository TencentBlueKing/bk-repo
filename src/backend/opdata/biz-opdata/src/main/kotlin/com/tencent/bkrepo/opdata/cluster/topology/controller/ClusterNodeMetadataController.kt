/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.cluster.topology.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ClusterNodeMetadataUpdateRequest
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ClusterNodeMetadataVO
import com.tencent.bkrepo.opdata.cluster.topology.service.ClusterNodeMetadataService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 节点元数据管理接口。
 *
 * 仅作用于长期组网节点；写入到独立扩展集合 cluster_node_extension。
 */
@RestController
@RequestMapping("/api/cluster/topology/metadata")
@Principal(PrincipalType.ADMIN)
class ClusterNodeMetadataController(
    private val metadataService: ClusterNodeMetadataService
) {

    /**
     * 列出所有长期组网节点 + 已补充的元数据。
     */
    @GetMapping
    fun listAll(): Response<List<ClusterNodeMetadataVO>> {
        return ResponseBuilder.success(metadataService.listAll())
    }

    /**
     * 更新指定节点的元数据。所有字段均为可选；传 null 表示清空。
     */
    @PutMapping("/{clusterName}")
    @LogOperate(type = "CLUSTER_NODE_METADATA_UPDATE")
    fun update(
        @PathVariable("clusterName") clusterName: String,
        @RequestBody request: ClusterNodeMetadataUpdateRequest
    ): Response<ClusterNodeMetadataVO> {
        return ResponseBuilder.success(metadataService.update(clusterName, request))
    }
}
