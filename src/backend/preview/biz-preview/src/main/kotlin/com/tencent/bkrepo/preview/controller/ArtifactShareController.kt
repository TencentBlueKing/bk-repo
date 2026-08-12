package com.tencent.bkrepo.preview.controller

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareBatchStatusRequest
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareInfo
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareOpenInfo
import com.tencent.bkrepo.preview.pojo.share.ArtifactSharePage
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareStatusItem
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareSummary
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareRenameRequest
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareUpsertRequest
import com.tencent.bkrepo.preview.service.share.ArtifactSharePreviewPage
import com.tencent.bkrepo.preview.service.share.ArtifactShareService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 作品分享接口，当前实现为素材分享，站点分享后续扩展。
 *
 * `/mine`、`/accessible`、`/{shareId}/open` 以分享权限为独立访问域，不校验仓库 READ。
 */
@Tag(name = "作品分享")
@RestController
@RequestMapping("/api/artifact/share")
@Principal(PrincipalType.GENERAL)
class ArtifactShareController(
    private val artifactShareService: ArtifactShareService,
) {

    @Operation(summary = "创建或更新作品分享")
    @PostMapping
    fun upsert(@RequestBody request: ArtifactShareUpsertRequest): Response<ArtifactShareInfo> {
        return ResponseBuilder.success(artifactShareService.upsert(SecurityUtils.getUserId(), request))
    }

    @Operation(summary = "按资源 ID 查询当前有效分享")
    @GetMapping("/by-resource-id")
    fun getByResourceId(
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam resourceId: Long,
    ): Response<ArtifactShareInfo?> {
        return ResponseBuilder.success(
            artifactShareService.getByResourceId(SecurityUtils.getUserId(), projectId, repoName, resourceId),
        )
    }

    @Operation(summary = "批量查询分享状态")
    @PostMapping("/status/batch")
    fun batchStatus(
        @RequestBody request: ArtifactShareBatchStatusRequest,
    ): Response<List<ArtifactShareStatusItem>> {
        return ResponseBuilder.success(artifactShareService.batchStatus(SecurityUtils.getUserId(), request))
    }

    @Operation(summary = "我创建的作品分享列表（游标分页）")
    @GetMapping("/mine")
    fun listMine(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): Response<ArtifactSharePage<ArtifactShareInfo>> {
        return ResponseBuilder.success(
            artifactShareService.listMine(SecurityUtils.getUserId(), keyword, cursor, limit),
        )
    }

    @Operation(summary = "我有权限访问的作品列表（公开+指定用户+指定组织，游标分页）")
    @GetMapping("/accessible")
    fun listAccessible(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) featured: Boolean?,
    ): Response<ArtifactSharePage<ArtifactShareSummary>> {
        return ResponseBuilder.success(
            artifactShareService.listAccessible(
                SecurityUtils.getUserId(),
                keyword,
                cursor,
                limit,
                channel,
                featured,
            ),
        )
    }

    @Operation(summary = "重命名作品分享")
    @PutMapping("/{shareId}/name")
    fun rename(
        @PathVariable shareId: String,
        @RequestBody request: ArtifactShareRenameRequest,
    ): Response<ArtifactShareInfo> {
        return ResponseBuilder.success(
            artifactShareService.rename(SecurityUtils.getUserId(), shareId, request.artifactName),
        )
    }

    @Operation(summary = "撤销分享（真实删除，幂等）")
    @DeleteMapping("/{shareId}")
    fun revoke(@PathVariable shareId: String): Response<Void> {
        artifactShareService.revoke(SecurityUtils.getUserId(), shareId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "打开分享（返回预览/下载地址）")
    @GetMapping("/{shareId}/open")
    fun open(@PathVariable shareId: String): Response<ArtifactShareOpenInfo> {
        return ResponseBuilder.success(artifactShareService.open(SecurityUtils.getUserId(), shareId))
    }

    @Operation(summary = "短链打开：校验权限后返回内嵌预览页，地址栏保持 /a/{shareId}")
    @GetMapping("/a/{shareId}", produces = [MediaType.TEXT_HTML_VALUE])
    fun openRedirect(
        @PathVariable shareId: String,
        response: HttpServletResponse,
    ) {
        val openInfo = artifactShareService.open(SecurityUtils.getUserId(), shareId)
        response.contentType = "${MediaType.TEXT_HTML_VALUE};charset=UTF-8"
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        val title = openInfo.share.artifactName?.trim().orEmpty()
        response.writer.write(ArtifactSharePreviewPage.render(openInfo.previewUrl, title))
    }
}
