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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.router.NodeLocationDao
import com.tencent.bkrepo.common.metadata.dao.router.RouterNodeDao
import com.tencent.bkrepo.common.metadata.dao.router.RouterPolicyDao
import com.tencent.bkrepo.common.metadata.message.RouterControllerMessageCode
import com.tencent.bkrepo.common.metadata.model.TRouterNode
import com.tencent.bkrepo.common.metadata.model.TRouterPolicy
import com.tencent.bkrepo.common.metadata.pojo.router.AddRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.AddRouterPolicyRequest
import com.tencent.bkrepo.common.metadata.pojo.router.NodeLocation
import com.tencent.bkrepo.common.metadata.pojo.router.RemoveRouterNodeRequest
import com.tencent.bkrepo.common.metadata.pojo.router.RemoveRouterPolicyRequest
import com.tencent.bkrepo.common.metadata.pojo.router.RouterNode
import com.tencent.bkrepo.common.metadata.pojo.router.RouterPolicy
import com.tencent.bkrepo.common.metadata.service.router.RouterAdminService
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(SyncCondition::class)
class RouterAdminServiceImpl(
    val routerPolicyDao: RouterPolicyDao,
    val routerNodeDao: RouterNodeDao,
    val nodeLocationDao: NodeLocationDao,
) : RouterAdminService {
    override fun addPolicy(addRouterPolicyRequest: AddRouterPolicyRequest): RouterPolicy {
        with(addRouterPolicyRequest) {
            val id = "policy-${StringPool.randomString(10)}"
            val policy = TRouterPolicy(
                id = id,
                users = users,
                projectIds = projectIds,
                destRouterNodeId = destRouterNodeId,
                createdDate = LocalDateTime.now(),
                createdBy = operator,
            )
            routerPolicyDao.save(policy).apply {
                return RouterPolicy(
                    id = id,
                    users = users,
                    projectIds = projectIds,
                    destRouterNodeId = destRouterNodeId,
                    createdDate = createdDate,
                    createdBy = createdBy,
                )
            }
        }
    }

    override fun removePolicy(removeRouterPolicyRequest: RemoveRouterPolicyRequest) {
        with(removeRouterPolicyRequest) {
            val policy = routerPolicyDao.findById(policyId) ?: throw ErrorCodeException(
                RouterControllerMessageCode.ROUTER_POLICY_NOT_FOUND,
            )
            routerPolicyDao.removeById(policy.id)
        }
    }

    override fun listPolicies(): List<RouterPolicy> {
        return routerPolicyDao.findAll().map {
            RouterPolicy(
                id = it.id,
                createdBy = it.createdBy,
                createdDate = it.createdDate,
                users = it.users,
                projectIds = it.projectIds,
                destRouterNodeId = it.destRouterNodeId,
            )
        }
    }

    override fun addRouterNode(addRouterNodeRequest: AddRouterNodeRequest): RouterNode {
        with(addRouterNodeRequest) {
            val routerNode = TRouterNode(
                id = id,
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                name = name,
                description = description,
                type = type,
                location = location,
            )
            routerNodeDao.save(routerNode).apply {
                return RouterNode(
                    id = id,
                    createdBy = createdBy,
                    createdDate = createdDate,
                    name = name,
                    description = description,
                    type = type,
                    location = location,
                )
            }
        }
    }

    override fun removeRouterNode(removeRouterNodeRequest: RemoveRouterNodeRequest) {
        with(removeRouterNodeRequest) {
            val routerNode = routerNodeDao.findById(nodeId) ?: throw ErrorCodeException(
                RouterControllerMessageCode.ROUTER_NODE_NOT_FOUND,
            )
            routerNodeDao.removeById(routerNode.id)
            nodeLocationDao.deleteAllByRouterNodeId(routerNode.id)
        }
    }

    override fun listRouterNodes(): List<RouterNode> {
        return routerNodeDao.findAll().map {
            RouterNode(
                id = it.id,
                createdBy = it.createdBy,
                createdDate = it.createdDate,
                name = it.name,
                description = it.description,
                type = it.type,
                location = it.location,
            )
        }
    }

    override fun listNodeLocations(projectId: String, repoName: String, fullPath: String): List<NodeLocation> {
        return nodeLocationDao.find(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
        ).map {
            NodeLocation(
                projectId = it.projectId,
                repoName = it.repoName,
                fullPath = it.fullPath,
                routerNodeId = it.routerNodeId,
            )
        }
    }
}
