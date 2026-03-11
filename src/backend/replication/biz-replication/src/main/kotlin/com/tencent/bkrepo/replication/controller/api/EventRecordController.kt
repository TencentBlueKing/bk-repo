package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.model.TEventRecord
import com.tencent.bkrepo.replication.pojo.event.EventRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.event.EventRecordListOption
import com.tencent.bkrepo.replication.pojo.event.EventRecordRetryRequest
import com.tencent.bkrepo.replication.service.EventRecordService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 事件记录接口
 */
@Tag(name = "事件记录接口")
@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/replica/event")
class EventRecordController(
    private val eventRecordService: EventRecordService
) {

    @Operation(summary = "分页查询事件记录")
    @GetMapping("/page")
    fun listPage(
        option: EventRecordListOption
    ): Response<Page<TEventRecord>> {
        return ResponseBuilder.success(eventRecordService.listPage(option))
    }

    @Operation(summary = "根据事件ID查询事件记录")
    @GetMapping("/{eventId}")
    fun findByEventId(
        @Parameter(name = "事件ID", required = true)
        @PathVariable eventId: String
    ): Response<TEventRecord?> {
        return ResponseBuilder.success(eventRecordService.findByEventId(eventId))
    }

    @Operation(summary = "重试指定的事件记录")
    @PostMapping("/retry")
    fun retryEventRecord(
        @RequestBody request: EventRecordRetryRequest
    ): Response<Boolean> {
        val success = eventRecordService.retryEventRecord(request)
        return ResponseBuilder.success(success)
    }

    @Operation(summary = "删除事件记录（支持按ID或taskKey删除）")
    @DeleteMapping
    fun deleteEventRecord(
        @RequestBody request: EventRecordDeleteRequest
    ): Response<Boolean> {
        val success = eventRecordService.deleteEventRecord(request)
        return ResponseBuilder.success(success)
    }
}

