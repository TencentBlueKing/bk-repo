/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.metadata.impl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.AsyncUtils.trace
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.CUSTOM
import com.tencent.bkrepo.common.artifact.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils.normalizeFullPath
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.pojo.webhook.NodeCreatedEventPayload
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory.buildMetadataDeletedEvent
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory.buildMetadataSavedEvent
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.ci.CIPermissionManager
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.condition.DefaultCondition
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import io.micrometer.observation.ObservationRegistry
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.Executors

/**
 * 元数据服务实现类
 */
@Service
@Conditional(SyncCondition::class, DefaultCondition::class)
class MetadataServiceImpl(
    private val nodeDao: NodeDao,
    private val repositoryProperties: RepositoryProperties,
    private val ciPermissionManager: CIPermissionManager,
    private val metadataLabelCacheService: MetadataLabelCacheService,
    private val registry: ObservationRegistry
) : MetadataService {

    private val okHttpClient = HttpClientBuilderFactory.create(registry = registry).build()

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
            checkNodeCluster(node)
            val oldMetadata = node.metadata ?: ArrayList()
            val newMetadata = MetadataUtils.compatibleConvertAndCheck(
                metadata,
                MetadataUtils.changeSystem(nodeMetadata, repositoryProperties.allowUserAddSystemMetadata)
            )
            checkEnumTypeMetadata(projectId, newMetadata)
            checkIfModifyPipelineMetadata(node, newMetadata.map { it.key })
            checkIfUpdateSystemMetadata(oldMetadata, newMetadata)
            node.metadata = if (replace) {
                newMetadata
            } else {
                MetadataUtils.merge(oldMetadata, newMetadata)
            }

            nodeDao.save(node)
            publishEvent(buildMetadataSavedEvent(request))
            pipelineArtifactCallback(node, newMetadata.map { it.key })
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
            checkIfModifyPipelineMetadata(node, request.keyList)
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
            pipelineArtifactCallback(node, request.keyList.toList())
            logger.info("Delete metadata[$keyList] on node[/$projectId/$repoName$fullPath] success.")
        }
    }

    fun checkNodeCluster(node: TNode) {
        return
    }

    fun checkEnumTypeMetadata(projectId: String, metadata: MutableList<TMetadata>) {
        val metadataList = metadata.map { MetadataModel(it.key, it.value) }
        metadataLabelCacheService.checkEnumMetadataLabel(projectId, metadataList)
    }

    private fun checkIfModifyPipelineMetadata(node: TNode, newMetadataKeys: Collection<String>) {
        val pipelineSource = node.repoName == PIPELINE || node.repoName == CUSTOM
        val pipelineMetadataKey = newMetadataKeys.find {
            CIPermissionManager.PIPELINE_METADATA.any { m -> m.equals(it, true) }
        }
        val illegal = !node.folder && pipelineSource &&
                pipelineMetadataKey != null && !ciPermissionManager.whiteListRequest()
        if (illegal) {
            ciPermissionManager.throwOrLogError(
                messageCode = RepositoryMessageCode.PIPELINE_METADATA_UPDATE_NOT_ALLOWED,
                pipelineMetadataKey!!
            )
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

    private fun pipelineArtifactCallback(node: TNode, changedMetadataKeys: List<String>) {
        if (repositoryProperties.updateArtifactUrl.isEmpty()) {
            return
        }
        if (node.repoName != PIPELINE && node.repoName != CUSTOM) {
            return
        }
        if (node.metadata?.find { it.key.equals(PIPELINE_ID, true) } == null) {
            return
        }
        val metadataLabelKeys = metadataLabelCacheService.listAll(node.projectId).map { it.labelKey }
        if (metadataLabelKeys.intersectIgnoreCase(changedMetadataKeys).isEmpty()) {
            return
        }
        val userId = SecurityUtils.getUserId()
        executor.submit {
            val payload = buildNodeCreatedEventPayload(node, userId)
            val request = Request.Builder()
                .url(repositoryProperties.updateArtifactUrl)
                .header(HEADER_DEVOPS_TOKEN, repositoryProperties.updateArtifactToken)
                .post(payload.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull()))
                .build()
            okHttpClient.newCall(request).execute().use {
                if (!it.isSuccessful) {
                    logger.error("Failed to callback pipeline artifact, " +
                            "bizId[${it.header(HEADER_DEVOPS_RID)}]," +
                            " payload[${payload.toJsonString()}]")
                    return@submit
                }
            }
        }
    }

    private fun buildNodeCreatedEventPayload(node: TNode, userId: String): NodeCreatedEventPayload =
        NodeCreatedEventPayload(
            UserInfo(
                userId = userId,
                name = userId,
                email = null,
                phone = null,
                createdDate = null,
                locked = false,
                admin = false,
                group = false,
            ),
            NodeBaseServiceHelper.convertToDetail(node)!!
        )

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataServiceImpl::class.java)
        private const val PIPELINE_ID = "pipelineId"
        private const val HEADER_DEVOPS_RID = "X-DEVOPS-RID"
        private const val HEADER_DEVOPS_TOKEN = "X-DEVOPS-BK-TOKEN"
        private val executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            ThreadFactoryBuilder().setNameFormat("pipeline-artifact-callback-%d").build()
        ).trace()
    }
}
