package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo.Companion.DEFAULT_STREAM_MAPPING_URI
import com.tencent.bkrepo.media.service.StreamService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 流控制器
 * */
@RestController
@RequestMapping("/api/user/stream")
class UserStreamController(
    private val streamService: StreamService,
    private val permissionManager: PermissionManager,
) {

    /**
     * 生成推流地址
     * */
    @PostMapping("/create/{projectId}/{repoName}")
    fun createStream(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam(required = false, defaultValue = "true") display: Boolean = true,
    ): Response<String> {
        permissionManager.checkProjectPermission(
            action = PermissionAction.MANAGE,
            projectId = projectId,
        )
        return ResponseBuilder.success(streamService.createStream(projectId, repoName, display))
    }

    /**
     * 下载流媒体文件
     * */
    @GetMapping(DEFAULT_STREAM_MAPPING_URI)
    @Permission(ResourceType.REPO, PermissionAction.MANAGE)
    fun download(@ArtifactPathVariable artifactInfo: MediaArtifactInfo) {
        streamService.download(artifactInfo)
    }
}
