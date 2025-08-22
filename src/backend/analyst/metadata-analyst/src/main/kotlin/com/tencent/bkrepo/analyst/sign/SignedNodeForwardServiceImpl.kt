/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.sign

import com.tencent.bkrepo.analyst.api.ScanClient
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.manager.NodeForwardService
import com.tencent.bkrepo.common.artifact.manager.sign.SignConfig
import com.tencent.bkrepo.common.artifact.manager.sign.SignProperties
import com.tencent.bkrepo.common.artifact.manager.sign.SignedNodeForwardMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.slf4j.LoggerFactory
import java.util.Base64

class SignedNodeForwardServiceImpl(
    private val signProperties: SignProperties,
    private val nodeService: NodeService,
    private val metadataService: MetadataService,
    private val scanClient: ScanClient,
    private val repositoryService: RepositoryService,
) : NodeForwardService {
    override fun forward(
        node: NodeDetail,
        userId: String
    ): NodeDetail? {
        val config = signProperties.config[node.projectId] ?: return null
        with(config) {
            if (notOnCondition(node, config) || fromScanService()) {
                return null
            }
            if (SecurityUtils.isAnonymous()) {
                logger.info("Refuse anonymous download signed node.")
                throw NodeNotFoundException(node.fullPath)
            }
            val traceableApkPath = "/${node.sha256}/${userId}_${node.name}"
            createRepoIfNotExist(projectId)
            val forwardNode = nodeService.getNodeDetail(
                ArtifactInfo(node.projectId, signProperties.signedRepoName, traceableApkPath)
            ) ?: getOldForwardNode(traceableApkPath, config)
            forwardNode ?: let {
                createApkDefenderTaskIfNot(node, config, userId)
                throw ErrorCodeException(
                    messageCode = SignedNodeForwardMessageCode.SIGNED_NODE_NOT_FOUND,
                    status = HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS,
                )
            }
            clearTask(node, userId)
            return forwardNode
        }
    }

    private fun getOldForwardNode(traceableApkPath: String, config: SignConfig): NodeDetail?{
        return if (config.oldSignedProjectId.isNotEmpty()) {
            nodeService.getNodeDetail(
                ArtifactInfo(
                    config.oldSignedProjectId,
                    config.oldSignedRepoName,
                    traceableApkPath
                )
            )
        } else {
            null
        }
    }

    private fun createRepoIfNotExist(projectId: String) {
        val request = RepoCreateRequest(
            projectId = projectId,
            name = signProperties.signedRepoName,
            operator = SYSTEM_USER,
            description = "Signed node repository",
            display = false,
            type = RepositoryType.GENERIC,
            public = false,
            category = RepositoryCategory.LOCAL,
        )
        try {
            repositoryService.createRepo(request)
        } catch (e: ErrorCodeException) {
            if (e.messageCode != ArtifactMessageCode.REPOSITORY_EXISTED) {
                throw e
            }
        }
    }

    private fun clearTask(node: NodeDetail, userId: String) {
        with(node) {
            val deleteRequest = MetadataDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                keyList = setOf(getKey(userId)),
                operator = userId,
            )
            metadataService.deleteMetadata(deleteRequest)
        }
    }

    /**
     * 转发策略，只有符合以下要求才进行转发
     * 1. 指定项目
     * 2. 对应文件类型
     * 3. BK_CI_APP_STAGE=Alpha
     * */
    private fun notOnCondition(node: NodeDetail, config: SignConfig): Boolean {
        if (node.folder) {
            return true
        }
        if (config.scanner[PathUtils.resolveExtension(node.name)] == null) {
            return true
        }
        return node.nodeMetadata.none { it.key.lowercase() == "bk-ci-app-stage" && config.tags.contains(it.value) }
    }

    private fun fromScanService(): Boolean {
        val ssid = HttpContextHolder.getRequestOrNull()?.getParameter("ssid") ?: return false
        val ssidStr = String(Base64.getDecoder().decode(ssid.toByteArray()))
        val parts = ssidStr.split(CharPool.COLON)
        require(parts.size == 2)
        return scanClient.verifyToken(subtaskId = parts[0], token = parts[1]).data ?: false
    }

    /**
     * 创建apk加固任务，如果已存在任务，则不创建
     * */
    private fun createApkDefenderTaskIfNot(node: NodeDetail, config: SignConfig, userId: String) {
        with(node) {
            val key = getKey(userId)
            val metadata = metadataService.listMetadata(projectId, repoName, fullPath)
            metadata[key]?.let {
                val task = scanClient.getTask(it.toString()).data ?: error("Request error")
                if (task.scanning > 0) {
                    logger.info("User[$userId]'s apk defender task[$it] in process.")
                    return
                }
            }
            val rules = ArrayList<Rule>()
            rules.add(Rule.QueryRule(NodeInfo::projectId.name, projectId, OperationType.EQ))
            rules.add(Rule.QueryRule(NodeInfo::repoName.name, repoName, OperationType.EQ))
            rules.add(Rule.QueryRule(NodeInfo::fullPath.name, fullPath, OperationType.EQ))
            val queryRule = Rule.NestedRule(rules, Rule.NestedRule.RelationType.AND)
            val scanRequest = ScanRequest(
                scanner = config.scanner[PathUtils.resolveExtension(node.name)],
                force = true,
                rule = queryRule,
                metadata = listOf(
                    TaskMetadata("users", userId),
                    TaskMetadata("sha256", node.sha256!!),
                    TaskMetadata(
                        "repoUrl",
                        "${signProperties.host}/generic/${node.projectId}/${signProperties.signedRepoName}"
                    )
                ),
            )
            val task = scanClient.scan(scanRequest).data ?: error("Request error")
            logger.info("Success create apk defender task[${task.taskId}] for user[$userId]")
            val createMetadataRequest = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                nodeMetadata = listOf(
                    MetadataModel(
                        key = key,
                        value = task.taskId,
                    ),
                ),
                operator = userId,
            )
            metadataService.saveMetadata(createMetadataRequest)
            logger.info("Save task metadata successful.")
        }
    }

    private fun getKey(userId: String): String {
        return "apk-defender-$userId"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SignedNodeForwardServiceImpl::class.java)
    }
}
