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

package com.tencent.bkrepo.repository.service.metadata.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils.normalizeFullPath
import com.tencent.bkrepo.common.artifact.util.ClusterUtils
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.service.cluster.DefaultCondition
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.service.metadata.MetadataService
import com.tencent.bkrepo.repository.util.MetadataUtils
import com.tencent.bkrepo.repository.util.NodeEventFactory.buildMetadataDeletedEvent
import com.tencent.bkrepo.repository.util.NodeEventFactory.buildMetadataSavedEvent
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 元数据服务实现类
 */
@Service
@Conditional(DefaultCondition::class)
class MetadataServiceImpl(
    private val nodeDao: NodeDao,
    private val repositoryProperties: RepositoryProperties
) : MetadataService {

    override fun listMetadata(projectId: String, repoName: String, fullPath: String): Map<String, Any> {
        return MetadataUtils.toMap(nodeDao.findOne(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))?.metadata)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun saveMetadata(request: MetadataSaveRequest) {
        with(request) {
            if (metadata.isNullOrEmpty() && nodeMetadata.isNullOrEmpty()) {
                logger.info("Metadata is empty, skip saving")
                return
            }
            val fullPath = normalizeFullPath(fullPath)
            val node = nodeDao.findNode(projectId, repoName, fullPath)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
            ClusterUtils.checkContainsSrcCluster(node.clusterNames)
            val oldMetadata = node.metadata ?: ArrayList()
            val newMetadata = MetadataUtils.compatibleConvertAndCheck(
                metadata,
                MetadataUtils.changeSystem(nodeMetadata, repositoryProperties.allowUserAddSystemMetadata)
            )
            checkIfUpdateSystemMetadata(oldMetadata, newMetadata)
            node.metadata = if (replace) {
                newMetadata
            } else {
                MetadataUtils.merge(oldMetadata, newMetadata)
            }

            nodeDao.save(node)
            publishEvent(buildMetadataSavedEvent(request))
            logger.info("Save metadata[$newMetadata] on node[/$projectId/$repoName$fullPath] success.")
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun addForbidMetadata(request: MetadataSaveRequest) {
        with(request) {
            val forbidMetadata = MetadataUtils.extractForbidMetadata(nodeMetadata!!)
            if (forbidMetadata.isNullOrEmpty()) {
                logger.info("forbidMetadata is empty, skip saving[$request]")
                return
            }
            saveMetadata(request.copy(metadata = null, nodeMetadata = forbidMetadata))
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun deleteMetadata(request: MetadataDeleteRequest, allowDeleteSystemMetadata: Boolean) {
        with(request) {
            if (keyList.isEmpty()) {
                logger.info("Metadata key list is empty, skip deleting")
                return
            }
            val fullPath = normalizeFullPath(request.fullPath)
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)

            // 检查是否有更新权限
            val node = nodeDao.findOne(query) ?: throw NodeNotFoundException(fullPath)
            ClusterUtils.checkContainsSrcCluster(node.clusterNames)
            node.metadata?.forEach {
                if (it.key in keyList && it.system && !allowDeleteSystemMetadata) {
                    throw PermissionException("No permission to update system metadata[${it.key}]")
                }
            }

            val update = Update().pull(
                TNode::metadata.name,
                Query.query(where(TMetadata::key).inValues(keyList))
            )
            nodeDao.updateMulti(query, update)
            publishEvent(buildMetadataDeletedEvent(this))
            logger.info("Delete metadata[$keyList] on node[/$projectId/$repoName$fullPath] success.")
        }
    }

    /**
     * 检查是否有更新允许用户添加的系统元数据
     */
    private fun checkIfUpdateSystemMetadata(
        oldMetadata: MutableList<TMetadata>,
        newMetadata: MutableList<TMetadata>
    ) {
        val oldAllowUserAddSystemMetadata =
            oldMetadata.map { it.key }.intersectIgnoreCase(repositoryProperties.allowUserAddSystemMetadata)
        val newAllowUserAddSystemMetadata =
            newMetadata.map { it.key }.intersectIgnoreCase(repositoryProperties.allowUserAddSystemMetadata)
        val updateSystemMetadata = oldAllowUserAddSystemMetadata.intersect(newAllowUserAddSystemMetadata)
        if (updateSystemMetadata.isNotEmpty()) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_INVALID,
                updateSystemMetadata.joinToString(StringPool.COMMA)
            )
        }
    }

    private fun List<String>.intersectIgnoreCase(list: List<String>): List<String> {
        return this.filter { k -> list.any { it.equals(k, true) } }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataServiceImpl::class.java)
    }
}
