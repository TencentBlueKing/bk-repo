package com.tencent.bkrepo.fs.server.service.drive

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.message.DriveMessageCode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
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
import com.tencent.bkrepo.fs.server.request.drive.toMoveRequest
import com.tencent.bkrepo.fs.server.request.drive.toUpdateRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveNode
import com.tencent.bkrepo.fs.server.response.drive.DriveNodeBatchResponse
import com.tencent.bkrepo.fs.server.response.drive.DriveNodeBatchResult
import com.tencent.bkrepo.fs.server.response.drive.CursorPage
import com.tencent.bkrepo.fs.server.response.drive.toDriveNode
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import com.tencent.bkrepo.fs.server.utils.DriveNodeRequestValidator
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils
import com.tencent.bkrepo.fs.server.utils.ExceptionUtils
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional

@Service
class DriveNodeService(
    private val driveNodeDao: RDriveNodeDao,
    private val driveSnapSeqService: DriveSnapSeqService,
    private val driveProperties: DriveProperties,
    private val driveNodeRequestValidator: DriveNodeRequestValidator,
) {
    private val nodeByInoCache: AsyncLoadingCache<NodeCacheKey, Optional<TDriveNode>> = Caffeine.newBuilder()
        .maximumSize(NODE_CACHE_MAX_SIZE)
        .expireAfterWrite(Duration.ofSeconds(NODE_CACHE_EXPIRE_SECONDS))
        .buildAsync { key, _ ->
            mono {
                Optional.ofNullable(driveNodeDao.findByProjectIdAndRepoNameAndIno(key.projectId, key.repoName, key.ino))
            }.toFuture()
        }

    suspend fun getNode(projectId: String, repoName: String, id: String): DriveNode? {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        return driveNodeDao.findByProjectIdAndRepoNameAndId(projectId, repoName, id)?.toDriveNode()
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, id)
    }

    suspend fun getNodeByIno(projectId: String, repoName: String, ino: Long): DriveNode {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        return queryNodeByIno(projectId, repoName, ino, true)?.toDriveNode()
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
        pageSize: Int,
        lastName: String? = null,
        lastId: String? = null,
        snapSeq: Long? = null,
    ): CursorPage<DriveNode> {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        DriveServiceUtils.validatePageSize(pageSize, driveProperties.listCountLimit)
        // 多查询 1 条用于判断是否存在下一页（hasMore）。
        val records = driveNodeDao.nodePage(
            projectId = projectId,
            repoName = repoName,
            parent = parent,
            pageSize = pageSize + 1,
            lastName = lastName,
            lastId = lastId,
            snapSeq = snapSeq,
        )
        return CursorPage.fromRecords(records, pageSize) { it.toDriveNode() }
    }

    suspend fun listModifiedNodesPage(
        projectId: String,
        repoName: String,
        pageSize: Int,
        lastModifiedDate: LocalDateTime,
        lastId: String,
    ): CursorPage<DriveNode> {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        DriveServiceUtils.validatePageSize(pageSize, driveProperties.listCountLimit)
        // 多查询 1 条用于判断是否存在下一页（hasMore）。
        val records = driveNodeDao.modifiedNodePage(
            projectId = projectId,
            repoName = repoName,
            pageSize = pageSize + 1,
            lastModifiedDate = lastModifiedDate,
            lastId = lastId,
        )
        return CursorPage.fromRecords(records, pageSize) { it.toDriveNode() }
    }

    suspend fun createNode(createRequest: DriveNodeCreateRequest, snapSeq: Long? = null): DriveNode {
        with(createRequest) {
            driveNodeRequestValidator.validateCreateRequest(createRequest)
            val driveNode = createRequest.toDriveNode(
                snapSeq = resolveSnapSeq(projectId, repoName, snapSeq),
                operator = ReactiveSecurityUtils.getUser(),
            )
            val createdNode = doCreate(driveNode)
            logger.info("Create drive node[$projectId/$repoName/${createdNode.realIno}/${createdNode.name}] success.")
            return createdNode.toDriveNode()
        }
    }

    suspend fun moveNode(moveRequest: DriveNodeMoveRequest, snapSeq: Long? = null): DriveNode {
        with(moveRequest) {
            driveNodeRequestValidator.validateMoveRequest(moveRequest)
            val srcNode = queryNodeByIno(projectId, repoName, ino, bypassCache = ifMatch != null)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, ino)
            checkIfMatch(ifMatch, srcNode)
            if (srcNode.parent == destParent && srcNode.name == destName) {
                throw ErrorCodeException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "${srcNode.name} and $destName are the same file"
                )
            }
            val operator = ReactiveSecurityUtils.getUser()
            val currentSnapSeq = resolveSnapSeq(projectId, repoName, snapSeq)
            val now = LocalDateTime.now()

            // 更新原node父目录与文件名
            val movedNode = if (srcNode.snapSeq != currentSnapSeq) {
                // 目标路径已存在文件，需要判断是否覆盖
                driveNodeDao.findCurrentNode(projectId, repoName, destParent, destName)?.let {
                    overwriteTarget(srcNode, it, currentSnapSeq, overwrite)
                }
                val copiedNode = DriveNodeQueryHelper.buildCowNode(srcNode, currentSnapSeq, operator, now).apply {
                    parent = destParent
                    name = destName
                    mtime = moveRequest.mtime ?: srcNode.mtime
                    ctime = moveRequest.ctime ?: srcNode.ctime
                    atime = moveRequest.atime ?: srcNode.atime
                }
                // COW操作仅标记旧node为已删除，不需要清理drive-node-block
                driveNodeDao.markNodeDeleted(srcNode.projectId, srcNode.repoName, srcNode.id!!, currentSnapSeq)
                val createdNode = doCreate(copiedNode)
                createdNode
            } else {
                val updatedNode = srcNode.copy(
                    parent = destParent,
                    name = destName,
                    mtime = moveRequest.mtime ?: srcNode.mtime,
                    ctime = moveRequest.ctime ?: srcNode.ctime,
                    atime = moveRequest.atime ?: srcNode.atime,
                    lastModifiedBy = operator,
                    lastModifiedDate = now
                )
                try {
                    driveNodeDao.updateByNodeId(projectId, repoName, srcNode.id!!, ifMatch, updatedNode)
                } catch (e: DuplicateKeyException) {
                    // 目标路径已存在文件，需要判断是否覆盖
                    driveNodeDao.findCurrentNode(projectId, repoName, destParent, destName)?.let {
                        overwriteTarget(srcNode, it, currentSnapSeq, overwrite)
                    }
                    driveNodeDao.updateByNodeId(projectId, repoName, srcNode.id!!, ifMatch, updatedNode)
                }
                updatedNode
            }
            invalidateNodeCache(projectId, repoName, ino)
            logger.info(
                "Move drive node[$projectId/$repoName/${srcNode.realIno}] from[${srcNode.parent}/${srcNode.name}] " +
                        "to[$destParent/$destName] success."
            )
            return movedNode.toDriveNode()
        }
    }

    suspend fun update(updateRequest: DriveNodeUpdateRequest, snapSeq: Long? = null): DriveNode {
        with(updateRequest) {
            driveNodeRequestValidator.validateUpdateRequest(updateRequest)
            val srcNode = queryNodeByIno(projectId, repoName, ino, bypassCache = ifMatch != null)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, ino)
            checkIfMatch(ifMatch, srcNode)
            val operator = ReactiveSecurityUtils.getUser()
            val currentSnapSeq = resolveSnapSeq(projectId, repoName, snapSeq)
            val now = LocalDateTime.now()
            val updatedNode = buildUpdatedNode(srcNode, updateRequest, operator, now)
            val savedNode = saveUpdatedNode(srcNode, updatedNode, updateRequest, currentSnapSeq, operator, now)
            invalidateNodeCache(projectId, repoName, ino)
            logger.info("Update drive node[$projectId/$repoName/$ino] success.")
            return savedNode.toDriveNode()
        }
    }

    suspend fun delete(deleteRequest: DriveNodeDeleteRequest, snapSeq: Long? = null): DriveNode {
        with(deleteRequest) {
            driveNodeRequestValidator.validateDeleteRequest(deleteRequest)
            val srcNode = queryNodeByIno(projectId, repoName, ino, bypassCache = ifMatch != null)
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, ino)
            checkIfMatch(ifMatch, srcNode)
            val currentSnapSeq = resolveSnapSeq(projectId, repoName, snapSeq)
            doDelete(srcNode, currentSnapSeq, ifMatch)
            invalidateNodeCache(projectId, repoName, ino)
            logger.info(
                "Delete drive node[$projectId/$repoName/$ino] " +
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
                        DriveNodeBatchOp.RENAME -> moveNode(
                            it.node.toMoveRequest(projectId, repoName),
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
                        message = ExceptionUtils.getMsg(e)
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

    private suspend fun overwriteTarget(
        srcNode: TDriveNode,
        targetNode: TDriveNode,
        currentSnapSeq: Long,
        overwrite: Boolean
    ) {
        val directoryOverwrite = srcNode.type == TYPE_DIRECTORY || targetNode.type == TYPE_DIRECTORY
        if (!overwrite || srcNode.type != targetNode.type && directoryOverwrite) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, targetNode.name)
        }
        if (targetNode.realIno == srcNode.realIno) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_INVALID,
                "${srcNode.name} and ${targetNode.name} are the same file"
            )
        }
        doDelete(targetNode, currentSnapSeq)
        logger.info("delete target node[${srcNode.projectId}/${srcNode.repoName}/${targetNode.ino}]")
        invalidateNodeCache(srcNode.projectId, srcNode.repoName, targetNode.ino)
    }

    private suspend fun doDelete(driveNode: TDriveNode, curSnapSeq: Long, ifMatch: LocalDateTime? = null) {
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
            val result = driveNodeDao.markNodeDeleted(projectId, repoName, nodeId, curSnapSeq, ifMatch)
            if (result.modifiedCount != 1L) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, name)
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
            if (updatedNode.parent != srcNode.parent || updatedNode.name != srcNode.name) {
                checkConflict(updatedNode)
            }
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
            srcNode.projectId,
            srcNode.repoName,
            srcNode.id!!,
            currentSnapSeq,
            updateRequest.ifMatch
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
                srcNode.projectId,
                srcNode.repoName,
                srcNode.id!!,
                updateRequest.ifMatch,
                updatedNode
            )
        } catch (_: DuplicateKeyException) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, updatedNode.name)
        }
        checkUpdateResult(result.modifiedCount, srcNode.name)
        return updatedNode
    }

    private fun checkIfMatch(ifMatch: LocalDateTime?, srcNode: TDriveNode) {
        if (ifMatch != null && ifMatch != srcNode.lastModifiedDate) {
            throw ErrorCodeException(
                status = HttpStatus.PRECONDITION_FAILED,
                messageCode = CommonMessageCode.PRECONDITION_FAILED,
                params = arrayOf("ifMatch"),
            )
        }
    }

    private fun checkUpdateResult(modifiedCount: Long, nodeName: String) {
        if (modifiedCount != 1L) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_CONFLICT, nodeName)
        }
    }

    /**
     * ino 查询短期缓存，用于降低高频读取当前节点时的 Mongo 查询压力。
     * 当请求带 ifMatch 时，建议绕过缓存，避免旧值导致预条件判断误判。
     */
    private suspend fun queryNodeByIno(
        projectId: String,
        repoName: String,
        ino: Long,
        bypassCache: Boolean = false,
    ): TDriveNode? {
        if (bypassCache) {
            return driveNodeDao.findByProjectIdAndRepoNameAndIno(projectId, repoName, ino)
        }
        val cacheKey = NodeCacheKey(projectId, repoName, ino)
        return nodeByInoCache.get(cacheKey).toMono().awaitSingle().orElse(null)
    }

    private fun invalidateNodeCache(projectId: String, repoName: String, ino: Long) {
        nodeByInoCache.synchronous().invalidate(NodeCacheKey(projectId, repoName, ino))
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

    companion object {
        private const val SUCCESS_CODE = 0
        private const val NODE_CACHE_EXPIRE_SECONDS = 3L
        private const val NODE_CACHE_MAX_SIZE = 10_000L
        private val logger = LoggerFactory.getLogger(DriveNodeService::class.java)
    }

    private data class NodeCacheKey(
        val projectId: String,
        val repoName: String,
        val ino: Long,
    )
}
