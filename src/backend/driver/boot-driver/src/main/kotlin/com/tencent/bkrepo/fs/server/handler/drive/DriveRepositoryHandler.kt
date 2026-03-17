package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.fs.server.readBody
import com.tencent.bkrepo.fs.server.request.drive.UserDriveRepoCreateRequest
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
    suspend fun createRepository(request: ServerRequest): ServerResponse {
        val body = request.readBody(UserDriveRepoCreateRequest::class.java)
        val projectId = request.pathVariable(PROJECT_ID)
        val repoName = request.pathVariable(REPO_NAME)
        val repository = driveRepositoryService.createRepository(body.toReq(projectId, repoName))
        return ReactiveResponseBuilder.success(repository)
    }
}
