package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import com.tencent.bkrepo.replication.pojo.tracking.FederationMetadataTrackingDeleteRequest
import com.tencent.bkrepo.replication.pojo.tracking.FederationMetadataTrackingListOption
import com.tencent.bkrepo.replication.pojo.tracking.FederationMetadataTrackingRetryRequest
import com.tencent.bkrepo.replication.service.FederationMetadataTrackingService
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
 * 联邦元数据跟踪接口
 */
@Tag(name = "联邦元数据跟踪接口")
@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/federation/tracking")
class FederationMetadataTrackingController(
    private val federationMetadataTrackingService: FederationMetadataTrackingService
) {

    @Operation(summary = "分页查询联邦元数据跟踪记录")
    @GetMapping("/page")
    fun listPage(
        option: FederationMetadataTrackingListOption
    ): Response<Page<TFederationMetadataTracking>> {
        return ResponseBuilder.success(federationMetadataTrackingService.listPage(option))
    }

    @Operation(summary = "根据ID查询联邦元数据跟踪记录")
    @GetMapping("/{id}")
    fun findById(
        @Parameter(name = "记录ID", required = true)
        @PathVariable id: String
    ): Response<TFederationMetadataTracking?> {
        return ResponseBuilder.success(federationMetadataTrackingService.findById(id))
    }

    @Operation(summary = "根据条件删除联邦元数据跟踪记录")
    @DeleteMapping("/delete")
    fun deleteByConditions(
        @RequestBody request: FederationMetadataTrackingDeleteRequest
    ): Response<Long> {
        val deletedCount = federationMetadataTrackingService.deleteByConditions(request)
        return ResponseBuilder.success(deletedCount)
    }

    @Operation(summary = "重试指定的联邦元数据跟踪记录")
    @PostMapping("/retry")
    fun retryRecord(
        @RequestBody request: FederationMetadataTrackingRetryRequest
    ): Response<Boolean> {
        val success = federationMetadataTrackingService.retryRecord(request)
        return ResponseBuilder.success(success)
    }
}

