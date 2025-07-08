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

package com.tencent.bkrepo.repository.api.cluster

import com.tencent.bkrepo.common.api.constant.REPOSITORY_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.pojo.node.NodeRestoreRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

@Tag(name = "节点集群接口")
@FeignClient(REPOSITORY_SERVICE_NAME, contextId = "ClusterNodeClient")
@RequestMapping("/cluster/node")
interface ClusterNodeClient {

    @Operation(summary = "根据路径查看节点详情")
    @GetMapping("/detail/{projectId}/{repoName}")
    fun getNodeDetail(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String
    ): Response<NodeDetail?>

    @Operation(summary = "创建节点")
    @PostMapping("/create")
    fun createNode(@RequestBody nodeCreateRequest: NodeCreateRequest): Response<NodeDetail>

    @Operation(summary = "更新节点")
    @PostMapping("/update")
    fun updateNode(@RequestBody nodeUpdateRequest: NodeUpdateRequest): Response<Void>

    @Operation(summary = "更新节点访问时间")
    @PostMapping("/update/access/")
    fun updateNodeAccessDate(@RequestBody nodeUpdateAccessDateRequest: NodeUpdateAccessDateRequest): Response<Void>

    @Operation(summary = "重命名节点")
    @PostMapping("/rename")
    fun renameNode(@RequestBody nodeRenameRequest: NodeRenameRequest): Response<Void>

    @Operation(summary = "移动节点")
    @PostMapping("/move")
    fun moveNode(@RequestBody nodeMoveRequest: NodeMoveCopyRequest): Response<Void>

    @Operation(summary = "复制节点")
    @PostMapping("/copy")
    fun copyNode(@RequestBody nodeCopyRequest: NodeMoveCopyRequest): Response<Void>

    @Operation(summary = "删除节点")
    @DeleteMapping("/delete")
    fun deleteNode(@RequestBody nodeDeleteRequest: NodeDeleteRequest): Response<Void>

    @Operation(summary = "删除节点")
    @DeleteMapping("/batch/delete")
    fun deleteNodes(@RequestBody nodesDeleteRequest: NodesDeleteRequest): Response<NodeDeleteResult>

    @Operation(summary = "删除节点")
    @DeleteMapping("/clean/{projectId}/{repoName}/")
    fun deleteNodeLastModifiedBeforeDate(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String,
        @RequestParam date: LocalDateTime,
        @RequestParam operator: String
    ): Response<NodeDeleteResult>

    @Operation(summary = "恢复节点")
    @PostMapping("/restore")
    fun restoreNode(@RequestBody nodeRestoreRequest: NodeRestoreRequest): Response<NodeRestoreResult>
}
