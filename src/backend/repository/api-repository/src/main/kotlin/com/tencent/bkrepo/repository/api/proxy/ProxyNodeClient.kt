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

package com.tencent.bkrepo.repository.api.proxy

import com.tencent.bkrepo.common.api.constant.REPOSITORY_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "节点服务接口")
@FeignClient(REPOSITORY_SERVICE_NAME, contextId = "ProxyNodeClient", primary = false)
@RequestMapping("/proxy/node")
interface ProxyNodeClient {

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

    @Operation(summary = "列表查询指定目录下所有节点")
    @GetMapping("/list/{projectId}/{repoName}")
    fun listNode(
        @Parameter(name = "所属项目", required = true)
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称", required = true)
        @PathVariable repoName: String,
        @Parameter(name = "所属目录", required = true)
        @RequestParam path: String,
        @Parameter(name = "是否包含目录", required = false)
        @RequestParam includeFolder: Boolean = true,
        @Parameter(name = "是否深度查询文件", required = false)
        @RequestParam deep: Boolean = false,
        @Parameter(name = "是否包含元数据", required = false)
        @RequestParam includeMetadata: Boolean = false
    ): Response<List<NodeInfo>>

    @Operation(summary = "更新节点访问时间")
    @PostMapping("/update/access/")
    fun updateNodeAccessDate(@RequestBody nodeUpdateAccessDateRequest: NodeUpdateAccessDateRequest): Response<Void>

}
