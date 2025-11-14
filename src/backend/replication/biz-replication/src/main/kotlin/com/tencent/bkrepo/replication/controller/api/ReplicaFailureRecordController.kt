package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordListOption
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordRetryRequest
import com.tencent.bkrepo.replication.service.ReplicaFailureRecordService
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
 * 同步失败记录接口
 */
@Tag(name = "同步失败记录接口")
@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/replica/failure")
class ReplicaFailureRecordController(
    private val replicaFailureRecordService: ReplicaFailureRecordService
) {

    @Operation(summary = "分页查询同步失败记录")
    @GetMapping("/page")
    fun listPage(
        option: ReplicaFailureRecordListOption
    ): Response<Page<TReplicaFailureRecord>> {
        return ResponseBuilder.success(replicaFailureRecordService.listPage(option))
    }

    @Operation(summary = "根据ID查询同步失败记录")
    @GetMapping("/{id}")
    fun findById(
        @Parameter(name = "记录ID", required = true)
        @PathVariable id: String
    ): Response<TReplicaFailureRecord?> {
        return ResponseBuilder.success(replicaFailureRecordService.findById(id))
    }

    @Operation(summary = "根据条件删除同步失败记录")
    @DeleteMapping("/delete")
    fun deleteByConditions(
        @RequestBody request: ReplicaFailureRecordDeleteRequest
    ): Response<Long> {
        val deletedCount = replicaFailureRecordService.deleteByConditions(request)
        return ResponseBuilder.success(deletedCount)
    }

    @Operation(summary = "重试指定的同步失败记录")
    @PostMapping("/retry")
    fun retryRecord(
        @RequestBody request: ReplicaFailureRecordRetryRequest
    ): Response<Boolean> {
        val success = replicaFailureRecordService.retryRecord(request)
        return ResponseBuilder.success(success)
    }
}

