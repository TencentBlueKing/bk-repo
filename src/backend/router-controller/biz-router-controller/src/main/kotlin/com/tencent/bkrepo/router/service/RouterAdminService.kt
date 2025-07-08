/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.router.service

import com.tencent.bkrepo.router.pojo.AddRouterNodeRequest
import com.tencent.bkrepo.router.pojo.AddRouterPolicyRequest
import com.tencent.bkrepo.router.pojo.NodeLocation
import com.tencent.bkrepo.router.pojo.RemoveRouterNodeRequest
import com.tencent.bkrepo.router.pojo.RemoveRouterPolicyRequest
import com.tencent.bkrepo.router.pojo.RouterNode
import com.tencent.bkrepo.router.pojo.RouterPolicy

/**
 * 路由控制器管理员服务
 * */
interface RouterAdminService {
    /**
     * 新增策略
     * */
    fun addPolicy(addRouterPolicyRequest: AddRouterPolicyRequest): RouterPolicy

    /**
     * 删除策略
     * */
    fun removePolicy(removeRouterPolicyRequest: RemoveRouterPolicyRequest)

    /**
     * 获取所有策略
     * */
    fun listPolicies(): List<RouterPolicy>

    /**
     * 新增路由节点
     * */
    fun addRouterNode(addRouterNodeRequest: AddRouterNodeRequest): RouterNode

    /**
     * 移除路由节点
     * */
    fun removeRouterNode(removeRouterNodeRequest: RemoveRouterNodeRequest)

    /**
     * 获取所有路由节点
     * */
    fun listRouterNodes(): List<RouterNode>

    /**
     * 获取文件节点位置
     * */
    fun listNodeLocations(projectId: String, repoName: String, fullPath: String): List<NodeLocation>
}
