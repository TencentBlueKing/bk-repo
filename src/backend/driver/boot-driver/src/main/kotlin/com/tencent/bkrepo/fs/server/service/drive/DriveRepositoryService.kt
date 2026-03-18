package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.impl.RRepositoryServiceImpl
import com.tencent.bkrepo.fs.server.model.drive.TDriveNode
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.utils.DriveNodeQueryHelper.ROOT_INO
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils.toNanoTimestamp
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Drive 仓库服务
 */
@Service
class DriveRepositoryService(
    private val repositoryService: RRepositoryServiceImpl,
    private val driveNodeDao: RDriveNodeDao,
    private val driveSnapSeqService: DriveSnapSeqService,
) {

    /**
     * 初始化 DRIVE 仓库
     *
     * 确保 DRIVE 仓库的 SnapSeq 和根节点已创建。该方法为幂等操作，可安全重复调用。
     *
     * @param projectId 项目 ID
     * @param repoName 仓库名称
     */
    suspend fun initDriveRepository(projectId: String, repoName: String) {
        val repo = repositoryService.getRepoDetail(projectId, repoName)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, "$projectId/$repoName")
        initDriveRepository(repo, ReactiveSecurityUtils.getUser())
    }

    suspend fun initDriveRepository(repo: RepositoryDetail, operator: String) {
        with(repo) {
            checkDriveType(projectId, name, repo.type)
            driveSnapSeqService.createSnapSeq(projectId, name)
            ensureRootNode(projectId, name, operator)
            logger.info("Initialize drive repository[$projectId/$name] success.")
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
