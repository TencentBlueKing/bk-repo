package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.stereotype.Service

/**
 * Drive 仓库服务
 */
@Service
class DriveRepositoryService(
    private val driveRepositoryInitService: DriveRepositoryInitService,
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
        driveRepositoryInitService.ensureInitialized(projectId, repoName, ReactiveSecurityUtils.getUser())
    }

    suspend fun initDriveRepository(repo: RepositoryDetail, operator: String) {
        driveRepositoryInitService.ensureInitialized(repo, operator)
    }
}
