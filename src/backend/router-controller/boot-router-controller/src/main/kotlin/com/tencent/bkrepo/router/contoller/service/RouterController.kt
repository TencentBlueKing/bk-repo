/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.router.contoller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.router.api.RouterControllerClient
import com.tencent.bkrepo.router.pojo.AddRouterNodeRequest
import com.tencent.bkrepo.router.pojo.RemoveRouterNodeRequest
import com.tencent.bkrepo.router.pojo.RouterNode
import com.tencent.bkrepo.router.pojo.RouterPolicy
import com.tencent.bkrepo.router.service.NodeRedirectService
import com.tencent.bkrepo.router.service.RouterAdminService
import com.tencent.bkrepo.router.service.RouterControllerService
import org.springframework.web.bind.annotation.RestController

/**
 * 路由控制器
 * */
@RestController
class RouterController(
    val routerControllerService: RouterControllerService,
    val redirectService: NodeRedirectService,
    val routerAdminService: RouterAdminService,
) : RouterControllerClient {

    /**
     * 新增文件节点
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 文件完整路径
     * @param routerNodeId 所在节点id
     * */
    override fun addNode(projectId: String, repoName: String, fullPath: String, routerNodeId: String) {
        routerControllerService.addNode(projectId, repoName, fullPath, routerNodeId)
    }

    /**
     * 移除文件节点
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 文件完整路径
     * @param routerNodeId 所在节点id
     * */
    override fun removeNode(projectId: String, repoName: String, fullPath: String, routerNodeId: String) {
        routerControllerService.removeNode(projectId, repoName, fullPath, routerNodeId)
    }

    override fun removeNodes(projectId: String, repoName: String, fullPath: String) {
        routerControllerService.remoteNodes(projectId, repoName, fullPath)
    }

    /**
     * 获取文件的重定向地址。
     * 路由控制器，会根据配置的策略，生成一个转发地址。
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 文件完整路径
     * @param originUrl 原始请求
     * */
    override fun getRedirectUrl(
        projectId: String,
        repoName: String,
        fullPath: String,
        originUrl: String,
        serviceName: String,
    ): Response<String?> {
        val url = redirectService.generateRedirectUrl(
            originUrl = originUrl,
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            user = SecurityUtils.getUserId(),
            serviceName = serviceName,
        )
        return ResponseBuilder.success(url)
    }

    /**
     * 获取所有的路由策略
     * */
    override fun listRouterPolicies(): Response<List<RouterPolicy>> {
        return ResponseBuilder.success(routerAdminService.listPolicies())
    }

    /**
     * 添加路由节点
     * */
    override fun addRouterNode(request: AddRouterNodeRequest): Response<RouterNode> {
        return ResponseBuilder.success(routerAdminService.addRouterNode(request))
    }

    /**
     * 删除路由节点
     */
    override fun removeRouterNode(request: RemoveRouterNodeRequest): Response<Void> {
        routerAdminService.removeRouterNode(request)
        return ResponseBuilder.success()
    }
}
