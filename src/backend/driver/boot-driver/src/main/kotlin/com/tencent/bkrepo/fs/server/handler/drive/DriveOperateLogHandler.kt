package com.tencent.bkrepo.fs.server.handler.drive

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.fs.server.request.drive.DriveOpLogPageRequest
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogService
import com.tencent.bkrepo.fs.server.utils.ReactiveResponseBuilder
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * Drive 操作审计日志查询处理器
 */
@Component
class DriveOperateLogHandler(
    private val driveOperateLogService: DriveOperateLogService,
    private val permissionService: PermissionService,
) {

    /**
     * 分页查询 Drive 操作审计日志
     */
    suspend fun page(request: ServerRequest): ServerResponse {
        val pageRequest = DriveOpLogPageRequest(request)
        checkManagePermission(pageRequest.projectId, pageRequest.repoName)
        val page = driveOperateLogService.page(pageRequest)
        return ReactiveResponseBuilder.success(page)
    }

    private suspend fun checkManagePermission(projectId: String, repoName: String) {
        val userId = ReactiveSecurityUtils.getUser()
        if (!permissionService.checkPermission(projectId, repoName, PermissionAction.MANAGE, userId)) {
            throw PermissionException()
        }
    }
}
