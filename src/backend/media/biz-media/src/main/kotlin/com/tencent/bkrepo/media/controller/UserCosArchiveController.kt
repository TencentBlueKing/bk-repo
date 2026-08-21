package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.media.artifact.CosArchiveArtifactInfo
import com.tencent.bkrepo.media.artifact.CosArchiveArtifactInfo.Companion.COS_ARCHIVE_MAPPING_URI
import com.tencent.bkrepo.media.service.CosArchiveService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * COS 归档用户接口
 */
@Tag(name = "媒体 COS 归档用户接口")
@RestController
@RequestMapping("/api/user/cos")
class UserCosArchiveController(
    private val cosArchiveService: CosArchiveService,
) {

    /**
     * 下载 COS 归档录屏，登录态 + 仓库权限校验，不依赖临时 token。
     * 存储层按仓库凭证自动解密。
     */
    @Operation(summary = "下载 COS 归档文件")
    @Permission(ResourceType.REPO, PermissionAction.MANAGE)
    @GetMapping(COS_ARCHIVE_MAPPING_URI)
    fun download(@ArtifactPathVariable artifactInfo: CosArchiveArtifactInfo) {
        cosArchiveService.download(artifactInfo)
    }
}
