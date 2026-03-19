package com.tencent.bkrepo.fs.server.utils

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode.Companion.TYPE_DIRECTORY
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeBaseRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeCreateRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeDeleteRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeMoveRequest
import com.tencent.bkrepo.fs.server.request.drive.DriveNodeUpdateRequest
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component

@Component
class DriveNodeRequestValidator(
    private val driveNodeDao: RDriveNodeDao,
    private val driveProperties: DriveProperties,
) {
    suspend fun validateCreateRequest(createRequest: DriveNodeCreateRequest) {
        with(createRequest) {
            DriveServiceUtils.validateProjectRepoAndParent(projectId, repoName, parent)
            Preconditions.checkArgument(ino >= DriveNodeQueryHelper.ROOT_INO, DriveNodeCreateRequest::ino.name)
            Preconditions.checkArgument(type in TDriveNode.ALLOWED_TYPES, TDriveNode::type.name)
            if (type == TDriveNode.TYPE_SYMLINK) {
                Preconditions.checkArgument(!symlinkTarget.isNullOrBlank(), TDriveNode::symlinkTarget.name)
            } else {
                Preconditions.checkArgument(symlinkTarget == null, TDriveNode::symlinkTarget.name)
            }
            mtime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::mtime.name) }
            ctime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::ctime.name) }
            atime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::atime.name) }
            validateCommonFields(createRequest)
        }
    }

    suspend fun validateMoveRequest(moveRequest: DriveNodeMoveRequest) {
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

    fun validateDeleteRequest(deleteRequest: DriveNodeDeleteRequest) {
        with(deleteRequest) {
            DriveServiceUtils.validateProjectRepo(projectId, repoName)
            Preconditions.checkArgument(ino >= DriveNodeQueryHelper.ROOT_INO, DriveNodeDeleteRequest::ino.name)
        }
    }

    suspend fun validateUpdateRequest(updateRequest: DriveNodeUpdateRequest) {
        with(updateRequest) {
            DriveServiceUtils.validateProjectRepo(projectId, repoName)
            Preconditions.checkArgument(ino >= DriveNodeQueryHelper.ROOT_INO, DriveNodeUpdateRequest::ino.name)
            mtime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::mtime.name) }
            ctime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::ctime.name) }
            atime?.let { Preconditions.checkArgument(it >= 0, TDriveNode::atime.name) }
            validateCommonFields(updateRequest)
        }
    }

    private suspend fun validateCommonFields(request: DriveNodeBaseRequest) {
        with(request) {
            size?.let { Preconditions.checkArgument(it >= 0, TDriveNode::size.name) }
            mode?.let { Preconditions.checkArgument(it >= 0, TDriveNode::mode.name) }
            nlink?.let { Preconditions.checkArgument(it >= 0, TDriveNode::nlink.name) }
            uid?.let { Preconditions.checkArgument(it >= 0, TDriveNode::uid.name) }
            gid?.let { Preconditions.checkArgument(it >= 0, TDriveNode::gid.name) }
            rdev?.let { Preconditions.checkArgument(it >= 0, TDriveNode::rdev.name) }
            flags?.let { Preconditions.checkArgument(it >= 0, TDriveNode::flags.name) }
            parent?.let { checkParent(it, projectId, repoName) }
            name?.let {
                PathUtils.validateFileName(it)
                DriveServiceUtils.validateLength(it, TDriveNode::name.name, driveProperties.nameMaxLength)
            }
            symlinkTarget?.let {
                Preconditions.checkArgument(it.isNotBlank(), TDriveNode::symlinkTarget.name)
                DriveServiceUtils.validateLength(
                    it,
                    TDriveNode::symlinkTarget.name,
                    driveProperties.descriptionMaxLength
                )
            }
        }
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
}
