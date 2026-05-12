package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.impl.RRepositoryServiceImpl
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapSeq
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.repository.drive.RDriveSnapSeqDao
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper.ROOT_INO
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils.toNanoTimestamp
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Drive 仓库初始化服务
 */
@Service
class DriveRepositoryInitService(
    private val repositoryService: RRepositoryServiceImpl,
    private val driveNodeDao: RDriveNodeDao,
    private val driveSnapSeqDao: RDriveSnapSeqDao,
) {

    suspend fun ensureInitialized(projectId: String, repoName: String) {
        ensureInitialized(projectId, repoName, DriveServiceUtils.getUserOrSystem())
    }

    suspend fun ensureInitialized(projectId: String, repoName: String, operator: String) {
        val repo = repositoryService.getRepoDetail(projectId, repoName)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, "$projectId/$repoName")
        ensureInitialized(repo, operator)
    }

    suspend fun ensureInitialized(repo: RepositoryDetail, operator: String) {
        with(repo) {
            checkDriveType(projectId, name, type)
            ensureSnapSeq(projectId, name, operator)
            ensureRootNode(projectId, name, operator)
            logger.info("Initialize drive repository[$projectId/$name] success.")
        }
    }

    private suspend fun ensureSnapSeq(projectId: String, repoName: String, operator: String) {
        val now = LocalDateTime.now()
        val snapSeq = TDriveSnapSeq(
            id = null,
            createdBy = operator,
            createdDate = now,
            lastModifiedBy = operator,
            lastModifiedDate = now,
            projectId = projectId,
            repoName = repoName,
            snapSeq = 0L,
        )
        try {
            driveSnapSeqDao.insert(snapSeq)
            logger.info("Initialize drive snapSeq[$projectId/$repoName] success.")
        } catch (_: DuplicateKeyException) {
            logger.info("Drive snapSeq[$projectId/$repoName] already initialized.")
        }
    }

    private suspend fun ensureRootNode(projectId: String, repoName: String, operator: String) {
        if (driveNodeDao.findByProjectIdAndRepoNameAndIno(projectId, repoName, ROOT_INO) != null) {
            return
        }
        val now = LocalDateTime.now()
        val nowTimestamp = toNanoTimestamp(now)
        val rootNode = TDriveNode(
            createdBy = operator,
            createdDate = now,
            lastModifiedBy = operator,
            lastModifiedDate = now,
            mtime = nowTimestamp,
            ctime = nowTimestamp,
            atime = nowTimestamp,
            projectId = projectId,
            repoName = repoName,
            ino = ROOT_INO,
            targetIno = null,
            parent = null,
            name = ROOT_NAME,
            size = ROOT_SIZE,
            mode = ROOT_MODE,
            type = TDriveNode.TYPE_DIRECTORY,
            nlink = ROOT_NLINK,
            uid = ROOT_UID,
            gid = ROOT_GID,
            rdev = ROOT_RDEV,
            flags = ROOT_FLAGS,
            symlinkTarget = null,
            snapSeq = 0,
        )
        try {
            driveNodeDao.insert(rootNode)
            logger.info("Initialize drive root node[$projectId/$repoName/$ROOT_INO] success.")
        } catch (_: DuplicateKeyException) {
            logger.info("Drive root node[$projectId/$repoName/$ROOT_INO] already initialized.")
        }
    }

    private fun checkDriveType(projectId: String, repoName: String, type: RepositoryType) {
        if (type != RepositoryType.DRIVE) {
            throw ErrorCodeException(
                CommonMessageCode.METHOD_NOT_ALLOWED,
                "Repository[$projectId/$repoName] type[$type] is not DRIVE.",
            )
        }
    }

    companion object {
        private const val ROOT_NAME = ""
        private const val ROOT_SIZE = 0L
        private const val ROOT_MODE = 16877
        private const val ROOT_NLINK = 2
        private const val ROOT_UID = 0
        private const val ROOT_GID = 0
        private const val ROOT_RDEV = 0
        private const val ROOT_FLAGS = 0
        private val logger = LoggerFactory.getLogger(DriveRepositoryInitService::class.java)
    }
}
