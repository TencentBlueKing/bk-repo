package com.tencent.bkrepo.replication.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetailListOption
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.record.ReplicaTaskRecordInfo
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 任务执行日志接口
 */
@Api("任务执行日志接口")
@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/task/record")
class ReplicaRecordController(
    private val replicaRecordService: ReplicaRecordService
) {
    @ApiOperation("根据recordId查询任务执行日志详情")
    @GetMapping("/{recordId}")
    fun getRecordAndTaskInfoByRecordId(@PathVariable recordId: String): Response<ReplicaTaskRecordInfo> {
        return ResponseBuilder.success(replicaRecordService.getRecordAndTaskInfoByRecordId(recordId))
    }

    @ApiOperation("根据taskKey查询任务执行日志")
    @GetMapping("/list/{key}")
    fun listRecordsByTaskKey(@PathVariable key: String): Response<List<ReplicaRecordInfo>> {
        return ResponseBuilder.success(replicaRecordService.listRecordsByTaskKey(key))
    }

    @ApiOperation("根据taskKey分页查询任务执行日志")
    @GetMapping("/page/{key}/{pageNumber}/{pageSize}")
    fun listRecordsPage(
        @PathVariable key: String,
        @PathVariable pageNumber: Int,
        @PathVariable pageSize: Int
    ): Response<Page<ReplicaRecordInfo>> {
        return ResponseBuilder.success(replicaRecordService.listRecordsPage(key, pageNumber, pageSize))
    }

    @ApiOperation("根据recordId查询任务执行日志详情")
    @GetMapping("/detail/list/{recordId}")
    fun listDetailsByRecordId(@PathVariable recordId: String): Response<List<ReplicaRecordDetail>> {
        return ResponseBuilder.success(replicaRecordService.listDetailsByRecordId(recordId))
    }

    @ApiOperation("根据recordId分页查询任务执行日志")
    @GetMapping("/detail/page/{recordId}")
    fun listRecordDetailPage(
        @ApiParam(value = "执行记录id", required = true)
        @PathVariable recordId: String,
        option: ReplicaRecordDetailListOption
    ): Response<Page<ReplicaRecordDetail>> {
        return ResponseBuilder.success(replicaRecordService.listRecordDetailPage(recordId, option))
    }
}
