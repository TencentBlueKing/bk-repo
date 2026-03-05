package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.model.drive.DriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.toDriveNode
import com.tencent.bkrepo.fs.server.repository.RDriveNodeDao
import com.tencent.bkrepo.fs.server.request.service.DriveNodeCreateRequest
import com.tencent.bkrepo.fs.server.request.service.toDriveNode
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

@Service
class DriveNodeService(
    private val driveNodeDao: RDriveNodeDao,
    private val driveSnapSeqService: DriveSnapSeqService,
    private val driveProperties: DriveProperties,
) {
    suspend fun getNode(projectId: String, repoName: String, parent: String, name: String): DriveNode? {
        validateProjectRepoAndParent(projectId, repoName, parent)
        PathUtils.validateFileName(name)
        val query = Query(DriveNodeQueryHelper.currentCriteria(projectId, repoName, parent, name))
        return driveNodeDao.findOne(query)?.toDriveNode()
    }

    suspend fun listNodes(projectId: String, repoName: String, parent: String): List<DriveNode> {
        validateProjectRepoAndParent(projectId, repoName, parent)
        val query = Query(DriveNodeQueryHelper.listChildrenCriteria(projectId, repoName, parent))
        return driveNodeDao.find(query).map { it.toDriveNode() }
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

        val criteria = DriveNodeQueryHelper.listChildrenCriteria(projectId, repoName, parent)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = if (includeTotalRecords) driveNodeDao.count(Query(criteria)) else 0L
        val records = driveNodeDao.find(Query(criteria).with(pageRequest)).map { it.toDriveNode() }
        return Pages.ofResponse(pageRequest, totalRecords, records)
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

    private suspend fun validateCreateRequest(createRequest: DriveNodeCreateRequest) {
        with(createRequest) {
            validateProjectRepoAndParent(projectId, repoName, parent)
            Preconditions.checkArgument(size >= 0, TDriveNode::size.name)
            Preconditions.checkArgument(type in TDriveNode.ALLOWED_TYPES, TDriveNode::type.name)
            PathUtils.validateFileName(name)
            checkParent(parent, projectId, repoName)
        }
    }

    private fun validateProjectRepoAndParent(projectId: String, repoName: String, parent: String) {
        Preconditions.checkArgument(projectId.isNotBlank(), TDriveNode::projectId.name)
        Preconditions.checkArgument(repoName.isNotBlank(), TDriveNode::repoName.name)
        Preconditions.checkArgument(parent.isNotBlank(), TDriveNode::parent.name)
    }

    /**
     * 检查parent与要创建的driveNode属于同一个仓库
     */
    private suspend fun checkParent(parent: String, projectId: String, repoName: String) {
        val criteria = where(TDriveNode::projectId).isEqualTo(projectId)
            .and(TDriveNode::repoName).isEqualTo(repoName)
            .and(TDriveNode::ino).isEqualTo(parent)
            .and(TDriveNode::deleteSnapSeq).isEqualTo(Long.MAX_VALUE)
            .and(TDriveNode::deleted).isEqualTo(null)
        if (!driveNodeDao.exists(Query(criteria))) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, parent)
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

    private suspend fun doCreate(driveNode: TDriveNode): TDriveNode {
        return try {
            driveNodeDao.insert(driveNode)
        } catch (_: DuplicateKeyException) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, driveNode.name)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveNodeService::class.java)
    }
}
