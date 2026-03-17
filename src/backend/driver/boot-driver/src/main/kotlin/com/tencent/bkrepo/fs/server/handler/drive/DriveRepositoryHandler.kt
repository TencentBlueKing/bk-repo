package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.fs.server.service.drive.DriveRepositoryService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Drive 仓库操作处理器
 */
@Component
class DriveRepositoryHandler(
    private val driveRepositoryService: DriveRepositoryService,
) {

    /**
     * 手动初始化 DRIVE 仓库资源（SnapSeq 和根节点）
     *
     * 该接口为幂等操作，可安全重复调用，通常用于：
     * 1. 事件监听失败后的手动补偿
     * 2. 仓库异常状态的修复
     */
    suspend fun initRepository(request: ServerRequest): ServerResponse {
        val projectId = request.pathVariable(PROJECT_ID)
        val repoName = request.pathVariable(REPO_NAME)
        driveRepositoryService.initDriveRepository(projectId, repoName)
        return ReactiveResponseBuilder.success()
    }
}
