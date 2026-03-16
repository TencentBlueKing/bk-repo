package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.fs.server.request.drive.DriveRepoCreateRequest
import com.tencent.bkrepo.fs.server.service.drive.DrivePermissionManager
import com.tencent.bkrepo.fs.server.service.drive.DriveRepositoryService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Drive 仓库操作处理器
 */
@Component
class DriveRepositoryHandler(
    private val drivePermissionManager: DrivePermissionManager,
    private val driveRepositoryService: DriveRepositoryService,
) {
    suspend fun createRepository(request: ServerRequest): ServerResponse {
        val body = request.bodyToMono(DriveRepoCreateRequest::class.java).awaitSingle()
        val user = ReactiveSecurityUtils.getUser()
        drivePermissionManager.checkRepoPermission(body.projectId, body.name, PermissionAction.WRITE, user)
        val repository = driveRepositoryService.createRepository(body)
        return ReactiveResponseBuilder.success(repository)
    }
}
