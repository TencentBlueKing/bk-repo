package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleLoadCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleResult
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
import java.time.LocalDateTime

@Tag(name = "预约下载接口")
@RestController
@RequestMapping("/api/schedule")
class ScheduleLoadController(
    private val scheduleLoadService: ScheduleLoadService
) {
    @Operation(summary = "创建或更新预约下载任务")
    @PostMapping("/save")
    fun saveScheduleLoadTask(
        @RequestAttribute userId: String,
        @RequestBody request: ScheduleRequest
    ): Response<Void> {
        with(request) {
            val createRequest = ScheduleLoadCreateRequest(
                userId = userId,
                projectId = projectId,
                pipeLineId = pipeLineId,
                buildId = buildId,
                cronExpression = cronExpression,
                isEnabled = isEnabled,
                platform = platform,
                rules = rules,
                createdDate = LocalDateTime.now(),
                lastModifiedDate = LocalDateTime.now(),
            )
            scheduleLoadService.saveScheduleLoad(createRequest)
            return ResponseBuilder.success()
        }
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/delete/{id}")
    fun removeSchedule(
        @RequestAttribute userId: String,
        @PathVariable id: String
    ): Response<Void> {
        scheduleLoadService.getScheduleLoadById(id)?.let {
            scheduleLoadService.removeScheduleLoad(id)
            return ResponseBuilder.success()
        }
        return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "id not existed")
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
