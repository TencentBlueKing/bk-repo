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

package com.tencent.bkrepo.replication.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeDeleteRequest
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.UserClusterNodeCreateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("集群节点用户接口")
@RestController
@RequestMapping("/api/cluster/node")
@Principal(PrincipalType.ADMIN)
class ClusterNodeController(
    private val clusterNodeService: ClusterNodeService
) {

    @ApiOperation("获取集群主节点")
    @PostMapping("/query/master")
    fun queryMasterName(): Response<String> {
        return ResponseBuilder.success()
    }

    @ApiOperation("创建集群节点")
    @PostMapping("/create")
    @Principal(PrincipalType.ADMIN)
    fun createClusterNode(
        @RequestAttribute userId: String,
        @RequestBody userClusterCreateRequest: UserClusterNodeCreateRequest
    ): Response<Void> {
        val createRequest = with(userClusterCreateRequest) {
            com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeCreateRequest(
                name = name,
                url = url,
                certificate = certificate,
                username = username,
                password = password,
                type = type,
                operator = userId
            )
        }
        clusterNodeService.createClusterNode(createRequest)
        return ResponseBuilder.success()
    }

    @ApiOperation("删除集群节点")
    @DeleteMapping("/delete/{name}")
    @Principal(PrincipalType.ADMIN)
    fun deleteClusterNode(
        @RequestAttribute userId: String,
        @ApiParam(value = "集群节点名称", required = true)
        @PathVariable name: String
    ): Response<Void> {
        clusterNodeService.deleteClusterNode(ClusterNodeDeleteRequest(name, userId))
        return ResponseBuilder.success()
    }

    @ApiOperation("列表查询集群节点")
    @GetMapping("/list")
    fun listClusterNode(
        @RequestAttribute userId: String,
        @ApiParam(value = "集群名称", required = false)
        @RequestParam name: String? = null,
        @ApiParam(value = "集群类型", required = false)
        @RequestParam type: String? = null
    ): Response<List<ClusterNodeInfo>> {
        return ResponseBuilder.success(clusterNodeService.listClusterNode(name, type))
    }

    @ApiOperation("分页查询集群节点")
    @GetMapping("/page/{pageNumber}/{pageSize}")
    fun listClusterNodePage(
        @RequestAttribute userId: String,
        @ApiParam(value = "当前页", required = true, example = "0")
        @PathVariable pageNumber: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @PathVariable pageSize: Int,
        @ApiParam(value = "集群名称", required = false)
        @RequestParam name: String? = null,
        @ApiParam(value = "集群类型", required = false)
        @RequestParam type: String? = null
    ): Response<Page<ClusterNodeInfo>> {
        return ResponseBuilder.success(clusterNodeService.listClusterNodePage(name, type, pageNumber, pageSize))
    }

    @ApiOperation("查询节点详情")
    @GetMapping("/detail")
    fun detailClusterNode(
        @RequestAttribute userId: String,
        @ApiParam(value = "集群名称", required = true)
        @RequestParam name: String
    ): Response<ClusterNodeInfo> {
        return ResponseBuilder.success(clusterNodeService.detailClusterNode(name))
    }
}
