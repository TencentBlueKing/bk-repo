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

package com.tencent.bkrepo.common.metadata.service.router.impl

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.router.NodeLocationDao
import com.tencent.bkrepo.common.metadata.dao.router.RouterNodeDao
import com.tencent.bkrepo.common.metadata.dao.router.RouterPolicyDao
import com.tencent.bkrepo.common.metadata.message.RouterControllerMessageCode
import com.tencent.bkrepo.common.metadata.model.TRouterPolicy
import com.tencent.bkrepo.common.metadata.service.router.NodeRedirectService
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.net.URL
import java.time.Duration

@Service
@Conditional(SyncCondition::class)
class NodeRedirectServiceImpl(
    val routerPolicyDao: RouterPolicyDao,
    val routerNodeDao: RouterNodeDao,
    val nodeLocationDao: NodeLocationDao,
    val temporaryTokenClient: ServiceTemporaryTokenClient
) : NodeRedirectService {

    override fun generateRedirectUrl(
        originUrl: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        user: String,
        serviceName: String,
    ): String? {
        val locations = nodeLocationDao.find(projectId, repoName, fullPath)
        if (locations.isEmpty()) {
            // 没有其他节点拥有文件，不做转发
            return null
        }
        val policies = routerPolicyDao.findAllByUsersInOrProjectIdsIn(
            user = user,
            projectId = projectId,
        )
        /*
        * 1. 未找到匹配策略：转发到任意节点
        * 2. 根据用户，项目优先级找到指定策略，转发到指定节点
        * */
        val policy = lookupPolicy(policies, user, projectId)
        val dest: String = policy?.destRouterNodeId ?: locations.first().routerNodeId
        val node = routerNodeDao.findById(dest)
            ?: throw ErrorCodeException(RouterControllerMessageCode.ROUTER_NODE_NOT_FOUND)
        val url = URL(originUrl)
        return when (serviceName) {
            "generic" -> generateTemporaryUrl(projectId, repoName, fullPath, node.location, url)
            else -> "${node.location}$serviceName${url.path}?${url.query}"
        }
    }

    private fun lookupPolicy(policies: List<TRouterPolicy>, user: String, projectId: String): TRouterPolicy? {
        if (policies.isEmpty()) {
            return null
        }
        policies.find { it.users.contains(user) }?.let {
            // 匹配用户
            return it
        }
        policies.find { it.projectIds.contains(projectId) }?.let {
            // 匹配项目
            return it
        }
        return null
    }

    private fun generateTemporaryUrl(
        projectId: String,
        repoName: String,
        fullPath: String,
        location: String,
        url: URL
    ): String {
        if (url.path.contains("temporary/download")) {
            return "$location/generic${url.path}?${url.query}"
        }
        val request = TemporaryTokenCreateRequest(
            projectId = projectId,
            repoName = repoName,
            fullPathSet = setOf(fullPath),
            expireSeconds = Duration.ofHours(1).seconds,
            type = TokenType.DOWNLOAD
        )
        val tokenInfo = temporaryTokenClient.createToken(request).data!!.first()
        return "$location/generic/temporary/download/$projectId/$repoName$fullPath" +
            "?token=${tokenInfo.token}&${url.query}"
    }
}
