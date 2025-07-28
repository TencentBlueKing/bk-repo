package com.tencent.bkrepo.repository.controller.user

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleLoadCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleResult
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleType
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleUpdateRequest
import com.tencent.bkrepo.repository.service.schedule.ScheduleLoadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "预约下载接口")
@RestController
@RequestMapping("/api/schedule")
class ScheduleLoadController(
    private val scheduleLoadService: ScheduleLoadService,
    private val permissionManager: PermissionManager
) {
    @Operation(summary = "创建或更新预约下载任务")
    @PostMapping("/create")
    fun createScheduleLoadTask(
        @RequestAttribute userId: String,
        @RequestBody request: ScheduleRequest
    ): Response<Void> {
        with(request) {
            var userIdParams = userId
            if (type == ScheduleType.USER) {
                permissionManager.checkRepoPermission(PermissionAction.VIEW, projectId, repoName)
            } else {
                userIdParams = ANONYMOUS_USER
                permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId)
            }
            val createRequest = ScheduleLoadCreateRequest(
                userId = userIdParams,
                projectId = projectId,
                repoName = repoName,
                fullPathRegex = fullPathRegex,
                nodeMetadata = nodeMetadata,
                cronExpression = cronExpression,
                isCovered = isCovered,
                isEnabled = isEnabled,
                platform = platform,
                type = type,
            )
            scheduleLoadService.createScheduleLoad(createRequest)
            return ResponseBuilder.success()
        }
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/delete/{id}")
    fun removeSchedule(
        @RequestAttribute userId: String,
        @PathVariable id: String,
    ): Response<DeleteResult> {
        scheduleLoadService.removeScheduleLoad(id)
        return ResponseBuilder.success(scheduleLoadService.removeScheduleLoad(id))
    }

    @Operation(summary = "更新任务状态")
    @PutMapping("/update/{id}")
    fun updateScheduleStatus(
        @RequestAttribute userId: String,
        @PathVariable id: String,
        @RequestBody request: ScheduleUpdateRequest
    ): Response<Void> {
        scheduleLoadService.getScheduleLoadById(id)?.let {
            scheduleLoadService.updateScheduleStatus(id, request.isEnabled)
            return ResponseBuilder.success()
        }
        return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "id not existed")
    }

    @Operation(summary = "查询预约下载任务")
    @PostMapping("/query")
    fun pageScheduleLoadTask(
        @RequestAttribute userId: String,
        @RequestBody request: ScheduleQueryRequest
    ): Response<Page<ScheduleResult>> {
        return ResponseBuilder.success(scheduleLoadService.queryScheduleLoad(userId, request))
    }
}
