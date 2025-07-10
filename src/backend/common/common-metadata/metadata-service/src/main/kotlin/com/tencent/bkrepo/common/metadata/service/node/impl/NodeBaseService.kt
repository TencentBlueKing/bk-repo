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

package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.constant.PROXY_HEADER_NAME
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_FULL_PATH
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_PROJECT
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_LINK_REPO
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.constant.FAKE_MD5
import com.tencent.bkrepo.common.metadata.constant.FAKE_SEPARATE
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.QuotaService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.TOPIC
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.checkNodeListOption
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.convert
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.convertToDetail
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.parseExpireDate
import com.tencent.bkrepo.common.metadata.util.NodeBaseServiceHelper.validateParameter
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory.buildCreatedEvent
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper.listPermissionPaths
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.common.stream.constant.BinderType
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.fs.server.constant.UPLOADID_KEY
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.router.api.RouterControllerClient
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 节点基础服务，实现了CRUD基本操作
 */
abstract class NodeBaseService(
    open val nodeDao: NodeDao,
    open val repositoryDao: RepositoryDao,
    open val fileReferenceService: FileReferenceService,
    open val storageCredentialService: StorageCredentialService,
    open val quotaService: QuotaService,
    open val repositoryProperties: RepositoryProperties,
    open val messageSupplier: MessageSupplier,
    open val servicePermissionClient: ServicePermissionClient,
    open val routerControllerClient: RouterControllerClient,
    open val routerControllerProperties: RouterControllerProperties,
    open val blockNodeService: BlockNodeService,
    open val projectService: ProjectService,
) : NodeService {

    override fun getNodeDetail(artifact: ArtifactInfo, repoType: String?): NodeDetail? {
        with(artifact) {
            val node = nodeDao.findNode(projectId, repoName, getArtifactFullPath())
            return convertToDetail(node)
        }
    }

    override fun listNode(artifact: ArtifactInfo, option: NodeListOption): List<NodeInfo> {
        checkNodeListOption(option)
        with(artifact) {
            val userId = SecurityUtils.getUserId()
            val (hasPermissionPaths, noPermissionPaths) = getPermissionPaths(userId, projectId, repoName)
            option.hasPermissionPath = hasPermissionPaths
            option.noPermissionPath = noPermissionPaths
            var query = NodeQueryHelper.nodeListQuery(projectId, repoName, getArtifactFullPath(), option)
            val totalNum = getTotalNodeNum(artifact, query)
            if (totalNum > repositoryProperties.listCountLimit) {
                val pageRequest = Pages.ofRequest(1, repositoryProperties.listCountLimit.toInt())
                query = query.with(pageRequest)
            }
            return nodeDao.find(query).map { convert(it)!! }
        }
    }

    override fun listNodePage(artifact: ArtifactInfo, option: NodeListOption): Page<NodeInfo> {
        checkNodeListOption(option)
        with(artifact) {
            val userId = SecurityUtils.getUserId()
            val (hasPermissionPaths, noPermissionPaths) = getPermissionPaths(userId, projectId, repoName)
            option.hasPermissionPath = hasPermissionPaths
            option.noPermissionPath = noPermissionPaths
            val pageNumber = option.pageNumber
            val pageSize = option.pageSize
            Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
            Preconditions.checkArgument(pageSize >= 0 && pageSize <= repositoryProperties.listCountLimit, "pageSize")
            val query = NodeQueryHelper.nodeListQuery(projectId, repoName, getArtifactFullPath(), option)
            val totalRecords = getTotalNodeNum(artifact, query)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val records = nodeDao.find(query.with(pageRequest)).map { convert(it)!! }
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    override fun listNodePageBySha256(sha256: String, option: NodeListOption): Page<NodeInfo> {
        val nodes = nodeDao.pageBySha256(sha256, option, true)
        return Pages.ofResponse(
            Pages.ofRequest(option.pageNumber, option.pageSize),
            nodes.totalElements,
            nodes.content.map { convert(it)!! },
        )
    }

    override fun listNodeBySha256(
        sha256: String,
        limit: Int,
        includeMetadata: Boolean,
        includeDeleted: Boolean,
        tillLimit: Boolean
    ): List<NodeInfo> {
        return nodeDao.listBySha256(sha256, limit, includeMetadata, includeDeleted, tillLimit).map { convert(it)!! }
    }

    override fun checkExist(artifact: ArtifactInfo): Boolean {
        return nodeDao.exists(artifact.projectId, artifact.repoName, artifact.getArtifactFullPath())
    }

    override fun listExistFullPath(projectId: String, repoName: String, fullPathList: List<String>): List<String> {
        val queryList = fullPathList.map { PathUtils.normalizeFullPath(it) }.filter { !PathUtils.isRoot(it) }
        val nodeQuery = NodeQueryHelper.nodeQuery(projectId, repoName, queryList)
        return nodeDao.find(nodeQuery).map { it.fullPath }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun createNode(createRequest: NodeCreateRequest): NodeDetail {
        with(createRequest) {
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            Preconditions.checkArgument(!PathUtils.isRoot(fullPath), this::fullPath.name)
            Preconditions.checkArgument(folder || !sha256.isNullOrBlank(), this::sha256.name)
            Preconditions.checkArgument(folder || !md5.isNullOrBlank(), this::md5.name)
            // 仓库是否存在
            val repo = checkRepo(projectId, repoName)
            // 路径唯一性校验
            checkConflictAndQuota(createRequest, fullPath)
            // 判断父目录是否存在，不存在先创建
            mkdirs(projectId, repoName, PathUtils.resolveParent(fullPath), operator)
            // 创建节点
            val node = buildTNode(this)
            doCreate(node, separate = separate)
            afterCreate(repo, node, source)
            logger.info("Create node[/$projectId/$repoName$fullPath], sha256[$sha256] success.")
            return convertToDetail(node)!!
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun link(request: NodeLinkRequest): NodeDetail {
        with(request) {
            val targetArtifact = "/$targetProjectId/$targetRepoName/$targetFullPath"
            if (checkTargetExist) {
                val targetNode = nodeDao.findNode(targetProjectId, targetRepoName, targetFullPath)
                    ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, targetArtifact)

                // 不支持链接到目录
                if (targetNode.folder) {
                    throw BadRequestException(ArtifactMessageCode.NODE_LINK_FOLDER_UNSUPPORTED, targetArtifact)
                }
            }

            val metadata = listOf(
                MetadataModel(key = METADATA_KEY_LINK_PROJECT, targetProjectId, system = true),
                MetadataModel(key = METADATA_KEY_LINK_REPO, targetRepoName, system = true),
                MetadataModel(key = METADATA_KEY_LINK_FULL_PATH, targetFullPath, system = true),
            )
            val createRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                folder = false,
                overwrite = overwrite,
                sha256 = FAKE_SHA256,
                md5 = FAKE_MD5,
                nodeMetadata = nodeMetadata?.let { it + metadata } ?: metadata,
                operator = operator,
            )
            // 创建链接节点
            return createNode(createRequest)
        }
    }

    open fun buildTNode(request: NodeCreateRequest): TNode {
        return NodeBaseServiceHelper.buildTNode(request, repositoryProperties.allowUserAddSystemMetadata)
    }

    private fun getTotalNodeNum(artifact: ArtifactInfo, query: Query): Long {
        // 避免当目录下节点过多去进行count产生慢查询，使用目录对应的子节点个数进行判断
        // 只有节点个数小于配置的大小时，才去实时获取对应总节点个数
        val subNodeNum = getSubNodeNum(artifact)
        val limit = if (repositoryProperties.listCountLimit > repositoryProperties.subNodeLimit) {
            repositoryProperties.listCountLimit
        } else {
            repositoryProperties.subNodeLimit
        }
        return if (subNodeNum <= limit) {
            nodeDao.count(query)
        } else {
            // *2主要是因为subnodeNum不包含目录
            subNodeNum * 2
        }
    }

    /**
     * 获取该节点下的子节点（不包含目录）个数
     */
    private fun getSubNodeNum(artifact: ArtifactInfo): Long {
        with(artifact) {
            val fullPath = artifact.getArtifactFullPath()
            if (PathUtils.isRoot(fullPath)) {
                return try {
                    projectService.getProjectMetricsInfo(artifact.projectId)?.repoMetrics?.firstOrNull {
                        it.repoName == artifact.repoName
                    }?.num ?: -1
                } catch (e: Exception) {
                    -1
                }
            } else {
                val node = nodeDao.findNode(projectId, repoName, fullPath) ?: return 0
                if (!node.folder) return 0
                if (node.nodeNum == null) return -1
                return node.nodeNum!!
            }
        }
    }

    fun afterCreate(repo: TRepository, node: TNode, source: String?) {
        with(node) {
            if (isGenericRepo(repo)) {
                publishEvent(buildCreatedEvent(node, source))
                createRouter(this)
            }
            reportNode2Bkbase(node)
        }
    }

    /**
     * 创建下载转发路由
     */
    private fun createRouter(node: TNode) {
        HeaderUtils.getHeader(PROXY_HEADER_NAME)?.let {
            routerControllerClient.addNode(node.projectId, node.repoName, node.fullPath, it)
        }
    }

    /**
     * 上报节点数据到数据平台
     */
    private fun reportNode2Bkbase(node: TNode) {
        if (!node.folder) {
            messageSupplier.delegateToSupplier(node, topic = TOPIC, binderType = BinderType.KAFKA)
        }
    }

    /**
     * 判断仓库是否为generic类型仓库
     */
    private fun isGenericRepo(repo: TRepository): Boolean {
        return repo.type == RepositoryType.GENERIC
    }

    /**
     * 校验仓库是否存在
     */
    open fun checkRepo(projectId: String, repoName: String): TRepository {
        return repositoryDao.findByNameAndType(projectId, repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun updateNode(updateRequest: NodeUpdateRequest) {
        with(updateRequest) {
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            val node = nodeDao.findNode(projectId, repoName, fullPath)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
            val selfQuery = NodeQueryHelper.nodeQuery(projectId, repoName, node.fullPath)
            val selfUpdate = NodeQueryHelper.nodeExpireDateUpdate(parseExpireDate(expires), operator)
            nodeDao.updateFirst(selfQuery, selfUpdate)
            logger.info("Update node [$this] success.")
        }
    }

    override fun updateNodeAccessDate(updateAccessDateRequest: NodeUpdateAccessDateRequest) {
        with(updateAccessDateRequest) {
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            val node = nodeDao.findNode(projectId, repoName, fullPath)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)

            val criteria = where(TNode::projectId).isEqualTo(projectId)
                .and(TNode::repoName).isEqualTo(repoName)
                .and(TNode::deleted).isEqualTo(null)
                .and(TNode::fullPath).isEqualTo(node.fullPath)
                .and(TNode::folder).isEqualTo(false)
            val query = Query(criteria).withHint(TNode.FULL_PATH_IDX)
            val update = Update().set(TNode::lastAccessDate.name, accessDate)
            nodeDao.updateFirst(query, update)
            logger.info("Update node access time [$this] success.")
        }
    }

    open fun doCreate(node: TNode, repository: TRepository? = null, separate: Boolean = false): TNode {
        try {
            nodeDao.insert(node)
            if (!node.folder) {
                // 软链接node或fs-server创建的node的sha256为FAKE_SHA256不会关联实际文件，无需增加引用数
                if (node.sha256 != FAKE_SHA256) {
                    incrementFileReference(node, repository)
                }
                quotaService.increaseUsedVolume(node.projectId, node.repoName, node.size)
            }
        } catch (exception: DuplicateKeyException) {
            if (separate){
                logger.warn("Insert block base node[$node] error: [${exception.message}]")
                throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, node.fullPath)
            }
            logger.warn("Insert node[$node] error: [${exception.message}]")
        }

        return node
    }

    /**
     * 递归创建目录
     */
    fun mkdirs(projectId: String, repoName: String, path: String, createdBy: String): List<TNode> {
        val nodes = mutableListOf<TNode>()
        // 格式化
        val fullPath = PathUtils.toFullPath(path)
        val creatingNode = nodeDao.findNode(projectId, repoName, fullPath)
        if (creatingNode != null && !creatingNode.folder) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
        }
        if (creatingNode == null) {
            val parentPath = PathUtils.resolveParent(fullPath)
            val name = PathUtils.resolveName(fullPath)
            val creates = mkdirs(projectId, repoName, parentPath, createdBy)
            val node = TNode(
                folder = true,
                path = parentPath,
                name = name,
                fullPath = PathUtils.combineFullPath(parentPath, name),
                size = 0,
                expireDate = null,
                metadata = mutableListOf(),
                projectId = projectId,
                repoName = repoName,
                createdBy = createdBy,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = createdBy,
                lastModifiedDate = LocalDateTime.now(),
            )
            doCreate(node)
            nodes.addAll(creates)
            nodes.add(node)
        }
        return nodes
    }

    open fun checkConflictAndQuota(createRequest: NodeCreateRequest, fullPath: String) {
        with(createRequest) {
            val existNode = nodeDao.findNode(projectId, repoName, fullPath)

            // 如果节点不存在，进行配额检查后直接返回
            if (existNode == null) {
                quotaService.checkRepoQuota(projectId, repoName, this.size ?: 0)
                return
            }

            // 如果不允许覆盖，抛出节点已存在异常
            if (!overwrite) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, fullPath)
            }

            // 如果存在文件夹冲突，抛出节点冲突异常
            if (existNode.folder || this.folder) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
            }

            // 子类的附加检查方法
            additionalCheck(existNode)

            // 计算变更大小，并检查仓库配额
            val changeSize = this.size?.minus(existNode.size) ?: -existNode.size
            quotaService.checkRepoQuota(projectId, repoName, changeSize)

            if (separate) {
                // 删除旧节点，并检查旧节点是否被删除，防止并发删除
                val currentVersion = metadata!![UPLOADID_KEY].toString()
                val oldNodeId = currentVersion.substringAfter("/")

                if (oldNodeId == FAKE_SEPARATE) {
                    return
                }

                val deleteRes = deleteNodeById(projectId, repoName, fullPath, operator, oldNodeId)
                if (deleteRes.deletedNumber == 0L) {
                    logger.warn("Delete block base node[$fullPath] by [$operator] error: node was deleted")
                    throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
                }
                logger.info("Delete block base node[$fullPath] by [$operator] success: $oldNodeId.")

            } else {
                deleteByFullPathWithoutDecreaseVolume(projectId, repoName, fullPath, operator)
            }

            // 更新配额使用量
            quotaService.decreaseUsedVolume(projectId, repoName, existNode.size)
        }
    }

    open fun additionalCheck(existNode: TNode) {
        // 默认不做任何操作
    }

    private fun incrementFileReference(node: TNode, repository: TRepository?): Boolean {
        if (!validateParameter(node)) return false
        return try {
            val credentialsKey = findCredentialsKey(node, repository)
            fileReferenceService.increment(node.sha256!!, credentialsKey)
        } catch (exception: IllegalArgumentException) {
            logger.error("Failed to increment reference of node [$node], repository not found.")
            false
        }
    }

    private fun findCredentialsKey(node: TNode, repository: TRepository?): String? {
        if (repository != null) {
            return repository.credentialsKey
        }
        val tRepository = repositoryDao.findByNameAndType(node.projectId, node.repoName)
        require(tRepository != null)
        return tRepository.credentialsKey
    }

    /**
     * 获取用户无权限路径列表
     */
    private fun getPermissionPaths(
        userId: String,
        projectId: String,
        repoName: String
    ): Pair<List<String>?, List<String>> {
        if (userId == SYSTEM_USER) {
            return Pair(null, emptyList())
        }
        return servicePermissionClient.listPermissionPaths(userId, projectId, repoName)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeBaseService::class.java)
    }
}
