package com.tencent.bkrepo.fs.server.service.drive

import cn.hutool.core.lang.hash.CityHash
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.constant.ID
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.message.DriveMessageCode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBaseRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchOp
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBatchRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeCreateRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeDeleteRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeMoveRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeUpdateRequest
import com.tencent.bkrepo.fs.server.request.drive.normalizedSymlinkTarget
import com.tencent.bkrepo.fs.server.request.drive.toCreateRequest
import com.tencent.bkrepo.fs.server.request.drive.toDeleteRequest
import com.tencent.bkrepo.fs.server.request.drive.toDriveNode
import com.tencent.bkrepo.fs.server.request.drive.toUpdateRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveNode
import com.tencent.bkrepo.fs.server.response.drive.DriveNodeBatchResponse
import com.tencent.bkrepo.fs.server.response.drive.DriveNodeBatchResult
import com.tencent.bkrepo.fs.server.response.drive.toDriveNode
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper.ROOT_INO
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class DriveNodeService(
    private val driveNodeDao: RDriveNodeDao,
    private val driveSnapSeqService: DriveSnapSeqService,
    private val driveProperties: DriveProperties,
) {
    suspend fun getNode(projectId: String, repoName: String, id: String): DriveNode? {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        return driveNodeDao.findByProjectIdAndRepoNameAndId(projectId, repoName, id)?.toDriveNode()
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, id)
    }

    suspend fun getNodeByIno(projectId: String, repoName: String, ino: Long): DriveNode {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        return driveNodeDao.findByProjectIdAndRepoNameAndIno(projectId, repoName, ino)?.toDriveNode()
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, ino)
    }

    suspend fun listNodes(projectId: String, repoName: String, parent: Long): List<DriveNode> {
        DriveServiceUtils.validateProjectRepoAndParent(projectId, repoName, parent)
        return driveNodeDao.listNode(projectId, repoName, parent).map { it.toDriveNode() }
    }

    suspend fun listNodesPage(
        projectId: String,
        repoName: String,
        parent: Long?,
        pageNumber: Int,
        pageSize: Int,
        includeTotalRecords: Boolean = false,
        snapSeq: Long? = null,
    ): Page<DriveNode> {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        DriveServiceUtils.validatePage(pageNumber, pageSize, driveProperties.listCountLimit)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val (records, totalRecords) = driveNodeDao.nodePage(
            projectId,
            repoName,
            parent,
            pageRequest,
            includeTotalRecords,
            snapSeq,
        )
        return Pages.ofResponse(pageRequest, totalRecords, records.map { it.toDriveNode() })
    }

    suspend fun listModifiedNodesPage(
        projectId: String,
        repoName: String,
        lastModifiedDate: LocalDateTime,
        pageNumber: Int,
        pageSize: Int,
        includeTotalRecords: Boolean = false,
    ): Page<DriveNode> {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        DriveServiceUtils.validatePage(pageNumber, pageSize, driveProperties.listCountLimit)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val (records, totalRecords) = driveNodeDao.modifiedNodePage(
            projectId = projectId,
            repoName = repoName,
            lastModifiedDate = lastModifiedDate,
            pageRequest = pageRequest,
            includeTotalRecords = includeTotalRecords
        )
        return Pages.ofResponse(pageRequest, totalRecords, records.map { it.toDriveNode() })
    }

    suspend fun createNode(createRequest: DriveNodeCreateRequest, snapSeq: Long? = null): DriveNode {
        with(createRequest) {
            validateCreateRequest(createRequest)
            val driveNode = createRequest.toDriveNode(
                snapSeq = resolveSnapSeq(projectId, repoName, snapSeq),
                operator = ReactiveSecurityUtils.getUser(),
            )
            checkConflict(driveNode)
            val createdNode = doCreate(driveNode)
            logger.info("Create drive node[$projectId/$repoName/${createdNode.realIno()}/${createdNode.name}] success.")
            return createdNode.toDriveNode()
        }
    }

    suspend fun moveNode(moveRequest: DriveNodeMoveRequest): DriveNode {
        with(moveRequest) {
            validateMoveRequest(moveRequest)
            val srcNode = getRequiredSource(projectId, repoName, srcNodeId, srcParent, srcName)
            if (srcNode.parent == destParent && srcNode.name == destName) {
                throw ErrorCodeException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "${srcNode.name} and $destName are the same file"
                )
            }
            val operator = ReactiveSecurityUtils.getUser()
            val currentSnapSeq = driveSnapSeqService.getLatestSnapSeq(projectId, repoName)
            val now = LocalDateTime.now()

            // 目标路径已存在文件，需要判断是否覆盖
            val targetNode = driveNodeDao.findCurrentNode(projectId, repoName, destParent, destName)
            if (targetNode != null) {
                if (!overwrite || srcNode.type != targetNode.type) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, destName)
                }
                if (targetNode.realIno() == srcNode.realIno()) {
                    throw ErrorCodeException(
                        CommonMessageCode.PARAMETER_INVALID,
                        "${srcNode.name} and ${targetNode.name} are the same file"
                    )
                }
                doDelete(targetNode, currentSnapSeq)
            }

            // 更新原node父目录与文件名
            val movedNode = if (srcNode.snapSeq != currentSnapSeq) {
                val copiedNode = DriveNodeQueryHelper.buildCowNode(srcNode, currentSnapSeq, operator, now).apply {
                    parent = destParent
                    name = destName
                }
                val createdNode = doCreate(copiedNode)
                // COW操作仅标记旧node为已删除，不需要清理drive-node-block
                driveNodeDao.markNodeDeleted(srcNode.projectId, srcNode.repoName, srcNode.id!!, currentSnapSeq)
                createdNode
            } else {
                val update = Update()
                    .set(TDriveNode::parent.name, destParent)
                    .set(TDriveNode::name.name, destName)
                    .set(TDriveNode::lastModifiedBy.name, operator)
                    .set(TDriveNode::lastModifiedDate.name, now)
                driveNodeDao.updateFirst(Query(Criteria.where(ID).isEqualTo(srcNode.id)), update)
                srcNode.copy(parent = destParent, name = destName, lastModifiedBy = operator, lastModifiedDate = now)
            }
            logger.info(
                "Move drive node[$projectId/$repoName/${srcNode.realIno()}] from[${srcNode.parent}/${srcNode.name}] " +
                        "to[$destParent/$destName] success."
            )
            return movedNode.toDriveNode()
        }
    }

    suspend fun update(updateRequest: DriveNodeUpdateRequest, snapSeq: Long? = null): DriveNode {
        with(updateRequest) {
            validateUpdateRequest(updateRequest)
            val srcNode = findRequiredNode(projectId, repoName, nodeId)
            val operator = ReactiveSecurityUtils.getUser()
            val currentSnapSeq = resolveSnapSeq(projectId, repoName, snapSeq)
            val now = LocalDateTime.now()
            val updatedNode = buildUpdatedNode(srcNode, updateRequest, operator, now)
            if (updatedNode.parent != srcNode.parent || updatedNode.name != srcNode.name) {
                checkConflict(updatedNode)
            }
            val savedNode = saveUpdatedNode(srcNode, updatedNode, updateRequest, currentSnapSeq, operator, now)
            logger.info("Update drive node[$projectId/$repoName/${srcNode.realIno()}] id[$nodeId] success.")
            return savedNode.toDriveNode()
        }
    }

    suspend fun delete(deleteRequest: DriveNodeDeleteRequest, snapSeq: Long? = null): DriveNode {
        with(deleteRequest) {
            validateDeleteRequest(deleteRequest)
            val srcNode = driveNodeDao.findByProjectIdAndRepoNameAndId(projectId, repoName, nodeId)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, nodeId)
            val currentSnapSeq = resolveSnapSeq(projectId, repoName, snapSeq)
            doDelete(srcNode, currentSnapSeq, if (force) null else lastModifiedDate)
            logger.info(
                "Delete drive node[$projectId/$repoName/${srcNode.realIno()}] " +
                        "at[${srcNode.parent}/${srcNode.name}] success."
            )
            return srcNode.toDriveNode()
        }
    }

    suspend fun batch(batchRequest: DriveNodeBatchRequest): DriveNodeBatchResponse {
        with(batchRequest) {
            DriveServiceUtils.validateProjectRepo(projectId, repoName)
            val currentSnapSeq = driveSnapSeqService.getLatestSnapSeq(projectId, repoName)
            var successCount = 0
            var failedCount = 0
            val results = operations.map {
                try {
                    val resultNode = when (it.op) {
                        DriveNodeBatchOp.CREATE -> createNode(
                            it.node.toCreateRequest(projectId, repoName),
                            currentSnapSeq
                        )

                        DriveNodeBatchOp.UPDATE -> update(it.node.toUpdateRequest(projectId, repoName), currentSnapSeq)
                        DriveNodeBatchOp.DELETE -> delete(it.node.toDeleteRequest(projectId, repoName), currentSnapSeq)
                        DriveNodeBatchOp.CREATE_HARD_LINK -> createHardLinkNode(
                            it.node.toCreateRequest(projectId, repoName),
                            currentSnapSeq
                        )
                    }
                    successCount++

                    val node = if (it.op == DriveNodeBatchOp.DELETE) null else resultNode
                    DriveNodeBatchResult(
                        op = it.op,
                        ino = resultNode.ino,
                        nodeId = resultNode.id,
                        node = node,
                        code = SUCCESS_CODE,
                        message = null
                    )
                } catch (e: Exception) {
                    failedCount++
                    val code = if (e is ErrorCodeException) {
                        e.messageCode.getCode()
                    } else {
                        CommonMessageCode.SYSTEM_ERROR.getCode()
                    }
                    DriveNodeBatchResult(
                        op = it.op,
                        ino = it.node.ino,
                        nodeId = it.node.nodeId,
                        code = code,
                        message = e.message
                    )
                }
            }
            logger.info(
                "Batch operate drive nodes[$projectId/$repoName], " +
                        "total[${operations.size}] success[$successCount] failed[$failedCount]."
            )
            return results
        }
    }

    private suspend fun createHardLinkNode(createRequest: DriveNodeCreateRequest, snapSeq: Long): DriveNode {
        val hardLinkRequest = createRequest.copy(ino = generatePlaceholderIno(), targetIno = createRequest.ino)
        return createNode(hardLinkRequest, snapSeq)
    }

    private suspend fun doDelete(driveNode: TDriveNode, curSnapSeq: Long, lastModifiedDate: LocalDateTime? = null) {
        val nodeId = driveNode.id
        requireNotNull(nodeId)
        with(driveNode) {
            if (isRootNode(driveNode)) {
                throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "Can't delete drive root node.")
            }
            // 不允许删除非空目录
            if (driveNode.type == TYPE_DIRECTORY && driveNodeDao.existsChild(projectId, repoName, ino)) {
                throw ErrorCodeException(DriveMessageCode.DIRECTORY_NOT_EMPTY, name)
            }
            val result = driveNodeDao.markNodeDeleted(projectId, repoName, nodeId, curSnapSeq, lastModifiedDate)
            if (result.modifiedCount != 1L) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, name)
            }
            logger.info(
                "Delete drive node[$name] id[$nodeId] ino[$ino] of parent[$parent] at snap[$curSnapSeq] success."
            )
        }
    }

    private fun isRootNode(node: TDriveNode): Boolean {
        return node.parent == null && node.type == TYPE_DIRECTORY && node.name.isEmpty()
    }

    private suspend fun resolveSnapSeq(projectId: String, repoName: String, snapSeq: Long?): Long {
        return snapSeq ?: driveSnapSeqService.getLatestSnapSeq(projectId, repoName)
    }

    private suspend fun findRequiredNode(projectId: String, repoName: String, nodeId: String): TDriveNode {
        return driveNodeDao.findByProjectIdAndRepoNameAndId(projectId, repoName, nodeId)
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, nodeId)
    }

    private suspend fun getRequiredSource(
        projectId: String,
        repoName: String,
        nodeId: String?,
        parent: Long?,
        name: String?,
    ): TDriveNode {
        if (!nodeId.isNullOrBlank()) {
            return driveNodeDao.findByProjectIdAndRepoNameAndId(projectId, repoName, nodeId)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, nodeId)
        }
        return driveNodeDao.findCurrentNode(projectId, repoName, parent!!, name!!)
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, name)
    }

    private suspend fun doCreate(driveNode: TDriveNode): TDriveNode {
        return try {
            driveNodeDao.insert(driveNode)
        } catch (_: DuplicateKeyException) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, driveNode.name)
        }
    }

    private suspend fun saveUpdatedNode(
        srcNode: TDriveNode,
        updatedNode: TDriveNode,
        updateRequest: DriveNodeUpdateRequest,
        currentSnapSeq: Long,
        operator: String,
        now: LocalDateTime,
    ): TDriveNode {
        return if (srcNode.snapSeq != currentSnapSeq) {
            saveCowUpdatedNode(srcNode, updatedNode, updateRequest, currentSnapSeq, operator, now)
        } else {
            updateCurrentSnapNode(srcNode, updatedNode, updateRequest)
        }
    }

    private suspend fun saveCowUpdatedNode(
        srcNode: TDriveNode,
        updatedNode: TDriveNode,
        updateRequest: DriveNodeUpdateRequest,
        currentSnapSeq: Long,
        operator: String,
        now: LocalDateTime,
    ): TDriveNode {
        val deleteResult = driveNodeDao.markNodeDeleted(
            updateRequest.projectId,
            updateRequest.repoName,
            updateRequest.nodeId,
            currentSnapSeq,
            if (updateRequest.force) null else updateRequest.lastModifiedDate
        )
        checkUpdateResult(deleteResult.modifiedCount, srcNode.name)
        return doCreate(DriveNodeQueryHelper.buildCowNode(updatedNode, currentSnapSeq, operator, now))
    }

    private suspend fun updateCurrentSnapNode(
        srcNode: TDriveNode,
        updatedNode: TDriveNode,
        updateRequest: DriveNodeUpdateRequest,
    ): TDriveNode {
        val result = try {
            driveNodeDao.updateByNodeId(
                updateRequest.projectId,
                updateRequest.repoName,
                updateRequest.nodeId,
                if (updateRequest.force) null else updateRequest.lastModifiedDate,
                updatedNode
            )
        } catch (_: DuplicateKeyException) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, updatedNode.name)
        }
        checkUpdateResult(result.modifiedCount, srcNode.name)
        return updatedNode
    }

    private fun checkUpdateResult(modifiedCount: Long, nodeName: String) {
        if (modifiedCount != 1L) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, nodeName)
        }
    }

    private suspend fun checkConflict(driveNode: TDriveNode) {
        with(driveNode) {
            val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
                .and(TDriveNode::repoName).isEqualTo(repoName)
                .and(TDriveNode::parent).isEqualTo(parent)
                .and(TDriveNode::name).isEqualTo(name)
                .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
                .and(TDriveNode::deleted).isEqualTo(null)
            if (driveNodeDao.exists(Query(criteria))) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, name)
            }
        }
    }

    private suspend fun validateCreateRequest(createRequest: DriveNodeCreateRequest) {
        with(createRequest) {
            DriveServiceUtils.validateProjectRepoAndParent(projectId, repoName, parent)
            // ino必须大于0
            Preconditions.checkArgument(ino >= ROOT_INO, DriveNodeCreateRequest::ino.name)
            // 节点类型校验
            Preconditions.checkArgument(type in TDriveNode.ALLOWED_TYPES, TDriveNode::type.name)
            // 软链接类型关联校验
            if (type == TDriveNode.TYPE_SYMLINK) {
                Preconditions.checkArgument(
                    !symlinkTarget.isNullOrBlank(), TDriveNode::symlinkTarget.name
                )
            } else {
                Preconditions.checkArgument(
                    symlinkTarget == null, TDriveNode::symlinkTarget.name
                )
            }
            // 时间戳非负校验
            mtime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::mtime.name) }
            ctime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::ctime.name) }
            atime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::atime.name) }
            validateCommonFields(createRequest)
        }
    }

    private suspend fun validateMoveRequest(moveRequest: DriveNodeMoveRequest) {
        with(moveRequest) {
            DriveServiceUtils.validateProjectRepoAndParent(projectId, repoName, destParent)
            PathUtils.validateFileName(destName)
            checkParent(destParent, projectId, repoName)
            if (srcNodeId.isNullOrBlank()) {
                DriveServiceUtils.validateProjectRepoAndParent(projectId, repoName, srcParent)
                PathUtils.validateFileName(srcName.orEmpty())
                checkParent(srcParent!!, projectId, repoName)
            }
        }
    }

    private suspend fun validateDeleteRequest(deleteRequest: DriveNodeDeleteRequest) {
        with(deleteRequest) {
            DriveServiceUtils.validateProjectRepo(projectId, repoName)
            Preconditions.checkArgument(nodeId.isNotBlank(), DriveNodeDeleteRequest::nodeId.name)
            if (!force) {
                Preconditions.checkArgument(lastModifiedDate != null, DriveNodeDeleteRequest::lastModifiedDate.name)
            }
        }
    }

    private suspend fun validateUpdateRequest(updateRequest: DriveNodeUpdateRequest) {
        with(updateRequest) {
            DriveServiceUtils.validateProjectRepo(projectId, repoName)
            Preconditions.checkArgument(nodeId.isNotBlank(), DriveNodeUpdateRequest::nodeId.name)
            // 时间戳非负校验
            mtime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::mtime.name) }
            ctime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::ctime.name) }
            atime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::atime.name) }
            validateCommonFields(updateRequest)
            if (!force) {
                Preconditions.checkArgument(lastModifiedDate != null, DriveNodeUpdateRequest::lastModifiedDate.name)
            }
        }
    }

    /**
     * 校验DriveNodeBaseRequest中的公共字段
     * 字段为可空类型，非null时才进行校验，兼容create（字段非空）和update（字段可选）两种场景
     */
    private suspend fun validateCommonFields(request: DriveNodeBaseRequest) {
        with(request) {
            // 数值字段非负校验
            size?.let { Preconditions.checkArgument(it >= 0, TDriveNode::size.name) }
            mode?.let { Preconditions.checkArgument(it >= 0, TDriveNode::mode.name) }
            nlink?.let { Preconditions.checkArgument(it >= 0, TDriveNode::nlink.name) }
            uid?.let { Preconditions.checkArgument(it >= 0, TDriveNode::uid.name) }
            gid?.let { Preconditions.checkArgument(it >= 0, TDriveNode::gid.name) }
            rdev?.let { Preconditions.checkArgument(it >= 0, TDriveNode::rdev.name) }
            flags?.let { Preconditions.checkArgument(it >= 0, TDriveNode::flags.name) }
            // 父节点校验
            parent?.let { checkParent(it, projectId, repoName) }
            // 文件名校验
            name?.let {
                PathUtils.validateFileName(it)
                DriveServiceUtils.validateLength(it, TDriveNode::name.name, driveProperties.nameMaxLength)
            }
            // 软链接目标路径校验
            symlinkTarget?.let {
                Preconditions.checkArgument(it.isNotBlank(), TDriveNode::symlinkTarget.name)
                DriveServiceUtils.validateLength(
                    it, TDriveNode::symlinkTarget.name, driveProperties.descriptionMaxLength
                )
            }
        }
    }

    private fun buildUpdatedNode(
        srcNode: TDriveNode,
        updateRequest: DriveNodeUpdateRequest,
        operator: String,
        now: LocalDateTime,
    ): TDriveNode {
        return srcNode.copy(
            parent = updateRequest.parent ?: srcNode.parent,
            name = updateRequest.name ?: srcNode.name,
            size = updateRequest.size ?: srcNode.size,
            mode = updateRequest.mode ?: srcNode.mode,
            nlink = updateRequest.nlink ?: srcNode.nlink,
            uid = updateRequest.uid ?: srcNode.uid,
            gid = updateRequest.gid ?: srcNode.gid,
            rdev = updateRequest.rdev ?: srcNode.rdev,
            flags = updateRequest.flags ?: srcNode.flags,
            symlinkTarget = updateRequest.normalizedSymlinkTarget() ?: srcNode.symlinkTarget,
            mtime = updateRequest.mtime ?: srcNode.mtime,
            ctime = updateRequest.ctime ?: srcNode.ctime,
            atime = updateRequest.atime ?: srcNode.atime,
            lastModifiedBy = operator,
            lastModifiedDate = now,
        )
    }

    /**
     * 检查parent与要创建的driveNode属于同一个仓库
     */
    private suspend fun checkParent(parent: Long, projectId: String, repoName: String) {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::ino).isEqualTo(parent)
            .and(TDriveNode::type).isEqualTo(TYPE_DIRECTORY)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isEqualTo(null)
        if (!driveNodeDao.exists(Query(criteria))) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, parent)
        }
    }

    companion object {
        private const val SUCCESS_CODE = 0
        private val logger = LoggerFactory.getLogger(DriveNodeService::class.java)

        private fun generatePlaceholderIno(): Long {
            return CityHash.hash64(ObjectId.get().toByteArray())
        }
    }
}
