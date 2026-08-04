package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogEntry
import com.tencent.bkrepo.repository.service.clientupgrade.ClientChangelogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "客户端更新日志（只读）")
@RestController
@RequestMapping("/api/client/upgrade/changelog")
class UserClientChangelogController(
    private val clientChangelogService: ClientChangelogService,
) {

    @Principal(PrincipalType.GENERAL)
    @Operation(summary = "查询单版本 changelog")
    @GetMapping
    fun get(
        @RequestParam productId: String,
        @RequestParam version: String,
    ): Response<ClientChangelogEntry?> {
        return ResponseBuilder.success(
            clientChangelogService.findPublishedEntry(
                productId = productId,
                version = version,
            ),
        )
    }

    @Principal(PrincipalType.GENERAL)
    @Operation(summary = "查询 changelog 历史列表")
    @GetMapping("/history")
    fun history(
        @RequestParam productId: String,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<ClientChangelogEntry>> {
        return ResponseBuilder.success(
            clientChangelogService.pagePublishedHistory(
                productId = productId,
                pageNumber = pageNumber,
                pageSize = pageSize,
            ),
        )
    }
}
