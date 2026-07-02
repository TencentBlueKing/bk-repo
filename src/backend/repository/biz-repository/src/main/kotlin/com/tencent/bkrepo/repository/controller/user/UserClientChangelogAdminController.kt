package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogListOption
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogUpsertRequest
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogVo
import com.tencent.bkrepo.repository.service.clientupgrade.ClientChangelogService
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

@Tag(name = "客户端更新日志（管理端）")
@Principal(PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/client/upgrade/changelog")
class UserClientChangelogAdminController(
    private val clientChangelogService: ClientChangelogService,
) {

    @Operation(summary = "分页查询 changelog（多条件）")
    @PostMapping("/list")
    fun listPage(
        @RequestBody option: ClientChangelogListOption,
    ): Response<Page<ClientChangelogVo>> {
        return ResponseBuilder.success(clientChangelogService.listPage(option))
    }

    @Operation(summary = "按 ID 查询 changelog 详情")
    @GetMapping("/detail/{id}")
    fun detail(
        @PathVariable id: String,
    ): Response<ClientChangelogVo> {
        return ResponseBuilder.success(clientChangelogService.getById(id))
    }

    @Operation(summary = "按唯一键查询 changelog 详情")
    @GetMapping("/detail")
    fun detailByKey(
        @RequestParam productId: String,
        @RequestParam version: String,
    ): Response<ClientChangelogVo> {
        return ResponseBuilder.success(
            clientChangelogService.getByKey(productId, version),
        )
    }

    @Operation(summary = "新增或更新 changelog")
    @PostMapping("/upsert")
    fun upsert(
        @RequestAttribute userId: String,
        @RequestBody request: ClientChangelogUpsertRequest,
    ): Response<ClientChangelogVo> {
        return ResponseBuilder.success(clientChangelogService.upsert(userId, request))
    }

    @Operation(summary = "删除 changelog（软删除）")
    @DeleteMapping("/{id}")
    fun remove(
        @RequestAttribute userId: String,
        @PathVariable id: String,
    ): Response<Void> {
        clientChangelogService.remove(userId, id)
        return ResponseBuilder.success()
    }
}
