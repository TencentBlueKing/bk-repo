/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkiamv3.callback

import com.tencent.bk.sdk.iam.dto.PageInfoDTO
import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.BaseDataResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.InstanceInfoDTO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.util.BkIamV3Utils.buildId
import com.tencent.bkrepo.auth.util.BkIamV3Utils.splitId
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

/**
 * 节点资源回调实现
 */
@Component
class BkiamNodeResourceService(
    private val nodeClient: NodeClient,
    private val mongoTemplate: MongoTemplate
    ): BkiamResourceBaseService {
    override fun resourceType(): ResourceType {
        return ResourceType.NODE
    }

    override fun fetchInstanceInfo(request: CallbackRequestDTO): CallbackBaseResponseDTO {
        logger.info("v3 Node fetchInstanceInfo, request $request")
        val ids = request.filter.idList.map { it.toString() }
        return buildFetchInstanceInfoResponseDTO(filterNodeInfo(ids))
    }

    override fun searchInstanceInfo(request: CallbackRequestDTO): CallbackBaseResponseDTO {
        logger.info("v3 Node searchInstanceInfo, request $request")
        val parentId = request.filter.parent.id
        return buildSearchInstanceResponseDTO(
            listNode(parentId = parentId, page = request.page, path = request.filter.keyword)
        )
    }

    override fun listInstanceInfo(request: CallbackRequestDTO): CallbackBaseResponseDTO {
        logger.info("v3 Node listInstanceInfo, request $request")
        val parentId = request.filter.parent.id
        return buildListInstanceResponseDTO(
            listNode(parentId = parentId, page = request.page)
        )
    }


    private fun filterNodeInfo(
        idList: List<String>
    ): List<InstanceInfoDTO> {
        logger.info("v3 filterNodeInfo, idList: $idList")
        val result = mutableListOf<InstanceInfoDTO>()
        idList.forEach {
            val (id, index) = splitId(it)
            val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(id))
            val data = mongoTemplate.find<Map<String, Any?>>(nodeQuery, "$NODE_COLLECTION_NAME$index")
            if (data.isEmpty()) return@forEach
            val entity = InstanceInfoDTO()
            entity.id = it
            entity.displayName = data.first()[NodeInfo::fullPath.name].toString()
            result.add(entity)
        }
        return result
    }

    private fun getRepoNameById(id: String?): Pair<String, String>?{
        if (id == null) return null
        val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(id))
        val data = mongoTemplate.find<Map<String, Any?>>(nodeQuery, REPO_COLLECTION_NAME)
        if (data.isEmpty()) return null
        return Pair(
            data.first()[RepositoryInfo::projectId.name].toString(),
            data.first()[RepositoryInfo::name.name].toString()
        )
    }

    private fun listNode(
        parentId: String,
        page: PageInfoDTO? = null,
        path: String = "/"
    ): BaseDataResponseDTO<InstanceInfoDTO> {
        logger.info("v3 listNode, parentId: $parentId, page $page, id: $path")
        val (projectId, repoName) = getRepoNameById(parentId) ?: return buildBaseDataResponseDTO(0, emptyList())
        val index = HashShardingUtils.shardingSequenceFor(projectId, 256).toString()
        var offset = 0
        var limit = 20
        if (page != null) {
            offset = page.offset.toInt()
            limit = page.limit.toInt()
        }
        val nodePage = nodeClient.listNodePage(
            projectId, repoName, path, NodeListOption(pageNumber = offset, pageSize = limit, deep = true)
        ).data!!
        val nodes = nodePage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = buildId(it.id!!, index)
            entity.displayName = it.fullPath
            entity
        }
        return buildBaseDataResponseDTO(nodePage.totalRecords, nodes)
    }
    companion object {
        private val logger = LoggerFactory.getLogger(BkiamNodeResourceService::class.java)
        private const val REPO_COLLECTION_NAME = "repository"
        private const val NODE_COLLECTION_NAME = "node_"
        private const val ID = "_id"
    }
}
