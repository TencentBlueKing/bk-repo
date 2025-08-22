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

package com.tencent.bkrepo.router.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.router.enum.RouterControllerMessageCode
import com.tencent.bkrepo.router.model.TRouterNode
import com.tencent.bkrepo.router.model.TRouterPolicy
import com.tencent.bkrepo.router.pojo.AddRouterNodeRequest
import com.tencent.bkrepo.router.pojo.AddRouterPolicyRequest
import com.tencent.bkrepo.router.pojo.NodeLocation
import com.tencent.bkrepo.router.pojo.RemoveRouterNodeRequest
import com.tencent.bkrepo.router.pojo.RemoveRouterPolicyRequest
import com.tencent.bkrepo.router.pojo.RouterNode
import com.tencent.bkrepo.router.pojo.RouterPolicy
import com.tencent.bkrepo.router.repository.NodeLocationRepository
import com.tencent.bkrepo.router.repository.RouterNodeRepository
import com.tencent.bkrepo.router.repository.RouterPolicyRepository
import com.tencent.bkrepo.router.service.RouterAdminService
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class RouterAdminServiceImpl(
    val routerPolicyRepository: RouterPolicyRepository,
    val routerNodeRepository: RouterNodeRepository,
    val nodeLocationRepository: NodeLocationRepository,
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
            routerPolicyRepository.save(policy).apply {
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
            val policy = routerPolicyRepository.findByIdOrNull(policyId) ?: throw ErrorCodeException(
                RouterControllerMessageCode.ROUTER_POLICY_NOT_FOUND,
            )
            routerPolicyRepository.delete(policy)
        }
    }

    override fun listPolicies(): List<RouterPolicy> {
        return routerPolicyRepository.findAll().map {
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
            routerNodeRepository.save(routerNode).apply {
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
            val routerNode = routerNodeRepository.findByIdOrNull(nodeId) ?: throw ErrorCodeException(
                RouterControllerMessageCode.ROUTER_NODE_NOT_FOUND,
            )
            routerNodeRepository.delete(routerNode)
            nodeLocationRepository.deleteAllByRouterNodeId(routerNode.id)
        }
    }

    override fun listRouterNodes(): List<RouterNode> {
        return routerNodeRepository.findAll().map {
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
        return nodeLocationRepository.findAllByProjectIdAndRepoNameAndFullPath(
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
