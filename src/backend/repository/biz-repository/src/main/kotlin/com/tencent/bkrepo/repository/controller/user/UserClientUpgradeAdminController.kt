package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigUpsertRequest
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientVersionConfigVo
import com.tencent.bkrepo.repository.service.clientupgrade.ClientVersionConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "客户端版本配置")
@Principal(PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/client/upgrade")
class UserClientUpgradeAdminController(
    private val clientVersionConfigService: ClientVersionConfigService,
) {

    @Operation(summary = "按制品产品线查询版本配置列表")
    @GetMapping("/list")
    fun listPage(
        @RequestParam(required = false) productId: String?,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<ClientVersionConfigVo>> {
        return ResponseBuilder.success(clientVersionConfigService.listPage(productId, pageNumber, pageSize))
    }

    @Operation(summary = "新增或更新版本配置")
    @PostMapping("/upsert")
    fun upsert(
        @RequestAttribute userId: String,
        @RequestBody request: ClientVersionConfigUpsertRequest,
    ): Response<Void> {
        clientVersionConfigService.upsert(userId, request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "批量新增或更新版本配置（上限 50 条）")
    @PostMapping("/upsert/batch")
    fun batchUpsert(
        @RequestAttribute userId: String,
        @RequestBody requests: List<ClientVersionConfigUpsertRequest>,
    ): Response<Void> {
        clientVersionConfigService.batchUpsert(userId, requests)
        return ResponseBuilder.success()
    }

    @Operation(summary = "删除版本配置")
    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String): Response<Void> {
        clientVersionConfigService.remove(id)
        return ResponseBuilder.success()
    }

    @Operation(summary = "批量删除版本配置（上限 50 条）")
    @DeleteMapping("/batch")
    fun batchRemove(@RequestBody ids: List<String>): Response<Void> {
        clientVersionConfigService.batchRemove(ids)
        return ResponseBuilder.success()
    }
}
