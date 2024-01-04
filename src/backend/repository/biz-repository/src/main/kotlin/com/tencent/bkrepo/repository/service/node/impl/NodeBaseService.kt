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

package com.tencent.bkrepo.repository.service.node.impl

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
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
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao.Companion.ID
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.stream.constant.BinderType
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.fs.server.constant.FAKE_MD5
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.ConflictStrategy
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.service.file.FileReferenceService
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.service.repo.QuotaService
import com.tencent.bkrepo.repository.service.repo.StorageCredentialService
import com.tencent.bkrepo.repository.util.MetadataUtils
import com.tencent.bkrepo.repository.util.NodeEventFactory.buildCreatedEvent
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 节点基础服务，实现了CRUD基本操作
 */
abstract class NodeBaseService(
    open val nodeDao: NodeDao,
    open val repositoryDao: RepositoryDao,
    open val fileReferenceService: FileReferenceService,
    open val storageCredentialService: StorageCredentialService,
    open val storageService: StorageService,
    open val quotaService: QuotaService,
    open val repositoryProperties: RepositoryProperties,
    open val messageSupplier: MessageSupplier,
    open val servicePermissionClient: ServicePermissionClient,
) : NodeService {

    @Autowired
    @Lazy
    protected lateinit var permissionManager: PermissionManager

    override fun getNodeDetail(artifact: ArtifactInfo, repoType: String?): NodeDetail? {
        with(artifact) {
            val node = nodeDao.findNode(projectId, repoName, getArtifactFullPath())
            return convertToDetail(node)
        }
    }

    override fun listNode(artifact: ArtifactInfo, option: NodeListOption): List<NodeInfo> {
        checkNodeListOption(option)
        with(artifact) {
            getNoPermissionPaths(SecurityUtils.getUserId(), projectId, repoName)?.let { option.noPermissionPath = it }
            val query = NodeQueryHelper.nodeListQuery(projectId, repoName, getArtifactFullPath(), option)
            if (nodeDao.count(query) > repositoryProperties.listCountLimit) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_LIST_TOO_LARGE)
            }
            return nodeDao.find(query).map { convert(it)!! }
        }
    }

    override fun listNodePage(artifact: ArtifactInfo, option: NodeListOption): Page<NodeInfo> {
        checkNodeListOption(option)
        with(artifact) {
            getNoPermissionPaths(SecurityUtils.getUserId(), projectId, repoName)?.let { option.noPermissionPath = it }
            val pageNumber = option.pageNumber
            val pageSize = option.pageSize
            Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
            Preconditions.checkArgument(pageSize >= 0 && pageSize <= repositoryProperties.listCountLimit, "pageSize")
            val query = NodeQueryHelper.nodeListQuery(projectId, repoName, getArtifactFullPath(), option)
            val totalRecords = nodeDao.count(query)
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
            val createStart = System.currentTimeMillis()
            val fullPath = PathUtils.normalizeFullPath(fullPath)
            Preconditions.checkArgument(!PathUtils.isRoot(fullPath), this::fullPath.name)
            Preconditions.checkArgument(folder || !sha256.isNullOrBlank(), this::sha256.name)
            Preconditions.checkArgument(folder || !md5.isNullOrBlank(), this::md5.name)
            // 仓库是否存在
            val repo = checkRepo(projectId, repoName)
            // 路径唯一性校验
            val deletedTime = checkConflictAndQuota(createRequest, fullPath)
            // 判断父目录是否存在，不存在先创建
            val parents = mkdirs(projectId, repoName, PathUtils.resolveParent(fullPath), operator)
            // 创建节点
            val node = buildTNode(this)
            doCreate(node)
            afterCreate(repo, node, createStart, parents, deletedTime)
            logger.info("Create node[/$projectId/$repoName$fullPath], sha256[$sha256] success.")
            return convertToDetail(node)!!
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun link(request: NodeLinkRequest): NodeDetail {
        with(request) {
            // 校验源仓库与目标节点权限
            permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName, userId = operator)
            permissionManager.checkNodePermission(
                PermissionAction.READ, targetProjectId, targetRepoName, targetFullPath, userId = operator
            )

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
        with(request) {
            val normalizeFullPath = PathUtils.normalizeFullPath(fullPath)
            return TNode(
                projectId = projectId,
                repoName = repoName,
                path = PathUtils.resolveParent(normalizeFullPath),
                name = PathUtils.resolveName(normalizeFullPath),
                fullPath = normalizeFullPath,
                folder = folder,
                expireDate = if (folder) null else parseExpireDate(expires),
                size = if (folder) 0 else size ?: 0,
                sha256 = if (folder) null else sha256,
                md5 = if (folder) null else md5,
                nodeNum = null,
                metadata = MetadataUtils.compatibleConvertAndCheck(
                    metadata,
                    MetadataUtils.changeSystem(nodeMetadata, repositoryProperties.allowUserAddSystemMetadata),
                ),
                createdBy = createdBy ?: operator,
                createdDate = createdDate ?: LocalDateTime.now(),
                lastModifiedBy = createdBy ?: operator,
                lastModifiedDate = lastModifiedDate ?: LocalDateTime.now(),
                lastAccessDate = LocalDateTime.now(),
            )
        }
    }

    private fun afterCreate(
        repo: TRepository,
        node: TNode,
        createStart: Long,
        parents: List<TNode>,
        deletedTime: LocalDateTime?,
    ) {
        with(node) {
            val createEnd = System.currentTimeMillis()
            val timeout = createEnd - createStart > repositoryProperties.nodeCreateTimeout
            if (timeout) {
                logger.info("Create node[$fullPath] timeout")
                rollbackCreate(parents, node, deletedTime)
                throw ErrorCodeException(ArtifactMessageCode.NODE_CREATE_TIMEOUT, fullPath)
            }
            if (isGenericRepo(repo)) {
                publishEvent(buildCreatedEvent(node))
            }
            reportNode2Bkbase(node)
        }
    }

    /**
     * 回滚创建的节点和目录
     * */
    private fun rollbackCreate(parents: List<TNode>, newNode: TNode, deletedTime: LocalDateTime?) {
        val toDeletedDirs = mutableListOf<TNode>()
        val projectId = newNode.projectId
        val repoName = newNode.repoName
        toDeletedDirs.addAll(parents)
        if (newNode.folder) {
            toDeletedDirs.add(newNode)
        } else {
            // 删除新创建的
            nodeDao.remove(
                Query(
                    Criteria(ID).isEqualTo(newNode.id)
                        .and(TNode::projectId).isEqualTo(projectId),
                ),
            )
            // 软链接node或fs-server创建的node的sha256为FAKE_SHA256，不会增加引用数，回滚时无需减少
            if (newNode.sha256 != FAKE_SHA256) {
                fileReferenceService.decrement(newNode)
            }
            quotaService.decreaseUsedVolume(projectId, repoName, newNode.size)
            logger.info("Rollback node [$projectId/$repoName${newNode.fullPath}]")
        }

        toDeletedDirs.sortByDescending { it.fullPath }
        for (dir in toDeletedDirs) {
            val option = NodeListOption(deep = true)
            val query = NodeQueryHelper.nodeListQuery(dir.projectId, dir.repoName, dir.fullPath, option)
            val size = nodeDao.find(query).size
            if (size > 0) {
                break
            }
            nodeDao.remove(
                Query(
                    Criteria(ID).isEqualTo(dir.id)
                        .and(TNode::projectId).isEqualTo(dir.projectId),
                ),
            )
            logger.info("Rollback node [$projectId/$repoName${dir.fullPath}]")
        }

        // 恢复创建前的情况，可能是新创建也可能是被覆盖。deletedTime不为空，则表示是覆盖创建
        with(newNode) {
            deletedTime?.let {
                val restoreContext = NodeRestoreSupport.RestoreContext(
                    projectId = projectId,
                    repoName = repoName,
                    rootFullPath = fullPath,
                    deletedTime = it,
                    conflictStrategy = ConflictStrategy.FAILED,
                    operator = createdBy,
                )
                restoreNode(restoreContext)
                logger.info("Restore node [$projectId/$repoName$fullPath]")
            }
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
                .and(TNode::fullPath).regex("^${PathUtils.escapeRegex(node.fullPath)}")
                .and(TNode::folder).isEqualTo(false)
            val query = Query(criteria).withHint(TNode.FULL_PATH_IDX)
            val update = Update().set(TNode::lastAccessDate.name, accessDate)
            nodeDao.updateMulti(query, update)
            logger.info("Update node access time [$this] success.")
        }
    }

    open fun doCreate(node: TNode, repository: TRepository? = null): TNode {
        try {
            nodeDao.insert(node)
            if (!node.folder) {
                // 软链接node或fs-server创建的node的sha256为FAKE_SHA256不会关联实际文件，无需增加引用数
                if (node.sha256 != FAKE_SHA256) {
                    fileReferenceService.increment(node, repository)
                }
                quotaService.increaseUsedVolume(node.projectId, node.repoName, node.size)
            }
        } catch (exception: DuplicateKeyException) {
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

    open fun checkConflictAndQuota(createRequest: NodeCreateRequest, fullPath: String): LocalDateTime? {
        with(createRequest) {
            val existNode = nodeDao.findNode(projectId, repoName, fullPath)
            if (existNode != null) {
                if (!overwrite) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, fullPath)
                } else if (existNode.folder || this.folder) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, fullPath)
                } else {
                    val changeSize = this.size?.minus(existNode.size) ?: -existNode.size
                    quotaService.checkRepoQuota(projectId, repoName, changeSize)
                    return deleteByPath(projectId, repoName, fullPath, operator).deletedTime
                }
            } else {
                quotaService.checkRepoQuota(projectId, repoName, this.size ?: 0)
            }
            return null
        }
    }

    private fun checkNodeListOption(option: NodeListOption) {
        Preconditions.checkArgument(
            option.sortProperty.none { !TNode::class.java.declaredFields.map { f -> f.name }.contains(it) },
            "sortProperty",
        )
        Preconditions.checkArgument(
            option.direction.none { it != Sort.Direction.DESC.name && it != Sort.Direction.ASC.name },
            "direction",
        )
    }

    /**
     * 获取用户无权限路径列表
     */
    private fun getNoPermissionPaths(userId: String, projectId: String, repoName: String): List<String>? {
        if (userId == SYSTEM_USER) {
            return null
        }
        val result = servicePermissionClient.listPermissionPath(userId, projectId, repoName).data!!
        if (result.status) {
            val paths = result.path.flatMap {
                require(it.key == OperationType.NIN)
                it.value
            }.ifEmpty { null }
            logger.info(
                "user[$userId] does not have permission of paths[$paths] in [$projectId/$repoName], will be filterd"
            )
            return paths
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeBaseService::class.java)
        private const val TOPIC = "bkbase_bkrepo_artifact_node_created"

        private fun convert(tNode: TNode?): NodeInfo? {
            return tNode?.let {
                val metadata = MetadataUtils.toMap(it.metadata)
                NodeInfo(
                    id = it.id,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    projectId = it.projectId,
                    repoName = it.repoName,
                    folder = it.folder,
                    path = it.path,
                    name = it.name,
                    fullPath = it.fullPath,
                    size = if (it.size < 0L) 0L else it.size,
                    nodeNum = it.nodeNum?.let { nodeNum ->
                        if (nodeNum < 0L) 0L else nodeNum
                    },
                    sha256 = it.sha256,
                    md5 = it.md5,
                    metadata = metadata,
                    nodeMetadata = MetadataUtils.toList(it.metadata),
                    copyFromCredentialsKey = it.copyFromCredentialsKey,
                    copyIntoCredentialsKey = it.copyIntoCredentialsKey,
                    deleted = it.deleted?.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastAccessDate = it.lastAccessDate?.format(DateTimeFormatter.ISO_DATE_TIME),
                    clusterNames = it.clusterNames,
                    archived = it.archived,
                    compressed = it.compressed,
                )
            }
        }

        fun convertToDetail(tNode: TNode?): NodeDetail? {
            return convert(tNode)?.let { NodeDetail(it) }
        }

        /**
         * 根据有效天数，计算到期时间
         */
        fun parseExpireDate(expireDays: Long?): LocalDateTime? {
            return expireDays?.takeIf { it > 0 }?.run { LocalDateTime.now().plusDays(this) }
        }
    }
}
