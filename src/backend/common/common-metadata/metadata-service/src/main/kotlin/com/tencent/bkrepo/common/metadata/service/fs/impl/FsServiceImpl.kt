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

package com.tencent.bkrepo.common.metadata.service.fs.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.constant.FAKE_MD5
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.dao.node.RNodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.fs.FsService
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.convertToDetail
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.parseExpireDate
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.service.cluster.condition.DefaultCondition
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSetLengthRequest
import com.tencent.bkrepo.repository.service.fs.FsService
import com.tencent.bkrepo.repository.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(ReactiveCondition::class)
class FsServiceImpl(
    private val nodeDao: RNodeDao,
) : FsService {
    override suspend fun createNode(createRequest: NodeCreateRequest): NodeDetail {
        with(createRequest) {
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            Preconditions.checkArgument(!PathUtils.isRoot(fullPath), this::fullPath.name)
            Preconditions.checkArgument(folder || sha256 == FAKE_SHA256, this::sha256.name)
            Preconditions.checkArgument(folder || md5 == FAKE_MD5, this::md5.name)
            // 创建节点
            val node = buildTNode(this)
            try {
                nodeDao.insert(node)
            } catch (exception: DuplicateKeyException) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, fullPath)
            }
            SpringContextUtils.publishEvent(NodeEventFactory.buildCreatedEvent(node))
            logger.info("Create node[/$projectId/$repoName$fullPath], sha256[$sha256] success.")
            return convertToDetail(node)!!
        }
    }

    fun buildTNode(request: NodeCreateRequest): TNode {
        with(request) {
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            return TNode(
                projectId = projectId,
                repoName = repoName,
                path = PathUtils.resolveParent(fullPath),
                name = PathUtils.resolveName(fullPath),
                fullPath = fullPath,
                folder = folder,
                expireDate = if (folder) null else parseExpireDate(expires),
                size = if (folder) 0 else size ?: 0,
                sha256 = if (folder) null else sha256,
                md5 = if (folder) null else md5,
                metadata = MetadataUtils.compatibleConvertAndCheck(metadata, nodeMetadata),
                createdBy = createdBy ?: operator,
                createdDate = createdDate ?: LocalDateTime.now(),
                lastModifiedBy = createdBy ?: operator,
                lastModifiedDate = lastModifiedDate ?: LocalDateTime.now(),
                lastAccessDate = LocalDateTime.now(),
            )
        }
    }

    override suspend fun setLength(setLengthRequest: NodeSetLengthRequest) {
        with(setLengthRequest) {
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            val node = nodeDao.findNode(projectId, repoName, fullPath)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
            val selfQuery = NodeQueryHelper.nodeQuery(projectId, repoName, node.fullPath)
            val selfUpdate = NodeQueryHelper.nodeSetLength(newLength, operator)
            nodeDao.updateFirst(selfQuery, selfUpdate)
            logger.info("Set node length [$this] success.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FsServiceImpl::class.java)
    }
}
