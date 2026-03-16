package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.impl.RRepositoryServiceImpl
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.repository.RDriveNodeDao
import com.tencent.bkrepo.fs.server.request.drive.DriveRepoCreateRequest
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils.toNanoTimestamp
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Drive 仓库服务
 *
 * 仅支持创建/初始化 DRIVE 类型仓库。
 */
@Service
class DriveRepositoryService(
    private val repositoryService: RRepositoryServiceImpl,
    private val driveNodeDao: RDriveNodeDao,
    private val driveSnapSeqService: DriveSnapSeqService,
) {
    suspend fun createRepository(request: DriveRepoCreateRequest): RepositoryDetail {
        val operator = ReactiveSecurityUtils.getUser()
        val repository = repositoryService.getRepoDetail(request.projectId, request.name)?.also {
            checkDriveType(request.projectId, request.name, it.type)
        } ?: repositoryService.createRepo(
            RepoCreateRequest(
                projectId = request.projectId,
                name = request.name,
                type = RepositoryType.DRIVE,
                category = RepositoryCategory.LOCAL,
                public = false,
                operator = operator,
                configuration = request.configuration,
                storageCredentialsKey = request.storageCredentialsKey,
                quota = request.quota,
                description = request.description,
                display = true,
            )
        )
        ensureDriveRepositoryInitialized(request.projectId, request.name, operator)
        return repository
    }

    private suspend fun ensureDriveRepositoryInitialized(projectId: String, repoName: String, operator: String) {
        driveSnapSeqService.createSnapSeq(projectId, repoName)
        ensureRootNode(projectId, repoName, operator)
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
            // 并发初始化时允许其他请求先完成写入。
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
        private const val ROOT_INO = 2L
        private const val ROOT_NAME = ""
        private const val ROOT_SIZE = 0L
        private const val ROOT_MODE = 16877
        private const val ROOT_NLINK = 2
        private const val ROOT_UID = 0
        private const val ROOT_GID = 0
        private const val ROOT_RDEV = 0
        private const val ROOT_FLAGS = 0
        private val logger = LoggerFactory.getLogger(DriveRepositoryService::class.java)
    }
}
