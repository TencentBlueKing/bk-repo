package com.tencent.bkrepo.fs.server.service.drive

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
import com.tencent.bkrepo.fs.server.model.drive.DriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.model.drive.toDriveNode
import com.tencent.bkrepo.fs.server.repository.RDriveNodeDao
import com.tencent.bkrepo.fs.server.request.service.DriveNodeCreateRequest
import com.tencent.bkrepo.fs.server.request.service.DriveNodeDeleteRequest
import com.tencent.bkrepo.fs.server.request.service.DriveNodeMoveRequest
import com.tencent.bkrepo.fs.server.request.service.toDriveNode
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
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
    private val driveBlockNodeService: DriveBlockNodeService,
) {
    suspend fun getNode(projectId: String, repoName: String, id: String): DriveNode? {
        validateProjectRepo(projectId, repoName)
        return driveNodeDao.findByProjectIdAndRepoNameAndId(projectId, repoName, id)?.toDriveNode()
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, id)
    }

    suspend fun listNodes(projectId: String, repoName: String, parent: String): List<DriveNode> {
        validateProjectRepoAndParent(projectId, repoName, parent)
        return driveNodeDao.listNode(projectId, repoName, parent).map { it.toDriveNode() }
    }

    suspend fun listNodesPage(
        projectId: String,
        repoName: String,
        parent: String,
        pageNumber: Int,
        pageSize: Int,
        includeTotalRecords: Boolean = false,
    ): Page<DriveNode> {
        validateProjectRepoAndParent(projectId, repoName, parent)
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize >= 0 && pageSize <= driveProperties.listCountLimit, "pageSize")
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val (records, totalRecords) = driveNodeDao.nodePage(
            projectId,
            repoName,
            parent,
            pageRequest,
            includeTotalRecords
        )
        return Pages.ofResponse(pageRequest, totalRecords, records.map { it.toDriveNode() })
    }

    suspend fun createNode(createRequest: DriveNodeCreateRequest): DriveNode {
        with(createRequest) {
            validateCreateRequest(createRequest)
            val driveNode = createRequest.toDriveNode(
                snapSeq = driveSnapSeqService.getLatestSnapSeq(projectId, repoName),
                operator = ReactiveSecurityUtils.getUser(),
            )
            checkConflict(driveNode)
            val createdNode = doCreate(driveNode)
            logger.info("Create drive node[$projectId/$repoName/${createdNode.ino}/${createdNode.name}] success.")
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
                if (targetNode.ino == srcNode.ino) {
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
                driveNodeDao.markNodeDeleted(srcNode.id!!, currentSnapSeq)
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
                "Move drive node[$projectId/$repoName/${srcNode.ino}] from[${srcNode.parent}/${srcNode.name}] " +
                        "to[$destParent/$destName] success."
            )
            return movedNode.toDriveNode()
        }
    }

    suspend fun delete(deleteRequest: DriveNodeDeleteRequest): DriveNode {
        with(deleteRequest) {
            validateDeleteRequest(deleteRequest)
            val srcNode = getRequiredSource(projectId, repoName, nodeId, parent, name)
            val currentSnapSeq = driveSnapSeqService.getLatestSnapSeq(projectId, repoName)
            doDelete(srcNode, currentSnapSeq)
            logger.info(
                "Delete drive node[$projectId/$repoName/${srcNode.ino}] at[${srcNode.parent}/${srcNode.name}] success."
            )
            return srcNode.toDriveNode()
        }
    }

    private suspend fun doDelete(driveNode: TDriveNode, curSnapSeq: Long) {
        val nodeId = driveNode.id
        requireNotNull(nodeId)
        with(driveNode) {
            // 不允许删除非空目录
            if (driveNode.type == TYPE_DIRECTORY && driveNodeDao.existsChild(projectId, repoName, ino)) {
                throw ErrorCodeException(DriveMessageCode.DIRECTORY_NOT_EMPTY, name)
            }
            driveNodeDao.markNodeDeleted(nodeId, curSnapSeq)
            logger.info("Delete drive node[$name] id[$nodeId] ino[$ino] of parent[$parent] at snap[$curSnapSeq] success.")
            if (driveNode.type != TYPE_DIRECTORY && driveNodeDao.sameInoCount(projectId, repoName, ino) == 0L) {
                driveBlockNodeService.deleteBlocks(ino, curSnapSeq)
            }
        }
    }

    private suspend fun getRequiredSource(
        projectId: String,
        repoName: String,
        nodeId: String?,
        parent: String?,
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
            validateProjectRepoAndParent(projectId, repoName, parent)
            Preconditions.checkArgument(size >= 0, TDriveNode::size.name)
            Preconditions.checkArgument(type in TDriveNode.ALLOWED_TYPES, TDriveNode::type.name)
            PathUtils.validateFileName(name)
            checkParent(parent, projectId, repoName)
        }
    }

    private suspend fun validateMoveRequest(moveRequest: DriveNodeMoveRequest) {
        with(moveRequest) {
            validateProjectRepoAndParent(projectId, repoName, destParent)
            PathUtils.validateFileName(destName)
            checkParent(destParent, projectId, repoName)
            if (srcNodeId.isNullOrBlank()) {
                validateProjectRepoAndParent(projectId, repoName, srcParent)
                PathUtils.validateFileName(srcName.orEmpty())
                checkParent(srcParent.orEmpty(), projectId, repoName)
            }
        }
    }

    private suspend fun validateDeleteRequest(deleteRequest: DriveNodeDeleteRequest) {
        with(deleteRequest) {
            if (nodeId.isNullOrBlank()) {
                validateProjectRepoAndParent(projectId, repoName, parent)
                PathUtils.validateFileName(name.orEmpty())
                checkParent(parent!!, projectId, repoName)
            } else {
                validateProjectRepo(projectId, repoName)
                Preconditions.checkArgument(nodeId.isNotBlank(), DriveNodeDeleteRequest::nodeId.name)
            }
        }
    }

    /**
     * 检查parent与要创建的driveNode属于同一个仓库
     */
    private suspend fun checkParent(parent: String, projectId: String, repoName: String) {
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

    private suspend fun validateProjectRepoAndParent(projectId: String, repoName: String, parent: String?) {
        validateProjectRepo(projectId, repoName)
        Preconditions.checkArgument(!parent.isNullOrBlank(), TDriveNode::parent.name)
    }

    private suspend fun validateProjectRepo(projectId: String, repoName: String) {
        Preconditions.checkArgument(projectId.isNotBlank(), TDriveNode::projectId.name)
        Preconditions.checkArgument(repoName.isNotBlank(), TDriveNode::repoName.name)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveNodeService::class.java)
    }
}
